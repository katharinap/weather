package com.katharina.weather.ui.debug

import com.katharina.weather.data.local.WeatherCacheDao

suspend fun insertTestAlert(
    placeKey: String,
    latitude: Double,
    longitude: Double,
    weatherCacheDao: WeatherCacheDao
) {
    // No-op in release builds
}
