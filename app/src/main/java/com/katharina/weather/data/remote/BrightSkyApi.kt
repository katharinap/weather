package com.katharina.weather.data.remote

import com.katharina.weather.data.remote.dto.CurrentWeatherResponseDto
import com.katharina.weather.data.remote.dto.WeatherResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

interface BrightSkyApi {
    @GET("current_weather")
    suspend fun currentWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("tz") tz: String? = null
    ): CurrentWeatherResponseDto

    @GET("weather")
    suspend fun weather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("date") date: String,
        @Query("last_date") lastDate: String,
        @Query("tz") tz: String? = null
    ): WeatherResponseDto
}
