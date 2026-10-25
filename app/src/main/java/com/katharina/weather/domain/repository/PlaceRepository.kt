package com.katharina.weather.domain.repository

import com.katharina.weather.domain.model.Place
import com.katharina.weather.domain.model.PlaceSearchResult
import com.katharina.weather.domain.model.PlaceSelection
import kotlinx.coroutines.flow.Flow

interface PlaceRepository {
    val savedPlaces: Flow<List<Place>>
    val selection: Flow<PlaceSelection>
    suspend fun save(result: PlaceSearchResult): Place
    suspend fun delete(id: Long)
    suspend fun select(selection: PlaceSelection)
}
