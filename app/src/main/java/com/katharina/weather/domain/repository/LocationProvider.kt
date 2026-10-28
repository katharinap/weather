package com.katharina.weather.domain.repository

import com.katharina.weather.domain.model.Coordinates

interface LocationProvider {
    suspend fun currentLocation(): Result<Coordinates>
}
