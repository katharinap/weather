package com.katharina.weather.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [PlaceEntity::class, WeatherCacheEntity::class, SeenAlertEntity::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3)
    ]
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun placeDao(): PlaceDao
    abstract fun weatherCacheDao(): WeatherCacheDao
    abstract fun seenAlertDao(): SeenAlertDao
}
