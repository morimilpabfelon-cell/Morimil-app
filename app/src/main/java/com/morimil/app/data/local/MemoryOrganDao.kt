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

    @Query("SELECT * FROM knowledge_capsules WHERE status = 'active' ORDER BY updatedAtMillis DESC LIMIT 20")
    fun observeRecentKnowledgeCapsules(): Flow<List<KnowledgeCapsuleEntity>>

    @Query("SELECT * FROM knowledge_capsules WHERE status = 'active' ORDER BY confidence DESC, updatedAtMillis DESC LIMIT :limit")
    suspend fun loadKnowledgeCapsules(limit: Int): List<KnowledgeCapsuleEntity>

    @Query("SELECT * FROM knowledge_capsules ORDER BY createdAtMillis ASC, capsuleId ASC")
    suspend fun loadKnowledgeCapsuleChain(): List<KnowledgeCapsuleEntity>

    @Query("SELECT * FROM knowledge_capsules WHERE title = :title ORDER BY capsuleVersion DESC, createdAtMillis DESC")
    suspend fun loadCapsulesByTitle(title: String): List<KnowledgeCapsuleEntity>

    @Query("UPDATE knowledge_capsules SET status = 'superseded', updatedAtMillis = :updatedAtMillis WHERE title = :title AND status = 'active'")
    suspend fun markActiveCapsulesSuperseded(title: String, updatedAtMillis: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertKnowledgeCapsule(capsule: KnowledgeCapsuleEntity)
}