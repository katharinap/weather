package com.katharina.weather.domain.usecase

import com.katharina.weather.data.local.LocationDataStore
import com.katharina.weather.data.local.WeatherCacheDao
import com.katharina.weather.data.local.deleteOrphans
import com.katharina.weather.domain.model.CURRENT_LOCATION_ID
import com.katharina.weather.domain.model.Place
import com.katharina.weather.domain.model.PlaceSelection
import com.katharina.weather.domain.model.cacheKey
import com.katharina.weather.domain.repository.PlaceRepository
import com.katharina.weather.domain.repository.WeatherRepository
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.time.Clock
import java.time.Duration
import javax.inject.Inject

enum class RefreshResult {
    Done,
    Retry,
    GiveUp
}

class RefreshSelectedPlaceUseCase @Inject constructor(
    private val placeRepository: PlaceRepository,
    private val weatherRepository: WeatherRepository,
    private val locationDataStore: LocationDataStore,
    private val weatherCacheDao: WeatherCacheDao,
    private val clock: Clock = Clock.systemUTC()
) {
    suspend operator fun invoke(): RefreshResult {
        val selection = placeRepository.selection.first()
        val place = when (selection) {
            is PlaceSelection.None -> return RefreshResult.Done
            is PlaceSelection.CurrentLocation -> {
                val lastFix = locationDataStore.lastLocation.first() ?: return RefreshResult.Done
                Place(
                    id = CURRENT_LOCATION_ID,
                    name = lastFix.name,
                    region = null,
                    country = null,
                    latitude = lastFix.latitude,
                    longitude = lastFix.longitude,
                    isCurrentLocation = true
                )
            }
            is PlaceSelection.Saved -> {
                val savedPlaces = placeRepository.savedPlaces.first()
                savedPlaces.find { it.id == selection.id } ?: return RefreshResult.Done
            }
        }

        val outcome = weatherRepository.refresh(place)

        // Run eviction
        val savedList = placeRepository.savedPlaces.first()
        val savedKeys = savedList.map { it.cacheKey() }
        weatherCacheDao.deleteOrphans(savedKeys)
        val sevenDaysAgo = clock.millis() - Duration.ofDays(7).toMillis()
        weatherCacheDao.deleteOlderThan(sevenDaysAgo)

        if (outcome.allFailed) {
            val firstError = outcome.firstError
            val message = firstError?.message ?: ""
            return if (message.contains("404") || message.contains("No sources match")) {
                RefreshResult.GiveUp
            } else if (firstError is IOException || message.contains("5") || message.contains("Server")) {
                RefreshResult.Retry
            } else {
                RefreshResult.Retry
            }
        }

        return RefreshResult.Done
    }
}
