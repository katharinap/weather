package com.katharina.weather.domain.usecase

import com.katharina.weather.data.local.LastKnownLocationFix
import com.katharina.weather.data.local.LocationDataStore
import com.katharina.weather.data.local.WeatherCacheDao
import com.katharina.weather.data.local.WeatherCacheEntity
import com.katharina.weather.domain.model.*
import com.katharina.weather.domain.repository.PlaceRepository
import com.katharina.weather.domain.repository.WeatherRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class RefreshSelectedPlaceUseCaseTest {

    private lateinit var fakePlaceRepository: FakePlaceRepositoryForUseCase
    private lateinit var fakeWeatherRepository: FakeWeatherRepositoryForUseCase
    private lateinit var fakeLocationDataStore: FakeLocationDataStoreForUseCase
    private lateinit var mockDao: WeatherCacheDao
    private val clock = Clock.fixed(Instant.parse("2026-10-30T14:00:00Z"), ZoneId.of("UTC"))
    private lateinit var useCase: RefreshSelectedPlaceUseCase

    @Before
    fun setUp() {
        fakePlaceRepository = FakePlaceRepositoryForUseCase()
        fakeWeatherRepository = FakeWeatherRepositoryForUseCase()
        fakeLocationDataStore = FakeLocationDataStoreForUseCase()
        mockDao = mockk(relaxed = true)

        useCase = RefreshSelectedPlaceUseCase(
            fakePlaceRepository,
            fakeWeatherRepository,
            fakeLocationDataStore,
            mockDao,
            clock
        )
    }

    @Test
    fun testNoneSelectedReturnsDone() = runBlocking {
        fakePlaceRepository.selectionFlow.value = PlaceSelection.None

        val result = useCase()

        assertEquals(RefreshResult.Done, result)
        assertEquals(0, fakeWeatherRepository.refreshCallCount)
    }

    @Test
    fun testSavedPlaceRefreshedSuccessfullyReturnsDoneAndRunsEviction() = runBlocking {
        val place = Place(1L, "Garching", "Munich", "Germany", 48.248, 11.651)
        fakePlaceRepository.placesFlow.value = listOf(place)
        fakePlaceRepository.selectionFlow.value = PlaceSelection.Saved(1L)
        fakeWeatherRepository.refreshOutcome = RefreshOutcome(Result.success(Unit), Result.success(Unit))

        val result = useCase()

        assertEquals(RefreshResult.Done, result)
        assertEquals(1, fakeWeatherRepository.refreshCallCount)
        coVerify { mockDao.deleteOrphansInternal(listOf(place.cacheKey())) }
        coVerify { mockDao.deleteOlderThan(any()) }
    }

    @Test
    fun testCurrentLocationWithLastFixRefreshesSuccessfully() = runBlocking {
        fakePlaceRepository.selectionFlow.value = PlaceSelection.CurrentLocation
        fakeLocationDataStore.state.value = LastKnownLocationFix(48.137, 11.575, "Munich")
        fakeWeatherRepository.refreshOutcome = RefreshOutcome(Result.success(Unit), Result.success(Unit))

        val result = useCase()

        assertEquals(RefreshResult.Done, result)
        assertEquals(1, fakeWeatherRepository.refreshCallCount)
    }

    @Test
    fun testCurrentLocationWithoutLastFixReturnsDoneWithoutRefreshing() = runBlocking {
        fakePlaceRepository.selectionFlow.value = PlaceSelection.CurrentLocation
        fakeLocationDataStore.state.value = null

        val result = useCase()

        assertEquals(RefreshResult.Done, result)
        assertEquals(0, fakeWeatherRepository.refreshCallCount)
    }

    @Test
    fun testIOExceptionReturnsRetry() = runBlocking {
        val place = Place(1L, "Garching", "Munich", "Germany", 48.248, 11.651)
        fakePlaceRepository.placesFlow.value = listOf(place)
        fakePlaceRepository.selectionFlow.value = PlaceSelection.Saved(1L)
        fakeWeatherRepository.refreshOutcome = RefreshOutcome(
            current = Result.failure(IOException("Network error")),
            forecast = Result.failure(IOException("Network error"))
        )

        val result = useCase()

        assertEquals(RefreshResult.Retry, result)
    }

    @Test
    fun testHttp404ReturnsGiveUp() = runBlocking {
        val place = Place(1L, "Garching", "Munich", "Germany", 48.248, 11.651)
        fakePlaceRepository.placesFlow.value = listOf(place)
        fakePlaceRepository.selectionFlow.value = PlaceSelection.Saved(1L)
        fakeWeatherRepository.refreshOutcome = RefreshOutcome(
            current = Result.failure(RuntimeException("HTTP 404 No sources match")),
            forecast = Result.failure(RuntimeException("HTTP 404 No sources match"))
        )

        val result = useCase()

        assertEquals(RefreshResult.GiveUp, result)
    }

    @Test
    fun testPartialSuccessReturnsDone() = runBlocking {
        val place = Place(1L, "Garching", "Munich", "Germany", 48.248, 11.651)
        fakePlaceRepository.placesFlow.value = listOf(place)
        fakePlaceRepository.selectionFlow.value = PlaceSelection.Saved(1L)
        fakeWeatherRepository.refreshOutcome = RefreshOutcome(
            current = Result.success(Unit),
            forecast = Result.failure(IOException("Forecast error"))
        )

        val result = useCase()

        assertEquals(RefreshResult.Done, result)
    }
}

class FakePlaceRepositoryForUseCase : PlaceRepository {
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

class FakeWeatherRepositoryForUseCase : WeatherRepository {
    var refreshOutcome = RefreshOutcome(Result.success(Unit), Result.success(Unit))
    var refreshCallCount = 0

    override fun observe(place: Place): Flow<WeatherSnapshot> {
        return MutableStateFlow(WeatherSnapshot(null, null))
    }

    override suspend fun refresh(place: Place): RefreshOutcome {
        refreshCallCount++
        return refreshOutcome
    }
}

class FakeLocationDataStoreForUseCase : LocationDataStore(
    dataStore = mockk(relaxed = true)
) {
    val state = MutableStateFlow<LastKnownLocationFix?>(null)
    override val lastLocation: Flow<LastKnownLocationFix?> = state

    override suspend fun saveLastLocation(lat: Double, lon: Double, name: String) {
        state.value = LastKnownLocationFix(lat, lon, name)
    }
}
