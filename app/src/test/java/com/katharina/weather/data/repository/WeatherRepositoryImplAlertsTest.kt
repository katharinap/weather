package com.katharina.weather.data.repository

import com.katharina.weather.data.remote.dto.AlertsResponseDto
import com.katharina.weather.data.remote.dto.CurrentWeatherEntryDto
import com.katharina.weather.data.remote.dto.CurrentWeatherResponseDto
import com.katharina.weather.data.remote.dto.WeatherResponseDto
import com.katharina.weather.domain.model.Place
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class WeatherRepositoryImplAlertsTest {

    private lateinit var fakeDao: FakeWeatherCacheDao
    private lateinit var fakeApi: FakeBrightSkyApi
    private lateinit var repository: WeatherRepositoryImpl
    private val clock = Clock.fixed(Instant.parse("2026-10-30T14:00:00Z"), ZoneId.of("UTC"))
    private val place = Place(1L, "Garching", "Bavaria", "Germany", 48.248, 11.651)

    @Before
    fun setUp() {
        fakeDao = FakeWeatherCacheDao()
        fakeApi = FakeBrightSkyApi()
        repository = WeatherRepositoryImpl(fakeApi, fakeDao, clock)
    }

    @Test
    fun testAlertsFailureDoesNotBlockWeatherRefresh() = runBlocking {
        fakeApi.currentDto = CurrentWeatherResponseDto(weather = CurrentWeatherEntryDto(temperature = 20.0, icon = "sunny"))
        fakeApi.weatherDto = WeatherResponseDto(weather = emptyList())
        fakeApi.alertsException = IOException("Alerts API error")

        val outcome = repository.refresh(place)

        assertTrue(outcome.current.isSuccess)
        assertTrue(outcome.forecast.isSuccess)
        assertTrue(outcome.alerts.isFailure)

        // Verify current and forecast stored in cache (2 entities)
        assertEquals(2, fakeDao.entities.size)
    }
}
