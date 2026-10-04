package com.katharina.weather.ui.home

import app.cash.turbine.test
import com.katharina.weather.domain.model.CurrentWeather
import com.katharina.weather.domain.model.DefaultPlace
import com.katharina.weather.domain.model.HourlyForecast
import com.katharina.weather.domain.model.WeatherCondition
import com.katharina.weather.domain.repository.WeatherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private val repository = FakeWeatherRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testLoadWeatherSuccess() =
        runTest {
            val weather =
                CurrentWeather(
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
                    stationDistance = 1000.0,
                )
            repository.currentWeatherResult = Result.success(weather)

            val viewModel = HomeViewModel(repository)

            viewModel.uiState.test {
                assertEquals(HomeUiState.Loading, awaitItem())

                viewModel.onStart()

                val success = awaitItem() as HomeUiState.Success
                assertEquals(weather, success.weather)
                assertEquals(DefaultPlace, success.place)
            }
        }

    @Test
    fun testLoadWeatherNetworkError() =
        runTest {
            repository.currentWeatherResult = Result.failure(IOException("Network error"))

            val viewModel = HomeViewModel(repository)

            viewModel.uiState.test {
                assertEquals(HomeUiState.Loading, awaitItem())

                viewModel.onStart()

                val error = awaitItem() as HomeUiState.Error
                assertEquals(ErrorKind.Network, error.kind)
            }
        }

    @Test
    fun testRefreshFailureKeepsDataAndShowsTransientError() =
        runTest {
            val weather =
                CurrentWeather(
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
                    stationDistance = 1000.0,
                )
            repository.currentWeatherResult = Result.success(weather)

            val viewModel = HomeViewModel(repository)
            viewModel.onStart()
            advanceUntilIdle()

            // Now refresh fails
            repository.currentWeatherResult = Result.failure(RuntimeException("Server error"))

            viewModel.uiState.test {
                val initial = awaitItem() as HomeUiState.Success
                assertEquals(weather, initial.weather)

                viewModel.refresh()

                val refreshing = awaitItem() as HomeUiState.Success
                assertTrue(refreshing.isRefreshing)

                val refreshed = awaitItem() as HomeUiState.Success
                assertEquals(weather, refreshed.weather)
                assertEquals("Server error", refreshed.transientError)
            }
        }
}

class FakeWeatherRepository : WeatherRepository {
    var currentWeatherResult: Result<CurrentWeather> = Result.failure(IllegalStateException("Not set"))

    override suspend fun getCurrentWeather(
        lat: Double,
        lon: Double,
        tz: String?,
    ): Result<CurrentWeather> = currentWeatherResult

    override suspend fun getWeather(
        lat: Double,
        lon: Double,
        days: Int,
        tz: String?,
    ): Result<List<HourlyForecast>> = Result.success(emptyList())
}
