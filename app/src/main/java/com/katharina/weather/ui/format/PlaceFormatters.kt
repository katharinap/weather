package com.katharina.weather.ui.format

fun formatPlaceSubtitle(region: String?): String {
    if (region.isNullOrBlank()) return ""
    return region
        .split(",")
        .map { it.trim() }
        .filterNot { it.equals("Urban District", ignoreCase = true) }
        .joinToString(", ")
}
