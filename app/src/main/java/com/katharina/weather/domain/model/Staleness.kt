package com.katharina.weather.domain.model

import java.time.Duration
import java.time.Instant

object Staleness {
    val CURRENT_MAX_AGE: Duration = Duration.ofMinutes(15)
    val FORECAST_MAX_AGE: Duration = Duration.ofMinutes(60)
    val OUTDATED_AFTER: Duration = Duration.ofHours(6)
    val MIN_REFRESH_INTERVAL: Duration = Duration.ofSeconds(60)

    fun needsRefresh(s: WeatherSnapshot, now: Instant): Boolean {
        if (s.current == null || s.forecast == null) return true
        val currentAge = Duration.between(s.current.fetchedAt, now)
        val forecastAge = Duration.between(s.forecast.fetchedAt, now)
        return currentAge > CURRENT_MAX_AGE || forecastAge > FORECAST_MAX_AGE
    }

    fun isOutdated(s: WeatherSnapshot, now: Instant): Boolean {
        if (s.current == null) return false
        val currentAge = Duration.between(s.current.fetchedAt, now)
        return currentAge > OUTDATED_AFTER
    }
}
