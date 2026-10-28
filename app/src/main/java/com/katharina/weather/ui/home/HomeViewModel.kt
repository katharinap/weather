package com.katharina.weather.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.katharina.weather.data.location.GeocoderHelper
import com.katharina.weather.domain.forecast.toDailyForecasts
import com.katharina.weather.domain.forecast.upcoming
import com.katharina.weather.domain.model.CURRENT_LOCATION_ID
import com.katharina.weather.domain.model.LocationPermissionException
import com.katharina.weather.domain.model.LocationUnavailableException
import com.katharina.weather.domain.model.Place
import com.katharina.weather.domain.model.PlaceSelection
import com.katharina.weather.domain.repository.LocationProvider
import com.katharina.weather.domain.repository.PlaceRepository
import com.katharina.weather.domain.repository.WeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val placeRepository: PlaceRepository,
    private val weatherRepository: WeatherRepository,
    private val locationProvider: LocationProvider,
    private val geocoderHelper: GeocoderHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var currentPlace: Place? = null
    private var lastFetchedTime: Instant? = null
    private var loadJob: Job? = null

    init {
        observeSelection()
    }

    private fun observeSelection() {
        combine(
            placeRepository.selection,
            placeRepository.savedPlaces
        ) { selection, savedPlaces ->
            selection to savedPlaces
        }.onEach { (selection, savedPlaces) ->
            when (selection) {
                is PlaceSelection.None -> {
                    loadJob?.cancel()
                    currentPlace = null
                    _uiState.value = HomeUiState.NoPlace
                }
                is PlaceSelection.CurrentLocation -> {
                    loadCurrentLocationWeather()
                }
                is PlaceSelection.Saved -> {
                    val targetPlace = savedPlaces.find { it.id == selection.id }
                    if (targetPlace == null) {
                        loadJob?.cancel()
                        currentPlace = null
                        _uiState.value = HomeUiState.NoPlace
                    } else if (targetPlace.id != currentPlace?.id) {
                        currentPlace = targetPlace
                        loadJob?.cancel()
                        loadWeatherForPlace(targetPlace, isRefreshing = false)
                    }
                }
            }
        }.launchIn(viewModelScope)
    }

    fun onStart() {
        val place = currentPlace ?: return
        val currentState = _uiState.value
        if (currentState is HomeUiState.Success) {
            if (lastFetchedTime != null && Duration.between(lastFetchedTime, Instant.now()).toMinutes() >= 15) {
                refresh()
            }
        } else if (currentState is HomeUiState.Loading) {
            loadWeatherForPlace(place, isRefreshing = false)
        }
    }

    fun refresh() {
        val currentState = _uiState.value
        if (currentState is HomeUiState.Success && currentState.place.isCurrentLocation) {
            loadCurrentLocationWeather()
        } else {
            val place = currentPlace ?: return
            loadWeatherForPlace(place, isRefreshing = true)
        }
    }

    fun selectCurrentLocation() {
        viewModelScope.launch {
            placeRepository.select(PlaceSelection.CurrentLocation)
        }
    }

    private fun loadCurrentLocationWeather() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            val coordsResult = locationProvider.currentLocation()
            coordsResult.fold(
                onSuccess = { coords ->
                    val placeName = geocoderHelper.getPlaceName(coords)
                    val currentLocationPlace = Place(
                        id = CURRENT_LOCATION_ID,
                        name = placeName,
                        region = null,
                        country = null,
                        latitude = coords.latitude,
                        longitude = coords.longitude,
                        isCurrentLocation = true
                    )
                    currentPlace = currentLocationPlace
                    loadWeatherForPlace(currentLocationPlace, isRefreshing = false)
                },
                onFailure = { throwable ->
                    val kind = when (throwable) {
                        is LocationPermissionException -> ErrorKind.LocationPermissionRequired
                        is LocationUnavailableException -> ErrorKind.LocationUnavailable
                        else -> ErrorKind.LocationUnavailable
                    }
                    _uiState.value = HomeUiState.Error(place = null, kind = kind)
                }
            )
        }
    }

    private fun loadWeatherForPlace(targetPlace: Place, isRefreshing: Boolean) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (isRefreshing && currentState is HomeUiState.Success) {
                _uiState.update { currentState.copy(isRefreshing = true, transientError = null) }
            } else if (!isRefreshing) {
                _uiState.value = HomeUiState.Loading
            }

            val (currentResult, forecastResult) = coroutineScope {
                val c = async { weatherRepository.getCurrentWeather(targetPlace.latitude, targetPlace.longitude) }
                val f = async { weatherRepository.getWeather(targetPlace.latitude, targetPlace.longitude, days = 7) }
                c.await() to f.await()
            }

            if (currentPlace?.id != targetPlace.id) return@launch

            val now = Instant.now()
            val previousSuccess = currentState as? HomeUiState.Success

            if (currentResult.isSuccess) {
                val weather = currentResult.getOrThrow()
                val forecastState = if (forecastResult.isSuccess) {
                    val forecastList = forecastResult.getOrThrow()
                    val hours24 = forecastList.upcoming(now, 24)
                    val hours48 = forecastList.upcoming(now, 48)
                    val days = forecastList.toDailyForecasts()
                    ForecastState.Loaded(hours24 = hours24, hours48 = hours48, days = days)
                } else {
                    previousSuccess?.forecast ?: ForecastState.Unavailable
                }

                val transientErr = if (forecastResult.isFailure && currentResult.isSuccess && isRefreshing) {
                    forecastResult.exceptionOrNull()?.localizedMessage ?: "Failed to refresh forecast."
                } else null

                lastFetchedTime = now
                _uiState.value = HomeUiState.Success(
                    weather = weather,
                    place = targetPlace,
                    fetchedAt = lastFetchedTime,
                    forecast = forecastState,
                    isRefreshing = false,
                    transientError = transientErr
                )
            } else {
                if (previousSuccess != null) {
                    _uiState.value = previousSuccess.copy(
                        isRefreshing = false,
                        transientError = currentResult.exceptionOrNull()?.localizedMessage ?: "Failed to refresh weather data."
                    )
                } else {
                    val kind = classifyError(currentResult.exceptionOrNull() ?: Exception())
                    _uiState.value = HomeUiState.Error(place = targetPlace, kind = kind)
                }
            }
        }
    }

    private fun classifyError(throwable: Throwable): ErrorKind {
        return when (throwable) {
            is IOException -> ErrorKind.Network
            else -> {
                val message = throwable.message ?: ""
                if (message.contains("5") || message.contains("Server") || message.contains("HTTP 5")) {
                    ErrorKind.Server
                } else if (message.contains("4") || message.contains("HTTP 4")) {
                    ErrorKind.Server
                } else {
                    ErrorKind.Unknown
                }
            }
        }
    }
}
