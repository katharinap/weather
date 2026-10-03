package com.katharina.weather.domain.model

import java.time.Instant

data class CurrentWeather(
    val timestamp: Instant,
    val temperature: Double,
    val condition: WeatherCondition,
    val icon: String,
    val windSpeed: Double?,
    val windGustSpeed: Double?,
    val windDirection: Int?,
    val cloudCover: Int?,
    val relativeHumidity: Int?,
    val precipitation10: Double?,
    val precipitation60: Double?,
    val stationName: String?,
    val stationDistance: Double?
)
