package com.katharina.weather.domain.model

data class WeatherSnapshot(
    val current: Cached<CurrentWeather>?,
    val forecast: Cached<List<HourlyForecast>>?
)
