package com.katharina.weather.data.remote.mapper

import com.katharina.weather.data.remote.dto.CurrentWeatherResponseDto
import com.katharina.weather.data.remote.dto.HourlyWeatherEntryDto
import com.katharina.weather.domain.model.CurrentWeather
import com.katharina.weather.domain.model.HourlyForecast
import com.katharina.weather.domain.model.WeatherCondition
import java.time.Instant
import java.time.OffsetDateTime

fun CurrentWeatherResponseDto.toCurrentWeather(): CurrentWeather? {
    val entry = weather ?: return null
    val matchingSource = sources?.find { it.id == entry.sourceId } ?: sources?.firstOrNull()
    val instant = try {
        entry.timestamp?.let { OffsetDateTime.parse(it).toInstant() } ?: Instant.now()
    } catch (_: Exception) {
        Instant.now()
    }
    val cond = WeatherCondition.fromIcon(entry.icon)
    return CurrentWeather(
        timestamp = instant,
        temperature = entry.temperature ?: 0.0,
        condition = cond,
        icon = entry.icon ?: "unknown",
        windSpeed = entry.windSpeed10,
        windGustSpeed = entry.windGustSpeed10,
        windDirection = entry.windDirection10,
        cloudCover = entry.cloudCover,
        relativeHumidity = entry.relativeHumidity,
        precipitation10 = entry.precipitation10,
        precipitation60 = entry.precipitation60,
        stationName = matchingSource?.stationName,
        stationDistance = matchingSource?.distance
    )
}

fun HourlyWeatherEntryDto.toHourlyForecast(): HourlyForecast {
    val instant = try {
        timestamp?.let { OffsetDateTime.parse(it).toInstant() } ?: Instant.now()
    } catch (_: Exception) {
        Instant.now()
    }
    val cond = WeatherCondition.fromIcon(icon)
    return HourlyForecast(
        timestamp = instant,
        temperature = temperature ?: 0.0,
        condition = cond,
        icon = icon ?: "unknown",
        precipitation = precipitation,
        precipitationProbability = precipitationProbability,
        windSpeed = windSpeed,
        windGustSpeed = windGustSpeed,
        windDirection = windDirection,
        cloudCover = cloudCover,
        sunshine = sunshine,
        relativeHumidity = relativeHumidity
    )
}
