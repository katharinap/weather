package com.katharina.weather.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.katharina.weather.domain.forecast.toDailyForecasts
import com.katharina.weather.domain.forecast.upcoming
import com.katharina.weather.domain.model.DefaultPlace
import com.katharina.weather.domain.model.Place
import com.katharina.weather.domain.repository.WeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: WeatherRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val place: Place = DefaultPlace
    private var lastFetchedTime: Instant? = null

    fun onStart() {
        val currentState = _uiState.value
        if (currentState is HomeUiState.Success) {
            if (lastFetchedTime != null && Duration.between(lastFetchedTime, Instant.now()).toMinutes() >= 15) {
                refresh()
            }
        } else if (currentState is HomeUiState.Loading) {
            loadWeather(isRefreshing = false)
        }
    }

    fun refresh() {
        loadWeather(isRefreshing = true)
    }

    fun loadWeather(isRefreshing: Boolean) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (isRefreshing && currentState is HomeUiState.Success) {
                _uiState.update { currentState.copy(isRefreshing = true, transientError = null) }
            } else if (!isRefreshing) {
                _uiState.value = HomeUiState.Loading
            }

            val (currentResult, forecastResult) = coroutineScope {
                val c = async { repository.getCurrentWeather(place.latitude, place.longitude) }
                val f = async { repository.getWeather(place.latitude, place.longitude, days = 7) }
                c.await() to f.await()
            }

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
                    place = place,
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
                    _uiState.value = HomeUiState.Error(kind)
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
