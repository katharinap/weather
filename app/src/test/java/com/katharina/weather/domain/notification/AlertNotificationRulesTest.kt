package com.katharina.weather.domain.notification

import com.katharina.weather.data.local.SeenAlertEntity
import com.katharina.weather.domain.model.AlertSeverity
import com.katharina.weather.domain.model.WeatherAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AlertNotificationRulesTest {

    private val now = Instant.parse("2026-11-02T14:00:00Z")

    private val severeThunderstorm = WeatherAlert(
        id = "alert-1",
        eventCode = 99,
        event = "Thunderstorm",
        headline = "Severe Thunderstorm Warning",
        description = "Heavy rain and hail",
        instruction = "Take shelter",
        severity = AlertSeverity.SEVERE,
        effective = now.minusSeconds(3600),
        onset = now.minusSeconds(1800),
        expires = now.plusSeconds(3600)
    )

    @Test
    fun testFirstRunReturnsEmptyList() {
        val selected = selectAlertsToNotify(
            alerts = listOf(severeThunderstorm),
            now = now,
            minSeverity = AlertSeverity.SEVERE,
            seenAlertIds = emptySet(),
            recentAlerts = emptyList(),
            isFirstRun = true
        )
        assertTrue(selected.isEmpty())
    }

    @Test
    fun testBelowMinSeverityIsSkipped() {
        val minorAlert = severeThunderstorm.copy(severity = AlertSeverity.MINOR)
        val selected = selectAlertsToNotify(
            alerts = listOf(minorAlert),
            now = now,
            minSeverity = AlertSeverity.SEVERE,
            seenAlertIds = emptySet(),
            recentAlerts = emptyList(),
            isFirstRun = false
        )
        assertTrue(selected.isEmpty())
    }

    @Test
    fun testUpcomingOrExpiredAlertsAreSkipped() {
        val upcomingAlert = severeThunderstorm.copy(onset = now.plusSeconds(1800))
        val expiredAlert = severeThunderstorm.copy(expires = now.minusSeconds(10))

        val selectedUpcoming = selectAlertsToNotify(
            alerts = listOf(upcomingAlert),
            now = now,
            minSeverity = AlertSeverity.SEVERE,
            seenAlertIds = emptySet(),
            recentAlerts = emptyList()
        )
        assertTrue(selectedUpcoming.isEmpty())

        val selectedExpired = selectAlertsToNotify(
            alerts = listOf(expiredAlert),
            now = now,
            minSeverity = AlertSeverity.SEVERE,
            seenAlertIds = emptySet(),
            recentAlerts = emptyList()
        )
        assertTrue(selectedExpired.isEmpty())
    }

    @Test
    fun testAlreadySeenAlertIdIsSkipped() {
        val selected = selectAlertsToNotify(
            alerts = listOf(severeThunderstorm),
            now = now,
            minSeverity = AlertSeverity.SEVERE,
            seenAlertIds = setOf("alert-1"),
            recentAlerts = emptyList()
        )
        assertTrue(selected.isEmpty())
    }

    @Test
    fun testSameEventAndEqualOrLowerSeverityInLast6HoursIsSkipped() {
        val recentEntity = SeenAlertEntity(
            alertId = "old-alert-0",
            placeKey = "saved:1",
            eventKey = "99",
            severity = "severe",
            firstSeenAt = now.minusSeconds(7200).toEpochMilli(),
            expiresAt = now.plusSeconds(3600).toEpochMilli(),
            notified = true
        )

        val selected = selectAlertsToNotify(
            alerts = listOf(severeThunderstorm),
            now = now,
            minSeverity = AlertSeverity.SEVERE,
            seenAlertIds = emptySet(),
            recentAlerts = listOf(recentEntity)
        )
        assertTrue(selected.isEmpty())
    }

    @Test
    fun testSameEventHigherSeverityInLast6HoursIsSelected() {
        val recentEntity = SeenAlertEntity(
            alertId = "old-alert-0",
            placeKey = "saved:1",
            eventKey = "99",
            severity = "moderate",
            firstSeenAt = now.minusSeconds(7200).toEpochMilli(),
            expiresAt = now.plusSeconds(3600).toEpochMilli(),
            notified = true
        )

        val selected = selectAlertsToNotify(
            alerts = listOf(severeThunderstorm),
            now = now,
            minSeverity = AlertSeverity.SEVERE,
            seenAlertIds = emptySet(),
            recentAlerts = listOf(recentEntity)
        )
        assertEquals(1, selected.size)
        assertEquals("alert-1", selected[0].id)
    }

    @Test
    fun testEventTextUsedAsEventKeyWhenEventCodeIsNull() {
        val alertWithoutCode = severeThunderstorm.copy(
            id = "alert-no-code",
            eventCode = null,
            event = "Heavy Rain"
        )
        val recentEntity = SeenAlertEntity(
            alertId = "old-alert-rain",
            placeKey = "saved:1",
            eventKey = "Heavy Rain",
            severity = "severe",
            firstSeenAt = now.minusSeconds(3600).toEpochMilli(),
            expiresAt = now.plusSeconds(3600).toEpochMilli(),
            notified = true
        )

        val selected = selectAlertsToNotify(
            alerts = listOf(alertWithoutCode),
            now = now,
            minSeverity = AlertSeverity.SEVERE,
            seenAlertIds = emptySet(),
            recentAlerts = listOf(recentEntity)
        )
        assertTrue(selected.isEmpty())
    }

    @Test
    fun testOpenEndedAlertWithNullExpiresIsSelected() {
        val openEnded = severeThunderstorm.copy(expires = null)

        val selected = selectAlertsToNotify(
            alerts = listOf(openEnded),
            now = now,
            minSeverity = AlertSeverity.SEVERE,
            seenAlertIds = emptySet(),
            recentAlerts = emptyList()
        )
        assertEquals(1, selected.size)
        assertEquals("alert-1", selected[0].id)
    }

    @Test
    fun testMultipleAlertsFiltering() {
        val minorAlert = severeThunderstorm.copy(id = "alert-minor", severity = AlertSeverity.MINOR)
        val extremeAlert = severeThunderstorm.copy(id = "alert-extreme", severity = AlertSeverity.EXTREME)

        val selected = selectAlertsToNotify(
            alerts = listOf(minorAlert, severeThunderstorm, extremeAlert),
            now = now,
            minSeverity = AlertSeverity.SEVERE,
            seenAlertIds = emptySet(),
            recentAlerts = emptyList()
        )
        assertEquals(2, selected.size)
        assertEquals("alert-1", selected[0].id)
        assertEquals("alert-extreme", selected[1].id)
    }
}
