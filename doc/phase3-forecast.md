# Phase 3: Forecast (Hourly Strip, Daily List, Chart)

**Goal:** below the current-weather cards, show (1) an hourly strip for the next 24 hours, (2) a daily list for the next 7 days, and (3) a temperature/precipitation chart for the next 48 hours. Still Munich, still no caching.

Work in three commits: **3a** data and aggregation, **3b** hourly strip + daily list, **3c** chart.

## Decisions

| Topic | Decision |
|---|---|
| Data | One call: `getHourlyForecast(lat, lon, days = 7)` |
| Where "past hours" are filtered | **Changed from Phase 1:** the repository now returns *all* records from local midnight today. The domain layer filters. Reason: today's min/max and rain total must include hours that have already passed. |
| Aggregation | Pure functions in `domain/forecast`, unit-tested against your real fixture |
| Failure handling | Forecast and current weather load in parallel; if only the forecast fails, the current weather stays and the forecast section shows a small "Forecast unavailable" message |
| Chart | Custom `Canvas`, no charting library |
| Rain display | Show probability only when ≥ 10 %; the daily row shows total mm if > 0 |
| Day labels | "Today", "Tomorrow", then short weekday ("Mon"), from the device locale |
| Icons | Hourly strip uses the icon as delivered (day/night variants). Daily rows always use the **day** variant. |

No new dependencies.

## 3a: Data and aggregation

### Domain model

```kotlin
data class DailyForecast(
    val date: LocalDate,
    val minTempC: Double?,
    val maxTempC: Double?,
    val precipitationMm: Double,
    val maxPrecipProbabilityPercent: Int?,
    val maxGustKmh: Double?,
    val condition: WeatherCondition,   // always a day variant
)
```

### Aggregation rules

1. Group records by local date (`Europe/Berlin`).
2. **Drop days with fewer than 12 records** (the trailing midnight record, or a day cut off by the forecast horizon).
3. `minTempC`/`maxTempC`: min/max of non-null temperatures; `null` if none.
4. `precipitationMm`: sum, treating `null` as 0.
5. `maxPrecipProbabilityPercent`: max of non-null values; `null` if none (past hours of today have no probability).
6. `maxGustKmh`: max of non-null gusts.
7. **Dominant condition** (daytime = 06:00-21:59 local; if the day has no such hours, use all):
   - Map night icons to day icons (`CLEAR_NIGHT` → `CLEAR_DAY`, `PARTLY_CLOUDY_NIGHT` → `PARTLY_CLOUDY_DAY`).
   - If there are **≥ 2 daytime hours** with precipitation (`THUNDERSTORM`, `HAIL`, `SNOW`, `SLEET`, `RAIN`), use the most severe in that order.
   - Otherwise use the most frequent condition; ties go to the cloudier one (`CLOUDY` > `PARTLY_CLOUDY_DAY` > `CLEAR_DAY` > the rest).

### Reference code (domain/forecast/DailyForecasts.kt)

```kotlin
private val BERLIN = ZoneId.of("Europe/Berlin")
private const val MIN_RECORDS_PER_DAY = 12
private val PRECIPITATION_BY_SEVERITY = listOf(
    WeatherCondition.THUNDERSTORM, WeatherCondition.HAIL, WeatherCondition.SNOW,
    WeatherCondition.SLEET, WeatherCondition.RAIN,
)
private val TIE_BREAK = listOf(
    WeatherCondition.CLOUDY, WeatherCondition.PARTLY_CLOUDY_DAY, WeatherCondition.CLEAR_DAY,
)

fun List<HourlyForecast>.upcoming(now: Instant, hours: Int = 24): List<HourlyForecast> {
    val start = now.truncatedTo(ChronoUnit.HOURS)
    return filter { it.time >= start }.take(hours)
}

fun List<HourlyForecast>.toDailyForecasts(zone: ZoneId = BERLIN): List<DailyForecast> =
    groupBy { it.time.atZone(zone).toLocalDate() }
        .filterValues { it.size >= MIN_RECORDS_PER_DAY }
        .toSortedMap()
        .map { (date, hours) ->
            DailyForecast(
                date = date,
                minTempC = hours.mapNotNull { it.temperatureC }.minOrNull(),
                maxTempC = hours.mapNotNull { it.temperatureC }.maxOrNull(),
                precipitationMm = hours.sumOf { it.precipitationMm ?: 0.0 },
                maxPrecipProbabilityPercent = hours.mapNotNull { it.precipitationProbabilityPercent }.maxOrNull(),
                maxGustKmh = hours.mapNotNull { it.windGustKmh }.maxOrNull(),
                condition = dominantCondition(hours, zone),
            )
        }

internal fun dominantCondition(hours: List<HourlyForecast>, zone: ZoneId): WeatherCondition {
    val daytime = hours.filter { it.time.atZone(zone).hour in 6..21 }.ifEmpty { hours }
    val conditions = daytime.map { it.condition.toDayVariant() }

    val precipitation = conditions.filter { it in PRECIPITATION_BY_SEVERITY }
    if (precipitation.size >= 2) {
        return PRECIPITATION_BY_SEVERITY.first { it in precipitation }
    }
    return conditions.groupingBy { it }.eachCount().entries
        .maxWithOrNull(
            compareBy<Map.Entry<WeatherCondition, Int>> { it.value }
                .thenBy { -TIE_BREAK.indexOf(it.key).let { i -> if (i < 0) TIE_BREAK.size else i } }
        )?.key ?: WeatherCondition.UNKNOWN
}

internal fun WeatherCondition.toDayVariant() = when (this) {
    WeatherCondition.CLEAR_NIGHT -> WeatherCondition.CLEAR_DAY
    WeatherCondition.PARTLY_CLOUDY_NIGHT -> WeatherCondition.PARTLY_CLOUDY_DAY
    else -> this
}
```

### Tests (using your real `weather_munich.json`)

I computed these from your fixture, so they're real expected values.

```kotlin
@Test fun `fixture yields two full days, trailing midnight record dropped`() {
    val days = forecast().toDailyForecasts()
    assertEquals(listOf(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 3)), days.map { it.date })
}

@Test fun `october 2 aggregates`() {
    val d = forecast().toDailyForecasts()[0]
    assertEquals(14.0, d.minTempC);  assertEquals(18.6, d.maxTempC)
    assertEquals(0.0, d.precipitationMm, 0.001)
    assertEquals(2, d.maxPrecipProbabilityPercent)
    assertEquals(27.0, d.maxGustKmh)
    assertEquals(WeatherCondition.CLOUDY, d.condition)
}

@Test fun `october 3 aggregates`() {
    val d = forecast().toDailyForecasts()[1]
    assertEquals(12.0, d.minTempC);  assertEquals(20.3, d.maxTempC)
    assertEquals(4, d.maxPrecipProbabilityPercent)
    assertEquals(14.8, d.maxGustKmh)
    assertEquals(WeatherCondition.PARTLY_CLOUDY_DAY, d.condition)   // 10 vs 6 daytime hours
}
```

Add synthetic tests with hand-built `HourlyForecast` lists (a helper `hour(time, condition, temp, mm, prob)`):
- 3 rain hours + 12 cloudy hours → `RAIN`; 1 rain hour only → not `RAIN`
- thunderstorm + rain hours → `THUNDERSTORM`
- all-night icons → day variants
- a day with 11 records is dropped, 12 is kept
- all temperatures null → `minTempC == null`
- DST changeover day (2026-10-25: 25 records; 2027-03-28: 23 records) is kept and doesn't crash
- `upcoming()`: now = 10:30 starts at the 10:00 record and returns at most 24 items

## 3b: UI

### State

`HomeUiState.Success` gets a forecast section; a forecast failure must not replace the current weather:

```kotlin
sealed interface ForecastState {
    data class Loaded(val hours: List<HourlyForecast>, val days: List<DailyForecast>) : ForecastState
    data object Unavailable : ForecastState
}
// Success(..., val forecast: ForecastState)
```

In `HomeViewModel.load()`, fetch both in parallel:

```kotlin
val (current, forecast) = coroutineScope {
    val c = async { repository.getCurrentWeather(lat, lon) }
    val f = async { repository.getHourlyForecast(lat, lon, days = 7) }
    c.await() to f.await()
}
```

- Current fails and there is no previous data → `Error` as before.
- Current succeeds, forecast fails → `Success` with `ForecastState.Unavailable` (or keep the previous forecast if there was one, plus a `transientError`).
- Compute `hours = forecast.upcoming(clock.instant())` and `days = forecast.toDailyForecasts()` in the ViewModel, not in composables.

### Hourly strip

`LazyRow`, 24 items, each item a narrow column: time label ("Now" for the first, then "20:00"), icon, temperature (`17°`), and rain probability when ≥ 10 % (small, in a secondary color). Fixed item width (e.g. 64 dp), `key = { it.time }` for stable items.

### Daily list

A `Column` (not a nested `LazyColumn` inside a scrolling parent) with 7 rows: day label, day-variant icon, rain info (probability and total mm when > 0), min and max temperature. Optional: a temperature range bar scaled to the min and max across all 7 days (a simple `Canvas` or `Box` with fractional width).

Layout order on the home screen: header → current weather → detail cards → hourly strip → daily list → chart (3c) → footer.

### Prompt for 3a + 3b

```
Context: see AGENTS.md. We are in Phase 3a/3b: Forecast. Follow docs/phase3-forecast.md exactly.
Ground truth: app/src/test/resources/weather_munich.json.

1. Repository: remove the "before current hour" filter from getHourlyForecast (return everything
   from local midnight today), update its tests.
2. domain/forecast: DailyForecast, upcoming(), toDailyForecasts(), dominantCondition() per the
   rules in the doc, with the unit tests from the doc plus synthetic cases.
3. HomeUiState/HomeViewModel: ForecastState, parallel loading, partial failure handling.
   Add ViewModel tests: forecast-only failure keeps current weather; current-only failure with
   no previous data -> Error.
4. UI: HourlyStrip (LazyRow) and DailyList composables, stateless, with previews incl. empty
   and all-null data; strings in strings.xml (English), weekday names from Locale.
No new dependencies. List files first, then code, then tests.
Acceptance: real Munich hourly strip and 7-day list on the emulator; airplane mode on refresh
keeps data; ./gradlew testDebugUnitTest passes.
```

## 3c: Temperature and precipitation chart

Do this only after 3a/3b are committed.

- Next 48 hours, `Canvas` in a fixed-height box (about 180 dp), horizontally scrollable is not needed.
- Temperature as a line, precipitation (mm) as bars along the bottom with their own scale, x-axis labels every 6 hours, y-axis labels for min and max temperature.
- Leave gaps for `null` temperatures instead of drawing zeros.
- Put the scaling math in a pure, tested function (value → pixel), not inside the draw code:

```kotlin
fun scale(value: Double, min: Double, max: Double, size: Float): Float =
    if (max <= min) size / 2f else (size * (1 - (value - min) / (max - min))).toFloat()
```
- Pad the y-range slightly (e.g. ±1 °C) so the line doesn't touch the edges; use at least 1 mm as the precipitation scale maximum so tiny amounts don't look huge.
- Use theme colors (`MaterialTheme.colorScheme`), support dark mode, and add `Modifier.semantics { contentDescription = ... }` with a short text summary ("Next 48 hours: 12 to 20 degrees, no rain expected").

### Prompt for 3c

```
Context: see AGENTS.md, Phase 3c. Add HourlyChart (Canvas, no libraries) per docs/phase3-forecast.md.
Pure scaling function with unit tests (equal min/max, negative values, null gaps).
Previews: normal, dark, all-null temperatures, rainy day. No new dependencies.
```

## Review checklist

- [ ] Today's min/max includes hours that already passed
- [ ] The trailing midnight record never appears as an eighth, one-hour day
- [ ] No composable does aggregation or timezone math
- [ ] Hourly "Now" item is the current hour, not the next one
- [ ] Forecast failure leaves the current weather visible
- [ ] Null temperatures/probabilities render as "–" or are hidden, never "0"
- [ ] Daily list doesn't nest a scrollable `LazyColumn` inside the scrolling screen
- [ ] DST days (25 h / 23 h) don't crash
- [ ] Dark mode checked; previews exist; tests green; commit after 3a, 3b and 3c

**Done when:** the three sections show real Munich data, the aggregation tests pass against your fixture, and a forecast-only failure degrades gracefully.

## Next: Phase 4

Locations: replace the hardcoded `DefaultPlace` with "use my location", place search and a saved-places list (Room). The Bright Sky API only takes coordinates, so we need a place-name source for search (Android's `Geocoder` is the zero-dependency option). I'll lay out the options in the Phase 4 guide.
