package com.katharina.weather.domain.model

data class Place(
    val id: Long,
    val name: String,
    val region: String?,
    val country: String?,
    val latitude: Double,
    val longitude: Double,
    val isCurrentLocation: Boolean = false
)

const val CURRENT_LOCATION_ID = -1L

fun Place.cacheKey(): String = if (isCurrentLocation) "current" else "saved:$id"
