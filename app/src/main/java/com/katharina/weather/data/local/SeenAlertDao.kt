package com.katharina.weather.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface SeenAlertDao {
    @Query("SELECT * FROM seen_alerts WHERE placeKey = :placeKey")
    suspend fun getSeenAlertsForPlace(placeKey: String): List<SeenAlertEntity>

    @Query("SELECT * FROM seen_alerts WHERE placeKey = :placeKey AND eventKey = :eventKey AND firstSeenAt >= :sinceEpoch")
    suspend fun getRecentAlertsForEvent(placeKey: String, eventKey: String, sinceEpoch: Long): List<SeenAlertEntity>

    @Upsert
    suspend fun upsert(entity: SeenAlertEntity)

    @Upsert
    suspend fun upsertAll(entities: List<SeenAlertEntity>)

    @Query("DELETE FROM seen_alerts WHERE expiresAt IS NOT NULL AND expiresAt < :olderThanEpoch")
    suspend fun deleteExpired(olderThanEpoch: Long)
}
