package com.katharina.weather.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.katharina.weather.domain.model.DefaultPlace
import com.katharina.weather.domain.model.Place
import com.katharina.weather.domain.repository.WeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Instant
import java.time.Duration
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

            val result = repository.getCurrentWeather(place.latitude, place.longitude)
            result.fold(
                onSuccess = { weather ->
                    lastFetchedTime = Instant.now()
                    _uiState.value = HomeUiState.Success(
                        weather = weather,
                        place = place,
                        fetchedAt = lastFetchedTime,
                        isRefreshing = false,
                        transientError = null
                    )
                },
                onFailure = { throwable ->
                    if (currentState is HomeUiState.Success) {
                        _uiState.value = currentState.copy(
                            isRefreshing = false,
                            transientError = throwable.localizedMessage ?: "Failed to refresh weather data."
                        )
                    } else {
                        val kind = classifyError(throwable)
                        _uiState.value = HomeUiState.Error(kind)
                    }
                }
            )
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
