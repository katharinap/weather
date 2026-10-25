package com.katharina.weather.ui.places

import com.katharina.weather.domain.model.Place
import com.katharina.weather.domain.model.PlaceSearchResult
import com.katharina.weather.domain.model.PlaceSelection

data class PlacesUiState(
    val query: String = "",
    val searchState: SearchUiState = SearchUiState.Idle,
    val savedPlaces: List<Place> = emptyList(),
    val selection: PlaceSelection = PlaceSelection.None,
    val deletedPlaceMessage: String? = null
)

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Success(val results: List<PlaceSearchResult>) : SearchUiState
    data object Empty : SearchUiState
    data class Error(val message: String) : SearchUiState
}
