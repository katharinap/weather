package com.katharina.weather.ui.places

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.katharina.weather.R
import com.katharina.weather.domain.model.Place
import com.katharina.weather.domain.model.PlaceSearchResult
import com.katharina.weather.domain.model.PlaceSelection
import com.katharina.weather.ui.format.formatPlaceSubtitle
import com.katharina.weather.ui.theme.WeatherTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacesScreen(
    state: PlacesUiState,
    onQueryChanged: (String) -> Unit,
    onSearchResultClicked: (PlaceSearchResult) -> Unit,
    onSelectSavedPlace: (PlaceSelection) -> Unit,
    onDeletePlace: (Place) -> Unit,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.title_places)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_arrow_back),
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(text = stringResource(R.string.search_hint)) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_search),
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChanged("") }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_clear),
                                contentDescription = "Clear"
                            )
                        }
                    }
                },
                singleLine = true
            )

            // Body Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (state.query.trim().length >= 2) {
                    SearchResultsContent(
                        searchState = state.searchState,
                        onSearchResultClicked = onSearchResultClicked
                    )
                } else {
                    SavedPlacesAndLocationContent(
                        savedPlaces = state.savedPlaces,
                        selection = state.selection,
                        onSelectSavedPlace = onSelectSavedPlace,
                        onDeletePlace = onDeletePlace
                    )
                }
            }

            // Open-Meteo Attribution
            Text(
                text = stringResource(R.string.attribution_open_meteo),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 12.dp)
            )
        }
    }
}

@Composable
fun SearchResultsContent(
    searchState: SearchUiState,
    onSearchResultClicked: (PlaceSearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (searchState) {
            is SearchUiState.Idle, is SearchUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            is SearchUiState.Empty -> {
                Text(
                    text = stringResource(R.string.places_empty_search),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            is SearchUiState.Error -> {
                Text(
                    text = stringResource(R.string.places_error_search),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            is SearchUiState.Success -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items = searchState.results, key = { it.geonamesId }) { result ->
                        SearchResultItem(result = result, onClick = { onSearchResultClicked(result) })
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResultItem(result: PlaceSearchResult, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = result.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            val subtitle = formatPlaceSubtitle(result.region)
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SavedPlacesAndLocationContent(
    savedPlaces: List<Place>,
    selection: PlaceSelection,
    onSelectSavedPlace: (PlaceSelection) -> Unit,
    onDeletePlace: (Place) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Current Location Option
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectSavedPlace(PlaceSelection.CurrentLocation) },
                colors = CardDefaults.cardColors(
                    containerColor = if (selection is PlaceSelection.CurrentLocation) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_location_on),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.btn_use_current_location),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Saved Places List Header
        if (savedPlaces.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.title_saved_places),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(items = savedPlaces, key = { it.id }) { place ->
                val isSelected = selection is PlaceSelection.Saved && selection.id == place.id
                SavedPlaceItem(
                    place = place,
                    isSelected = isSelected,
                    onSelect = { onSelectSavedPlace(PlaceSelection.Saved(place.id)) },
                    onDelete = { onDeletePlace(place) }
                )
            }
        }
    }
}

@Composable
fun SavedPlaceItem(
    place: Place,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = place.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                val subtitle = formatPlaceSubtitle(place.region)
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_delete),
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// Previews
@Preview(showBackground = true)
@Composable
fun PlacesScreenSavedListPreview() {
    WeatherTheme {
        PlacesScreen(
            state = PlacesUiState(
                query = "",
                savedPlaces = listOf(
                    Place(1, "Garching", "Munich, Bavaria", "Germany", 48.248, 11.651),
                    Place(2, "Munich", "Bavaria", "Germany", 48.137, 11.575)
                ),
                selection = PlaceSelection.Saved(1)
            ),
            onQueryChanged = {},
            onSearchResultClicked = {},
            onSelectSavedPlace = {},
            onDeletePlace = {},
            onBack = {},
            snackbarHostState = SnackbarHostState()
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PlacesScreenSearchResultsPreview() {
    WeatherTheme {
        PlacesScreen(
            state = PlacesUiState(
                query = "Garching",
                searchState = SearchUiState.Success(
                    results = listOf(
                        PlaceSearchResult(2922582, "Garching", "Munich, Bavaria", "Germany", 48.24896, 11.65101),
                        PlaceSearchResult(2922583, "Garching an der Alz", "Landkreis Altötting, Bavaria", "Germany", 48.13451, 12.58152)
                    )
                )
            ),
            onQueryChanged = {},
            onSearchResultClicked = {},
            onSelectSavedPlace = {},
            onDeletePlace = {},
            onBack = {},
            snackbarHostState = SnackbarHostState()
        )
    }
}
