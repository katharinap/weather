package com.katharina.weather.domain.model

data class RefreshOutcome(
    val current: Result<Unit>,
    val forecast: Result<Unit>
) {
    val allFailed: Boolean get() = current.isFailure && forecast.isFailure
    val firstError: Throwable? get() = current.exceptionOrNull() ?: forecast.exceptionOrNull()
}
