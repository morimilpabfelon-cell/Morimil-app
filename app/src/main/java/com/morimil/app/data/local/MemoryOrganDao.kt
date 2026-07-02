package com.morimil.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryOrganDao {
    @Query("SELECT * FROM autobiographical_snapshots WHERE snapshotId = 'current' LIMIT 1")
    fun observeCurrentSelfSnapshot(): Flow<AutobiographicalSnapshotEntity?>

    @Query("SELECT * FROM autobiographical_snapshots WHERE snapshotId = 'current' LIMIT 1")
    suspend fun getCurrentSelfSnapshot(): AutobiographicalSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSelfSnapshot(snapshot: AutobiographicalSnapshotEntity)

    @Query("SELECT * FROM knowledge_capsules ORDER BY updatedAtMillis DESC LIMIT :limit")
    fun observeKnowledgeCapsules(limit: Int = 20): Flow<List<KnowledgeCapsuleEntity>>

    @Query("SELECT * FROM knowledge_capsules ORDER BY updatedAtMillis DESC LIMIT :limit")
    suspend fun loadKnowledgeCapsules(limit: Int = 20): List<KnowledgeCapsuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertKnowledgeCapsule(capsule: KnowledgeCapsuleEntity)
}
