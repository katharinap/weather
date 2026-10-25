package com.katharina.weather.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GeocodingResponseDto(
    val results: List<GeocodingResultDto> = emptyList()
)

@Serializable
data class GeocodingResultDto(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val admin1: String? = null,
    val admin3: String? = null,
    val country: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    val population: Long? = null,
)
