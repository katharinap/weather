package com.katharina.weather.ui.places

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun PlacesRoute(
    onBack: () -> Unit,
    viewModel: PlacesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    HomeScreenSnackbarHandler(
        state = state,
        snackbarHostState = snackbarHostState,
        onUndo = { viewModel.undoDelete() }
    )

    PlacesScreen(
        state = state,
        onQueryChanged = { viewModel.onQueryChanged(it) },
        onSearchResultClicked = { result ->
            viewModel.onSearchResultClicked(result, onSaved = onBack)
        },
        onSelectSavedPlace = { selection ->
            viewModel.onSelectSavedPlace(selection, onSelected = onBack)
        },
        onDeletePlace = { place ->
            viewModel.onDeletePlace(place)
        },
        onBack = onBack,
        snackbarHostState = snackbarHostState
    )
}

@Composable
private fun HomeScreenSnackbarHandler(
    state: PlacesUiState,
    snackbarHostState: SnackbarHostState,
    onUndo: () -> Unit
) {
    val deletedMessage = state.deletedPlaceMessage
    if (deletedMessage != null) {
        LaunchedEffect(deletedMessage) {
            val result = snackbarHostState.showSnackbar(
                message = deletedMessage,
                actionLabel = "Undo"
            )
            if (result == SnackbarResult.ActionPerformed) {
                onUndo()
            }
        }
    }
}
