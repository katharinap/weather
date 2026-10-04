package com.katharina.weather.ui.format

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.katharina.weather.R
import com.katharina.weather.domain.model.WeatherCondition
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@DrawableRes
fun getWeatherIconRes(icon: String?): Int {
    val normalized = icon?.lowercase()?.replace('_', '-')
    return when (normalized) {
        "sunny", "clear-day" -> R.drawable.ic_weather_clear_day
        "clear-night" -> R.drawable.ic_weather_clear_night
        "partly-cloudy", "partly-cloudy-day" -> R.drawable.ic_weather_partly_cloudy_day
        "partly-cloudy-night" -> R.drawable.ic_weather_partly_cloudy_night
        "cloudy", "overcast" -> R.drawable.ic_weather_cloudy
        "fog", "foggy" -> R.drawable.ic_weather_fog
        "wind", "windy" -> R.drawable.ic_weather_wind
        "rain", "heavy-rain", "light-rain" -> R.drawable.ic_weather_rain
        "sleet" -> R.drawable.ic_weather_sleet
        "snow", "heavy-snow", "light-snow" -> R.drawable.ic_weather_snow
        "hail" -> R.drawable.ic_weather_hail
        "thunderstorm" -> R.drawable.ic_weather_thunderstorm
        else -> R.drawable.ic_weather_unknown
    }
}

@StringRes
fun WeatherCondition.toNameStringRes(): Int {
    return when (this) {
        WeatherCondition.SUNNY, WeatherCondition.CLEAR_DAY, WeatherCondition.CLEAR_NIGHT -> R.string.condition_sunny
        WeatherCondition.PARTLY_CLOUDY, WeatherCondition.PARTLY_CLOUDY_DAY, WeatherCondition.PARTLY_CLOUDY_NIGHT -> R.string.condition_partly_cloudy
        WeatherCondition.CLOUDY -> R.string.condition_cloudy
        WeatherCondition.RAIN -> R.string.condition_rain
        WeatherCondition.SNOW -> R.string.condition_snow
        WeatherCondition.SLEET -> R.string.condition_sleet
        WeatherCondition.HAIL -> R.string.condition_hail
        WeatherCondition.THUNDERSTORM -> R.string.condition_thunderstorm
        WeatherCondition.FOG -> R.string.condition_fog
        WeatherCondition.WINDY -> R.string.condition_windy
        WeatherCondition.UNKNOWN -> R.string.condition_unknown
    }
}

fun formatObservationTime(instant: Instant?): String {
    if (instant == null) return "–"
    val zoneId = ZoneId.of("Europe/Berlin")
    val zdt = ZonedDateTime.ofInstant(instant, zoneId)
    val now = ZonedDateTime.now(zoneId)

    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val timeStr = timeFormatter.format(zdt)

    return when (zdt.toLocalDate()) {
        now.toLocalDate() -> timeStr
        now.toLocalDate().minusDays(1) -> "Yesterday $timeStr"
        else -> {
            val dateFormatter = DateTimeFormatter.ofPattern("MMM d, ")
            dateFormatter.format(zdt) + timeStr
        }
    }
}

fun formatDistance(distanceMeters: Double?): String {
    if (distanceMeters == null) return "–"
    val km = distanceMeters / 1000.0
    return String.format(Locale.getDefault(), "%.1f", km)
}
