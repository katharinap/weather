package com.katharina.weather.data.repository

import app.cash.turbine.test
import com.katharina.weather.data.local.WeatherCacheDao
import com.katharina.weather.data.local.WeatherCacheEntity
import com.katharina.weather.data.local.deleteOrphans
import com.katharina.weather.data.remote.BrightSkyApi
import com.katharina.weather.data.remote.dto.AlertsResponseDto
import com.katharina.weather.data.remote.dto.CurrentWeatherEntryDto
import com.katharina.weather.data.remote.dto.CurrentWeatherResponseDto
import com.katharina.weather.data.remote.dto.WeatherResponseDto
import com.katharina.weather.domain.model.Place
import com.katharina.weather.domain.model.cacheKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class WeatherRepositoryImplTest {

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
    fun testRefreshStoresBothParts() = runBlocking {
        fakeApi.currentDto = CurrentWeatherResponseDto(weather = CurrentWeatherEntryDto(temperature = 20.0, icon = "sunny"))
        fakeApi.weatherDto = WeatherResponseDto(weather = emptyList())

        val outcome = repository.refresh(place)

        assertTrue(outcome.current.isSuccess)
        assertTrue(outcome.forecast.isSuccess)
        assertEquals(3, fakeDao.entities.size) // current, forecast, alerts
    }

    @Test
    fun testPartialFailureKeepsOldValue() = runBlocking {
        // Pre-populate old current weather in cache
        fakeDao.upsert(
            WeatherCacheEntity(
                placeKey = place.cacheKey(),
                kind = "current",
                fetchedAt = 1000L,
                formatVersion = CACHE_FORMAT_VERSION,
                latitude = place.latitude,
                longitude = place.longitude,
                payload = """{"weather":{"temperature":15.0,"icon":"cloudy"}}"""
            )
        )

        // API: current weather fails, forecast succeeds
        fakeApi.currentException = IOException("Network error")
        fakeApi.weatherDto = WeatherResponseDto(weather = emptyList())

        val outcome = repository.refresh(place)

        assertTrue(outcome.current.isFailure)
        assertTrue(outcome.forecast.isSuccess)

        // Verify current weather in cache is unchanged (temperature 15.0)
        val currentEntity = fakeDao.find(place.cacheKey(), "current")
        assertNotNull(currentEntity)
        assertEquals(1000L, currentEntity!!.fetchedAt)
    }

    @Test
    fun testWrongFormatVersionAndCorruptedPayloadTreatedAsMissing() = runBlocking {
        // Wrong formatVersion
        fakeDao.upsert(
            WeatherCacheEntity(
                placeKey = place.cacheKey(),
                kind = "current",
                fetchedAt = 1000L,
                formatVersion = 999, // wrong
                latitude = place.latitude,
                longitude = place.longitude,
                payload = """{"weather":{"temperature":20.0}}"""
            )
        )

        // Corrupted JSON payload
        fakeDao.upsert(
            WeatherCacheEntity(
                placeKey = place.cacheKey(),
                kind = "forecast",
                fetchedAt = 1000L,
                formatVersion = CACHE_FORMAT_VERSION,
                latitude = place.latitude,
                longitude = place.longitude,
                payload = "CORRUPTED_JSON_NOT_VALID"
            )
        )

        repository.observe(place).test {
            val snapshot = awaitItem()
            assertNull(snapshot.current)
            assertNull(snapshot.forecast)
        }
    }

    @Test
    fun testDeleteOrphansAndDeleteOlderThan() = runBlocking {
        fakeDao.upsert(WeatherCacheEntity("saved:1", "current", 2000L, 1, 0.0, 0.0, "{}"))
        fakeDao.upsert(WeatherCacheEntity("saved:2", "current", 1000L, 1, 0.0, 0.0, "{}"))
        fakeDao.upsert(WeatherCacheEntity("current", "current", 2000L, 1, 0.0, 0.0, "{}"))

        // Delete orphans: keep saved:1
        fakeDao.deleteOrphans(listOf("saved:1"))

        assertNotNull(fakeDao.find("saved:1", "current"))
        assertNull(fakeDao.find("saved:2", "current"))
        assertNotNull(fakeDao.find("current", "current")) // current is never deleted by deleteOrphans

        // Delete older than 1500L
        fakeDao.deleteOlderThan(1500L)
        assertNotNull(fakeDao.find("saved:1", "current"))
    }
}

class FakeWeatherCacheDao : WeatherCacheDao {
    val entities = mutableListOf<WeatherCacheEntity>()
    private val flow = MutableStateFlow<List<WeatherCacheEntity>>(emptyList())

    override fun observe(placeKey: String): Flow<List<WeatherCacheEntity>> {
        return MutableStateFlow(entities.filter { it.placeKey == placeKey })
    }

    override suspend fun find(placeKey: String, kind: String): WeatherCacheEntity? {
        return entities.find { it.placeKey == placeKey && it.kind == kind }
    }

    override suspend fun upsert(entity: WeatherCacheEntity) {
        entities.removeAll { it.placeKey == entity.placeKey && it.kind == entity.kind }
        entities.add(entity)
        flow.value = entities.toList()
    }

    override suspend fun deleteForPlace(placeKey: String) {
        entities.removeAll { it.placeKey == placeKey }
        flow.value = entities.toList()
    }

    override suspend fun deleteOrphansInternal(keepKeys: List<String>) {
        entities.removeAll { it.placeKey != "current" && !keepKeys.contains(it.placeKey) }
        flow.value = entities.toList()
    }

    override suspend fun deleteAllNonCurrent() {
        entities.removeAll { it.placeKey != "current" }
        flow.value = entities.toList()
    }

    override suspend fun deleteOlderThan(olderThan: Long) {
        entities.removeAll { it.fetchedAt < olderThan }
        flow.value = entities.toList()
    }
}

class FakeBrightSkyApi : BrightSkyApi {
    var currentDto = CurrentWeatherResponseDto(weather = CurrentWeatherEntryDto(temperature = 20.0, icon = "sunny"))
    var weatherDto = WeatherResponseDto(weather = emptyList())
    var alertsDto = AlertsResponseDto(alerts = emptyList())
    var currentException: Exception? = null
    var weatherException: Exception? = null
    var alertsException: Exception? = null

    override suspend fun currentWeather(lat: Double, lon: Double, tz: String?): CurrentWeatherResponseDto {
        currentException?.let { throw it }
        return currentDto
    }

    override suspend fun weather(lat: Double, lon: Double, date: String, lastDate: String, tz: String?): WeatherResponseDto {
        weatherException?.let { throw it }
        return weatherDto
    }

    override suspend fun alerts(lat: Double, lon: Double, tz: String): AlertsResponseDto {
        alertsException?.let { throw it }
        return alertsDto
    }
}
