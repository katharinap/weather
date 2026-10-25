package com.katharina.weather.data.repository

import com.katharina.weather.data.remote.GeocodingApi
import com.katharina.weather.data.remote.mapper.toPlaceSearchResult
import com.katharina.weather.domain.model.PlaceSearchResult
import com.katharina.weather.domain.repository.GeocodingRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class GeocodingRepositoryImpl @Inject constructor(
    private val api: GeocodingApi
) : GeocodingRepository {

    override suspend fun search(query: String): Result<List<PlaceSearchResult>> {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            return Result.success(emptyList())
        }
        return try {
            val response = api.search(name = trimmed)
            val results = response.results.map { it.toPlaceSearchResult() }
            Result.success(results)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }
}
