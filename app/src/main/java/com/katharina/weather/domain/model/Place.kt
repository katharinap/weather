package com.katharina.weather.domain.model

data class Place(
    val name: String,
    val latitude: Double,
    val longitude: Double
)

val DefaultPlace = Place("Munich", 48.137, 11.575)
