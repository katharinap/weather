package com.katharina.weather.domain.model

enum class AlertSeverity {
    MINOR,
    MODERATE,
    SEVERE,
    EXTREME,
    UNKNOWN;

    companion object {
        fun parse(value: String?) = when (value?.lowercase()?.trim()) {
            "minor" -> MINOR
            "moderate" -> MODERATE
            "severe" -> SEVERE
            "extreme" -> EXTREME
            else -> UNKNOWN
        }
    }
}
