# Phase 2: Home Screen (Current Weather)

**Goal:** a first real screen showing current weather for a hardcoded location (Munich), with loading, error and pull-to-refresh states. Uses only the `getCurrentWeather` repository method from Phase 1. The forecast is Phase 3.

## Before you start (easy to miss)

- **`INTERNET` permission.** The Compose project template doesn't include it, so the first request fails without it. Add to `AndroidManifest.xml`:
  ```xml
  <uses-permission android:name="android.permission.INTERNET" />
  ```
- Phase 1 tests are green and the repository is bound in Hilt.
- `MainActivity` is `@AndroidEntryPoint`, calls `enableEdgeToEdge()`, and wraps content in your theme.

## Scope and decisions

| Topic | Decision |
|---|---|
| Location | Hardcoded `Place("Munich", 48.137, 11.575)` in the domain layer. Phase 4 replaces it. |
| State | `sealed interface HomeUiState`: `Loading`, `Success`, `Error` |
| Refresh | Pull-to-refresh (`PullToRefreshBox`), a Retry button on the error screen, and an automatic refresh on returning to the app when data is older than 15 minutes |
| Refresh failure | If data is already shown, keep it and show a snackbar instead of replacing it with an error screen |
| Icons | Vector drawables created with Android Studio's **Vector Asset** tool (Material Symbols). Avoids the large `material-icons-extended` dependency. |
| Time display | "Observed 19:00" in Europe/Berlin time (no relative time) |
| Offline cache | Not yet (Phase 5). A failed first load shows the error screen. |

Allowed new dependencies for this phase: `androidx.hilt:hilt-navigation-compose`, `androidx.lifecycle:lifecycle-runtime-compose`, and for tests `kotlinx-coroutines-test`, `app.cash.turbine`, `io.mockk`. Use the latest stable versions via the version catalog.

## Screen layout

```
┌───────────────────────────┐
│ Munich                    │  place name (title)
│                           │
│        [icon]             │  large condition icon
│        17°                │  large temperature
│        Cloudy             │  condition text
│                           │
│ ┌────────┐ ┌────────┐     │  detail cards (2 columns)
│ │ Wind   │ │Humidity│     │  Wind: 8 km/h from NE, gusts 13
│ └────────┘ └────────┘     │  Humidity, Pressure, Rain (now and last hour),
│ ┌────────┐ ┌────────┐     │  Cloud cover, Visibility, Dew point
│ │Pressure│ │ Rain   │     │
│ └────────┘ └────────┘     │
│ ...                       │
│ Observed 19:00 · Station  │  footer
│ Muenchen-Stadt (3.8 km)   │
│ Data: Deutscher           │
│ Wetterdienst via Bright   │
│ Sky                       │
└───────────────────────────┘
```

Null values are shown as "–", never as "null" or "0".

