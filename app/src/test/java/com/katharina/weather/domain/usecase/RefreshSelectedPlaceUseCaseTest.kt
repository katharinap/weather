package com.katharina.weather.domain.usecase

import android.content.Context
import com.katharina.weather.data.local.LastKnownLocationFix
import com.katharina.weather.data.local.LocationDataStore
import com.katharina.weather.data.local.NotificationDataStore
import com.katharina.weather.data.local.SeenAlertDao
import com.katharina.weather.data.local.SeenAlertEntity
import com.katharina.weather.data.local.WeatherCacheDao
import com.katharina.weather.domain.model.*
import com.katharina.weather.domain.notification.AlertNotifier
import com.katharina.weather.domain.notification.PermissionChecker
import com.katharina.weather.domain.repository.PlaceRepository
import com.katharina.weather.domain.repository.WeatherRepository
import io.mockk.coVerify
import io.mockk.every
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
    private lateinit var fakeNotificationDataStore: FakeNotificationDataStoreForUseCase
    private lateinit var mockDao: WeatherCacheDao
    private lateinit var fakeSeenAlertDao: FakeSeenAlertDao
    private lateinit var mockNotifier: AlertNotifier
    private lateinit var mockPermissionChecker: PermissionChecker
    private lateinit var mockContext: Context
    private val clock = Clock.fixed(Instant.parse("2026-10-30T14:00:00Z"), ZoneId.of("UTC"))
    private lateinit var useCase: RefreshSelectedPlaceUseCase

    @Before
    fun setUp() {
        fakePlaceRepository = FakePlaceRepositoryForUseCase()
        fakeWeatherRepository = FakeWeatherRepositoryForUseCase()
        fakeLocationDataStore = FakeLocationDataStoreForUseCase()
        fakeNotificationDataStore = FakeNotificationDataStoreForUseCase()
        mockDao = mockk(relaxed = true)
        fakeSeenAlertDao = FakeSeenAlertDao()
        mockNotifier = mockk(relaxed = true)
        mockPermissionChecker = mockk {
            every { hasNotificationPermission(any()) } returns true
        }
        mockContext = mockk(relaxed = true)

        useCase = RefreshSelectedPlaceUseCase(
            fakePlaceRepository,
            fakeWeatherRepository,
            fakeLocationDataStore,
            fakeNotificationDataStore,
            mockDao,
            fakeSeenAlertDao,
            mockNotifier,
            mockPermissionChecker,
            mockContext,
            clock
        )
    }

    @Test
    fun testNoneSelectedReturnsDone() = runBlocking {
        fakePlaceRepository.selectionFlow.value = PlaceSelection.None

        val result = useCase()

        assertEquals(RefreshResult.Done, result.outcome)
        assertEquals(null, result.placeKey)
        assertEquals(0, fakeWeatherRepository.refreshCallCount)
    }

    @Test
    fun testSavedPlaceRefreshedSuccessfullyReturnsDoneAndRunsEviction() = runBlocking {
        val place = Place(1L, "Garching", "Munich", "Germany", 48.248, 11.651)
        fakePlaceRepository.placesFlow.value = listOf(place)
        fakePlaceRepository.selectionFlow.value = PlaceSelection.Saved(1L)
        fakeWeatherRepository.refreshOutcome = RefreshOutcome(Result.success(Unit), Result.success(Unit), Result.success(Unit))

        val result = useCase()

        assertEquals(RefreshResult.Done, result.outcome)
        assertEquals(place.cacheKey(), result.placeKey)
        assertEquals(1, fakeWeatherRepository.refreshCallCount)
        coVerify { mockDao.deleteOrphansInternal(listOf(place.cacheKey())) }
        coVerify { mockDao.deleteOlderThan(any()) }
    }

    @Test
    fun testCurrentLocationWithLastFixRefreshesSuccessfully() = runBlocking {
        fakePlaceRepository.selectionFlow.value = PlaceSelection.CurrentLocation
        fakeLocationDataStore.state.value = LastKnownLocationFix(48.137, 11.575, "Munich")
        fakeWeatherRepository.refreshOutcome = RefreshOutcome(Result.success(Unit), Result.success(Unit), Result.success(Unit))

        val result = useCase()

        assertEquals(RefreshResult.Done, result.outcome)
        assertEquals("current", result.placeKey)
        assertEquals(1, fakeWeatherRepository.refreshCallCount)
    }

    @Test
    fun testCurrentLocationWithoutLastFixReturnsDoneWithoutRefreshing() = runBlocking {
        fakePlaceRepository.selectionFlow.value = PlaceSelection.CurrentLocation
        fakeLocationDataStore.state.value = null

        val result = useCase()

        assertEquals(RefreshResult.Done, result.outcome)
        assertEquals("current", result.placeKey)
        assertEquals(0, fakeWeatherRepository.refreshCallCount)
    }

    @Test
    fun testIOExceptionReturnsRetry() = runBlocking {
        val place = Place(1L, "Garching", "Munich", "Germany", 48.248, 11.651)
        fakePlaceRepository.placesFlow.value = listOf(place)
        fakePlaceRepository.selectionFlow.value = PlaceSelection.Saved(1L)
        fakeWeatherRepository.refreshOutcome = RefreshOutcome(
            current = Result.failure(IOException("Network error")),
            forecast = Result.failure(IOException("Network error")),
            alerts = Result.failure(IOException("Network error"))
        )

        val result = useCase()

        assertEquals(RefreshResult.Retry, result.outcome)
        assertEquals(place.cacheKey(), result.placeKey)
    }

    @Test
    fun testHttp404ReturnsGiveUp() = runBlocking {
        val place = Place(1L, "Garching", "Munich", "Germany", 48.248, 11.651)
        fakePlaceRepository.placesFlow.value = listOf(place)
        fakePlaceRepository.selectionFlow.value = PlaceSelection.Saved(1L)
        fakeWeatherRepository.refreshOutcome = RefreshOutcome(
            current = Result.failure(RuntimeException("HTTP 404 No sources match")),
            forecast = Result.failure(RuntimeException("HTTP 404 No sources match")),
            alerts = Result.failure(RuntimeException("HTTP 404 No sources match"))
        )

        val result = useCase()

        assertEquals(RefreshResult.GiveUp, result.outcome)
        assertEquals(place.cacheKey(), result.placeKey)
    }

    @Test
    fun testNotificationStepNotifiesOnceAndNotOnSecondRun() = runBlocking {
        val place = Place(1L, "Garching", "Munich", "Germany", 48.248, 11.651)
        fakePlaceRepository.placesFlow.value = listOf(place)
        fakePlaceRepository.selectionFlow.value = PlaceSelection.Saved(1L)
        fakeNotificationDataStore.enabledState.value = true

        val severeAlert = WeatherAlert(
            id = "alert-severe-1",
            eventCode = 99,
            event = "Thunderstorm",
            headline = "Severe Thunderstorm",
            description = null,
            instruction = null,
            severity = AlertSeverity.SEVERE,
            effective = null,
            onset = clock.instant().minusSeconds(100),
            expires = clock.instant().plusSeconds(7200)
        )

        fakeWeatherRepository.snapshots[place.cacheKey()] = WeatherSnapshot(
            current = null,
            forecast = null,
            alerts = Cached(AlertsResult(listOf(severeAlert), null), clock.instant())
        )

        // Run 1: First run with empty seen_alerts table -> records baseline without notifying
        useCase()
        coVerify(exactly = 0) { mockNotifier.postNotification(any(), any()) }

        // Run 2: An alert with higher severity (EXTREME) for the same event arrives -> 1 notification posted
        val extremeAlert = severeAlert.copy(id = "alert-extreme-2", severity = AlertSeverity.EXTREME)
        fakeWeatherRepository.snapshots[place.cacheKey()] = WeatherSnapshot(
            current = null,
            forecast = null,
            alerts = Cached(AlertsResult(listOf(extremeAlert), null), clock.instant())
        )

        useCase()
        coVerify(exactly = 1) { mockNotifier.postNotification(place, extremeAlert) }

        // Run 3: Same alert again -> no second notification
        useCase()
        coVerify(exactly = 1) { mockNotifier.postNotification(place, any()) }
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
    val snapshots = mutableMapOf<String, WeatherSnapshot>()
    var refreshOutcome = RefreshOutcome(Result.success(Unit), Result.success(Unit), Result.success(Unit))
    var refreshCallCount = 0

    override fun observe(place: Place): Flow<WeatherSnapshot> {
        return MutableStateFlow(snapshots[place.cacheKey()] ?: WeatherSnapshot(null, null, null))
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

class FakeNotificationDataStoreForUseCase : NotificationDataStore(
    dataStore = mockk(relaxed = true)
) {
    val enabledState = MutableStateFlow(false)
    override val alertNotificationsEnabled: Flow<Boolean> = enabledState

    override suspend fun setAlertNotificationsEnabled(enabled: Boolean) {
        enabledState.value = enabled
    }
}

class FakeSeenAlertDao : SeenAlertDao {
    private val entities = mutableListOf<SeenAlertEntity>()

    override suspend fun getSeenAlertsForPlace(placeKey: String): List<SeenAlertEntity> {
        return entities.filter { it.placeKey == placeKey }
    }

    override suspend fun getRecentAlertsForEvent(
        placeKey: String,
        eventKey: String,
        sinceEpoch: Long
    ): List<SeenAlertEntity> {
        return entities.filter { it.placeKey == placeKey && it.eventKey == eventKey && it.firstSeenAt >= sinceEpoch }
    }

    override suspend fun upsert(entity: SeenAlertEntity) {
        entities.removeAll { it.alertId == entity.alertId }
        entities.add(entity)
    }

    override suspend fun upsertAll(entities: List<SeenAlertEntity>) {
        entities.forEach { upsert(it) }
    }

    override suspend fun deleteExpired(olderThanEpoch: Long) {
        entities.removeAll { it.expiresAt != null && it.expiresAt < olderThanEpoch }
    }
}
