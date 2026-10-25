package com.katharina.weather.data.remote.mapper

import com.katharina.weather.data.remote.dto.GeocodingResponseDto
import com.katharina.weather.data.remote.dto.GeocodingResultDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GeocodingMapperTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun testGarchingFixtureMapping() {
        val file = File("src/test/resources/geocoding_garching.json")
        val content = file.readText()
        val response = json.decodeFromString<GeocodingResponseDto>(content)
        val results = response.results.map { it.toPlaceSearchResult() }

        assertEquals(2, results.size)

        val first = results[0]
        assertEquals(2922582L, first.geonamesId)
        assertEquals("Garching", first.name)
        assertEquals("Munich, Bavaria", first.region)
        assertEquals("Germany", first.country)
        assertEquals(48.24896, first.latitude, 0.00001)
        assertEquals(11.65101, first.longitude, 0.00001)

        val second = results[1]
        assertEquals(2922583L, second.geonamesId)
        assertEquals("Garching an der Alz", second.name)
        assertEquals("Landkreis Altötting, Bavaria", second.region)
    }

    @Test
    fun testEmptyFixtureMapping() {
        val file = File("src/test/resources/geocoding_empty.json")
        val content = file.readText()
        val response = json.decodeFromString<GeocodingResponseDto>(content)
        val results = response.results.map { it.toPlaceSearchResult() }

        assertTrue(results.isEmpty())
    }

    @Test
    fun testMissingOptionalFields() {
        val dto = GeocodingResultDto(
            id = 100L,
            name = "TestCity",
            latitude = 50.0,
            longitude = 10.0
        )
        val result = dto.toPlaceSearchResult()

        assertEquals(100L, result.geonamesId)
        assertEquals("TestCity", result.name)
        assertNull(result.region)
        assertNull(result.country)
    }
}
