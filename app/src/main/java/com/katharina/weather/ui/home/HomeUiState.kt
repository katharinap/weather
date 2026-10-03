package com.katharina.weather.ui.home

import com.katharina.weather.domain.model.CurrentWeather
import com.katharina.weather.domain.model.Place
import java.time.Instant

sealed interface HomeUiState {
    object Loading : HomeUiState

    data class Success(
        val weather: CurrentWeather,
        val place: Place,
        val fetchedAt: Instant?,
        val isRefreshing: Boolean = false,
        val transientError: String? = null
    ) : HomeUiState

    data class Error(val kind: ErrorKind) : HomeUiState
}

enum class ErrorKind {
    Network,
    Server,
    Unknown
}
