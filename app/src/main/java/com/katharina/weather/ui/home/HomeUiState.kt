package com.katharina.weather.ui.home

import com.katharina.weather.domain.model.AlertsUiState
import com.katharina.weather.domain.model.CurrentWeather
import com.katharina.weather.domain.model.DailyForecast
import com.katharina.weather.domain.model.HourlyForecast
import com.katharina.weather.domain.model.Place
import java.time.Instant

sealed interface HomeUiState {
    data object NoPlace : HomeUiState

    data object Loading : HomeUiState

    data class Success(
        val weather: CurrentWeather,
        val place: Place,
        val observedAt: Instant?,
        val updatedAt: Instant?,
        val hours24: List<HourlyForecast> = emptyList(),
        val hours48: List<HourlyForecast> = emptyList(),
        val days: List<DailyForecast> = emptyList(),
        val alertsUiState: AlertsUiState = AlertsUiState.Unknown,
        val isRefreshing: Boolean = false,
        val refreshFailed: Boolean = false,
        val isOutdated: Boolean = false,
        val outdatedHours: Long = 0,
        val bannerMessage: String? = null,
        val isOfflineBanner: Boolean = false,
        val userPullToRefreshError: String? = null
    ) : HomeUiState

    data class Error(
        val place: Place?,
        val kind: ErrorKind
    ) : HomeUiState
}

enum class ErrorKind {
    Network,
    Server,
    LocationPermissionRequired,
    LocationUnavailable,
    Unknown
}
