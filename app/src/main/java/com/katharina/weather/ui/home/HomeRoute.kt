package com.katharina.weather.ui.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun HomeRoute(
    onNavigateToPlaces: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.selectCurrentLocation()
    }

    LaunchedEffect(Unit) {
        viewModel.onStart()
    }

    if (state is HomeUiState.Success) {
        val userError = (state as HomeUiState.Success).userPullToRefreshError
        if (userError != null) {
            LaunchedEffect(userError) {
                snackbarHostState.showSnackbar(userError)
            }
        }
    }

    HomeScreen(
        state = state,
        onRefresh = { viewModel.refresh(userInitiated = true) },
        onRetry = { viewModel.refresh(userInitiated = false) },
        onNavigateToPlaces = onNavigateToPlaces,
        onUseCurrentLocation = {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                viewModel.selectCurrentLocation()
            } else {
                permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        },
        snackbarHostState = snackbarHostState
    )
}
