package com.katharina.weather.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class RelativeTimeFormatterTest {

    private val now = Instant.parse("2026-10-30T14:00:00Z")
    private val zone = ZoneId.of("Europe/Berlin")

    @Test
    fun testJustNow() {
        val time = now.minusSeconds(30)
        assertEquals("just now", formatRelativeTime(time, now, zone))
    }

    @Test
    fun testMinutesAgo() {
        val time = now.minus(Duration.ofMinutes(5))
        assertEquals("5 min ago", formatRelativeTime(time, now, zone))
    }

    @Test
    fun testHoursAgo() {
        val time = now.minus(Duration.ofHours(2))
        assertEquals("2 h ago", formatRelativeTime(time, now, zone))
    }

    @Test
    fun testYesterday() {
        val time = now.minus(Duration.ofDays(1))
        assertEquals("yesterday 15:00", formatRelativeTime(time, now, zone))
    }
}
