package com.katharina.weather.domain.model

data class PlaceSearchResult(
    val geonamesId: Long,
    val name: String,
    val region: String?,
    val country: String?,
    val latitude: Double,
    val longitude: Double,
)
