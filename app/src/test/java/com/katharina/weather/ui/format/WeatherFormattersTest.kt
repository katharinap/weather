package com.katharina.weather.ui.format

import com.katharina.weather.R
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class WeatherFormattersTest {

    @Test
    fun testFormatObservationTimeToday() {
        val now = ZonedDateTime.now(ZoneId.of("Europe/Berlin"))
        val instant = now.withHour(19).withMinute(0).toInstant()
        assertEquals("19:00", formatObservationTime(instant))
        assertEquals("–", formatObservationTime(null))
    }

    @Test
    fun testFormatObservationTimeYesterday() {
        val now = ZonedDateTime.now(ZoneId.of("Europe/Berlin"))
        val yesterday = now.minusDays(1).withHour(19).withMinute(0)
        assertEquals("Yesterday 19:00", formatObservationTime(yesterday.toInstant()))
    }

    @Test
    fun testFormatDistance() {
        assertEquals("3.8", formatDistance(3767.0))
        assertEquals("0.0", formatDistance(0.0))
        assertEquals("–", formatDistance(null))
    }

    @Test
    fun testGetWeatherIconRes() {
        assertEquals(R.drawable.ic_weather_clear_day, getWeatherIconRes("sunny"))
        assertEquals(R.drawable.ic_weather_clear_day, getWeatherIconRes("clear-day"))
        assertEquals(R.drawable.ic_weather_clear_night, getWeatherIconRes("clear-night"))
        assertEquals(R.drawable.ic_weather_cloudy, getWeatherIconRes("cloudy"))
        assertEquals(R.drawable.ic_weather_rain, getWeatherIconRes("rain"))
        assertEquals(R.drawable.ic_weather_unknown, getWeatherIconRes("unknown-icon"))
        assertEquals(R.drawable.ic_weather_unknown, getWeatherIconRes(null))
    }
}
