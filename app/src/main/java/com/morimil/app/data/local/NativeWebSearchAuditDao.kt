package com.morimil.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NativeWebSearchAuditDao {
    @Query("SELECT * FROM native_web_search_audit ORDER BY createdAtMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<NativeWebSearchAuditEntity>>

    @Query("SELECT * FROM native_web_search_audit ORDER BY createdAtMillis DESC LIMIT :limit")
    suspend fun loadRecent(limit: Int): List<NativeWebSearchAuditEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: NativeWebSearchAuditEntity)
}
