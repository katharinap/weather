package com.katharina.weather.domain.notification

import com.katharina.weather.domain.model.Place
import com.katharina.weather.domain.model.WeatherAlert

interface AlertNotifier {
    suspend fun postNotification(place: Place, alert: WeatherAlert)
}
