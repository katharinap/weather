package com.katharina.weather.domain.model

import com.katharina.weather.data.remote.dto.AlertsResponseDto
import com.katharina.weather.data.remote.mapper.toAlertsResult
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant

class VisibleAlertsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun testVisibleAtWithActiveFixture() {
        val file = File("src/test/resources/alerts_active.json")
        val content = file.readText()
        val dto = json.decodeFromString<AlertsResponseDto>(content)
        val alerts = dto.toAlertsResult().alerts

        // now = 2026-11-02T15:00:00Z -> onset is 23:00 -> 1 upcoming, 0 active
        val t1 = Instant.parse("2026-11-02T15:00:00Z")
        val v1 = alerts.visibleAt(t1)
        assertEquals(0, v1.active.size)
        assertEquals(1, v1.upcoming.size)

        // now = 2026-11-03T01:00:00Z -> active 1, upcoming 0
        val t2 = Instant.parse("2026-11-03T01:00:00Z")
        val v2 = alerts.visibleAt(t2)
        assertEquals(1, v2.active.size)
        assertEquals(0, v2.upcoming.size)

        // now = 2026-11-03T07:00:00Z -> expired -> 0 active, 0 upcoming
        val t3 = Instant.parse("2026-11-03T07:00:00Z")
        val v3 = alerts.visibleAt(t3)
        assertEquals(0, v3.active.size)
        assertEquals(0, v3.upcoming.size)
    }

    @Test
    fun testNullExpiresIsAlwaysActive() {
        val now = Instant.now()
        val alert = WeatherAlert(
            id = "1",
            eventCode = null,
            event = "Wind",
            headline = null,
            description = null,
            instruction = null,
            severity = AlertSeverity.SEVERE,
            effective = null,
            onset = now.minusSeconds(100),
            expires = null
        )

        val visible = listOf(alert).visibleAt(now)
        assertEquals(1, visible.active.size)
        assertEquals(0, visible.upcoming.size)
    }

    @Test
    fun testSortingOrderSeverityDescThenOnset() {
        val now = Instant.now()
        val alertMinor = WeatherAlert("1", null, "Frost", null, null, null, AlertSeverity.MINOR, null, now.minusSeconds(10), null)
        val alertSevereLater = WeatherAlert("2", null, "Storm", null, null, null, AlertSeverity.SEVERE, null, now.minusSeconds(5), null)
        val alertSevereEarlier = WeatherAlert("3", null, "Storm", null, null, null, AlertSeverity.SEVERE, null, now.minusSeconds(20), null)

        val visible = listOf(alertMinor, alertSevereLater, alertSevereEarlier).visibleAt(now)
        assertEquals(3, visible.active.size)
        assertEquals("3", visible.active[0].id) // Severe earlier
        assertEquals("2", visible.active[1].id) // Severe later
        assertEquals("1", visible.active[2].id) // Minor
    }

    @Test
    fun testSeverityParseCaseAndUnknown() {
        assertEquals(AlertSeverity.MINOR, AlertSeverity.parse("MINOR"))
        assertEquals(AlertSeverity.MINOR, AlertSeverity.parse("minor"))
        assertEquals(AlertSeverity.SEVERE, AlertSeverity.parse("sEvErE"))
        assertEquals(AlertSeverity.UNKNOWN, AlertSeverity.parse("invalid"))
        assertEquals(AlertSeverity.UNKNOWN, AlertSeverity.parse(null))
    }
}
