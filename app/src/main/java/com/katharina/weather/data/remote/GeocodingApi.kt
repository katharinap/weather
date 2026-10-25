package com.katharina.weather.data.remote

import com.katharina.weather.data.remote.dto.GeocodingResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

interface GeocodingApi {
    @GET("v1/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("count") count: Int = 10,
        @Query("language") language: String = "en",
        @Query("countryCode") countryCode: String = "DE",
        @Query("format") format: String = "json"
    ): GeocodingResponseDto
}
