package com.katharina.weather.ui.home

import app.cash.turbine.test
import com.katharina.weather.domain.model.CurrentWeather
import com.katharina.weather.domain.model.HourlyForecast
import com.katharina.weather.domain.model.Place
import com.katharina.weather.domain.model.PlaceSearchResult
import com.katharina.weather.domain.model.PlaceSelection
import com.katharina.weather.domain.model.WeatherCondition
import com.katharina.weather.domain.repository.PlaceRepository
import com.katharina.weather.domain.repository.WeatherRepository
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
import java.io.IOException
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val weatherRepository = FakeWeatherRepositoryForHome()
    private val placeRepository = FakePlaceRepositoryForHome()

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
        val viewModel = HomeViewModel(placeRepository, weatherRepository)

        viewModel.uiState.test {
            val item = awaitItem()
            val finalItem = if (item is HomeUiState.Loading) awaitItem() else item
            assertEquals(HomeUiState.NoPlace, finalItem)
        }
    }

    @Test
    fun testPlaceSelectionLoadsWeather() = runTest {
        val place = Place(1L, "Garching", "Munich, Bavaria", "Germany", 48.248, 11.651)
        val weather = CurrentWeather(
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
        weatherRepository.currentWeatherResult = Result.success(weather)
        weatherRepository.weatherForecastResult = Result.success(emptyList())

        val viewModel = HomeViewModel(placeRepository, weatherRepository)

        viewModel.uiState.test {
            awaitItem() // Loading or NoPlace

            placeRepository.placesFlow.value = listOf(place)
            placeRepository.selectionFlow.value = PlaceSelection.Saved(1L)

            val next = awaitItem()
            val success = if (next is HomeUiState.Loading) awaitItem() as HomeUiState.Success else next as HomeUiState.Success
            assertEquals(weather, success.weather)
            assertEquals(place, success.place)
        }
    }

    @Test
    fun testLoadWeatherNetworkError() = runTest {
        val place = Place(1L, "Munich", "Bavaria", "Germany", 48.137, 11.575)
        placeRepository.placesFlow.value = listOf(place)
        placeRepository.selectionFlow.value = PlaceSelection.Saved(1L)
        weatherRepository.currentWeatherResult = Result.failure(IOException("Network error"))

        val viewModel = HomeViewModel(placeRepository, weatherRepository)

        viewModel.uiState.test {
            awaitItem() // Loading

            val error = awaitItem() as HomeUiState.Error
            assertEquals(ErrorKind.Network, error.kind)
        }
    }

    @Test
    fun testForecastOnlyFailureKeepsCurrentWeather() = runTest {
        val place = Place(1L, "Munich", "Bavaria", "Germany", 48.137, 11.575)
        val weather = CurrentWeather(
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
        placeRepository.placesFlow.value = listOf(place)
        placeRepository.selectionFlow.value = PlaceSelection.Saved(1L)
        weatherRepository.currentWeatherResult = Result.success(weather)
        weatherRepository.weatherForecastResult = Result.failure(IOException("Forecast error"))

        val viewModel = HomeViewModel(placeRepository, weatherRepository)

        viewModel.uiState.test {
            awaitItem() // Loading

            val success = awaitItem() as HomeUiState.Success
            assertEquals(weather, success.weather)
            assertEquals(ForecastState.Unavailable, success.forecast)
        }
    }

    @Test
    fun testSwitchingPlaceCancelsStaleResponse() = runTest {
        val place1 = Place(1L, "City1", null, null, 10.0, 10.0)
        val place2 = Place(2L, "City2", null, null, 20.0, 20.0)

        val weather1 = CurrentWeather(
            timestamp = Instant.now(),
            temperature = 15.0,
            condition = WeatherCondition.SUNNY,
            icon = "sunny",
            windSpeed = 5.0,
            windGustSpeed = 10.0,
            windDirection = 90,
            cloudCover = 0,
            relativeHumidity = 50,
            precipitation10 = 0.0,
            precipitation60 = 0.0,
            stationName = "City1",
            stationDistance = 500.0
        )

        val weather2 = CurrentWeather(
            timestamp = Instant.now(),
            temperature = 25.0,
            condition = WeatherCondition.SUNNY,
            icon = "sunny",
            windSpeed = 5.0,
            windGustSpeed = 10.0,
            windDirection = 90,
            cloudCover = 0,
            relativeHumidity = 50,
            precipitation10 = 0.0,
            precipitation60 = 0.0,
            stationName = "City2",
            stationDistance = 500.0
        )

        weatherRepository.currentWeatherResult = Result.success(weather1)
        placeRepository.placesFlow.value = listOf(place1, place2)
        placeRepository.selectionFlow.value = PlaceSelection.Saved(1L)

        val viewModel = HomeViewModel(placeRepository, weatherRepository)

        viewModel.uiState.test {
            val firstItem = awaitItem()
            val initialSuccess = if (firstItem is HomeUiState.Loading) awaitItem() as HomeUiState.Success else firstItem as HomeUiState.Success
            assertEquals(place1, initialSuccess.place)

            // Switch to place2
            weatherRepository.currentWeatherResult = Result.success(weather2)
            placeRepository.selectionFlow.value = PlaceSelection.Saved(2L)

            val loading = awaitItem()
            assertTrue(loading is HomeUiState.Loading)

            val success2 = awaitItem() as HomeUiState.Success
            assertEquals(place2, success2.place)
            assertEquals(25.0, success2.weather.temperature, 0.001)
        }
    }
}

class FakePlaceRepositoryForHome : PlaceRepository {
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

class FakeWeatherRepositoryForHome : WeatherRepository {
    var currentWeatherResult: Result<CurrentWeather> = Result.failure(IllegalStateException("Not set"))
    var weatherForecastResult: Result<List<HourlyForecast>> = Result.success(emptyList())

    override suspend fun getCurrentWeather(lat: Double, lon: Double, tz: String?): Result<CurrentWeather> {
        return currentWeatherResult
    }

    override suspend fun getWeather(lat: Double, lon: Double, days: Int, tz: String?): Result<List<HourlyForecast>> {
        return weatherForecastResult
    }
}
