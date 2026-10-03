package com.katharina.weather.ui.home

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun HomeRoute(
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.onStart()
    }

    if (state is HomeUiState.Success) {
        val transientError = (state as HomeUiState.Success).transientError
        if (transientError != null) {
            LaunchedEffect(transientError) {
                snackbarHostState.showSnackbar(transientError)
            }
        }
    }

    HomeScreen(
        state = state,
        onRefresh = { viewModel.refresh() },
        onRetry = { viewModel.loadWeather(isRefreshing = false) },
        snackbarHostState = snackbarHostState
    )
}
