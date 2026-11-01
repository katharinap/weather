package com.katharina.weather.data.local

import androidx.room.Entity

@Entity(tableName = "weather_cache", primaryKeys = ["placeKey", "kind"])
data class WeatherCacheEntity(
    val placeKey: String,       // "saved:12" or "current"
    val kind: String,           // "current" | "forecast"
    val fetchedAt: Long,        // epoch millis
    val formatVersion: Int,     // CACHE_FORMAT_VERSION
    val latitude: Double,
    val longitude: Double,
    val payload: String         // JSON of CurrentWeatherResponseDto / WeatherResponseDto
)
