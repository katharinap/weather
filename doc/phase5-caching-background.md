# Phase 5: Caching and Background Refresh

**Goal:** the app opens instantly with the last known data, refreshes quietly, keeps working offline, and a periodic background job keeps the data fresh. Room becomes the single source of truth for weather; the network only writes into it.

Two commits: **5a** cache + offline-first repository + UI changes, **5b** WorkManager.

## Decisions

| Topic | Decision |
|---|---|
| Cache format | **Raw API responses (the DTOs re-serialized as JSON) in one Room table**, not normalized columns. The existing mappers run on read, so mapper changes apply to cached data automatically, and there's no schema churn when you add a field. A `formatVersion` column makes DTO shape changes safe: mismatch or parse failure = cache miss, never a crash. |
| Cache key | `"saved:<id>"` for saved places, `"current"` for the device location (one slot, overwritten) |
| Source of truth | Room. ViewModels observe `Flow`s from the cache; `refresh()` only fetches and writes. |
| Partial results | Current weather and forecast are cached independently. If one fetch fails, the other still updates and the failed one keeps its previous value. |
| Freshness (foreground) | Refresh on app start/resume and on place switch when current weather is older than **15 min** or the forecast older than **60 min**. Pull-to-refresh always works but is ignored for 60 s after the last successful fetch for the same place. |
| Outdated | After **6 h** without a successful refresh the data is still shown, but labelled as outdated. Cached data is never hidden. |
| Background | `WorkManager` periodic work every **60 min** (constant for now; Phase 7 makes it a setting), network-connected constraint, selected place only |
| Background location | **No.** The app only has foreground coarse permission, so the worker can't request a location. For "current location" it uses the last known coordinates (stored when 4b got a fix). |
| Eviction | Delete cache rows for places that no longer exist and rows older than 7 days; run it from the worker |
| Migration | Real Room migration 1 → 2 (auto-migration). **Never** destructive: it holds your saved places. |

New dependencies: `androidx.work:work-runtime-ktx`, `androidx.hilt:hilt-work` plus its `hilt-compiler` (KSP). Optional, for the migration test only: `androidx.room:room-testing` (androidTest). Nothing else.

## 5a: Cache and offline-first repository

### Room

```kotlin
@Entity(tableName = "weather_cache", primaryKeys = ["placeKey", "kind"])
data class WeatherCacheEntity(
    val placeKey: String,       // "saved:12" or "current"
    val kind: String,           // "current" | "forecast"
    val fetchedAt: Long,        // epoch millis
    val formatVersion: Int,     // CACHE_FORMAT_VERSION, bump when a DTO changes shape
    val latitude: Double,
    val longitude: Double,
    val payload: String,        // JSON of CurrentWeatherResponseDto / WeatherResponseDto
)

@Dao
interface WeatherCacheDao {
    @Query("SELECT * FROM weather_cache WHERE placeKey = :placeKey")
    fun observe(placeKey: String): Flow<List<WeatherCacheEntity>>

    @Query("SELECT * FROM weather_cache WHERE placeKey = :placeKey AND kind = :kind")
    suspend fun find(placeKey: String, kind: String): WeatherCacheEntity?

    @Upsert suspend fun upsert(entity: WeatherCacheEntity)

    @Query("DELETE FROM weather_cache WHERE placeKey = :placeKey")
    suspend fun deleteForPlace(placeKey: String)

    @Query("DELETE FROM weather_cache WHERE placeKey != 'current' AND placeKey NOT IN (:keepKeys)")
    suspend fun deleteOrphans(keepKeys: List<String>)

    @Query("DELETE FROM weather_cache WHERE fetchedAt < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long)
}
```

Migration:
- `@Database(version = 2, exportSchema = true, autoMigrations = [AutoMigration(from = 1, to = 2)])`
- Commit `schemas/.../1.json` and `2.json`.
- **Test it by updating, not reinstalling:** install the Phase 4 build, add places, then run the Phase 5 build *over it* from Android Studio and confirm the places are still there. (An instrumented `MigrationTestHelper` test is a nice extra.)

Note: if `deleteOrphans` is called with an empty list, check that your Room version handles `NOT IN ()` correctly; guard it by deleting all non-"current" rows when there are no saved places.

### Domain and repository

```kotlin
data class Cached<T>(val value: T, val fetchedAt: Instant)

data class WeatherSnapshot(
    val current: Cached<CurrentWeather>?,
    val forecast: Cached<List<HourlyForecast>>?,   // all hours from local midnight, unfiltered
)

data class RefreshOutcome(val current: Result<Unit>, val forecast: Result<Unit>) {
    val allFailed get() = current.isFailure && forecast.isFailure
    val firstError get() = current.exceptionOrNull() ?: forecast.exceptionOrNull()
}

interface WeatherRepository {
    fun observe(place: Place): Flow<WeatherSnapshot>
    suspend fun refresh(place: Place): RefreshOutcome
}

fun Place.cacheKey() = if (isCurrentLocation) "current" else "saved:$id"
```

Implementation rules:
- `observe`: map the DAO rows to a snapshot by decoding the payload with the existing DTOs and mappers. A row with the wrong `formatVersion`, or one that fails to decode, counts as **missing** (return null for that part and optionally delete the row), never an exception.
- `refresh`: fetch current weather and forecast in parallel (`coroutineScope` + `async`); each success is written with `fetchedAt = clock.instant()`; each failure leaves the old row untouched. Rethrow `CancellationException`.
- The week's forecast fetch stays `days = 7` from Phase 3, still returning everything from local midnight.
- Writing through the DAO triggers `observe` to re-emit; no extra plumbing.
- Because results are written under the place's own key, **a late response can't show up under the wrong place anymore.** The Phase 4 "ignore results for another place" logic becomes unnecessary; observing is keyed by `flatMapLatest` on the selected place.

### Staleness (pure and unit-tested)

```kotlin
object Staleness {
    val CURRENT_MAX_AGE: Duration = Duration.ofMinutes(15)
    val FORECAST_MAX_AGE: Duration = Duration.ofMinutes(60)
    val OUTDATED_AFTER: Duration = Duration.ofHours(6)
    val MIN_REFRESH_INTERVAL: Duration = Duration.ofSeconds(60)

    fun needsRefresh(s: WeatherSnapshot, now: Instant): Boolean =
        s.current == null || s.forecast == null ||
        Duration.between(s.current.fetchedAt, now) > CURRENT_MAX_AGE ||
        Duration.between(s.forecast.fetchedAt, now) > FORECAST_MAX_AGE

    fun isOutdated(s: WeatherSnapshot, now: Instant): Boolean =
        s.current != null && Duration.between(s.current.fetchedAt, now) > OUTDATED_AFTER
}
```

### Current location

- Store the last known fix (`latitude`, `longitude`, `name`) in DataStore whenever 4b gets a location.
- On cold start with `PlaceSelection.CurrentLocation`, resolve the place **immediately** from the last known fix and show cached weather; fetch a fresh location in the background and refresh afterwards.
- If the new fix is more than **10 km** from the cached coordinates, call `deleteForPlace("current")` before refreshing, so another city's weather isn't shown under the new location. (Show Loading in that case.)
- No last known fix yet (first use) → the Phase 4b flow as before.

### ViewModel changes

- State is built from the cache: `selection → place → repository.observe(place)` (`flatMapLatest`), combined with a refresh state (`Idle`, `Refreshing`, `Failed(kind)`) and a **minute ticker** (emits every 60 s while the screen is visible, `WhileSubscribed(5_000)`).
- **Derive time-dependent lists on every emission** with the current time: `hours = forecast.upcoming(now)`, `days = forecast.toDailyForecasts().filter { it.date >= today }`. Otherwise cached data shows hours that are already in the past, and the "Now" slot never advances while the app is open.
- `Loading` only when a place has **no cache at all** and the first refresh is running. With a cache, the screen shows the data immediately and only the pull-to-refresh spinner moves.
- The full-screen `Error` only appears when there is no cache and the refresh failed. If a refresh fails but data exists: keep showing it, set `refreshFailed`, and show the offline banner (below).
- Triggers: on place selection and on `onStart()`, call `refresh` when `Staleness.needsRefresh(...)` is true. Switching to a cached place shows its data **instantly**.

### UI changes

- Footer: "Observed 18:00 · Updated 5 min ago". "Observed" is the measurement time from the station; "Updated" is when the app last fetched.
- Relative time formatter (pure, tested): "just now" (< 1 min), "N min ago", "N h ago", "yesterday 18:00", then the date. It updates with the minute ticker.
- Banner when the last refresh failed: "Can't refresh. Showing data from 18:00." (use "Offline" only if the error was `IOException`). It disappears after the next successful refresh.
- Label when `isOutdated`: "Data is N h old" in a warning color, in addition to the banner.
- The Phase 2 snackbar for a failed refresh is replaced by the banner. Keep a snackbar only for a failed *user-initiated* pull-to-refresh, and only if the banner isn't already showing.
- 404 "no data for this location" (Phase 4 handling) stays an error state for places without cache.

### Prompt for 5a

```
Context: see AGENTS.md. We are in Phase 5a: Offline-first caching. Follow docs/phase5-caching-background.md.

1. data/local: WeatherCacheEntity, WeatherCacheDao, AppDatabase version 2 with an AutoMigration
   from 1 (exportSchema = true, commit schemas, no destructive migration).
2. domain: Cached<T>, WeatherSnapshot, RefreshOutcome, Staleness, Place.cacheKey(); replace the
   repository methods getCurrentWeather/getHourlyForecast with observe(place)/refresh(place).
3. data/repository: cache raw DTOs as JSON with formatVersion; observe() treats version mismatch and
   decode failures as missing; refresh() fetches both in parallel, writes successes independently,
   leaves failed parts untouched.
4. Current location: store the last known fix in DataStore; on cold start use it immediately; clear the
   "current" cache when a new fix is > 10 km away.
5. HomeViewModel: observe the cache via flatMapLatest, minute ticker, derive upcoming hours and days
   (days >= today) on every emission, Loading only without cache, refresh triggers per Staleness,
   60 s minimum refresh interval, offline banner state. Remove the Phase 4 stale-response workaround.
6. UI: footer "Observed ... - Updated ...", relative-time formatter, offline banner, outdated label.
   Previews: cached+fresh, cached+offline banner, outdated, first load (Loading), no cache + error.
7. Tests (JVM, no new libraries; use a fake WeatherCacheDao and fake API):
   - repository: both parts stored; one part fails -> other stored, old value of the failed one kept;
     wrong formatVersion and corrupted payload -> missing, no crash; observe emits after refresh;
     deleteOrphans/deleteOlderThan behavior
   - Staleness: table tests around the 15/60 min and 6 h boundaries
   - relative-time formatter; "current" cache cleared at > 10 km, kept at < 10 km
   - HomeViewModel: cached data shown without Loading; switching to a cached place is instant;
     failed refresh keeps data and sets the banner; no cache + failure -> Error; hours/days recomputed
     when the ticker advances; ignores a second refresh within 60 s.
Dependencies: none new in 5a. List files first, then code, then tests.
Acceptance: see "Verification" in docs/phase5-caching-background.md.
```

## 5b: Background refresh (WorkManager)

### Setup (the usual traps)

- Add `work-runtime-ktx`, `hilt-work`, and `hilt-compiler` (KSP).
- **Custom initialization.** To use `@HiltWorker`, the `Application` must implement `Configuration.Provider` and return `Configuration.Builder().setWorkerFactory(hiltWorkerFactory).build()` (inject `HiltWorkerFactory`). In the manifest, remove the default initializer, or the worker is created without Hilt and crashes:

  ```xml
  <provider
      android:name="androidx.startup.InitializationProvider"
      android:authorities="${applicationId}.androidx-startup"
      android:exported="false"
      tools:node="merge">
      <meta-data
          android:name="androidx.work.WorkManagerInitializer"
          android:value="androidx.startup"
          tools:node="remove" />
  </provider>
  ```
- No new permissions. WorkManager reschedules itself after a reboot.

### Design

- **Keep the worker thin.** All logic lives in a use case, `RefreshSelectedPlaceUseCase`, which is testable on the JVM:
  1. Resolve the selected place. `None` → nothing to do. `CurrentLocation` → use the last known fix from DataStore; none → skip.
  2. Call `weatherRepository.refresh(place)`.
  3. Run eviction: `deleteOrphans(savedKeys)`, `deleteOlderThan(now - 7 days)`.
  4. Return a result: `Done`, `Retry` (all failed with `IOException` or HTTP 5xx), or `GiveUp` (404 no data, parse errors).
- `RefreshWeatherWorker` (`@HiltWorker`, `CoroutineWorker`) maps that result: `Done` and `GiveUp` → `Result.success()`; `Retry` → `Result.retry()` while `runAttemptCount < 3`, then `Result.success()` (the next period tries again).
- Scheduling (in `Application.onCreate`):

```kotlin
val request = PeriodicWorkRequestBuilder<RefreshWeatherWorker>(60, TimeUnit.MINUTES)
    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
    .build()
WorkManager.getInstance(this)
    .enqueueUniquePeriodicWork("weather-refresh", ExistingPeriodicWorkPolicy.KEEP, request)
```

Use `KEEP`, so every app start doesn't reset the schedule; use `UPDATE` once if you later change the request.
- **Don't expect exact timing.** Periodic work is batched by Android (Doze, battery optimization, app standby buckets). On Android 15 an app you rarely open may run less often than every 60 minutes. For a weather app that's fine, because the foreground refresh covers app opens.
- Be kind to the API: one worker run is two requests; at 60 min that is at most 48 requests per day.
- Design the use case so Phase 6 can add the alerts check into the same run.

### Prompt for 5b

```
Context: see AGENTS.md. We are in Phase 5b: Background refresh. Follow docs/phase5-caching-background.md.

1. Add dependencies: androidx.work:work-runtime-ktx, androidx.hilt:hilt-work, hilt-compiler (KSP).
2. Application implements Configuration.Provider with HiltWorkerFactory; remove the default
   WorkManagerInitializer in the manifest (tools:node="remove").
3. domain: RefreshSelectedPlaceUseCase (resolve selected place; current location -> last known fix;
   refresh; eviction; return Done / Retry / GiveUp).
4. RefreshWeatherWorker (@HiltWorker CoroutineWorker) mapping results as in the doc (retry max 3).
5. Schedule unique periodic work "weather-refresh": 60 min, network-connected, exponential backoff,
   ExistingPeriodicWorkPolicy.KEEP. Interval as a named constant.
6. Tests (JVM, fakes): use case for none selected, saved place, current location with and without
   last known fix, all-IOException -> Retry, 404 -> GiveUp, partial success -> Done, eviction called.
List files first, then code, then tests.
```

## Verification (manual scenarios)

1. **Offline cold start:** load data, enable airplane mode, force-stop the app, start it → data appears immediately with "Updated ..." and an offline banner after the failed refresh.
2. **Fresh install over the old build:** places from Phase 4 survive the update.
3. **Place switching:** switching between two places that have been loaded shows data instantly, without a Loading screen.
4. **Failed refresh with data:** airplane mode + pull-to-refresh keeps the data and shows the banner/snackbar behavior described above.
5. **Time passes:** leave the app open across the full hour: the hourly strip's "Now" item advances, and the daily list stays correct.
6. **Corrupted cache:** (debug only) change `CACHE_FORMAT_VERSION` and restart: the app shows Loading once, refetches, and doesn't crash.
7. **Worker:** use Android Studio's **App Inspection → Background Task Inspector** to run `weather-refresh` immediately. Check that `fetchedAt` in the database changes. Then repeat with airplane mode to see `retry`.
8. **Moved location:** with current location selected, change the emulator's position by more than 10 km (Extended controls → Location): the old city's weather must not appear under the new place.

## Review checklist

- [ ] ViewModels never call the network directly; only `refresh` does, and only the cache is observed
- [ ] No stale-response workaround remains; results are keyed by place
- [ ] Cache parse failure and version mismatch are misses, not crashes
- [ ] Hours and days are derived from "now" on every emission, not stored in state
- [ ] "Observed" and "Updated" are shown as two different times
- [ ] Worker contains no logic beyond mapping the use case result
- [ ] No background location access; no foreground service; no exact alarms
- [ ] Room migration is real (no `fallbackToDestructiveMigration`); schemas committed
- [ ] DWD and Open-Meteo attribution still visible
- [ ] Tests green; commit after 5a and after 5b

**Done when:** the app opens instantly offline with the last data, refreshes itself in the foreground and background, and no failure path hides cached data.

## Next: Phase 6

Alerts: Bright Sky `/alerts` by coordinates, severity colors, a banner on Home plus a detail view, and optionally a notification for new severe alerts (needs the `POST_NOTIFICATIONS` permission on Android 15 and builds on the worker from 5b). Alerts follow the same pattern as this phase: fetched into the cache, observed from Room.
