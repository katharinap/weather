package com.katharina.weather.ui.debug

import com.katharina.weather.data.local.SeenAlertDao
import com.katharina.weather.data.local.WeatherCacheDao
import com.katharina.weather.domain.model.Place
import com.katharina.weather.domain.notification.AlertNotifier

suspend fun insertTestAlert(
    placeKey: String,
    latitude: Double,
    longitude: Double,
    weatherCacheDao: WeatherCacheDao
) {
    // No-op in release builds
}

suspend fun postTestNotification(
    place: Place,
    alertNotifier: AlertNotifier,
    seenAlertDao: SeenAlertDao
) {
    // No-op in release builds
}
