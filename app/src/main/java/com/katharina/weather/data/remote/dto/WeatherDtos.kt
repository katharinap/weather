package com.katharina.weather.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CurrentWeatherResponseDto(
    @SerialName("weather") val weather: CurrentWeatherEntryDto? = null,
    @SerialName("sources") val sources: List<SourceDto>? = null
)

@Serializable
data class WeatherResponseDto(
    @SerialName("weather") val weather: List<HourlyWeatherEntryDto>? = null,
    @SerialName("sources") val sources: List<SourceDto>? = null
)

@Serializable
data class CurrentWeatherEntryDto(
    @SerialName("timestamp") val timestamp: String? = null,
    @SerialName("source_id") val sourceId: Int? = null,
    @SerialName("temperature") val temperature: Double? = null,
    @SerialName("condition") val condition: String? = null,
    @SerialName("icon") val icon: String? = null,
    @SerialName("cloud_cover") val cloudCover: Int? = null,
    @SerialName("dew_point") val dewPoint: Double? = null,
    @SerialName("precipitation_10") val precipitation10: Double? = null,
    @SerialName("precipitation_30") val precipitation30: Double? = null,
    @SerialName("precipitation_60") val precipitation60: Double? = null,
    @SerialName("pressure_msl") val pressureMsl: Double? = null,
    @SerialName("relative_humidity") val relativeHumidity: Int? = null,
    @SerialName("visibility") val visibility: Int? = null,
    @SerialName("wind_direction_10") val windDirection10: Int? = null,
    @SerialName("wind_speed_10") val windSpeed10: Double? = null,
    @SerialName("wind_gust_speed_10") val windGustSpeed10: Double? = null,
    @SerialName("sunshine_30") val sunshine30: Double? = null
)

@Serializable
data class HourlyWeatherEntryDto(
    @SerialName("timestamp") val timestamp: String? = null,
    @SerialName("source_id") val sourceId: Int? = null,
    @SerialName("temperature") val temperature: Double? = null,
    @SerialName("condition") val condition: String? = null,
    @SerialName("icon") val icon: String? = null,
    @SerialName("cloud_cover") val cloudCover: Int? = null,
    @SerialName("dew_point") val dewPoint: Double? = null,
    @SerialName("precipitation") val precipitation: Double? = null,
    @SerialName("precipitation_probability") val precipitationProbability: Int? = null,
    @SerialName("pressure_msl") val pressureMsl: Double? = null,
    @SerialName("relative_humidity") val relativeHumidity: Int? = null,
    @SerialName("visibility") val visibility: Int? = null,
    @SerialName("wind_direction") val windDirection: Int? = null,
    @SerialName("wind_speed") val windSpeed: Double? = null,
    @SerialName("wind_gust_speed") val windGustSpeed: Double? = null,
    @SerialName("sunshine") val sunshine: Double? = null
)

@Serializable
data class SourceDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("station_name") val stationName: String? = null,
    @SerialName("dwd_station_id") val dwdStationId: String? = null,
    @SerialName("lat") val lat: Double? = null,
    @SerialName("lon") val lon: Double? = null,
    @SerialName("height") val height: Double? = null,
    @SerialName("distance") val distance: Double? = null
)
