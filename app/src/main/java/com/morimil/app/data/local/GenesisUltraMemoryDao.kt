package com.morimil.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface GenesisUltraMemoryDao {
    @Query("SELECT COUNT(*) FROM genesis_ultra_memory_events")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM genesis_ultra_memory_events WHERE instanceId = :instanceId")
    suspend fun countForInstance(instanceId: String): Int

    @Query(
        "SELECT * FROM genesis_ultra_memory_events " +
            "WHERE instanceId = :instanceId ORDER BY sequence ASC"
    )
    suspend fun loadAscending(instanceId: String): List<GenesisUltraMemoryEventEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: GenesisUltraMemoryEventEntity)
}
