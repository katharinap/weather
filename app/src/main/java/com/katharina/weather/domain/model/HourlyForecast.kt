package com.katharina.weather.domain.model

import java.time.Instant

data class HourlyForecast(
    val timestamp: Instant,
    val temperature: Double,
    val condition: WeatherCondition,
    val icon: String,
    val precipitation: Double?,
    val precipitationProbability: Int?,
    val windSpeed: Double?,
    val windGustSpeed: Double?,
    val windDirection: Int?,
    val cloudCover: Int?,
    val sunshine: Double?,
    val relativeHumidity: Int?
)
