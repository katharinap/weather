package com.katharina.weather.domain.model

import java.time.LocalDate

data class DailyForecast(
    val date: LocalDate,
    val minTempC: Double?,
    val maxTempC: Double?,
    val precipitationMm: Double,
    val maxPrecipProbabilityPercent: Int?,
    val maxGustKmh: Double?,
    val condition: WeatherCondition,
)
