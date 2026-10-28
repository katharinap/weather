package com.katharina.weather.ui.places

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.katharina.weather.domain.model.PlaceSelection

@Composable
fun PlacesRoute(
    onBack: () -> Unit,
    viewModel: PlacesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.onSelectSavedPlace(PlaceSelection.CurrentLocation, onSelected = onBack)
        } else {
            viewModel.onSelectSavedPlace(PlaceSelection.CurrentLocation, onSelected = onBack)
        }
    }

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
            if (selection is PlaceSelection.CurrentLocation) {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                if (hasPermission) {
                    viewModel.onSelectSavedPlace(selection, onSelected = onBack)
                } else {
                    permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            } else {
                viewModel.onSelectSavedPlace(selection, onSelected = onBack)
            }
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
