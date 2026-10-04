package com.katharina.weather.domain.model

enum class WeatherCondition {
    SUNNY,
    CLEAR_DAY,
    CLEAR_NIGHT,
    PARTLY_CLOUDY,
    PARTLY_CLOUDY_DAY,
    PARTLY_CLOUDY_NIGHT,
    CLOUDY,
    RAIN,
    SNOW,
    SLEET,
    HAIL,
    THUNDERSTORM,
    FOG,
    WINDY,
    UNKNOWN;

    fun toDayVariant(): WeatherCondition = when (this) {
        CLEAR_NIGHT -> CLEAR_DAY
        PARTLY_CLOUDY_NIGHT -> PARTLY_CLOUDY_DAY
        SUNNY -> CLEAR_DAY
        PARTLY_CLOUDY -> PARTLY_CLOUDY_DAY
        else -> this
    }

    companion object {
        fun fromIcon(icon: String?): WeatherCondition {
            return when (icon?.lowercase()) {
                "sunny", "clear-day" -> CLEAR_DAY
                "clear-night" -> CLEAR_NIGHT
                "partly-cloudy-day" -> PARTLY_CLOUDY_DAY
                "partly-cloudy-night" -> PARTLY_CLOUDY_NIGHT
                "partly-cloudy" -> PARTLY_CLOUDY_DAY
                "cloudy", "overcast" -> CLOUDY
                "rain", "heavy-rain", "light-rain" -> RAIN
                "snow", "heavy-snow", "light-snow" -> SNOW
                "sleet" -> SLEET
                "hail" -> HAIL
                "thunderstorm" -> THUNDERSTORM
                "fog", "foggy" -> FOG
                "wind", "windy" -> WINDY
                else -> UNKNOWN
            }
        }
    }
}
