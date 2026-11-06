package com.katharina.weather.data.remote.mapper

import com.katharina.weather.data.remote.dto.AlertsResponseDto
import com.katharina.weather.domain.model.AlertArea
import com.katharina.weather.domain.model.AlertSeverity
import com.katharina.weather.domain.model.AlertsResult
import com.katharina.weather.domain.model.WeatherAlert
import java.time.OffsetDateTime

fun AlertsResponseDto.toAlertsResult(): AlertsResult {
    val mappedArea = location?.let {
        AlertArea(name = it.name, district = it.district, state = it.state)
    }

    val mappedAlerts = alerts.mapNotNull { dto ->
        val id = dto.alertId ?: return@mapNotNull null
        if (dto.status != null && !dto.status.equals("actual", ignoreCase = true)) {
            return@mapNotNull null
        }

        val eventRaw = dto.eventEn ?: dto.eventDe ?: "Weather warning"
        val eventText = eventRaw.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

        val headlineText = (dto.headlineEn ?: dto.headlineDe)?.trim()
        val descText = (dto.descriptionEn ?: dto.descriptionDe)?.trim()
        val instrText = (dto.instructionEn ?: dto.instructionDe)?.trim()

        val severityEnum = AlertSeverity.parse(dto.severity)

        val effInstant = try { dto.effective?.let { OffsetDateTime.parse(it).toInstant() } } catch (_: Exception) { null }
        val onsetInstant = try { dto.onset?.let { OffsetDateTime.parse(it).toInstant() } } catch (_: Exception) { null }
        val expInstant = try { dto.expires?.let { OffsetDateTime.parse(it).toInstant() } } catch (_: Exception) { null }

        WeatherAlert(
            id = id,
            eventCode = dto.eventCode,
            event = eventText,
            headline = headlineText,
            description = descText,
            instruction = instrText,
            severity = severityEnum,
            effective = effInstant,
            onset = onsetInstant,
            expires = expInstant
        )
    }

    return AlertsResult(alerts = mappedAlerts, area = mappedArea)
}
