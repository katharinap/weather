package com.katharina.weather.ui.places

import app.cash.turbine.test
import com.katharina.weather.domain.model.Place
import com.katharina.weather.domain.model.PlaceSearchResult
import com.katharina.weather.domain.model.PlaceSelection
import com.katharina.weather.domain.repository.GeocodingRepository
import com.katharina.weather.domain.repository.PlaceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlacesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fakePlaceRepository = FakePlaceRepositoryForPlaces()
    private val fakeGeocodingRepository = FakeGeocodingRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testMinLengthTwoDoesNotSearch() = runTest {
        val viewModel = PlacesViewModel(fakePlaceRepository, fakeGeocodingRepository)

        viewModel.uiState.test {
            val initial = awaitItem()
            assertEquals(SearchUiState.Idle, initial.searchState)

            viewModel.onQueryChanged("a")
            testScheduler.advanceTimeBy(400)

            val updated = awaitItem()
            assertEquals("a", updated.query)
            assertEquals(SearchUiState.Idle, updated.searchState)
            assertEquals(0, fakeGeocodingRepository.searchCallCount)
        }
    }

    @Test
    fun testDebounceAndSearchSuccess() = runTest {
        val results = listOf(
            PlaceSearchResult(2922582L, "Garching", "Munich, Bavaria", "Germany", 48.248, 11.651)
        )
        fakeGeocodingRepository.searchResult = Result.success(results)

        val viewModel = PlacesViewModel(fakePlaceRepository, fakeGeocodingRepository)

        viewModel.uiState.test {
            awaitItem() // initial

            viewModel.onQueryChanged("Garching")
            testScheduler.advanceTimeBy(350)

            skipItems(1)
            val item = awaitItem()
            val finalState = if (item.searchState is SearchUiState.Loading) awaitItem() else item
            assertTrue(finalState.searchState is SearchUiState.Success)
            assertEquals(results, (finalState.searchState as SearchUiState.Success).results)
        }
    }

    @Test
    fun testSearchEmpty() = runTest {
        fakeGeocodingRepository.searchResult = Result.success(emptyList())

        val viewModel = PlacesViewModel(fakePlaceRepository, fakeGeocodingRepository)

        viewModel.uiState.test {
            awaitItem()

            viewModel.onQueryChanged("zzzzqxw")
            testScheduler.advanceTimeBy(350)

            skipItems(1)
            val item = awaitItem()
            val finalState = if (item.searchState is SearchUiState.Loading) awaitItem() else item
            assertEquals(SearchUiState.Empty, finalState.searchState)
        }
    }

    @Test
    fun testSearchError() = runTest {
        fakeGeocodingRepository.searchResult = Result.failure(RuntimeException("No connection"))

        val viewModel = PlacesViewModel(fakePlaceRepository, fakeGeocodingRepository)

        viewModel.uiState.test {
            awaitItem()

            viewModel.onQueryChanged("Munich")
            testScheduler.advanceTimeBy(350)

            skipItems(1)
            val item = awaitItem()
            val finalState = if (item.searchState is SearchUiState.Loading) awaitItem() else item
            assertTrue(finalState.searchState is SearchUiState.Error)
        }
    }
}

class FakePlaceRepositoryForPlaces : PlaceRepository {
    val placesFlow = MutableStateFlow<List<Place>>(emptyList())
    val selectionFlow = MutableStateFlow<PlaceSelection>(PlaceSelection.None)

    override val savedPlaces: Flow<List<Place>> = placesFlow
    override val selection: Flow<PlaceSelection> = selectionFlow

    override suspend fun save(result: PlaceSearchResult): Place {
        val place = Place(result.geonamesId, result.name, result.region, result.country, result.latitude, result.longitude)
        placesFlow.value = placesFlow.value + place
        return place
    }

    override suspend fun delete(id: Long) {
        placesFlow.value = placesFlow.value.filter { it.id != id }
    }

    override suspend fun select(selection: PlaceSelection) {
        selectionFlow.value = selection
    }
}

class FakeGeocodingRepository : GeocodingRepository {
    var searchResult: Result<List<PlaceSearchResult>> = Result.success(emptyList())
    var searchCallCount = 0

    override suspend fun search(query: String): Result<List<PlaceSearchResult>> {
        searchCallCount++
        return searchResult
    }
}
