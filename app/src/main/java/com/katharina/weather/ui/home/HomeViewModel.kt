package com.katharina.weather.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.katharina.weather.data.local.LastKnownLocationFix
import com.katharina.weather.data.local.LocationDataStore
import com.katharina.weather.data.local.WeatherCacheDao
import com.katharina.weather.data.location.GeocoderHelper
import com.katharina.weather.data.util.distanceBetweenInKm
import com.katharina.weather.domain.forecast.toDailyForecasts
import com.katharina.weather.domain.forecast.upcoming
import com.katharina.weather.domain.model.*
import com.katharina.weather.domain.repository.LocationProvider
import com.katharina.weather.domain.repository.PlaceRepository
import com.katharina.weather.domain.repository.WeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val placeRepository: PlaceRepository,
    private val weatherRepository: WeatherRepository,
    private val locationProvider: LocationProvider,
    private val geocoderHelper: GeocoderHelper,
    private val locationDataStore: LocationDataStore,
    private val weatherCacheDao: WeatherCacheDao
) : ViewModel() {

    private val tickerFlow: Flow<Instant> = flow {
        while (true) {
            emit(Instant.now())
            delay(60_000L)
        }
    }

    private val _refreshState = MutableStateFlow<RefreshState>(RefreshState.Idle)
    private val lastSuccessfulRefreshTime = ConcurrentHashMap<String, Instant>()

    private var activePlace: Place? = null
    private var refreshJob: Job? = null

    val uiState: StateFlow<HomeUiState> = placeRepository.selection
        .flatMapLatest { selection ->
            observeSelectionStream(selection)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = HomeUiState.Loading
        )

    private fun observeSelectionStream(selection: PlaceSelection): Flow<HomeUiState> {
        return when (selection) {
            is PlaceSelection.None -> {
                activePlace = null
                flowOf(HomeUiState.NoPlace)
            }
            is PlaceSelection.CurrentLocation -> {
                observeCurrentLocationStream()
            }
            is PlaceSelection.Saved -> {
                observeSavedPlaceStream(selection.id)
            }
        }
    }

    private fun observeSavedPlaceStream(placeId: Long): Flow<HomeUiState> {
        return placeRepository.savedPlaces.flatMapLatest { savedList ->
            val place = savedList.find { it.id == placeId }
            if (place == null) {
                activePlace = null
                flowOf(HomeUiState.NoPlace)
            } else {
                activePlace = place
                observePlaceAndCache(place)
            }
        }
    }

    private fun observeCurrentLocationStream(): Flow<HomeUiState> {
        return locationDataStore.lastLocation.flatMapLatest { lastFix ->
            val initialPlace = if (lastFix != null) {
                Place(
                    id = CURRENT_LOCATION_ID,
                    name = lastFix.name,
                    region = null,
                    country = null,
                    latitude = lastFix.latitude,
                    longitude = lastFix.longitude,
                    isCurrentLocation = true
                )
            } else null

            activePlace = initialPlace

            // Trigger background location check and refresh
            triggerCurrentLocationFetch(lastFix)

            if (initialPlace != null) {
                observePlaceAndCache(initialPlace)
            } else {
                // First time current location use
                _refreshState.map { refState ->
                    when (refState) {
                        is RefreshState.Failed -> HomeUiState.Error(place = null, kind = refState.kind)
                        else -> HomeUiState.Loading
                    }
                }
            }
        }
    }

    private fun observePlaceAndCache(place: Place): Flow<HomeUiState> {
        return combine(
            weatherRepository.observe(place),
            tickerFlow.onStart { emit(Instant.now()) },
            _refreshState
        ) { snapshot, _, refreshState ->
            buildUiState(place, snapshot, Instant.now(), refreshState)
        }
    }

    private fun buildUiState(
        place: Place,
        snapshot: WeatherSnapshot,
        now: Instant,
        refreshState: RefreshState
    ): HomeUiState {
        val current = snapshot.current?.value
        val forecast = snapshot.forecast?.value

        if (current == null) {
            return when (refreshState) {
                is RefreshState.Failed -> HomeUiState.Error(place = place, kind = refreshState.kind)
                else -> HomeUiState.Loading
            }
        }

        val zoneId = ZoneId.of("Europe/Berlin")
        val today = LocalDate.now(zoneId)

        val hours24 = forecast?.upcoming(now, 24) ?: emptyList()
        val hours48 = forecast?.upcoming(now, 48) ?: emptyList()
        val days = forecast?.toDailyForecasts()?.filter { it.date >= today } ?: emptyList()

        val observedAt = current.timestamp
        val updatedAt = snapshot.current.fetchedAt

        val isOutdated = Staleness.isOutdated(snapshot, now)
        val outdatedHours = Duration.between(updatedAt, now).toHours()

        val alertsUiState = if (snapshot.alerts == null) {
            AlertsUiState.Unknown
        } else {
            val result = snapshot.alerts.value
            val visible = result.alerts.visibleAt(now)
            val isStale = Staleness.isAlertsStale(snapshot, now)
            AlertsUiState.Known(
                visible = visible,
                area = result.area,
                checkedAt = snapshot.alerts.fetchedAt,
                isStale = isStale
            )
        }

        val isRefreshing = refreshState is RefreshState.Refreshing
        val refreshFailed = refreshState is RefreshState.Failed

        val isOfflineBanner = refreshState is RefreshState.Failed && refreshState.kind == ErrorKind.Network
        val bannerMessage = if (refreshFailed) {
            val formattedTime = DateTimeFormatter.ofPattern("HH:mm")
                .withZone(zoneId)
                .format(updatedAt)
            if (isOfflineBanner) {
                "You're offline. Showing data from $formattedTime."
            } else {
                "Can't refresh. Showing data from $formattedTime."
            }
        } else null

        val userError = (refreshState as? RefreshState.Failed)?.userInitiatedMessage

        return HomeUiState.Success(
            weather = current,
            place = place,
            observedAt = observedAt,
            updatedAt = updatedAt,
            hours24 = hours24,
            hours48 = hours48,
            days = days,
            alertsUiState = alertsUiState,
            isRefreshing = isRefreshing,
            refreshFailed = refreshFailed,
            isOutdated = isOutdated,
            outdatedHours = outdatedHours,
            bannerMessage = bannerMessage,
            isOfflineBanner = isOfflineBanner,
            userPullToRefreshError = userError
        )
    }

    fun onStart() {
        val place = activePlace ?: return
        val currentUiState = uiState.value
        val snapshot = if (currentUiState is HomeUiState.Success) {
            WeatherSnapshot(
                current = Cached(currentUiState.weather, currentUiState.updatedAt ?: Instant.EPOCH),
                forecast = Cached(emptyList(), currentUiState.updatedAt ?: Instant.EPOCH)
            )
        } else WeatherSnapshot(null, null)

        val now = Instant.now()
        if (Staleness.needsRefresh(snapshot, now)) {
            triggerRefresh(place, userInitiated = false)
        }
    }

    fun refresh(userInitiated: Boolean = true) {
        val place = activePlace ?: return
        if (userInitiated) {
            val lastTime = lastSuccessfulRefreshTime[place.cacheKey()]
            if (lastTime != null && Duration.between(lastTime, Instant.now()) < Staleness.MIN_REFRESH_INTERVAL) {
                return
            }
        }
        triggerRefresh(place, userInitiated = userInitiated)
    }

    fun selectCurrentLocation() {
        viewModelScope.launch {
            placeRepository.select(PlaceSelection.CurrentLocation)
        }
    }

    private fun triggerCurrentLocationFetch(lastFix: LastKnownLocationFix?) {
        viewModelScope.launch {
            _refreshState.value = RefreshState.Refreshing
            val coordsResult = locationProvider.currentLocation()
            coordsResult.fold(
                onSuccess = { coords ->
                    val placeName = geocoderHelper.getPlaceName(coords)

                    if (lastFix != null) {
                        val distance = distanceBetweenInKm(lastFix.latitude, lastFix.longitude, coords.latitude, coords.longitude)
                        if (distance > 10.0) {
                            weatherCacheDao.deleteForPlace("current")
                        }
                    }

                    locationDataStore.saveLastLocation(coords.latitude, coords.longitude, placeName)

                    val newPlace = Place(
                        id = CURRENT_LOCATION_ID,
                        name = placeName,
                        region = null,
                        country = null,
                        latitude = coords.latitude,
                        longitude = coords.longitude,
                        isCurrentLocation = true
                    )
                    activePlace = newPlace
                    triggerRefresh(newPlace, userInitiated = false)
                },
                onFailure = { throwable ->
                    val kind = when (throwable) {
                        is LocationPermissionException -> ErrorKind.LocationPermissionRequired
                        is LocationUnavailableException -> ErrorKind.LocationUnavailable
                        else -> ErrorKind.LocationUnavailable
                    }
                    _refreshState.value = RefreshState.Failed(kind = kind, userInitiatedMessage = null)
                }
            )
        }
    }

    private fun triggerRefresh(place: Place, userInitiated: Boolean) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _refreshState.value = RefreshState.Refreshing
            val outcome = weatherRepository.refresh(place)
            if (outcome.allFailed) {
                val error = outcome.firstError ?: Exception()
                val kind = classifyError(error, place)
                val userMsg = if (userInitiated) "Failed to refresh weather data." else null
                _refreshState.value = RefreshState.Failed(kind = kind, userInitiatedMessage = userMsg)
            } else {
                lastSuccessfulRefreshTime[place.cacheKey()] = Instant.now()
                _refreshState.value = RefreshState.Idle
            }
        }
    }

    private fun classifyError(throwable: Throwable, place: Place?): ErrorKind {
        val message = throwable.message ?: ""
        if (message.contains("404") || message.contains("No sources match")) {
            return if (place?.isCurrentLocation == true) ErrorKind.LocationUnavailable else ErrorKind.Server
        }

        return when (throwable) {
            is IOException -> ErrorKind.Network
            else -> {
                if (message.contains("5") || message.contains("Server") || message.contains("HTTP 5")) {
                    ErrorKind.Server
                } else if (message.contains("4") || message.contains("HTTP 4")) {
                    ErrorKind.Server
                } else {
                    ErrorKind.Unknown
                }
            }
        }
    }

    sealed interface RefreshState {
        data object Idle : RefreshState
        data object Refreshing : RefreshState
        data class Failed(val kind: ErrorKind, val userInitiatedMessage: String?) : RefreshState
    }
}
