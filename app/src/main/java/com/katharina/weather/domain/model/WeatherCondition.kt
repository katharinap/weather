package com.katharina.weather.domain.model

enum class WeatherCondition {
    SUNNY,
    PARTLY_CLOUDY,
    CLOUDY,
    RAIN,
    SNOW,
    THUNDERSTORM,
    FOG,
    WINDY,
    UNKNOWN;

    companion object {
        fun fromIcon(icon: String?): WeatherCondition {
            return when (icon?.lowercase()) {
                "sunny", "clear-day" -> SUNNY
                "clear-night" -> SUNNY
                "partly-cloudy-day", "partly-cloudy-night" -> PARTLY_CLOUDY
                "cloudy" -> CLOUDY
                "rain", "heavy-rain", "light-rain" -> RAIN
                "snow", "heavy-snow", "light-snow" -> SNOW
                "thunderstorm" -> THUNDERSTORM
                "fog" -> FOG
                "wind" -> WINDY
                else -> UNKNOWN
            }
        }
    }
}
