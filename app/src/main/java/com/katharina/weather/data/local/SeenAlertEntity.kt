package com.katharina.weather.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "seen_alerts")
data class SeenAlertEntity(
    @PrimaryKey val alertId: String,
    val placeKey: String,
    val eventKey: String,
    val severity: String,
    val firstSeenAt: Long,
    val expiresAt: Long?,
    val notified: Boolean
)
