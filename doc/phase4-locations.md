# Phase 4: Locations

**Goal:** replace the hardcoded Munich with (1) saved places you can add, remove and switch between, (2) place search by name, and (3) "use my current location". Still no weather caching (Phase 5).

Two commits: **4a** saved places + search + switching, **4b** device location.

## Decisions

| Topic | Decision |
|---|---|
| Place-name search | **Open-Meteo Geocoding API** (`https://geocoding-api.open-meteo.com/v1/search`): free for non-commercial use, no key, structured JSON, built for as-you-type search. Bright Sky has no place search; it only takes coordinates. |
| Rejected: Android `Geocoder` | No dependency, but it relies on a Google backend that isn't guaranteed to exist (and is flaky on emulators), and results are less structured. Still used in 4b for the *reverse* lookup (coordinates → name), with a fallback label. |
| Rejected: Nominatim (OSM) | Its usage policy is strict about request rates and doesn't allow as-you-type search. |
| Search scope | **Germany only** (`countryCode=DE`), no worldwide switch in v1. Two reasons: Bright Sky's data quality outside Germany isn't verified, and the app hardcodes `Europe/Berlin` for the API `tz` parameter and for the daily aggregation, which would give wrong days for places in other time zones. (If you add worldwide search later, store the geocoder's `timezone` on each place and thread it through the repository and `toDailyForecasts`.) |
| Storage | **Room** for saved places, **DataStore** for the current selection |
| First launch | No hardcoded default. Home shows an empty state: "Choose a location" with "Search" and "Use my location" buttons. `DefaultPlace` is deleted. |
| Navigation | Two destinations: `Home` and `Places` (search + saved list on one screen), type-safe routes |
| Device location | `FusedLocationProviderClient` (`play-services-location`). **Coarse permission only**: city-level accuracy is plenty for weather, and it gives the user the privacy-friendlier dialog. Foreground only, no background location. |
| Attribution | Add "Place search: Open-Meteo (GeoNames data)" in small text on the Places screen |

New dependencies: `com.google.android.gms:play-services-location` (4b only). Room, DataStore and Navigation come from Phase 0; if any is missing, ask before adding. Tests: nothing new.

If your phone has no Google services, say so: then 4b should use the platform `LocationManager.getCurrentLocation` instead (no dependency).

## Fixtures to capture first

```bash
# a normal search
curl -s "https://geocoding-api.open-meteo.com/v1/search?name=Garching&count=10&language=en&countryCode=DE&format=json" \
  -o app/src/test/resources/geocoding_garching.json

# no matches
curl -s "https://geocoding-api.open-meteo.com/v1/search?name=zzzzqxw&count=10&language=en&countryCode=DE&format=json" \
  -o app/src/test/resources/geocoding_empty.json
```

Check these against the real output:
- The response has a `results` array with fields like `id` (GeoNames id), `name`, `latitude`, `longitude`, `country`, `country_code`, `admin1`, `population` and `timezone`. **Fields that don't apply are omitted**, so every field except `id`, `name`, `latitude` and `longitude` must be nullable.
- With no matches, the `results` key is missing entirely (confirmed by your fixture, which contains only `generationtime_ms`), so `results` must default to an empty list.
- **Verify the country filter parameter name** (`countryCode`) against the official docs page (open-meteo.com/en/docs/geocoding-api). I saw it spelled differently in a third-party library.
- Search needs at least 2 characters to be useful. Debounce input by about 300 ms.

## 4a: Saved places, search, switching

### Verified against your fixtures

- The empty response is just `{"generationtime_ms": ...}`, so `results = emptyList()` as default is required.
- The Garching response matches the DTO below: `id`, `name`, `latitude`, `longitude`, `admin1`, `admin3`, `country`, `country_code`, `population`. Extra fields (`elevation`, `feature_code`, `timezone`, `admin1_id`...`admin4`, `country_id`) are ignored by `ignoreUnknownKeys`.
- **Disambiguation:** the search returns "Garching" (district Munich) and "Garching an der Alz" (Landkreis Altötting), both in Bavaria. A subtitle of "Bavaria, Germany" wouldn't tell similar names apart, so build `region` as `listOfNotNull(admin3, admin1).distinct().joinToString(", ")`, which gives "Munich, Bavaria" and "Landkreis Altötting, Bavaria". Display `name` as the title, not `admin4`.
- Names contain real umlauts ("Landkreis Altötting"); read fixtures as UTF-8 in tests.
- Duplicate protection uses `id` (the GeoNames id) as `geonamesId`.

### Domain

```kotlin
data class Place(
    val id: Long,                 // Room id; CURRENT_LOCATION_ID (-1) for device location
    val name: String,
    val region: String?,          // e.g. "Bavaria"
    val country: String?,         // e.g. "Germany"
    val latitude: Double,
    val longitude: Double,
    val isCurrentLocation: Boolean = false,
)

data class PlaceSearchResult(
    val geonamesId: Long, val name: String, val region: String?, val country: String?,
    val latitude: Double, val longitude: Double,
)

sealed interface PlaceSelection {
    data object None : PlaceSelection
    data object CurrentLocation : PlaceSelection
    data class Saved(val id: Long) : PlaceSelection
}
```

Repositories (interfaces in `domain`):

```kotlin
interface PlaceRepository {
    val savedPlaces: Flow<List<Place>>
    val selection: Flow<PlaceSelection>
    suspend fun save(result: PlaceSearchResult): Place   // idempotent by geonamesId
    suspend fun delete(id: Long)
    suspend fun select(selection: PlaceSelection)
}

interface GeocodingRepository {
    suspend fun search(query: String): Result<List<PlaceSearchResult>>
}
```

### Persistence (Room + DataStore)

```kotlin
@Entity(tableName = "places", indices = [Index("geonamesId", unique = true)])
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val geonamesId: Long,
    val name: String,
    val region: String?,
    val country: String?,
    val latitude: Double,
    val longitude: Double,
    val createdAt: Long,
)

@Dao
interface PlaceDao {
    @Query("SELECT * FROM places ORDER BY createdAt")
    fun observeAll(): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM places WHERE geonamesId = :geonamesId")
    suspend fun findByGeonamesId(geonamesId: Long): PlaceEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: PlaceEntity): Long   // -1 if ignored

    @Query("DELETE FROM places WHERE id = :id")
    suspend fun delete(id: Long)
}
```

Selection in DataStore as a string: `"none"`, `"current"` or `"saved:<id>"`. Parse defensively: an unknown or stale value (for example a deleted id) means `None`.

Pitfalls:
- **Deleting the selected place:** select the first remaining saved place, or `None` if there is none. Do it in the repository, in one place.
- **Room schema:** `exportSchema = true` and commit the schema JSON. Phase 5 adds cache tables, so you'll need a real migration (an auto-migration is fine). Do **not** use `fallbackToDestructiveMigration` here, since it would wipe your saved places.

### Geocoding data layer

```kotlin
@Serializable
data class GeocodingResponseDto(val results: List<GeocodingResultDto> = emptyList())

@Serializable
data class GeocodingResultDto(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val admin1: String? = null,   // state, e.g. "Bavaria"
    val admin3: String? = null,   // district, e.g. "Munich"
    val country: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    val population: Long? = null,
)

interface GeocodingApi {
    @GET("v1/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("count") count: Int = 10,
        @Query("language") language: String = "en",
        @Query("countryCode") countryCode: String = "DE",   // verify the name in the docs
        @Query("format") format: String = "json",
    ): GeocodingResponseDto
}
```

Provide it with a **second Retrofit instance** (base URL `https://geocoding-api.open-meteo.com/`) in the Hilt module, using qualifiers, so the Bright Sky client isn't affected. Reuse the same `OkHttpClient` and `Json`. Return an empty list for queries under 2 characters without calling the network.

### Home screen changes

- Remove `DefaultPlace`. `HomeViewModel` observes `PlaceRepository.selection`, resolves it to a `Place`, and loads weather for that place.
- New state `HomeUiState.NoPlace` (empty state with the two buttons).
- The top bar shows the place name, with an icon button that opens `Places`.
- **Switching places must not show stale data.** When the selection changes, cancel any in-flight load, set `Loading`, and load for the new place. Never apply a response to a place other than the one it was requested for. Keep the place inside `Success` and ignore results whose place no longer matches.
- The "refresh when older than 15 minutes" logic from Phase 2 stays, but is checked per place.

### Places screen

- A search field at the top (debounced about 300 ms, minimum 2 characters, `flatMapLatest` so old responses can't overwrite newer ones), and results below. Each result shows `name` and a subtitle like "Bavaria, Germany". Tapping a result saves it, selects it and returns to Home.
- When the search field is empty: the saved places list (select on tap, delete via a trash icon or swipe, with an undo snackbar) and a "Use my current location" button (wired up in 4b).
- States for search: idle, loading, results, empty ("No places found"), error ("Search needs an internet connection").
- Stateless `PlacesScreen`, `PlacesViewModel` with one `StateFlow<PlacesUiState>`.

### Prompt for 4a

```
Context: see AGENTS.md. We are in Phase 4a: Saved places, search, switching.
Follow docs/phase4-locations.md exactly. Fixtures: app/src/test/resources/geocoding_garching.json
and geocoding_empty.json are ground truth.

1. domain: Place, PlaceSearchResult, PlaceSelection, PlaceRepository, GeocodingRepository.
2. data/local: PlaceEntity, PlaceDao, AppDatabase (exportSchema = true, no destructive migration),
   DataStore selection storage with defensive parsing.
3. data/remote: geocoding DTOs (all optional fields nullable, empty results by default),
   GeocodingApi on a second Retrofit instance (Hilt qualifiers), mapper, repository returning Result.
4. PlaceRepositoryImpl: save() idempotent by geonamesId, delete() reselects when the selected place
   is deleted.
5. Remove DefaultPlace. HomeViewModel loads for the selected place, NoPlace state, cancel in-flight
   loads on place change, never show another place's data.
6. Navigation: type-safe routes Home and Places; PlacesScreen + PlacesViewModel as described.
7. Strings in strings.xml (English). Previews for all Places states and the NoPlace state.
8. Tests: geocoding mapper from fixtures (Garching: 2 results, first has id 2922582, name "Garching", region "Munich, Bavaria", lat 48.24896, lon 11.65101, country "Germany"; second has region "Landkreis Altötting, Bavaria"; empty fixture: empty list; missing optional fields), PlacesViewModel
   with fakes (debounce, minimum length, latest-wins, error, empty), PlaceRepository with fake DAO and
   DataStore (duplicate save, delete selected), HomeViewModel place switching (stale response is ignored).
No new dependencies. List files first, then code, then tests.
Acceptance: search "Garching", save it, switch between two places; kill and restart the app and the
selection survives; airplane mode shows the search error; ./gradlew testDebugUnitTest passes.
```

## 4b: Current location

### Behavior

- "Use my current location" on the Places screen (and on the Home empty state) selects `PlaceSelection.CurrentLocation`.
- Each load for the current location first asks a `LocationProvider` for coordinates: `getCurrentLocation` with balanced power accuracy and a timeout of about 10 s, falling back to `lastLocation`. Don't save these coordinates as a place.
- The place name comes from a reverse lookup with `Geocoder` (async API on Android 13+). If that fails or isn't available, show "Current location". Never block loading the weather on the name.

```kotlin
interface LocationProvider {
    suspend fun currentLocation(): Result<Coordinates>   // failures below
}
class LocationPermissionException : Exception()
class LocationUnavailableException : Exception()
```

### Permission flow

- Manifest: `ACCESS_COARSE_LOCATION` only.
- Request from the UI (`rememberLauncherForActivityResult(RequestPermission())`), only when the user taps the button. Check with `ContextCompat.checkSelfPermission` before calling the provider.
- If denied: show a message explaining why it's needed, and a button that opens the app's settings page. If Android stops showing the dialog after repeated denials, the settings button is the only way. The app keeps working with saved places.
- Add error kinds `LocationPermissionRequired` and `LocationUnavailable` (location switched off in system settings, or no fix in time) with their own messages and actions on the Home error screen.

### Prompt for 4b

```
Context: see AGENTS.md. We are in Phase 4b: Current location. Follow docs/phase4-locations.md.
1. Add play-services-location (the only new dependency). Manifest: ACCESS_COARSE_LOCATION only.
2. domain: LocationProvider + exceptions. data: FusedLocationProvider-based implementation
   (getCurrentLocation, balanced power, 10 s timeout, lastLocation fallback), reverse-geocoding
   helper using Geocoder (async API) with fallback label "Current location".
3. HomeViewModel: when the selection is CurrentLocation, fetch coordinates before loading weather;
   map failures to new error kinds LocationPermissionRequired / LocationUnavailable.
4. UI: permission request launcher on the Places screen and Home empty/error states; denied state
   with an "Open settings" button.
5. Tests: HomeViewModel with a fake LocationProvider (success, permission failure, unavailable,
   timeout), selection survives restart.
List files first, then code, then tests.
Acceptance: on the emulator set a location via Extended controls and select "Current location";
revoking the permission (adb shell pm revoke <package> android.permission.ACCESS_COARSE_LOCATION)
shows the permission message; location services off shows the unavailable message.
```

## Review checklist

- [ ] First launch (clear app data) shows the empty state, not Munich
- [ ] Searching, saving the same place twice doesn't create a duplicate
- [ ] Switching places never briefly shows the previous place's weather under the new name
- [ ] Deleting the selected place selects another or shows the empty state
- [ ] Selection survives process death and a device restart
- [ ] Search is debounced; fast typing doesn't show out-of-order results
- [ ] All optional geocoding fields are nullable; the empty-result fixture parses
- [ ] No `fallbackToDestructiveMigration` on the places database; schema exported
- [ ] Only coarse location requested; no background location
- [ ] Denied permission and disabled location services both show a useful message
- [ ] Open-Meteo attribution visible on the Places screen; DWD attribution still on Home
- [ ] Tests green; commit after 4a and after 4b

**Done when:** you can search and save a few places, switch between them, use the device location, and everything survives an app restart.

## Next: Phase 5

Caching and background refresh: Room tables for the latest current and forecast data per place, timestamps and staleness rules, an offline-first repository (show cached data instantly, refresh in the background, show "last updated"), and a periodic WorkManager refresh. That needs the real Room migration mentioned above.
