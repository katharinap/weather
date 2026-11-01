package com.katharina.weather.ui.home

import app.cash.turbine.test
import com.katharina.weather.data.local.LocationDataStore
import com.katharina.weather.data.local.WeatherCacheDao
import com.katharina.weather.data.location.GeocoderHelper
import com.katharina.weather.domain.model.*
import com.katharina.weather.domain.repository.LocationProvider
import com.katharina.weather.domain.repository.PlaceRepository
import com.katharina.weather.domain.repository.WeatherRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val fakeWeatherRepository = FakeWeatherRepository()
    private val fakePlaceRepository = FakePlaceRepository()
    private val fakeLocationProvider = FakeLocationProvider()
    private val fakeLocationDataStore: LocationDataStore = mockk {
        coEvery { lastLocation } returns MutableStateFlow(null)
    }
    private val fakeWeatherCacheDao: WeatherCacheDao = mockk(relaxed = true)
    private val geocoderHelper: GeocoderHelper = mockk {
        coEvery { getPlaceName(any()) } returns "Current location"
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialNoPlace() = runTest {
        val viewModel = HomeViewModel(
            fakePlaceRepository,
            fakeWeatherRepository,
            fakeLocationProvider,
            geocoderHelper,
            fakeLocationDataStore,
            fakeWeatherCacheDao
        )

        viewModel.uiState.test {
            val item1 = awaitItem()
            val finalItem = if (item1 is HomeUiState.Loading) awaitItem() else item1
            assertEquals(HomeUiState.NoPlace, finalItem)
        }
    }

    @Test
    fun testCachedDataShownWithoutLoadingState() = runTest {
        val place = Place(1L, "Garching", "Munich, Bavaria", "Germany", 48.248, 11.651)
        val currentWeather = mockCurrentWeather()
        val snapshot = WeatherSnapshot(
            current = Cached(currentWeather, Instant.now()),
            forecast = Cached(emptyList(), Instant.now())
        )

        fakePlaceRepository.placesFlow.value = listOf(place)
        fakePlaceRepository.selectionFlow.value = PlaceSelection.Saved(1L)
        fakeWeatherRepository.snapshots[place.cacheKey()] = snapshot

        val viewModel = HomeViewModel(
            fakePlaceRepository,
            fakeWeatherRepository,
            fakeLocationProvider,
            geocoderHelper,
            fakeLocationDataStore,
            fakeWeatherCacheDao
        )

        viewModel.uiState.test {
            val item1 = awaitItem()
            val success = if (item1 is HomeUiState.Loading) awaitItem() as HomeUiState.Success else item1 as HomeUiState.Success
            assertEquals(currentWeather, success.weather)
            assertEquals(place, success.place)
            assertFalse(success.isRefreshing)
            assertFalse(success.refreshFailed)
        }
    }

    @Test
    fun testFailedRefreshKeepsCachedDataAndSetsBanner() = runTest {
        val place = Place(1L, "Garching", "Munich, Bavaria", "Germany", 48.248, 11.651)
        val currentWeather = mockCurrentWeather()
        val snapshot = WeatherSnapshot(
            current = Cached(currentWeather, Instant.now()),
            forecast = Cached(emptyList(), Instant.now())
        )

        fakePlaceRepository.placesFlow.value = listOf(place)
        fakePlaceRepository.selectionFlow.value = PlaceSelection.Saved(1L)
        fakeWeatherRepository.snapshots[place.cacheKey()] = snapshot
        fakeWeatherRepository.refreshOutcome = RefreshOutcome(
            current = Result.failure(IOException("Network error")),
            forecast = Result.failure(IOException("Network error"))
        )

        val viewModel = HomeViewModel(
            fakePlaceRepository,
            fakeWeatherRepository,
            fakeLocationProvider,
            geocoderHelper,
            fakeLocationDataStore,
            fakeWeatherCacheDao
        )

        viewModel.uiState.test {
            val item1 = awaitItem()
            val success = if (item1 is HomeUiState.Loading) awaitItem() as HomeUiState.Success else item1 as HomeUiState.Success
            assertEquals(currentWeather, success.weather)

            viewModel.refresh(userInitiated = false)

            val nextItem = awaitItem()
            val finalSuccess = if (nextItem is HomeUiState.Success && nextItem.isRefreshing) awaitItem() as HomeUiState.Success else nextItem as HomeUiState.Success

            assertEquals(currentWeather, finalSuccess.weather)
            assertTrue(finalSuccess.refreshFailed)
            assertNotNull(finalSuccess.bannerMessage)
        }
    }

    @Test
    fun testNoCacheAndRefreshFailureShowsError() = runTest {
        val place = Place(1L, "Garching", "Munich, Bavaria", "Germany", 48.248, 11.651)
        fakePlaceRepository.placesFlow.value = listOf(place)
        fakePlaceRepository.selectionFlow.value = PlaceSelection.Saved(1L)
        fakeWeatherRepository.refreshOutcome = RefreshOutcome(
            current = Result.failure(IOException("Network error")),
            forecast = Result.failure(IOException("Network error"))
        )

        val viewModel = HomeViewModel(
            fakePlaceRepository,
            fakeWeatherRepository,
            fakeLocationProvider,
            geocoderHelper,
            fakeLocationDataStore,
            fakeWeatherCacheDao
        )

        viewModel.uiState.test {
            runCurrent()
            viewModel.refresh(userInitiated = false)
            runCurrent()
            val events = cancelAndConsumeRemainingEvents()
            val error = events
                .filterIsInstance<app.cash.turbine.Event.Item<HomeUiState>>()
                .map { it.value }
                .filterIsInstance<HomeUiState.Error>()
                .last()
            assertEquals(ErrorKind.Network, error.kind)
        }
    }

    private fun mockCurrentWeather() = CurrentWeather(
        timestamp = Instant.now(),
        temperature = 20.0,
        condition = WeatherCondition.SUNNY,
        icon = "sunny",
        windSpeed = 5.0,
        windGustSpeed = 10.0,
        windDirection = 90,
        cloudCover = 0,
        relativeHumidity = 50,
        precipitation10 = 0.0,
        precipitation60 = 0.0,
        stationName = "Munich",
        stationDistance = 1000.0
    )
}

class FakePlaceRepository : PlaceRepository {
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

class FakeWeatherRepository : WeatherRepository {
    val snapshots = mutableMapOf<String, WeatherSnapshot>()
    var refreshOutcome = RefreshOutcome(Result.success(Unit), Result.success(Unit))

    override fun observe(place: Place): Flow<WeatherSnapshot> {
        return MutableStateFlow(snapshots[place.cacheKey()] ?: WeatherSnapshot(null, null))
    }

    override suspend fun refresh(place: Place): RefreshOutcome {
        return refreshOutcome
    }
}

class FakeLocationProvider : LocationProvider {
    var result: Result<Coordinates> = Result.failure(LocationUnavailableException())

    override suspend fun currentLocation(): Result<Coordinates> {
        return result
    }
}
