package com.katharina.weather.data.remote.mapper

import com.katharina.weather.data.local.PlaceEntity
import com.katharina.weather.data.remote.dto.GeocodingResultDto
import com.katharina.weather.domain.model.Place
import com.katharina.weather.domain.model.PlaceSearchResult

fun GeocodingResultDto.toPlaceSearchResult(): PlaceSearchResult {
    val region = listOfNotNull(admin3, admin1).distinct().joinToString(", ").ifEmpty { null }
    return PlaceSearchResult(
        geonamesId = id,
        name = name,
        region = region,
        country = country,
        latitude = latitude,
        longitude = longitude,
    )
}

fun PlaceEntity.toPlace(): Place {
    return Place(
        id = id,
        name = name,
        region = region,
        country = country,
        latitude = latitude,
        longitude = longitude,
        isCurrentLocation = false
    )
}
