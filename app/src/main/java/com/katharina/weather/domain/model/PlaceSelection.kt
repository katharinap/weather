package com.katharina.weather.domain.model

sealed interface PlaceSelection {
    data object None : PlaceSelection
    data object CurrentLocation : PlaceSelection
    data class Saved(val id: Long) : PlaceSelection
}
