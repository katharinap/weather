package com.katharina.weather.ui.alerts

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AlertsRoute(
    onBack: () -> Unit,
    viewModel: AlertsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            viewModel.setNotificationsEnabled(true)
        } else {
            viewModel.setNotificationsEnabled(false)
        }
    }

    AlertsScreen(
        state = state,
        isRefreshing = isRefreshing,
        hasNotificationPermission = hasPermission,
        onRefresh = { viewModel.refresh() },
        onToggleNotifications = { enabled ->
            if (enabled) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED

                    hasPermission = granted
                    if (granted) {
                        viewModel.setNotificationsEnabled(true)
                    } else {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    hasPermission = true
                    viewModel.setNotificationsEnabled(true)
                }
            } else {
                viewModel.setNotificationsEnabled(false)
            }
        },
        onBack = onBack,
        onInsertTestAlert = { viewModel.insertTestAlertForDebug() },
        onPostTestNotification = { viewModel.postTestNotificationForDebug() }
    )
}
