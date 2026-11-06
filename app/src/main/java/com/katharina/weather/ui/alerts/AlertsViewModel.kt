package com.katharina.weather.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.katharina.weather.data.local.WeatherCacheDao
import com.katharina.weather.domain.model.*
import com.katharina.weather.domain.repository.PlaceRepository
import com.katharina.weather.domain.repository.WeatherRepository
import com.katharina.weather.ui.debug.insertTestAlert
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val placeRepository: PlaceRepository,
    private val weatherRepository: WeatherRepository,
    private val weatherCacheDao: WeatherCacheDao
) : ViewModel() {

    private val tickerFlow: Flow<Instant> = flow {
        while (true) {
            emit(Instant.now())
            delay(60_000L)
        }
    }

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var currentPlace: Place? = null

    val uiState: StateFlow<AlertsUiState> = placeRepository.selection
        .flatMapLatest { selection ->
            when (selection) {
                is PlaceSelection.None -> {
                    currentPlace = null
                    flowOf(AlertsUiState.Unknown)
                }
                is PlaceSelection.CurrentLocation -> {
                    // For current location, resolve place or observe
                    placeRepository.savedPlaces.flatMapLatest { saved ->
                        val place = saved.find { it.isCurrentLocation } ?: Place(CURRENT_LOCATION_ID, "Current location", null, null, 52.52, 13.405, true)
                        currentPlace = place
                        observeAlerts(place)
                    }
                }
                is PlaceSelection.Saved -> {
                    placeRepository.savedPlaces.flatMapLatest { saved ->
                        val place = saved.find { it.id == selection.id }
                        if (place != null) {
                            currentPlace = place
                            observeAlerts(place)
                        } else {
                            currentPlace = null
                            flowOf(AlertsUiState.Unknown)
                        }
                    }
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = AlertsUiState.Unknown
        )

    private fun observeAlerts(place: Place): Flow<AlertsUiState> {
        return combine(
            weatherRepository.observe(place),
            tickerFlow.onStart { emit(Instant.now()) }
        ) { snapshot, now ->
            if (snapshot.alerts == null) {
                AlertsUiState.Unknown
            } else {
                val result = snapshot.alerts.value
                val visible = result.alerts.visibleAt(now)
                val isStale = Staleness.isAlertsStale(snapshot, now)
                AlertsUiState.Known(
                    visible = visible,
                    area = result.area,
                    checkedAt = snapshot.alerts.fetchedAt,
                    isStale = isStale
                )
            }
        }
    }

    fun refresh() {
        val place = currentPlace ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            weatherRepository.refresh(place)
            _isRefreshing.value = false
        }
    }

    fun insertTestAlertForDebug() {
        val place = currentPlace ?: return
        viewModelScope.launch {
            insertTestAlert(place.cacheKey(), place.latitude, place.longitude, weatherCacheDao)
        }
    }
}
