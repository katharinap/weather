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
    val dto = AlertsResponseDto(
        alerts = listOf(
            AlertDto(
                alertId = "debug-test-alert-${System.currentTimeMillis()}",
                status = "actual",
                severity = "severe",
                eventCode = 99,
                eventEn = "Thunderstorm",
                eventDe = "GEWITTER",
                headlineEn = "Test SEVERE Warning for Debugging",
                headlineDe = "Test WARNUNG vor Schwerem Gewitter",
                descriptionEn = "Synthetic test alert valid for 2 hours.",
                descriptionDe = "Synthetische Testwarnung für 2 Stunden.",
                instructionEn = "Take shelter immediately. Test warning.",
                instructionDe = "Suchen Sie Schutz. Testwarnung.",
                onset = DateTimeFormatter.ISO_INSTANT.format(now),
                expires = DateTimeFormatter.ISO_INSTANT.format(now.plus(Duration.ofHours(2)))
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
