package com.katharina.weather.data.remote.mapper

import com.katharina.weather.data.remote.dto.CurrentWeatherEntryDto
import com.katharina.weather.data.remote.dto.CurrentWeatherResponseDto
import com.katharina.weather.data.remote.dto.WeatherResponseDto
import com.katharina.weather.domain.model.WeatherCondition
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

class WeatherMapperTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun testCurrentWeatherMapping() {
        val file = File("src/test/resources/current_weather_munich.json")
        val content = file.readText()
        val response = json.decodeFromString<CurrentWeatherResponseDto>(content)

        assertNotNull(response.weather)
        val currentWeather = response.toCurrentWeather()
        assertNotNull(currentWeather)

        assertEquals(17.5, currentWeather!!.temperature, 0.001)
        assertEquals(WeatherCondition.CLOUDY, currentWeather.condition)
        assertEquals("cloudy", currentWeather.icon)
        assertEquals(7.6, currentWeather.windSpeed!!, 0.001)
        assertEquals(12.6, currentWeather.windGustSpeed!!, 0.001)
        assertEquals(40, currentWeather.windDirection)
        assertEquals(100, currentWeather.cloudCover)
        assertEquals(76, currentWeather.relativeHumidity)
        assertEquals(0.0, currentWeather.precipitation10!!, 0.001)
        assertEquals(0.0, currentWeather.precipitation60!!, 0.001)
        assertEquals("Muenchen-Stadt", currentWeather.stationName)
        assertEquals(3767.0, currentWeather.stationDistance!!, 0.001)
        assertNotNull(currentWeather.timestamp)
    }

    @Test
    fun testWeatherForecastMapping() {
        val file = File("src/test/resources/weather_munich.json")
        val content = file.readText()
        val response = json.decodeFromString<WeatherResponseDto>(content)

        assertNotNull(response.weather)
        val entries = response.weather!!
        val forecasts = entries.map { it.toHourlyForecast() }

        assertEquals(entries.size, forecasts.size)
        val first = forecasts[0]
        assertEquals(16.3, first.temperature!!, 0.001)
        assertEquals(WeatherCondition.CLOUDY, first.condition)
        assertEquals("cloudy", first.icon)
        assertEquals(5.0, first.windSpeed!!, 0.001)
        assertEquals(11.2, first.windGustSpeed!!, 0.001)
        assertEquals(260, first.windDirection)
        assertEquals(100, first.cloudCover)
        assertEquals(0.0, first.sunshine!!, 0.001)
        assertEquals(null, first.precipitationProbability)
        assertEquals(77, first.relativeHumidity)
        assertNotNull(first.timestamp)
    }

    @Test
    fun testUnknownWeatherConditionMapping() {
        val response = CurrentWeatherResponseDto(
            weather = CurrentWeatherEntryDto(
                icon = "unknown-icon-type",
                temperature = 20.0,
                timestamp = "2026-10-02T12:00:00+02:00"
            )
        )
        val currentWeather = response.toCurrentWeather()
        assertNotNull(currentWeather)
        assertEquals(WeatherCondition.UNKNOWN, currentWeather!!.condition)
    }
}
