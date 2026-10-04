package com.katharina.weather.domain.forecast

import com.katharina.weather.data.remote.dto.WeatherResponseDto
import com.katharina.weather.data.remote.mapper.toHourlyForecast
import com.katharina.weather.domain.model.HourlyForecast
import com.katharina.weather.domain.model.WeatherCondition
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DailyForecastsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private fun forecast(): List<HourlyForecast> {
        val file = File("src/test/resources/weather_munich.json")
        val content = file.readText()
        val response = json.decodeFromString<WeatherResponseDto>(content)
        return response.weather?.map { it.toHourlyForecast() } ?: emptyList()
    }

    @Test
    fun `fixture yields two full days, trailing midnight record dropped`() {
        val days = forecast().toDailyForecasts()
        assertEquals(listOf(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 3)), days.map { it.date })
    }

    @Test
    fun `october 2 aggregates`() {
        val d = forecast().toDailyForecasts()[0]
        assertEquals(14.0, d.minTempC!!, 0.001)
        assertEquals(18.6, d.maxTempC!!, 0.001)
        assertEquals(0.0, d.precipitationMm, 0.001)
        assertEquals(2, d.maxPrecipProbabilityPercent)
        assertEquals(27.0, d.maxGustKmh!!, 0.001)
        assertEquals(WeatherCondition.CLOUDY, d.condition)
    }

    @Test
    fun `october 3 aggregates`() {
        val d = forecast().toDailyForecasts()[1]
        assertEquals(12.0, d.minTempC!!, 0.001)
        assertEquals(20.3, d.maxTempC!!, 0.001)
        assertEquals(4, d.maxPrecipProbabilityPercent)
        assertEquals(14.8, d.maxGustKmh!!, 0.001)
        assertEquals(WeatherCondition.PARTLY_CLOUDY_DAY, d.condition)
    }

    @Test
    fun `weather_munich_7days aggregates correctly`() {
        val file = File("src/test/resources/weather_munich_7days.json")
        val content = file.readText()
        val response = json.decodeFromString<WeatherResponseDto>(content)
        val forecasts = response.weather?.map { it.toHourlyForecast() } ?: emptyList()
        val days = forecasts.toDailyForecasts()

        assertEquals(7, days.size)
        assertEquals(
            listOf(
                LocalDate.of(2026, 10, 4),
                LocalDate.of(2026, 10, 5),
                LocalDate.of(2026, 10, 6),
                LocalDate.of(2026, 10, 7),
                LocalDate.of(2026, 10, 8),
                LocalDate.of(2026, 10, 9),
                LocalDate.of(2026, 10, 10)
            ),
            days.map { it.date }
        )

        days.forEach { day ->
            assertTrue("Day ${day.date} has condition UNKNOWN", day.condition != WeatherCondition.UNKNOWN)
        }

        assertEquals(WeatherCondition.PARTLY_CLOUDY_DAY, days[2].condition)
        assertEquals(WeatherCondition.PARTLY_CLOUDY_DAY, days[3].condition)
        assertEquals(WeatherCondition.RAIN, days[4].condition)
        assertEquals(WeatherCondition.RAIN, days[5].condition)
        assertEquals(WeatherCondition.CLOUDY, days[6].condition)
    }

    @Test
    fun `rain severity thresholds`() {
        val zone = ZoneId.of("Europe/Berlin")
        val date = LocalDate.of(2026, 10, 10)

        val twoRainHours = (0..11).map { hour ->
            createHourly(
                instant = date.atTime(6 + hour, 0).atZone(zone).toInstant(),
                condition = if (hour < 2) WeatherCondition.RAIN else WeatherCondition.CLOUDY
            )
        }
        assertEquals(WeatherCondition.RAIN, twoRainHours.toDailyForecasts(zone)[0].condition)

        val oneRainHour = (0..11).map { hour ->
            createHourly(
                instant = date.atTime(6 + hour, 0).atZone(zone).toInstant(),
                condition = if (hour == 0) WeatherCondition.RAIN else WeatherCondition.CLOUDY
            )
        }
        assertEquals(WeatherCondition.CLOUDY, oneRainHour.toDailyForecasts(zone)[0].condition)
    }

    @Test
    fun `thunderstorm severity takes precedence`() {
        val zone = ZoneId.of("Europe/Berlin")
        val date = LocalDate.of(2026, 10, 10)
        val hours = (0..11).map { hour ->
            val cond = when (hour) {
                0 -> WeatherCondition.THUNDERSTORM
                1 -> WeatherCondition.RAIN
                else -> WeatherCondition.CLOUDY
            }
            createHourly(instant = date.atTime(6 + hour, 0).atZone(zone).toInstant(), condition = cond)
        }
        assertEquals(WeatherCondition.THUNDERSTORM, hours.toDailyForecasts(zone)[0].condition)
    }

    @Test
    fun `records count filtering`() {
        val zone = ZoneId.of("Europe/Berlin")
        val date = LocalDate.of(2026, 10, 10)

        val elevenHours = (0..10).map { hour ->
            createHourly(instant = date.atTime(hour, 0).atZone(zone).toInstant())
        }
        assertTrue(elevenHours.toDailyForecasts(zone).isEmpty())

        val twelveHours = (0..11).map { hour ->
            createHourly(instant = date.atTime(hour, 0).atZone(zone).toInstant())
        }
        assertEquals(1, twelveHours.toDailyForecasts(zone).size)
    }

    @Test
    fun `all temperatures null`() {
        val zone = ZoneId.of("Europe/Berlin")
        val date = LocalDate.of(2026, 10, 10)
        val hours = (0..11).map { hour ->
            createHourly(instant = date.atTime(hour, 0).atZone(zone).toInstant(), temp = null)
        }
        val d = hours.toDailyForecasts(zone)[0]
        assertNull(d.minTempC)
        assertNull(d.maxTempC)
    }

    @Test
    fun `upcoming returns 24 hours starting at current hour`() {
        val zone = ZoneId.of("Europe/Berlin")
        val date = LocalDate.of(2026, 10, 10)
        val hours = (0..48).map { h ->
            createHourly(instant = date.atTime(0, 0).plusHours(h.toLong()).atZone(zone).toInstant())
        }
        val now = date.atTime(10, 30).atZone(zone).toInstant()
        val upcoming = hours.upcoming(now, hours = 24)

        assertEquals(24, upcoming.size)
        val expectedStart = date.atTime(10, 0).atZone(zone).toInstant()
        assertEquals(expectedStart, upcoming[0].timestamp)
    }

    private fun createHourly(
        instant: Instant,
        condition: WeatherCondition = WeatherCondition.CLOUDY,
        temp: Double? = 15.0
    ): HourlyForecast {
        return HourlyForecast(
            timestamp = instant,
            temperature = temp,
            condition = condition,
            icon = "cloudy",
            precipitation = 0.0,
            precipitationProbability = 0,
            windSpeed = 5.0,
            windGustSpeed = 10.0,
            windDirection = 0,
            cloudCover = 50,
            sunshine = 0.0,
            relativeHumidity = 50
        )
    }
}
