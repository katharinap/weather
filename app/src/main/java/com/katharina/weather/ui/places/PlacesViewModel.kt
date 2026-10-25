package com.katharina.weather.ui.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.katharina.weather.domain.model.Place
import com.katharina.weather.domain.model.PlaceSearchResult
import com.katharina.weather.domain.model.PlaceSelection
import com.katharina.weather.domain.repository.GeocodingRepository
import com.katharina.weather.domain.repository.PlaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlacesViewModel @Inject constructor(
    private val placeRepository: PlaceRepository,
    private val geocodingRepository: GeocodingRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _searchState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)

    private var lastDeletedPlaceResult: PlaceSearchResult? = null

    val uiState: StateFlow<PlacesUiState> = combine(
        _query,
        _searchState,
        placeRepository.savedPlaces,
        placeRepository.selection
    ) { query, searchState, savedPlaces, selection ->
        PlacesUiState(
            query = query,
            searchState = searchState,
            savedPlaces = savedPlaces,
            selection = selection
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PlacesUiState()
    )

    init {
        _query
            .debounce(300)
            .distinctUntilChanged()
            .flatMapLatest { query ->
                flow {
                    val trimmed = query.trim()
                    if (trimmed.length < 2) {
                        emit(SearchUiState.Idle)
                    } else {
                        emit(SearchUiState.Loading)
                        val result = geocodingRepository.search(trimmed)
                        result.fold(
                            onSuccess = { list ->
                                if (list.isEmpty()) {
                                    emit(SearchUiState.Empty)
                                } else {
                                    emit(SearchUiState.Success(list))
                                }
                            },
                            onFailure = {
                                emit(SearchUiState.Error("Search needs an internet connection."))
                            }
                        )
                    }
                }
            }
            .onEach { state -> _searchState.value = state }
            .launchIn(viewModelScope)
    }

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
    }

    fun onSearchResultClicked(result: PlaceSearchResult, onSaved: () -> Unit) {
        viewModelScope.launch {
            val savedPlace = placeRepository.save(result)
            placeRepository.select(PlaceSelection.Saved(savedPlace.id))
            _query.value = ""
            _searchState.value = SearchUiState.Idle
            onSaved()
        }
    }

    fun onSelectSavedPlace(selection: PlaceSelection, onSelected: () -> Unit) {
        viewModelScope.launch {
            placeRepository.select(selection)
            onSelected()
        }
    }

    fun onDeletePlace(place: Place) {
        viewModelScope.launch {
            lastDeletedPlaceResult = PlaceSearchResult(
                geonamesId = place.id,
                name = place.name,
                region = place.region,
                country = place.country,
                latitude = place.latitude,
                longitude = place.longitude
            )
            placeRepository.delete(place.id)
        }
    }

    fun undoDelete() {
        val deletedResult = lastDeletedPlaceResult ?: return
        viewModelScope.launch {
            val restored = placeRepository.save(deletedResult)
            placeRepository.select(PlaceSelection.Saved(restored.id))
            lastDeletedPlaceResult = null
        }
    }
}
