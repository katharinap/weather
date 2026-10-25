package com.katharina.weather.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceDao {
    @Query("SELECT * FROM places ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM places WHERE geonamesId = :geonamesId")
    suspend fun findByGeonamesId(geonamesId: Long): PlaceEntity?

    @Query("SELECT * FROM places WHERE id = :id")
    suspend fun findById(id: Long): PlaceEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: PlaceEntity): Long

    @Query("DELETE FROM places WHERE id = :id")
    suspend fun delete(id: Long): Int
}
