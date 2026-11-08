package com.katharina.weather.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.katharina.weather.data.local.LocationDataStore
import com.katharina.weather.data.local.NotificationDataStore
import com.katharina.weather.data.local.SeenAlertDao
import com.katharina.weather.data.local.SeenAlertEntity
import com.katharina.weather.data.local.WeatherCacheDao
import com.katharina.weather.domain.model.*
import com.katharina.weather.domain.notification.AlertNotifier
import com.katharina.weather.domain.repository.PlaceRepository
import com.katharina.weather.domain.repository.WeatherRepository
import com.katharina.weather.ui.debug.insertTestAlert
import com.katharina.weather.ui.debug.postTestNotification
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val placeRepository: PlaceRepository,
    private val weatherRepository: WeatherRepository,
    private val locationDataStore: LocationDataStore,
    private val notificationDataStore: NotificationDataStore,
    private val weatherCacheDao: WeatherCacheDao,
    private val seenAlertDao: SeenAlertDao,
    private val alertNotifier: AlertNotifier,
    private val clock: Clock = Clock.systemUTC()
) : ViewModel() {

    private val tickerFlow: Flow<Instant> = flow {
        while (true) {
            emit(Instant.now())
            delay(60_000L)
        }
    }

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var currentPlace: Place? = null

    val uiState: StateFlow<AlertsUiState> = placeRepository.selection
        .flatMapLatest { selection ->
            when (selection) {
                is PlaceSelection.None -> {
                    currentPlace = null
                    flowOf(AlertsUiState.Unknown)
                }
                is PlaceSelection.CurrentLocation -> {
                    locationDataStore.lastLocation.flatMapLatest { lastFix ->
                        if (lastFix == null) {
                            currentPlace = null
                            flowOf(AlertsUiState.Unknown)
                        } else {
                            val place = Place(
                                id = CURRENT_LOCATION_ID,
                                name = lastFix.name,
                                region = null,
                                country = null,
                                latitude = lastFix.latitude,
                                longitude = lastFix.longitude,
                                isCurrentLocation = true
                            )
                            currentPlace = place
                            observeAlerts(place)
                        }
                    }
                }
                is PlaceSelection.Saved -> {
                    placeRepository.savedPlaces.flatMapLatest { saved ->
                        val place = saved.find { it.id == selection.id }
                        if (place != null) {
                            currentPlace = place
                            observeAlerts(place)
                        } else {
                            currentPlace = null
                            flowOf(AlertsUiState.Unknown)
                        }
                    }
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = AlertsUiState.Unknown
        )

    private fun observeAlerts(place: Place): Flow<AlertsUiState> {
        return combine(
            weatherRepository.observe(place),
            tickerFlow.onStart { emit(Instant.now()) },
            notificationDataStore.alertNotificationsEnabled
        ) { snapshot, _, notificationsEnabled ->
            val now = Instant.now()
            if (snapshot.alerts == null) {
                AlertsUiState.Unknown
            } else {
                val result = snapshot.alerts.value
                val visible = result.alerts.visibleAt(now)
                val isStale = Staleness.isAlertsStale(snapshot, now)

                // Mark displayed alerts as seen in database (without notifying)
                val allVisibleAlerts = visible.active + visible.upcoming
                if (allVisibleAlerts.isNotEmpty()) {
                    markAlertsAsSeen(place.cacheKey(), allVisibleAlerts)
                }

                AlertsUiState.Known(
                    visible = visible,
                    area = result.area,
                    checkedAt = snapshot.alerts.fetchedAt,
                    isStale = isStale,
                    isNotificationsEnabled = notificationsEnabled
                )
            }
        }
    }

    private fun markAlertsAsSeen(placeKey: String, alerts: List<WeatherAlert>) {
        viewModelScope.launch {
            val nowEpoch = clock.millis()
            val entities = alerts.map { alert ->
                SeenAlertEntity(
                    alertId = alert.id,
                    placeKey = placeKey,
                    eventKey = alert.eventCode?.toString() ?: alert.event,
                    severity = alert.severity.name.lowercase(),
                    firstSeenAt = nowEpoch,
                    expiresAt = alert.expires?.toEpochMilli(),
                    notified = false
                )
            }
            seenAlertDao.upsertAll(entities)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            notificationDataStore.setAlertNotificationsEnabled(enabled)
        }
    }

    fun refresh() {
        val place = currentPlace ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            weatherRepository.refresh(place)
            _isRefreshing.value = false
        }
    }

    fun insertTestAlertForDebug() {
        val place = currentPlace ?: return
        viewModelScope.launch {
            insertTestAlert(place.cacheKey(), place.latitude, place.longitude, weatherCacheDao)
        }
    }

    fun postTestNotificationForDebug() {
        val place = currentPlace ?: return
        viewModelScope.launch {
            postTestNotification(place, alertNotifier, seenAlertDao)
        }
    }
}
