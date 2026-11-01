package com.katharina.weather.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WeatherCacheDao {
    @Query("SELECT * FROM weather_cache WHERE placeKey = :placeKey")
    fun observe(placeKey: String): Flow<List<WeatherCacheEntity>>

    @Query("SELECT * FROM weather_cache WHERE placeKey = :placeKey AND kind = :kind")
    suspend fun find(placeKey: String, kind: String): WeatherCacheEntity?

    @Upsert
    suspend fun upsert(entity: WeatherCacheEntity)

    @Query("DELETE FROM weather_cache WHERE placeKey = :placeKey")
    suspend fun deleteForPlace(placeKey: String)

    @Query("DELETE FROM weather_cache WHERE placeKey != 'current' AND placeKey NOT IN (:keepKeys)")
    suspend fun deleteOrphansInternal(keepKeys: List<String>)

    @Query("DELETE FROM weather_cache WHERE placeKey != 'current'")
    suspend fun deleteAllNonCurrent()

    @Query("DELETE FROM weather_cache WHERE fetchedAt < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long)
}

suspend fun WeatherCacheDao.deleteOrphans(keepKeys: List<String>) {
    if (keepKeys.isEmpty()) {
        deleteAllNonCurrent()
    } else {
        deleteOrphansInternal(keepKeys)
    }
}
