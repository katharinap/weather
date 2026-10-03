package com.katharina.weather.domain.repository

import com.katharina.weather.domain.model.CurrentWeather
import com.katharina.weather.domain.model.HourlyForecast

interface WeatherRepository {
    suspend fun getCurrentWeather(
        lat: Double,
        lon: Double,
        tz: String? = null
    ): Result<CurrentWeather>

    suspend fun getWeather(
        lat: Double,
        lon: Double,
        date: String,
        lastDate: String,
        tz: String? = null
    ): Result<List<HourlyForecast>>
}
