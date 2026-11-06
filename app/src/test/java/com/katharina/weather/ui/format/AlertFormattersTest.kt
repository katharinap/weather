package com.katharina.weather.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class AlertFormattersTest {

    private val now = Instant.parse("2026-11-02T12:00:00Z") // Mon 2 Nov 2026 in UTC / 13:00 in Berlin
    private val zone = ZoneId.of("Europe/Berlin")
    private val locale = Locale.US

    @Test
    fun testSameDayToday() {
        val onset = Instant.parse("2026-11-02T14:48:00Z")   // 15:48 in Berlin
        val expires = Instant.parse("2026-11-02T16:48:00Z") // 17:48 in Berlin

        val result = formatAlertValidity(onset, expires, now, zone, locale)
        assertEquals("Today 15:48 – 17:48", result)
    }

    @Test
    fun testCrossingMidnightTodayToTomorrow() {
        val onset = Instant.parse("2026-11-02T21:00:00Z")   // 22:00 in Berlin (Today)
        val expires = Instant.parse("2026-11-03T05:00:00Z") // 06:00 in Berlin (Tomorrow)

        val result = formatAlertValidity(onset, expires, now, zone, locale)
        assertEquals("Today 22:00 – Tomorrow 06:00", result)
    }

    @Test
    fun testFarFutureDifferentDays() {
        val onset = Instant.parse("2026-11-09T14:00:00Z")   // Mon 9 Nov, 15:00
        val expires = Instant.parse("2026-11-10T17:00:00Z") // Tue 10 Nov, 18:00

        val result = formatAlertValidity(onset, expires, now, zone, locale)
        assertEquals("Mon 9 Nov, 15:00 – Tue 10 Nov, 18:00", result)
    }

    @Test
    fun testPastDates() {
        val onset = Instant.parse("2026-10-25T10:00:00Z")   // Sun 25 Oct, 11:00
        val expires = Instant.parse("2026-10-25T18:00:00Z") // Sun 25 Oct, 19:00

        val result = formatAlertValidity(onset, expires, now, zone, locale)
        assertEquals("Sun 25 Oct, 11:00 – 19:00", result)
    }

    @Test
    fun testOnlyExpiresOrOnlyOnset() {
        val expires = Instant.parse("2026-11-02T16:48:00Z") // Today 17:48
        assertEquals("Valid until Today 17:48", formatAlertValidity(null, expires, now, zone, locale))

        val onset = Instant.parse("2026-11-03T05:00:00Z") // Tomorrow 06:00
        assertEquals("From Tomorrow 06:00", formatAlertValidity(onset, null, now, zone, locale))

        assertEquals("", formatAlertValidity(null, null, now, zone, locale))
    }
}
