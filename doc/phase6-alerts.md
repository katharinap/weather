# Phase 6: Weather Alerts (DWD Warnings)

**Goal:** show official DWD warnings for the selected place: a banner on Home, a detail screen, and optional notifications for severe warnings. Alerts follow the Phase 5 pattern: fetched into the Room cache, observed from there.

Two commits: **6a** data, cache, Home banner and detail screen, **6b** notifications.

## Decisions

| Topic | Decision |
|---|---|
| Endpoint | Bright Sky `/alerts?lat=&lon=&tz=Europe/Berlin`. It resolves the coordinates to a DWD warn cell and returns all warnings for it, or an empty list. Outside Germany it answers 404, like the other endpoints. |
| Cache | A third `kind = "alerts"` in the existing `weather_cache` table. **No schema change** for the cache. |
| Freshness | Foreground refresh when alerts are older than **15 min** (same as current weather); the hourly worker refreshes them too |
| Text language | English fields (`*_en`), falling back to German (`*_de`) when the English one is missing |
| Severity | `minor` / `moderate` / `severe` / `extreme` (DWD: yellow, orange, red, violet), mapped case-insensitively with an `UNKNOWN` fallback |
| "No warnings" | Never shown without a timestamp. Three states: **Unknown** (never loaded, or unavailable), **Known** with an age, **Stale** (older than 60 min). Absence of warnings from stale data must not read as "all clear". |
| Notifications | Opt-in (default off), selected place only, severity ≥ `severe` by default (constant for now, setting in Phase 7), deduplicated, runs inside the existing worker |
| Time handling | `onset` and `expires` are ISO-8601 with offset. Active/upcoming/expired is **derived at render time** with the minute ticker, like the forecast hours. |

New dependency: none. Notifications need the `POST_NOTIFICATIONS` runtime permission on Android 13+ (so on your Android 15 phone), requested only when you switch notifications on.

**Honest limit for 6b:** WorkManager runs about hourly and not exactly on time, so a notification can arrive up to an hour or more after DWD issues a warning. It's a convenience, not a safety system. For time-critical warnings keep the official DWD WarnWetter app or NINA installed.

## Fixtures first

Find a place with active warnings (they're common, but not guaranteed on a given day):

```bash
for c in "48.137,11.575" "53.55,9.99" "47.49,11.10" "54.32,10.14" "50.11,8.68" "51.34,12.37"; do
  echo "== $c"
  curl -s "https://api.brightsky.dev/alerts?lat=${c%,*}&lon=${c#*,}&tz=Europe/Berlin" \
    | python3 -c "import json,sys; d=json.load(sys.stdin); print(len(d['alerts']), 'alerts')"
done
```

Save the full response of one with alerts, plus one with none:

```bash
curl -s "https://api.brightsky.dev/alerts?lat=<lat>&lon=<lon>&tz=Europe/Berlin" -o app/src/test/resources/alerts_active.json
curl -s "https://api.brightsky.dev/alerts?lat=48.137&lon=11.575&tz=Europe/Berlin" -o app/src/test/resources/alerts_none.json
```

If no place has a warning today, save one empty response and **let your AI write a synthetic `alerts_active.json` clearly named `alerts_synthetic.json`** based on the shape below. Replace it with a real one the next time a warning is active.

### Verified against your fixtures

`alerts_active.json` (Frankfurt-Nord, one frost warning) and `alerts_none.json` (Munich, empty list) confirm the shape:

- Top level: `alerts` (list) and `location` (`warn_cell_id`, `name`, `name_short`, `district`, `state`, `state_short`). Use `Long` for `warn_cell_id`.
- Per alert: `id` (a number: **don't use it as the key**), `alert_id` (the stable string, **use this**), `status`, `effective`, `onset`, `expires`, `category`, `response_type`, `urgency`, `severity`, `certainty`, `event_code`, `event_en`/`event_de`, `headline_en`/`headline_de`, `description_en`/`description_de`, `instruction_en`/`instruction_de`.
- `severity` is lowercase (`"minor"`); still parse case-insensitively.
- **Upcoming alerts are returned.** This one has `onset` tomorrow 00:00 and was fetched today, so at fetch time it is *not yet active*. Treat "upcoming" as a normal case, not an edge case.
- Timestamps carry the local offset (`+01:00` now that daylight saving time has ended). Parse with `OffsetDateTime`.
- Text contains literal line breaks (`\n`) and a trailing space in the instruction. Keep the line breaks when displaying, and trim.
- `event_en` is lowercase ("frost"): capitalize the first letter for display. `headline_en` is DWD's official wording ("Official WARNING of FROST"): show it as delivered.
- `status` is `"actual"` here. CAP also defines other values (such as test messages), so **ignore alerts whose `status` isn't `actual`** (keep them if `status` is null).
- Fields that may still be `null` in other alerts (not seen in this sample): `expires`, `instruction_*`, `description_*`. Keep every field except `alert_id` nullable.

## 6a: Data, cache, Home banner, detail screen

### Domain

```kotlin
enum class AlertSeverity { MINOR, MODERATE, SEVERE, EXTREME, UNKNOWN;
    companion object {
        fun parse(value: String?) = when (value?.lowercase()) {
            "minor" -> MINOR; "moderate" -> MODERATE; "severe" -> SEVERE; "extreme" -> EXTREME
            else -> UNKNOWN
        }
    }
}

data class WeatherAlert(
    val id: String,                 // alert_id
    val eventCode: Int?,
    val event: String,              // event_en ?: event_de ?: "Weather warning"
    val headline: String?,
    val description: String?,
    val instruction: String?,
    val severity: AlertSeverity,
    val effective: Instant?,
    val onset: Instant?,
    val expires: Instant?,
)

data class AlertArea(val name: String?, val district: String?, val state: String?)   // from `location`
data class AlertsResult(val alerts: List<WeatherAlert>, val area: AlertArea?)

data class VisibleAlerts(val active: List<WeatherAlert>, val upcoming: List<WeatherAlert>)

/** Expired alerts are dropped; both lists sorted by severity (desc), then onset. */
fun List<WeatherAlert>.visibleAt(now: Instant): VisibleAlerts {
    val live = filter { it.expires == null || it.expires > now }
    val order = compareByDescending<WeatherAlert> { it.severity.ordinal.takeIf { s -> s != AlertSeverity.UNKNOWN.ordinal } ?: -1 }
        .thenBy { it.onset ?: Instant.MIN }
    val (upcoming, active) = live.partition { it.onset != null && it.onset > now }
    return VisibleAlerts(active.sortedWith(order), upcoming.sortedWith(order))
}
```

Alerts state in the UI:

```kotlin
sealed interface AlertsUiState {
    data object Unknown : AlertsUiState                       // never loaded / unavailable
    data class Known(
        val visible: VisibleAlerts,
        val checkedAt: Instant,                               // fetchedAt of the alerts cache row
        val isStale: Boolean,                                 // older than 60 min
    ) : AlertsUiState
}
```

### Data layer

- DTOs: `AlertsResponseDto(alerts: List<AlertDto> = emptyList(), location: AlertLocationDto? = null)`, with every `AlertDto` field nullable except what the fixture proves is always present. `ignoreUnknownKeys` stays on.
- API: `@GET("alerts") suspend fun alerts(@Query("lat") lat: Double, @Query("lon") lon: Double, @Query("tz") tz: String = "Europe/Berlin"): AlertsResponseDto`
- Mapper: drop alerts without an `alert_id` or with a `status` other than `actual` (null is kept); text fallback `*_en` → `*_de`; timestamps via `OffsetDateTime.parse(...).toInstant()` (null-safe).
- Repository: `Cached<AlertsResult>?` becomes the third part of `WeatherSnapshot`; `RefreshOutcome` gets `alerts: Result<Unit>` and `allFailed` covers all three. Partial results stay independent: if only alerts fail, current weather and forecast still update, and the old alerts row stays (and ages).
- Bump nothing else in the cache. If the alerts DTO changes later, bump `CACHE_FORMAT_VERSION` as before.
- `Staleness`: alerts need a refresh after 15 min; alerts are "stale for display" after 60 min.

### Home UI

- **Banner** directly under the place name, only when there are active alerts. A card with a severity-colored leading bar and icon, the event name, headline, and the time range ("until 18:00"). If there is more than one: "+2 more". Tap → Alerts screen.
- **Upcoming** alerts: not in the banner; shown in the Alerts screen under an "Upcoming" heading with their start time.
- **Footer line** (always visible, tappable → Alerts screen). Variants: "Warnings: none · checked 5 min ago"; "Warnings: none active, 1 upcoming · checked 5 min ago"; "Warnings: 2 active · checked 5 min ago"; for stale or unknown data "Warnings: not checked recently (3 h ago)" / "Warnings: unavailable". This line is what prevents false reassurance when offline. Upcoming-only alerts do not get a banner (a minor frost warning for tomorrow night shouldn't take space on Home), only the footer mention.
- Severity colors from a fixed palette (yellow, orange, red, violet), with text colors chosen for contrast in light **and** dark mode. Never rely on color alone: show the label ("Severe weather warning") and an icon.
- Labels: MINOR "Weather warning", MODERATE "Marked weather warning", SEVERE "Severe weather warning", EXTREME "Extreme weather warning", UNKNOWN "Weather notice".

### Alerts screen (`Alerts` route, type-safe)

- Top: place name, "Checked 14:05", the warning area ("Warning area: Frankfurt-Nord, Frankfurt am Main"; warn cells are small, so this explains why a neighboring town can differ), and (6b) the notifications switch.
- "Active" section: one card per alert: severity chip, event, headline, validity ("Today 14:00 – Tomorrow 06:00"), description, and instruction in a separate labelled block (collapsed if long: "Show more"). Source line "Deutscher Wetterdienst".
- "Upcoming" section (same cards, starts-at wording).
- Empty state: "No active warnings for <place>. Checked 14:05." When stale/unknown: "Warnings could not be checked recently" plus a Retry button.
- Pull-to-refresh like Home; stateless `AlertsScreen`, `AlertsViewModel` sharing the same snapshot flow as Home (no second source of truth).

### Prompt for 6a

```
Context: see AGENTS.md. We are in Phase 6a: Alerts (data, cache, banner, screen). Follow docs/phase6-alerts.md.
Ground truth: app/src/test/resources/alerts_active.json (or alerts_synthetic.json) and alerts_none.json.

1. domain: AlertSeverity, WeatherAlert, VisibleAlerts + visibleAt(now), AlertsUiState.
2. data/remote: AlertsResponseDto/AlertDto (all optional fields nullable), BrightSkyApi.alerts(), mapper
   (drop alerts without alert_id, en->de text fallback, null-safe times).
3. Cache: store alerts as kind = "alerts" in weather_cache (no schema change); WeatherSnapshot gets
   alerts; RefreshOutcome gets alerts; partial failures stay independent; Staleness additions
   (15 min refresh, 60 min stale-for-display).
4. HomeViewModel: derive AlertsUiState from the snapshot + minute ticker (visibleAt(now)).
5. UI: alert banner under the place name, footer "Warnings: ..." line (tappable), Alerts screen
   (Active/Upcoming sections, empty state, stale state), severity colors for light and dark with label + icon
   (no color-only meaning). Previews: none, one minor, severe + 2 more, upcoming only, stale, unknown.
6. Debug only (src/debug): a menu action "Insert test alert" that writes a synthetic SEVERE alert into the
   alerts cache row of the selected place (valid for the next 2 h), so the UI can be tested without real warnings.
7. Tests: mapper from fixtures. alerts_active.json must give: 1 alert, id = the alert_id string
   (not the numeric id), severity MINOR, eventCode 22, event "frost", headline "Official WARNING of FROST",
   effective 2026-11-02T11:32:00Z, onset 2026-11-02T23:00:00Z, expires 2026-11-03T07:00:00Z, non-null
   description and instruction, area name "Frankfurt-Nord"; alerts_none.json gives an empty list and area
   "München-Altstadt-Lehel"; a synthetic alert with status "test" is dropped; null text fields and missing
   expires are handled; visibleAt with the active fixture: now = 2026-11-02T15:00Z -> upcoming 1 / active 0,
   now = 2026-11-03T01:00Z -> active 1, now = 2026-11-03T07:00Z -> nothing (expired); visibleAt (expired
   dropped, upcoming split, sort order, null expires = active), severity parsing (case, unknown),
   AlertsUiState stale/unknown logic, repository partial failure (alerts fail, weather still updates).
No new dependencies. List files first, then code, then tests.
```

## 6b: Notifications

### Rules (pure, tested)

Run inside the existing `RefreshSelectedPlaceUseCase` after the refresh, so the worker stays thin.

1. Only if notifications are enabled **and** the permission is granted.
2. Candidates: **active** alerts (not upcoming, not expired) with `severity >= NOTIFY_MIN_SEVERITY` (`SEVERE`).
3. Skip alerts whose `id` is already in `seen_alerts`.
4. Skip alerts where a row with the same place key, `eventKey` (the event code, or the event text if there is none) and severity exists from the last 6 h. DWD issues *updates* with new ids; this avoids a second notification for the same warning. A **higher severity** for the same event still notifies.
5. The foreground app records every alert it *displays* in `seen_alerts` (without notifying), so you are never notified about something you have just looked at.
6. First run with an empty table: record the current alerts as seen, without notifying (no burst of notifications when you switch the feature on).
7. Post one notification per new alert; record it; prune rows whose `expiresAt` is long past.

### Persistence

New Room table `seen_alerts` (`alertId` PK, `placeKey`, `eventKey`, `severity`, `firstSeenAt`, `expiresAt`, `notified`) → **database version 3 with `AutoMigration(2, 3)`**, schemas committed. As in Phase 5: test by installing the new build *over* the old one and checking your saved places and cache survive.

Switch state in DataStore: `alertNotificationsEnabled` (default `false`).

### Notification details

- Channel `weather_warnings`, "Weather warnings", `IMPORTANCE_HIGH`, created at app start.
- Title "Severe weather warning · <place>", text = headline (or event), `setTimeoutAfter(expires - now)` so it disappears when the warning ends, auto-cancel, small vector icon, `PendingIntent.FLAG_IMMUTABLE` opening the app on the Alerts screen for that place.
- Request `POST_NOTIFICATIONS` **when the user turns the switch on**. If denied, put the switch back off and explain, with a button to the app's notification settings. Also check the permission again when the worker runs, since it can be revoked at any time.
- "Current location" selected: the worker uses the last known fix (no background location), so alerts follow the last place the app saw you.

### Prompt for 6b

```
Context: see AGENTS.md. We are in Phase 6b: Alert notifications. Follow docs/phase6-alerts.md.

1. Room: seen_alerts entity + DAO, database version 3 with AutoMigration(2, 3), schemas committed.
2. domain: selectAlertsToNotify(alerts, now, minSeverity, seen, recent) as a pure function implementing
   rules 1-7 in the doc; AlertNotifier interface.
3. data: AlertNotifierImpl (channel creation, notification builder with timeout until expiry, PendingIntent
   to the Alerts screen), DataStore flag alertNotificationsEnabled.
4. RefreshSelectedPlaceUseCase: after refresh, run the notification step; the worker only maps results.
5. HomeViewModel/AlertsViewModel: mark displayed alerts as seen (no notification).
6. UI: "Notify me about severe warnings" switch on the Alerts screen; request POST_NOTIFICATIONS when
   switching on; denied state with a settings button.
7. Debug only: a menu action "Fire test notification" using the synthetic alert from 6a.
8. Tests (JVM, fakes): rules table tests (disabled, no permission, below threshold, upcoming, expired,
   already seen, same event within 6 h, higher severity same event, first run baseline, update with a
   new id), use case with a fake notifier (notifies once, not on the second run).
Dependencies: none new. List files first, then code, then tests.
```

## Verification

1. **No warnings:** footer shows "Warnings: none · checked N min ago"; Alerts screen shows the empty state.
2. **Test alert (debug action):** banner, "+N more" with two test alerts, tap leads to the Alerts screen; expiry makes it vanish by itself (set a short validity and wait).
3. **Real warning:** when DWD has one for your place, compare the banner text and validity with the DWD site.
4. **Offline:** airplane mode, wait past 60 min (or fake the clock in a debug build): the footer changes to "not checked recently" instead of "none".
5. **Dark mode:** all four severity colors readable, with label and icon.
6. **Notifications:** switch on (permission dialog appears), fire the debug notification, tap it (opens Alerts), and see it disappear at expiry. Revoke the permission in system settings: the worker doesn't crash.
7. **Dedupe:** run the worker twice with the same active test alert: one notification only.
8. **Migration:** update-install over the Phase 5 build; places and cached weather survive.

## Review checklist

- [ ] "No warnings" is never shown without a check time, and never when data is stale or unknown
- [ ] Expired alerts disappear without a refresh (derived at render time)
- [ ] Severity never conveyed by color alone
- [ ] All alert text fields null-safe; `en` → `de` fallback works
- [ ] Alerts failing never blocks weather data (and vice versa)
- [ ] Notifications off by default; permission requested only on opt-in; no crash if revoked
- [ ] Notification dedupe (id and event+severity within 6 h) covered by tests
- [ ] No background location, no foreground service, no exact alarms
- [ ] Room migration 2 → 3 real; schemas committed
- [ ] DWD attribution visible on the Alerts screen; tests green; commit after 6a and 6b

**Done when:** a real or test warning shows as a banner and in the detail screen, stale data is never mistaken for "all clear", and a severe warning triggers exactly one notification.

## Next: Phase 7

Personal polish: a settings screen (units, theme, refresh interval, notification threshold, optional German warning text), a Glance home-screen widget showing current weather and the top warning, and small leftovers from the earlier phases.
