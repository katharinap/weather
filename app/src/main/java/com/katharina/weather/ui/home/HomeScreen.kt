package com.katharina.weather.ui.home

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.katharina.weather.R
import com.katharina.weather.domain.model.CurrentWeather
import com.katharina.weather.domain.model.DefaultPlace
import com.katharina.weather.domain.model.Place
import com.katharina.weather.domain.model.WeatherCondition
import com.katharina.weather.ui.format.formatObservationTime
import com.katharina.weather.ui.format.getWeatherIconRes
import com.katharina.weather.ui.format.toNameStringRes
import com.katharina.weather.ui.theme.WeatherTheme
import com.katharina.weather.ui.util.toWindDirectionStringRes
import java.time.Instant
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (state) {
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
                        SuccessContent(weather = state.weather, place = state.place)
                    }
                }
            }
        }
    }
}

@Composable
fun ErrorContent(kind: ErrorKind, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val message = when (kind) {
        ErrorKind.Network -> stringResource(R.string.error_network)
        ErrorKind.Server -> stringResource(R.string.error_server)
        ErrorKind.Unknown -> stringResource(R.string.error_unknown)
    }
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onRetry) {
            Text(text = stringResource(R.string.btn_retry))
        }
    }
}

@Composable
fun SuccessContent(weather: CurrentWeather, place: Place, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Header
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = place.name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Image(
                painter = painterResource(id = getWeatherIconRes(weather.icon)),
                contentDescription = null,
                modifier = Modifier.size(96.dp)
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

        // Detail cards grid (2 columns) with equal height per row
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

        // Footer Metadata
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(vertical = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.format_observed, formatObservationTime(weather.timestamp)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
fun HomeScreenLoadingPreview() {
    WeatherTheme {
        HomeScreen(
            state = HomeUiState.Loading,
            onRefresh = {},
            onRetry = {},
            snackbarHostState = SnackbarHostState()
        )
    }
}

@Preview(showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun HomeScreenSuccessPreview() {
    WeatherTheme {
        HomeScreen(
            state = HomeUiState.Success(
                weather = CurrentWeather(
                    timestamp = Instant.now(),
                    temperature = 17.5,
                    condition = WeatherCondition.CLOUDY,
                    icon = "cloudy",
                    windSpeed = 7.6,
                    windGustSpeed = 12.6,
                    windDirection = 40,
                    cloudCover = 100,
                    relativeHumidity = 76,
                    precipitation10 = 0.0,
                    precipitation60 = 0.1,
                    stationName = "Muenchen-Stadt",
                    stationDistance = 3767.0
                ),
                place = DefaultPlace,
                fetchedAt = Instant.now()
            ),
            onRefresh = {},
            onRetry = {},
            snackbarHostState = SnackbarHostState()
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenSuccessNullsPreview() {
    WeatherTheme {
        HomeScreen(
            state = HomeUiState.Success(
                weather = CurrentWeather(
                    timestamp = Instant.now(),
                    temperature = 17.5,
                    condition = WeatherCondition.UNKNOWN,
                    icon = "unknown",
                    windSpeed = null,
                    windGustSpeed = null,
                    windDirection = null,
                    cloudCover = null,
                    relativeHumidity = null,
                    precipitation10 = null,
                    precipitation60 = null,
                    stationName = null,
                    stationDistance = null
                ),
                place = DefaultPlace,
                fetchedAt = Instant.now()
            ),
            onRefresh = {},
            onRetry = {},
            snackbarHostState = SnackbarHostState()
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenErrorPreview() {
    WeatherTheme {
        HomeScreen(
            state = HomeUiState.Error(ErrorKind.Network),
            onRefresh = {},
            onRetry = {},
            snackbarHostState = SnackbarHostState()
        )
    }
}
