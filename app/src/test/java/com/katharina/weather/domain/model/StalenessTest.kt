package com.katharina.weather.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class StalenessTest {

    private val now = Instant.now()

    @Test
    fun testNeedsRefreshWhenSnapshotHasNulls() {
        val emptySnapshot = WeatherSnapshot(null, null)
        assertTrue(Staleness.needsRefresh(emptySnapshot, now))

        val currentWeather = mockCurrentWeather()
        val currentOnly = WeatherSnapshot(Cached(currentWeather, now), null)
        assertTrue(Staleness.needsRefresh(currentOnly, now))

        val forecastOnly = WeatherSnapshot(null, Cached(emptyList(), now))
        assertTrue(Staleness.needsRefresh(forecastOnly, now))
    }

    @Test
    fun testNeedsRefreshBoundaries() {
        val currentWeather = mockCurrentWeather()

        // Fresh: current 10 min old, forecast 30 min old -> false
        val freshSnapshot = WeatherSnapshot(
            current = Cached(currentWeather, now.minus(Duration.ofMinutes(10))),
            forecast = Cached(emptyList(), now.minus(Duration.ofMinutes(30)))
        )
        assertFalse(Staleness.needsRefresh(freshSnapshot, now))

        // Current weather > 15 min -> true
        val staleCurrent = WeatherSnapshot(
            current = Cached(currentWeather, now.minus(Duration.ofMinutes(16))),
            forecast = Cached(emptyList(), now.minus(Duration.ofMinutes(30)))
        )
        assertTrue(Staleness.needsRefresh(staleCurrent, now))

        // Forecast > 60 min -> true
        val staleForecast = WeatherSnapshot(
            current = Cached(currentWeather, now.minus(Duration.ofMinutes(10))),
            forecast = Cached(emptyList(), now.minus(Duration.ofMinutes(61)))
        )
        assertTrue(Staleness.needsRefresh(staleForecast, now))
    }

    @Test
    fun testIsOutdatedBoundaries() {
        val currentWeather = mockCurrentWeather()

        // 5 hours old -> not outdated
        val freshSnapshot = WeatherSnapshot(
            current = Cached(currentWeather, now.minus(Duration.ofHours(5))),
            forecast = Cached(emptyList(), now.minus(Duration.ofHours(5)))
        )
        assertFalse(Staleness.isOutdated(freshSnapshot, now))

        // 7 hours old -> outdated
        val outdatedSnapshot = WeatherSnapshot(
            current = Cached(currentWeather, now.minus(Duration.ofHours(7))),
            forecast = Cached(emptyList(), now.minus(Duration.ofHours(7)))
        )
        assertTrue(Staleness.isOutdated(outdatedSnapshot, now))
    }

    private fun mockCurrentWeather() = CurrentWeather(
        timestamp = now,
        temperature = 20.0,
        condition = WeatherCondition.SUNNY,
        icon = "sunny",
        windSpeed = 5.0,
        windGustSpeed = 10.0,
        windDirection = 90,
        cloudCover = 0,
        relativeHumidity = 50,
        precipitation10 = 0.0,
        precipitation60 = 0.0,
        stationName = "Munich",
        stationDistance = 1000.0
    )
}
