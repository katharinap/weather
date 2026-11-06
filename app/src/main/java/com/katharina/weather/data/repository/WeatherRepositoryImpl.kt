package com.katharina.weather.data.repository

import com.katharina.weather.data.local.WeatherCacheDao
import com.katharina.weather.data.local.WeatherCacheEntity
import com.katharina.weather.data.remote.BrightSkyApi
import com.katharina.weather.data.remote.dto.AlertsResponseDto
import com.katharina.weather.data.remote.dto.CurrentWeatherResponseDto
import com.katharina.weather.data.remote.dto.WeatherResponseDto
import com.katharina.weather.data.remote.mapper.toAlertsResult
import com.katharina.weather.data.remote.mapper.toCurrentWeather
import com.katharina.weather.data.remote.mapper.toHourlyForecast
import com.katharina.weather.domain.model.Cached
import com.katharina.weather.domain.model.Place
import com.katharina.weather.domain.model.RefreshOutcome
import com.katharina.weather.domain.model.WeatherSnapshot
import com.katharina.weather.domain.model.cacheKey
import com.katharina.weather.domain.repository.WeatherRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

const val CACHE_FORMAT_VERSION = 1

class WeatherRepositoryImpl @Inject constructor(
    private val api: BrightSkyApi,
    private val weatherCacheDao: WeatherCacheDao,
    private val clock: Clock = Clock.systemUTC()
) : WeatherRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    override fun observe(place: Place): Flow<WeatherSnapshot> {
        return weatherCacheDao.observe(place.cacheKey()).map { entities ->
            val currentEntity = entities.find { it.kind == "current" }
            val forecastEntity = entities.find { it.kind == "forecast" }
            val alertsEntity = entities.find { it.kind == "alerts" }

            val currentCached = currentEntity?.let { entity ->
                if (entity.formatVersion != CACHE_FORMAT_VERSION) null
                else {
                    try {
                        val dto = json.decodeFromString<CurrentWeatherResponseDto>(entity.payload)
                        dto.toCurrentWeather()?.let {
                            Cached(it, Instant.ofEpochMilli(entity.fetchedAt))
                        }
                    } catch (_: Exception) {
                        null
                    }
                }
            }

            val forecastCached = forecastEntity?.let { entity ->
                if (entity.formatVersion != CACHE_FORMAT_VERSION) null
                else {
                    try {
                        val dto = json.decodeFromString<WeatherResponseDto>(entity.payload)
                        val hourlyList = dto.weather?.map { it.toHourlyForecast() } ?: emptyList()
                        Cached(hourlyList, Instant.ofEpochMilli(entity.fetchedAt))
                    } catch (_: Exception) {
                        null
                    }
                }
            }

            val alertsCached = alertsEntity?.let { entity ->
                if (entity.formatVersion != CACHE_FORMAT_VERSION) null
                else {
                    try {
                        val dto = json.decodeFromString<AlertsResponseDto>(entity.payload)
                        val alertsResult = dto.toAlertsResult()
                        Cached(alertsResult, Instant.ofEpochMilli(entity.fetchedAt))
                    } catch (_: Exception) {
                        null
                    }
                }
            }

            WeatherSnapshot(current = currentCached, forecast = forecastCached, alerts = alertsCached)
        }
    }

    override suspend fun refresh(place: Place): RefreshOutcome {
        val today = LocalDate.now(ZoneId.of("Europe/Berlin"))
        val startDate = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val lastDate = today.plusDays(7).format(DateTimeFormatter.ISO_LOCAL_DATE)

        val (currentRes, forecastRes, alertsRes) = coroutineScope {
            val c = async {
                try {
                    val dto = api.currentWeather(place.latitude, place.longitude)
                    Result.success(dto)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Result.failure(e)
                }
            }
            val f = async {
                try {
                    val dto = api.weather(place.latitude, place.longitude, date = startDate, lastDate = lastDate)
                    Result.success(dto)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Result.failure(e)
                }
            }
            val a = async {
                try {
                    val dto = api.alerts(place.latitude, place.longitude)
                    Result.success(dto)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Result.failure(e)
                }
            }
            Triple(c.await(), f.await(), a.await())
        }

        val nowEpoch = clock.millis()

        val currentResult = currentRes.fold(
            onSuccess = { dto ->
                try {
                    val payload = json.encodeToString(dto)
                    val entity = WeatherCacheEntity(
                        placeKey = place.cacheKey(),
                        kind = "current",
                        fetchedAt = nowEpoch,
                        formatVersion = CACHE_FORMAT_VERSION,
                        latitude = place.latitude,
                        longitude = place.longitude,
                        payload = payload
                    )
                    weatherCacheDao.upsert(entity)
                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            },
            onFailure = { Result.failure(it) }
        )

        val forecastResult = forecastRes.fold(
            onSuccess = { dto ->
                try {
                    val payload = json.encodeToString(dto)
                    val entity = WeatherCacheEntity(
                        placeKey = place.cacheKey(),
                        kind = "forecast",
                        fetchedAt = nowEpoch,
                        formatVersion = CACHE_FORMAT_VERSION,
                        latitude = place.latitude,
                        longitude = place.longitude,
                        payload = payload
                    )
                    weatherCacheDao.upsert(entity)
                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            },
            onFailure = { Result.failure(it) }
        )

        val alertsResult = alertsRes.fold(
            onSuccess = { dto ->
                try {
                    val payload = json.encodeToString(dto)
                    val entity = WeatherCacheEntity(
                        placeKey = place.cacheKey(),
                        kind = "alerts",
                        fetchedAt = nowEpoch,
                        formatVersion = CACHE_FORMAT_VERSION,
                        latitude = place.latitude,
                        longitude = place.longitude,
                        payload = payload
                    )
                    weatherCacheDao.upsert(entity)
                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            },
            onFailure = { Result.failure(it) }
        )

        return RefreshOutcome(current = currentResult, forecast = forecastResult, alerts = alertsResult)
    }
}
