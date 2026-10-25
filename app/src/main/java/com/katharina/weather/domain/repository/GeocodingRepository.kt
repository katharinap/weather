package com.katharina.weather.domain.repository

import com.katharina.weather.domain.model.PlaceSearchResult

interface GeocodingRepository {
    suspend fun search(query: String): Result<List<PlaceSearchResult>>
}
