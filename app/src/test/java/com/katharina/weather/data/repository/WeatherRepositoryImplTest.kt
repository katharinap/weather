package com.katharina.weather.data.repository

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.katharina.weather.data.remote.BrightSkyApi
import com.katharina.weather.data.remote.dto.CurrentWeatherResponseDto
import com.katharina.weather.data.remote.dto.WeatherResponseDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import java.io.File

class WeatherRepositoryImplTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var api: BrightSkyApi
    private lateinit var repository: WeatherRepositoryImpl

    private val json =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val contentType = "application/json".toMediaType()
        val retrofit =
            Retrofit
                .Builder()
                .baseUrl(mockWebServer.url("/"))
                .addConverterFactory(json.asConverterFactory(contentType))
                .build()

        api = retrofit.create(BrightSkyApi::class.java)
        repository = WeatherRepositoryImpl(api)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun testGetCurrentWeatherSuccess() =
        runBlocking {
            val jsonContent = File("src/test/resources/current_weather_munich.json").readText()
            mockWebServer.enqueue(MockResponse().setBody(jsonContent).setResponseCode(200))

            val result = repository.getCurrentWeather(48.137, 11.575)
            assertTrue(result.isSuccess)
            val currentWeather = result.getOrNull()
            assertNotNull(currentWeather)
            assertEquals(17.5, currentWeather!!.temperature, 0.001)
        }

    @Test
    fun testGetCurrentWeatherHttpError() =
        runBlocking {
            mockWebServer.enqueue(MockResponse().setResponseCode(500))

            val result = repository.getCurrentWeather(48.137, 11.575)
            assertTrue(result.isFailure)
        }

    @Test
    fun testGetCurrentWeatherMalformedJson() =
        runBlocking {
            mockWebServer.enqueue(MockResponse().setBody("{ malformed json").setResponseCode(200))

            val result = repository.getCurrentWeather(48.137, 11.575)
            assertTrue(result.isFailure)
        }

    @Test
    fun testGetCurrentWeatherMissingWeatherField() =
        runBlocking {
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))

            val result = repository.getCurrentWeather(48.137, 11.575)
            assertTrue(result.isFailure)
        }

    @Test
    fun testGetWeatherSuccess() =
        runBlocking {
            val jsonContent = File("src/test/resources/weather_munich.json").readText()
            mockWebServer.enqueue(MockResponse().setBody(jsonContent).setResponseCode(200))

            val result = repository.getWeather(48.137, 11.575, days = 2)
            assertTrue(result.isSuccess)
            val forecasts = result.getOrNull()
            assertNotNull(forecasts)
            assertTrue(forecasts!!.isNotEmpty())
            assertEquals(16.3, forecasts[0].temperature, 0.001)
        }

    @Test
    fun testGetWeatherHttpError() =
        runBlocking {
            mockWebServer.enqueue(MockResponse().setResponseCode(404))

            val result = repository.getWeather(48.137, 11.575, days = 2)
            assertTrue(result.isFailure)
        }

    @Test
    fun testGetWeatherNullEntries() =
        runBlocking {
            mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))

            val result = repository.getWeather(48.137, 11.575, days = 2)
            assertTrue(result.isSuccess)
            val forecasts = result.getOrNull()
            assertNotNull(forecasts)
            assertTrue(forecasts!!.isEmpty())
        }

    @Test(expected = CancellationException::class)
    fun testCancellationExceptionRethrown() =
        runBlocking {
            val failingApi =
                object : BrightSkyApi {
                    override suspend fun currentWeather(
                        lat: Double,
                        lon: Double,
                        tz: String?,
                    ): CurrentWeatherResponseDto = throw CancellationException("Cancelled")

                    override suspend fun weather(
                        lat: Double,
                        lon: Double,
                        date: String,
                        lastDate: String,
                        tz: String?,
                    ): WeatherResponseDto = throw CancellationException("Cancelled")
                }
            val repo = WeatherRepositoryImpl(failingApi)
            repo.getCurrentWeather(0.0, 0.0)
            Unit
        }
}
