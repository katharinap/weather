package com.katharina.weather.domain.model

data class RefreshOutcome(
    val current: Result<Unit>,
    val forecast: Result<Unit>,
    val alerts: Result<Unit> = Result.success(Unit)
) {
    val allFailed: Boolean get() = current.isFailure && forecast.isFailure && alerts.isFailure
    val firstError: Throwable? get() = current.exceptionOrNull() ?: forecast.exceptionOrNull() ?: alerts.exceptionOrNull()
}
