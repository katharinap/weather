package com.katharina.weather.domain.model

import java.time.Instant

data class WeatherAlert(
    val id: String,                 // alert_id
    val eventCode: Int?,
    val event: String,              // event_en ?: event_de ?: "Weather warning"
    val headline: String?,
    val description: String?,
    val instruction: String?,
    val severity: AlertSeverity,
    val effective: Instant?,
    val onset: Instant?,
    val expires: Instant?
)

data class AlertArea(
    val name: String?,
    val district: String?,
    val state: String?
)

data class AlertsResult(
    val alerts: List<WeatherAlert>,
    val area: AlertArea?
)

data class VisibleAlerts(
    val active: List<WeatherAlert>,
    val upcoming: List<WeatherAlert>
)

/** Expired alerts are dropped; both lists sorted by severity (desc), then onset. */
fun List<WeatherAlert>.visibleAt(now: Instant): VisibleAlerts {
    val live = filter { it.expires == null || it.expires > now }
    val order = compareByDescending<WeatherAlert> {
        it.severity.ordinal.takeIf { s -> s != AlertSeverity.UNKNOWN.ordinal } ?: -1
    }.thenBy { it.onset ?: Instant.MIN }

    val (upcoming, active) = live.partition { it.onset != null && it.onset > now }
    return VisibleAlerts(active.sortedWith(order), upcoming.sortedWith(order))
}
