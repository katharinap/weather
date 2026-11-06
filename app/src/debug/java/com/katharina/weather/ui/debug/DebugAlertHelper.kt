package com.katharina.weather.ui.debug

import com.katharina.weather.data.local.WeatherCacheDao
import com.katharina.weather.data.local.WeatherCacheEntity
import com.katharina.weather.data.remote.dto.AlertDto
import com.katharina.weather.data.remote.dto.AlertLocationDto
import com.katharina.weather.data.remote.dto.AlertsResponseDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter

suspend fun insertTestAlert(
    placeKey: String,
    latitude: Double,
    longitude: Double,
    weatherCacheDao: WeatherCacheDao
) {
    val now = Instant.now()
    val timestamp = System.currentTimeMillis()
    val dto = AlertsResponseDto(
        alerts = listOf(
            AlertDto(
                alertId = "debug-test-alert-severe-$timestamp",
                status = "actual",
                severity = "severe",
                eventCode = 99,
                eventEn = "Thunderstorm",
                eventDe = "GEWITTER",
                headlineEn = "Test SEVERE Warning for Debugging",
                headlineDe = "Test WARNUNG vor Schwerem Gewitter",
                descriptionEn = "Synthetic severe test alert valid for 2 hours.",
                descriptionDe = "Synthetische schwere Testwarnung für 2 Stunden.",
                instructionEn = "Take shelter immediately. Test warning.",
                instructionDe = "Suchen Sie Schutz. Testwarnung.",
                onset = DateTimeFormatter.ISO_INSTANT.format(now.minus(Duration.ofMinutes(1))),
                expires = DateTimeFormatter.ISO_INSTANT.format(now.plus(Duration.ofHours(2)))
            ),
            AlertDto(
                alertId = "debug-test-alert-moderate-$timestamp",
                status = "actual",
                severity = "moderate",
                eventCode = 51,
                eventEn = "Wind",
                eventDe = "WIND",
                headlineEn = "Test MODERATE Wind Warning for Debugging",
                headlineDe = "Test WARNUNG vor Windböen",
                descriptionEn = "Synthetic moderate wind test alert valid for 3 hours.",
                descriptionDe = "Synthetische mäßige Windtestwarnung für 3 Stunden.",
                instructionEn = "Secure loose outdoor objects.",
                instructionDe = "Sichern Sie lose Gegenstände im Freien.",
                onset = DateTimeFormatter.ISO_INSTANT.format(now.minus(Duration.ofMinutes(1))),
                expires = DateTimeFormatter.ISO_INSTANT.format(now.plus(Duration.ofHours(3)))
            ),
            AlertDto(
                alertId = "debug-test-alert-minor-$timestamp",
                status = "actual",
                severity = "minor",
                eventCode = 22,
                eventEn = "Frost",
                eventDe = "FROST",
                headlineEn = "Test MINOR Frost Warning for Debugging",
                headlineDe = "Test WARNUNG vor Leichtem Frost",
                descriptionEn = "Synthetic minor frost test alert valid for 4 hours.",
                descriptionDe = "Synthetische leichte Frosttestwarnung für 4 Stunden.",
                instructionEn = "Take precautions against frost.",
                instructionDe = "Frostschutzmaßnahmen ergreifen.",
                onset = DateTimeFormatter.ISO_INSTANT.format(now.minus(Duration.ofMinutes(1))),
                expires = DateTimeFormatter.ISO_INSTANT.format(now.plus(Duration.ofHours(4)))
            )
        ),
        location = AlertLocationDto(name = "Test Warning Area")
    )
    val payload = Json { ignoreUnknownKeys = true }.encodeToString(dto)
    weatherCacheDao.upsert(
        WeatherCacheEntity(
            placeKey = placeKey,
            kind = "alerts",
            fetchedAt = System.currentTimeMillis(),
            formatVersion = 1,
            latitude = latitude,
            longitude = longitude,
            payload = payload
        )
    )
}
