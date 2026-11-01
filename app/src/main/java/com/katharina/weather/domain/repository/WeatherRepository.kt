package com.katharina.weather.domain.repository

import com.katharina.weather.domain.model.Place
import com.katharina.weather.domain.model.RefreshOutcome
import com.katharina.weather.domain.model.WeatherSnapshot
import kotlinx.coroutines.flow.Flow

interface WeatherRepository {
    fun observe(place: Place): Flow<WeatherSnapshot>
    suspend fun refresh(place: Place): RefreshOutcome
}
