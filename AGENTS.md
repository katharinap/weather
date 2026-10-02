# Project: Personal DWD Weather App (Android)

## Goal
Personal-use weather app for Germany using DWD data via Bright Sky (https://api.brightsky.dev).
Not for release.  Android 15 only: minSdk = 35, targetSdk = 35, compileSdk = 37 (required by current AndroidX).
Only use APIs available at API 35; do not use newer platform APIs.

## Stack
Kotlin, Jetpack Compose (Material 3), Hilt, Retrofit + kotlinx.serialization,
Room, DataStore, WorkManager, Coroutines/Flow. Versions in gradle/libs.versions.toml.

## Architecture
- Layers: ui -> domain -> data. Domain has no Android dependencies.
- ViewModels expose one StateFlow<UiState>.
- DTOs only in data/remote; map to domain models. DTO fields nullable, ignoreUnknownKeys = true.
- Offline-first repositories returning Flow.
- Edge-to-edge UI; handle window insets.

## Conventions
- Stateless composables with hoisted state; @Preview for each screen/component.
- Theme tokens only, no hardcoded colors or dimens.
- UI language is English; all strings in strings.xml.
- Unit tests for mappers, use cases, ViewModels; MockWebServer for the API.
- No new dependencies without asking. No compatibility code for API < 35.
- Be gentle with the public API: cache, refresh at most every 30 minutes.

## Commands
- Build: ./gradlew assembleDebug
- Unit tests: ./gradlew testDebugUnitTest

## Current phase
Phase 0: Setup
