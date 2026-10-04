package com.katharina.weather.data.repository

import com.katharina.weather.data.remote.BrightSkyApi
import com.katharina.weather.data.remote.mapper.toCurrentWeather
import com.katharina.weather.data.remote.mapper.toHourlyForecast
import com.katharina.weather.domain.model.CurrentWeather
import com.katharina.weather.domain.model.HourlyForecast
import com.katharina.weather.domain.repository.WeatherRepository
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class WeatherRepositoryImpl @Inject constructor(
    private val api: BrightSkyApi
) : WeatherRepository {

    override suspend fun getCurrentWeather(
        lat: Double,
        lon: Double,
        tz: String?
    ): Result<CurrentWeather> {
        return try {
            val response = api.currentWeather(lat = lat, lon = lon, tz = tz)
            val currentWeather = response.toCurrentWeather() ?: throw IllegalStateException("Weather data is missing")
            Result.success(currentWeather)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun getWeather(
        lat: Double,
        lon: Double,
        days: Int,
        tz: String?
    ): Result<List<HourlyForecast>> {
        return try {
            val zone = tz?.let { ZoneId.of(it) } ?: ZoneId.of("Europe/Berlin")
            val today = LocalDate.now(zone)
            val startDate = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val lastDate = today.plusDays(days.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE)

            val response = api.weather(
                lat = lat,
                lon = lon,
                date = startDate,
                lastDate = lastDate,
                tz = tz
            )
            val entries = response.weather ?: emptyList()
            Result.success(entries.map { it.toHourlyForecast() })
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }
}
