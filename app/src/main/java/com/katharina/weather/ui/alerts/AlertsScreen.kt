package com.katharina.weather.ui.alerts

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.katharina.weather.R
import com.katharina.weather.domain.model.*
import com.katharina.weather.ui.format.formatAlertValidity
import com.katharina.weather.ui.format.formatRelativeTime
import com.katharina.weather.ui.theme.WeatherTheme
import com.katharina.weather.ui.util.getStyle
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    state: AlertsUiState,
    isRefreshing: Boolean,
    hasNotificationPermission: Boolean,
    onRefresh: () -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onBack: () -> Unit,
    onInsertTestAlert: () -> Unit,
    onPostTestNotification: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = "Warnings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_arrow_back),
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    var menuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_info),
                                contentDescription = "Debug options"
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Insert test alert (Cache)") },
                                onClick = {
                                    menuExpanded = false
                                    onInsertTestAlert()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Post test notification (OS)") },
                                onClick = {
                                    menuExpanded = false
                                    onPostTestNotification()
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (state) {
                is AlertsUiState.Unknown -> {
                    AlertsErrorContent(
                        message = "Warnings: unavailable",
                        onRetry = onRefresh,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is AlertsUiState.Known -> {
                    AlertsKnownContent(
                        state = state,
                        hasNotificationPermission = hasNotificationPermission,
                        onToggleNotifications = onToggleNotifications
                    )
                }
            }
        }
    }
}

@Composable
fun AlertsErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Button(onClick = onRetry) {
            Text(text = stringResource(R.string.btn_retry))
        }
    }
}

@Composable
fun AlertsKnownContent(
    state: AlertsUiState.Known,
    hasNotificationPermission: Boolean,
    onToggleNotifications: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val now = Instant.now()
    val context = LocalContext.current
    val checkedStr = formatRelativeTime(state.checkedAt, now)
    val areaStr = listOfNotNull(state.area?.name, state.area?.district).joinToString(", ")

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Stale Notice Banner at the top
        if (state.isStale) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Warnings could not be checked recently. They may be out of date.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Area & Metadata Header
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Checked $checkedStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (areaStr.isNotEmpty()) {
                    Text(
                        text = "Warning area: $areaStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Active Section
        if (state.visible.active.isNotEmpty()) {
            item {
                Text(
                    text = "Active warnings (${state.visible.active.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(items = state.visible.active, key = { it.id }) { alert ->
                AlertCard(alert = alert)
            }
        }

        // Upcoming Section
        if (state.visible.upcoming.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Upcoming warnings (${state.visible.upcoming.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(items = state.visible.upcoming, key = { it.id }) { alert ->
                AlertCard(alert = alert)
            }
        }

        // Empty state
        if (state.visible.active.isEmpty() && state.visible.upcoming.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No active warnings. Checked $checkedStr.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Notification Switch Card (Positioned below the warnings list)
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Notify me about severe warnings",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = state.isNotificationsEnabled && hasNotificationPermission,
                            onCheckedChange = { checked -> onToggleNotifications(checked) }
                        )
                    }

                    if (state.isNotificationsEnabled && !hasNotificationPermission) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Notification permission is required to receive warning alerts. Please enable notifications in system settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            }
                        ) {
                            Text(text = "Open settings")
                        }
                    }
                }
            }
        }

        // Attribution
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Source: Deutscher Wetterdienst",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun AlertCard(alert: WeatherAlert, modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    val style = alert.severity.getStyle(isDark)
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Severity Bar
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .fillMaxHeight()
                    .background(style.color)
            )

            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Severity Chip & Label
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = style.containerColor,
                        contentColor = style.onContainerColor,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = style.iconRes),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = style.onContainerColor
                            )
                            Text(
                                text = style.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Event & Headline
                Text(
                    text = alert.event,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (!alert.headline.isNullOrBlank()) {
                    Text(
                        text = alert.headline,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Validity Range
                val validityStr = formatAlertValidity(alert.onset, alert.expires)
                if (validityStr.isNotEmpty()) {
                    Text(
                        text = validityStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Description
                if (!alert.description.isNullOrBlank()) {
                    Text(
                        text = alert.description,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // Instruction
                if (!alert.instruction.isNullOrBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Instruction:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = alert.instruction,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = if (expanded) Int.MAX_VALUE else 3
                        )

                        if (alert.instruction.length > 100) {
                            TextButton(
                                onClick = { expanded = !expanded },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text(text = if (expanded) "Show less" else "Show more")
                            }
                        }
                    }
                }
            }
        }
    }
}

// Previews
@Preview(showBackground = true)
@Composable
fun AlertsScreenNonePreview() {
    WeatherTheme {
        AlertsScreen(
            state = AlertsUiState.Known(
                visible = VisibleAlerts(active = emptyList(), upcoming = emptyList()),
                area = AlertArea("München-Altstadt-Lehel", "München", "Bayern"),
                checkedAt = Instant.now(),
                isStale = false,
                isNotificationsEnabled = false
            ),
            isRefreshing = false,
            hasNotificationPermission = true,
            onRefresh = {},
            onToggleNotifications = {},
            onBack = {},
            onInsertTestAlert = {},
            onPostTestNotification = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AlertsScreenOneMinorPreview() {
    WeatherTheme {
        AlertsScreen(
            state = AlertsUiState.Known(
                visible = VisibleAlerts(
                    active = listOf(
                        WeatherAlert(
                            id = "1",
                            eventCode = 22,
                            event = "Frost",
                            headline = "Official WARNING of FROST",
                            description = "There is a risk of frost.",
                            instruction = "Take precautions against frost.",
                            severity = AlertSeverity.MINOR,
                            effective = Instant.now(),
                            onset = Instant.now(),
                            expires = Instant.now().plusSeconds(3600)
                        )
                    ),
                    upcoming = emptyList()
                ),
                area = AlertArea("Frankfurt-Nord", "Frankfurt am Main", "Hessen"),
                checkedAt = Instant.now(),
                isStale = false,
                isNotificationsEnabled = true
            ),
            isRefreshing = false,
            hasNotificationPermission = true,
            onRefresh = {},
            onToggleNotifications = {},
            onBack = {},
            onInsertTestAlert = {},
            onPostTestNotification = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AlertsScreenSevereAndMorePreview() {
    WeatherTheme {
        AlertsScreen(
            state = AlertsUiState.Known(
                visible = VisibleAlerts(
                    active = listOf(
                        WeatherAlert("1", 99, "Thunderstorm", "Severe Thunderstorm Warning", "Heavy rain and hail expected.", "Stay indoors.", AlertSeverity.SEVERE, Instant.now(), Instant.now(), Instant.now().plusSeconds(7200)),
                        WeatherAlert("2", 22, "Frost", "Minor Frost Warning", "Light frost.", null, AlertSeverity.MINOR, Instant.now(), Instant.now(), Instant.now().plusSeconds(3600)),
                        WeatherAlert("3", 51, "Wind", "Marked Wind Warning", "Strong gusts.", null, AlertSeverity.MODERATE, Instant.now(), Instant.now(), Instant.now().plusSeconds(3600))
                    ),
                    upcoming = emptyList()
                ),
                area = AlertArea("Frankfurt-Nord", "Frankfurt am Main", "Hessen"),
                checkedAt = Instant.now(),
                isStale = false,
                isNotificationsEnabled = true
            ),
            isRefreshing = false,
            hasNotificationPermission = true,
            onRefresh = {},
            onToggleNotifications = {},
            onBack = {},
            onInsertTestAlert = {},
            onPostTestNotification = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AlertsScreenUpcomingOnlyPreview() {
    WeatherTheme {
        AlertsScreen(
            state = AlertsUiState.Known(
                visible = VisibleAlerts(
                    active = emptyList(),
                    upcoming = listOf(
                        WeatherAlert("1", 22, "Frost", "Upcoming Frost Warning", "Frost tomorrow morning.", null, AlertSeverity.MINOR, Instant.now(), Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200))
                    )
                ),
                area = AlertArea("Frankfurt-Nord", "Frankfurt am Main", "Hessen"),
                checkedAt = Instant.now(),
                isStale = false,
                isNotificationsEnabled = false
            ),
            isRefreshing = false,
            hasNotificationPermission = true,
            onRefresh = {},
            onToggleNotifications = {},
            onBack = {},
            onInsertTestAlert = {},
            onPostTestNotification = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AlertsScreenStalePreview() {
    WeatherTheme {
        AlertsScreen(
            state = AlertsUiState.Known(
                visible = VisibleAlerts(
                    active = listOf(
                        WeatherAlert("1", 99, "Thunderstorm", "Severe Thunderstorm Warning", "Heavy rain and hail expected.", "Stay indoors.", AlertSeverity.SEVERE, Instant.now(), Instant.now(), Instant.now().plusSeconds(7200))
                    ),
                    upcoming = emptyList()
                ),
                area = AlertArea("Frankfurt-Nord", "Frankfurt am Main", "Hessen"),
                checkedAt = Instant.now().minusSeconds(7200),
                isStale = true,
                isNotificationsEnabled = false
            ),
            isRefreshing = false,
            hasNotificationPermission = true,
            onRefresh = {},
            onToggleNotifications = {},
            onBack = {},
            onInsertTestAlert = {},
            onPostTestNotification = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AlertsScreenUnknownPreview() {
    WeatherTheme {
        AlertsScreen(
            state = AlertsUiState.Unknown,
            isRefreshing = false,
            hasNotificationPermission = true,
            onRefresh = {},
            onToggleNotifications = {},
            onBack = {},
            onInsertTestAlert = {},
            onPostTestNotification = {}
        )
    }
}
