package com.katharina.weather.data.remote.mapper

import com.katharina.weather.data.remote.dto.AlertDto
import com.katharina.weather.data.remote.dto.AlertsResponseDto
import com.katharina.weather.domain.model.AlertSeverity
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant

class AlertsMapperTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun testAlertsActiveFixtureMapping() {
        val file = File("src/test/resources/alerts_active.json")
        val content = file.readText()
        val dto = json.decodeFromString<AlertsResponseDto>(content)
        val result = dto.toAlertsResult()

        assertEquals(1, result.alerts.size)
        assertEquals("Frankfurt-Nord", result.area?.name)

        val alert = result.alerts[0]
        assertEquals("2.49.0.0.276.0.DWD.PVW.1793619120000.2cbe765a-11e7-42f6-af21-ed3abe1beaf4", alert.id)
        assertEquals(AlertSeverity.MINOR, alert.severity)
        assertEquals(22, alert.eventCode)
        assertEquals("Frost", alert.event)
        assertEquals("Official WARNING of FROST", alert.headline)
        assertEquals(Instant.parse("2026-11-02T11:32:00Z"), alert.effective)
        assertEquals(Instant.parse("2026-11-02T23:00:00Z"), alert.onset)
        assertEquals(Instant.parse("2026-11-03T07:00:00Z"), alert.expires)
        assertNotNull(alert.description)
        assertNotNull(alert.instruction)
    }

    @Test
    fun testAlertsNoneFixtureMapping() {
        val file = File("src/test/resources/alerts_none.json")
        val content = file.readText()
        val dto = json.decodeFromString<AlertsResponseDto>(content)
        val result = dto.toAlertsResult()

        assertTrue(result.alerts.isEmpty())
        assertEquals("München-Altstadt-Lehel", result.area?.name)
    }

    @Test
    fun testTestStatusAlertIsDropped() {
        val dto = AlertsResponseDto(
            alerts = listOf(
                AlertDto(
                    alertId = "1",
                    status = "test",
                    severity = "severe",
                    eventEn = "Thunderstorm"
                ),
                AlertDto(
                    alertId = "2",
                    status = "actual",
                    severity = "minor",
                    eventEn = "Frost"
                )
            )
        )
        val result = dto.toAlertsResult()

        assertEquals(1, result.alerts.size)
        assertEquals("2", result.alerts[0].id)
    }

    @Test
    fun testNullTextFieldsAndMissingExpiresHandled() {
        val dto = AlertsResponseDto(
            alerts = listOf(
                AlertDto(
                    alertId = "1",
                    status = "actual",
                    severity = "unknown",
                    eventEn = null,
                    eventDe = null,
                    expires = null
                )
            )
        )
        val result = dto.toAlertsResult()

        assertEquals(1, result.alerts.size)
        val alert = result.alerts[0]
        assertEquals("Weather warning", alert.event)
        assertNull(alert.expires)
        assertEquals(AlertSeverity.UNKNOWN, alert.severity)
    }
}
