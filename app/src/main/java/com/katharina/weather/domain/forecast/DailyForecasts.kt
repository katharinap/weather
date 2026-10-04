package com.katharina.weather.domain.forecast

import com.katharina.weather.domain.model.DailyForecast
import com.katharina.weather.domain.model.HourlyForecast
import com.katharina.weather.domain.model.WeatherCondition
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private val BERLIN = ZoneId.of("Europe/Berlin")
private const val MIN_RECORDS_PER_DAY = 12
private val PRECIPITATION_BY_SEVERITY = listOf(
    WeatherCondition.THUNDERSTORM,
    WeatherCondition.HAIL,
    WeatherCondition.SNOW,
    WeatherCondition.SLEET,
    WeatherCondition.RAIN,
)
private val TIE_BREAK = listOf(
    WeatherCondition.CLOUDY,
    WeatherCondition.PARTLY_CLOUDY_DAY,
    WeatherCondition.CLEAR_DAY,
)

fun List<HourlyForecast>.upcoming(now: Instant, hours: Int = 24): List<HourlyForecast> {
    val start = now.truncatedTo(ChronoUnit.HOURS)
    return filter { it.timestamp >= start }.take(hours)
}

fun List<HourlyForecast>.toDailyForecasts(zone: ZoneId = BERLIN): List<DailyForecast> =
    groupBy { it.timestamp.atZone(zone).toLocalDate() }
        .filterValues { it.size >= MIN_RECORDS_PER_DAY }
        .toSortedMap()
        .map { (date, hours) ->
            DailyForecast(
                date = date,
                minTempC = hours.mapNotNull { it.temperature }.minOrNull(),
                maxTempC = hours.mapNotNull { it.temperature }.maxOrNull(),
                precipitationMm = hours.sumOf { it.precipitation ?: 0.0 },
                maxPrecipProbabilityPercent = hours.mapNotNull { it.precipitationProbability }.maxOrNull(),
                maxGustKmh = hours.mapNotNull { it.windGustSpeed }.maxOrNull(),
                condition = dominantCondition(hours, zone),
            )
        }

internal fun dominantCondition(hours: List<HourlyForecast>, zone: ZoneId): WeatherCondition {
    val daytime = hours.filter { it.timestamp.atZone(zone).hour in 6..21 }.ifEmpty { hours }
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
