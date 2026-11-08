package com.katharina.weather.ui.home

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.katharina.weather.R
import com.katharina.weather.domain.model.AlertsUiState
import com.katharina.weather.domain.model.CurrentWeather
import com.katharina.weather.domain.model.DailyForecast
import com.katharina.weather.domain.model.HourlyForecast
import com.katharina.weather.domain.model.Place
import com.katharina.weather.domain.model.WeatherAlert
import com.katharina.weather.domain.model.WeatherCondition
import com.katharina.weather.ui.debug.triggerDebugWorker
import com.katharina.weather.ui.format.formatAlertValidity
import com.katharina.weather.ui.format.formatObservationTime
import com.katharina.weather.ui.format.formatRelativeTime
import com.katharina.weather.ui.format.getWeatherIconRes
import com.katharina.weather.ui.format.toNameStringRes
import com.katharina.weather.ui.home.chart.HourlyChart
import com.katharina.weather.ui.theme.WeatherTheme
import com.katharina.weather.ui.util.getStyle
import com.katharina.weather.ui.util.toWindDirectionStringRes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onNavigateToPlaces: () -> Unit,
    onNavigateToAlerts: () -> Unit,
    onUseCurrentLocation: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val isScrolled = remember { derivedStateOf { scrollState.value > 0 } }.value

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        val titleText = when (state) {
                            is HomeUiState.Success -> state.place.name
                            is HomeUiState.Error -> state.place?.name ?: stringResource(R.string.app_name)
                            else -> stringResource(R.string.app_name)
                        }
                        Text(text = titleText)
                    },
                    actions = {
                        FilledTonalIconButton(
                            onClick = onNavigateToPlaces,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_location_on),
                                contentDescription = stringResource(R.string.title_places)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (isScrolled) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.background
                    )
                )
                if (isScrolled) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (state) {
                is HomeUiState.NoPlace -> {
                    NoPlaceContent(
                        onNavigateToPlaces = onNavigateToPlaces,
                        onUseCurrentLocation = onUseCurrentLocation,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is HomeUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is HomeUiState.Error -> {
                    ErrorContent(kind = state.kind, onRetry = onRetry, modifier = Modifier.align(Alignment.Center))
                }
                is HomeUiState.Success -> {
                    PullToRefreshBox(
                        isRefreshing = state.isRefreshing,
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        SuccessContent(
                            state = state,
                            onNavigateToAlerts = onNavigateToAlerts,
                            scrollState = scrollState
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NoPlaceContent(
    onNavigateToPlaces: () -> Unit,
    onUseCurrentLocation: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(24.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.no_place_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.no_place_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onNavigateToPlaces,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.btn_search_places))
        }
        OutlinedButton(
            onClick = onUseCurrentLocation,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.btn_use_current_location))
        }
    }
}

@Composable
fun ErrorContent(kind: ErrorKind, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val message = when (kind) {
        ErrorKind.Network -> stringResource(R.string.error_network)
        ErrorKind.Server -> stringResource(R.string.error_server)
        ErrorKind.LocationPermissionRequired -> stringResource(R.string.error_location_permission)
        ErrorKind.LocationUnavailable -> stringResource(R.string.error_location_unavailable)
        ErrorKind.Unknown -> stringResource(R.string.error_unknown)
    }

    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        if (kind == ErrorKind.LocationPermissionRequired) {
            Button(onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            }) {
                Text(text = stringResource(R.string.btn_open_settings))
            }
        } else {
            Button(onClick = onRetry) {
                Text(text = stringResource(R.string.btn_retry))
            }
        }
    }
}

@Composable
fun SuccessContent(
    state: HomeUiState.Success,
    onNavigateToAlerts: () -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState()
) {
    val weather = state.weather
    val now = Instant.now()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Refresh-Failure Banner
        if (state.bannerMessage != null) {
            val containerColor = if (state.isOfflineBanner) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
            val contentColor = if (state.isOfflineBanner) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onErrorContainer
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = containerColor,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = state.bannerMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                    modifier = Modifier.padding(12.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Outdated Warning Label
        if (state.isOutdated) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Data is ${state.outdatedHours} h old",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(12.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Active Weather Alert Banner
        val alertsState = state.alertsUiState
        if (alertsState is AlertsUiState.Known && alertsState.visible.active.isNotEmpty()) {
            val topAlert = alertsState.visible.active.first()
            val extraCount = alertsState.visible.active.size - 1
            AlertBanner(
                alert = topAlert,
                extraCount = extraCount,
                onClick = onNavigateToAlerts
            )
        }

        // Main Weather Header
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = getWeatherIconRes(weather.icon)),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(108.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.format_temperature, weather.temperature.toInt()),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(weather.condition.toNameStringRes()),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Detail cards grid (2 columns)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DetailCard(
                    title = stringResource(R.string.label_wind),
                    content = {
                        val windText = formatWindValue(weather.windSpeed, weather.windDirection)
                        Text(text = windText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        val gusts = weather.windGustSpeed
                        val speed = weather.windSpeed
                        if (gusts != null && speed != null && gusts > speed) {
                            Text(
                                text = stringResource(R.string.format_wind_gusts_line, gusts.roundToInt()),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                DetailCard(
                    title = stringResource(R.string.label_humidity),
                    content = {
                        Text(
                            text = weather.relativeHumidity?.let { stringResource(R.string.format_humidity, it) } ?: "–",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DetailCard(
                    title = stringResource(R.string.label_rain_last_hour),
                    content = {
                        Text(
                            text = formatRainHourly(weather.precipitation60),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                DetailCard(
                    title = stringResource(R.string.label_cloud_cover),
                    content = {
                        Text(
                            text = weather.cloudCover?.let { stringResource(R.string.format_cloud_cover, it) } ?: "–",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }

        // Forecast Section
        if (state.hours24.isNotEmpty() || state.days.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.hours24.isNotEmpty()) {
                    HourlyStrip(hours = state.hours24)
                }
                if (state.days.isNotEmpty()) {
                    DailyList(days = state.days)
                }
                if (state.hours48.isNotEmpty()) {
                    HourlyChart(hours = state.hours48)
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.forecast_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Footer Metadata & Warnings Line
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(vertical = 16.dp)
        ) {
            val observedTimeStr = formatObservationTime(weather.timestamp)
            val updatedTimeStr = state.updatedAt?.let { formatRelativeTime(it, now) } ?: "–"

            Text(
                text = "Observed $observedTimeStr · Updated $updatedTimeStr",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { triggerDebugWorker(context) }
                    )
                }
            )

            // Warnings Footer Line
            val warningsText = when (alertsState) {
                is AlertsUiState.Unknown -> "Warnings: unavailable"
                is AlertsUiState.Known -> {
                    val checkedStr = formatRelativeTime(alertsState.checkedAt, now)
                    if (alertsState.isStale) {
                        "Warnings: not checked recently ($checkedStr)"
                    } else {
                        val activeCount = alertsState.visible.active.size
                        val upcomingCount = alertsState.visible.upcoming.size
                        when {
                            activeCount == 0 && upcomingCount == 0 -> "Warnings: none · checked $checkedStr"
                            activeCount == 0 && upcomingCount > 0 -> "Warnings: none active, $upcomingCount upcoming · checked $checkedStr"
                            else -> "Warnings: $activeCount active · checked $checkedStr"
                        }
                    }
                }
            }

            Text(
                text = warningsText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onNavigateToAlerts)
            )

            val stationName = weather.stationName ?: "–"
            val distanceKm = (weather.stationDistance ?: 0.0) / 1000.0
            Text(
                text = stringResource(R.string.format_station, stationName, distanceKm),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.attribution_dwd),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                text = stringResource(R.string.attribution_icons),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun AlertBanner(
    alert: WeatherAlert,
    extraCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val style = alert.severity.getStyle(isDark)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = style.containerColor,
            contentColor = style.onContainerColor
        )
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Severity Color Indicator Bar
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(style.color)
            )

            Column(
                modifier = Modifier
                    .padding(14.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Icon(
                            painter = painterResource(id = style.iconRes),
                            contentDescription = null,
                            tint = style.color,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "${style.label} · ${alert.event}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = style.onContainerColor
                        )
                    }

                    if (extraCount > 0) {
                        Surface(
                            color = style.color,
                            contentColor = Color.White,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "+$extraCount more",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                if (!alert.headline.isNullOrBlank()) {
                    Text(
                        text = alert.headline,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = style.onContainerColor
                    )
                }

                val validityStr = formatAlertValidity(alert.onset, alert.expires)
                if (validityStr.isNotEmpty()) {
                    Text(
                        text = validityStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = style.onContainerColor.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

@Composable
fun HourlyStrip(hours: List<HourlyForecast>, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(items = hours, key = { _, item -> item.timestamp.toEpochMilli() }) { index, item ->
                HourlyItem(item = item, isFirst = index == 0)
            }
        }
    }
}

@Composable
fun HourlyItem(item: HourlyForecast, isFirst: Boolean) {
    val timeLabel = if (isFirst) {
        stringResource(R.string.label_now)
    } else {
        DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.of("Europe/Berlin"))
            .format(item.timestamp)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.width(68.dp)
    ) {
        Text(
            text = timeLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Image(
            painter = painterResource(id = getWeatherIconRes(item.icon)),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(46.dp)
        )
        Text(
            text = item.temperature?.let { stringResource(R.string.format_temperature, it.toInt()) } ?: "–",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        val prob = item.precipitationProbability
        if (prob != null && prob >= 10) {
            Text(
                text = "$prob%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
fun DailyList(days: List<DailyForecast>, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            days.forEach { day ->
                DailyRow(day = day)
            }
        }
    }
}

@Composable
fun DailyRow(day: DailyForecast) {
    val today = LocalDate.now(ZoneId.of("Europe/Berlin"))
    val dayLabel = when (day.date) {
        today -> stringResource(R.string.label_today)
        today.plusDays(1) -> stringResource(R.string.label_tomorrow)
        else -> day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = dayLabel,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(76.dp)
        )

        Box(
            modifier = Modifier.width(44.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = getWeatherIconRes(day.condition.toDayVariant().name)),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(42.dp)
            )
        }

        Box(
            modifier = Modifier.width(84.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            val prob = day.maxPrecipProbabilityPercent
            if (prob != null && prob >= 10) {
                Text(
                    text = if (day.precipitationMm > 0.0) "$prob% (${String.format(Locale.getDefault(), "%.1f", day.precipitationMm)} mm)" else "$prob%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Row(
            modifier = Modifier.width(70.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = day.minTempC?.let { "${it.toInt()}°" } ?: "–",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.width(30.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = day.maxTempC?.let { "${it.toInt()}°" } ?: "–",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                modifier = Modifier.width(30.dp)
            )
        }
    }
}

@Composable
fun DetailCard(title: String, content: @Composable ColumnScope.() -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            content()
        }
    }
}

@Composable
fun formatWindValue(speed: Double?, direction: Int?): String {
    if (speed == null) return "–"
    val dirStr = direction.toWindDirectionStringRes()?.let { stringResource(it) } ?: ""
    return stringResource(R.string.format_wind_value, speed.roundToInt(), dirStr).trim()
}

@Composable
fun formatRainHourly(p60: Double?): String {
    if (p60 == null) return "–"
    return stringResource(R.string.format_precipitation, p60)
}

// Previews
@Preview(showBackground = true)
@Composable
fun HomeScreenOfflineBannerPreview() {
    WeatherTheme {
        HomeScreen(
            state = HomeUiState.Success(
                weather = CurrentWeather(
                    timestamp = Instant.now(),
                    temperature = 18.0,
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
                ),
                place = Place(1, "Garching", "Munich, Bavaria", "Germany", 48.248, 11.651),
                observedAt = Instant.now(),
                updatedAt = Instant.now(),
                refreshFailed = true,
                bannerMessage = "You're offline. Showing data from 18:00.",
                isOfflineBanner = true
            ),
            onRefresh = {},
            onRetry = {},
            onNavigateToPlaces = {},
            onNavigateToAlerts = {},
            onUseCurrentLocation = {},
            snackbarHostState = SnackbarHostState()
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenErrorBannerPreview() {
    WeatherTheme {
        HomeScreen(
            state = HomeUiState.Success(
                weather = CurrentWeather(
                    timestamp = Instant.now(),
                    temperature = 18.0,
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
                ),
                place = Place(1, "Garching", "Munich, Bavaria", "Germany", 48.248, 11.651),
                observedAt = Instant.now(),
                updatedAt = Instant.now(),
                refreshFailed = true,
                bannerMessage = "Can't refresh. Showing data from 18:00.",
                isOfflineBanner = false
            ),
            onRefresh = {},
            onRetry = {},
            onNavigateToPlaces = {},
            onNavigateToAlerts = {},
            onUseCurrentLocation = {},
            snackbarHostState = SnackbarHostState()
        )
    }
}
