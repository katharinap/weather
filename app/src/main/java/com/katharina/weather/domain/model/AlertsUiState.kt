package com.katharina.weather.domain.model

import java.time.Instant

sealed interface AlertsUiState {
    data object Unknown : AlertsUiState

    data class Known(
        val visible: VisibleAlerts,
        val area: AlertArea?,
        val checkedAt: Instant,
        val isStale: Boolean,
        val isNotificationsEnabled: Boolean = false
    ) : AlertsUiState
}
