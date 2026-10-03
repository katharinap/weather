package com.katharina.weather.domain.model

fun Int?.toCompassDirection(): String? {
    if (this == null) return null
    val directions = arrayOf(
        "N", "NNE", "NE", "ENE",
        "E", "ESE", "SE", "SSE",
        "S", "SSW", "SW", "WSW",
        "W", "WNW", "NW", "NNW"
    )
    val normalized = ((this % 360) + 360) % 360
    val index = ((normalized + 11.25) / 22.5).toInt() % 16
    return directions[index]
}
