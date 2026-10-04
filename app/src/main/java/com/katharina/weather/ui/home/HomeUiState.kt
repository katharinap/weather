package com.katharina.weather.ui.home

import com.katharina.weather.domain.model.CurrentWeather
import com.katharina.weather.domain.model.DailyForecast
import com.katharina.weather.domain.model.HourlyForecast
import com.katharina.weather.domain.model.Place
import java.time.Instant

sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Success(
        val weather: CurrentWeather,
        val place: Place,
        val fetchedAt: Instant?,
        val forecast: ForecastState = ForecastState.Unavailable,
        val isRefreshing: Boolean = false,
        val transientError: String? = null
    ) : HomeUiState

    data class Error(val kind: ErrorKind) : HomeUiState
}

sealed interface ForecastState {
    data class Loaded(
        val hours24: List<HourlyForecast>,
        val hours48: List<HourlyForecast>,
        val days: List<DailyForecast>
    ) : ForecastState

    data object Unavailable : ForecastState
}

enum class ErrorKind {
    Network,
    Server,
    Unknown
}
