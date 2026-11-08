package com.katharina.weather.domain.notification

import com.katharina.weather.data.local.SeenAlertEntity
import com.katharina.weather.domain.model.AlertSeverity
import com.katharina.weather.domain.model.WeatherAlert
import java.time.Instant

val AlertSeverity.rank: Int
    get() = when (this) {
        AlertSeverity.UNKNOWN -> 0
        AlertSeverity.MINOR -> 1
        AlertSeverity.MODERATE -> 2
        AlertSeverity.SEVERE -> 3
        AlertSeverity.EXTREME -> 4
    }

fun selectAlertsToNotify(
    alerts: List<WeatherAlert>,
    now: Instant,
    minSeverity: AlertSeverity = AlertSeverity.SEVERE,
    seenAlertIds: Set<String>,
    recentAlerts: List<SeenAlertEntity>,
    isFirstRun: Boolean = false
): List<WeatherAlert> {
    if (isFirstRun) return emptyList()

    return alerts.filter { alert ->
        // Rule 2: Candidate must be active (not upcoming, not expired)
        val isActive = (alert.onset == null || alert.onset <= now) &&
                (alert.expires == null || alert.expires > now)
        if (!isActive) return@filter false

        // Rule 2: Candidate severity >= minSeverity
        if (alert.severity.rank < minSeverity.rank) return@filter false

        // Rule 3: Skip if alertId is already seen
        if (alert.id in seenAlertIds) return@filter false

        // Rule 4: Check recent alerts for same eventKey
        val eventKey = alert.eventCode?.toString() ?: alert.event
        val recentForEvent = recentAlerts.filter { it.eventKey == eventKey }
        val highestRecentRank = recentForEvent.maxOfOrNull { AlertSeverity.parse(it.severity).rank } ?: -1

        // Notify if candidate severity is strictly higher than recent severity for the same event
        alert.severity.rank > highestRecentRank
    }
}
