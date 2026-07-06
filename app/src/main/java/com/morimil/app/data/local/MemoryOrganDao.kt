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

    @Query("SELECT * FROM recall_schedules WHERE status = 'active' ORDER BY dueAtMillis ASC, priority DESC, recallId ASC LIMIT 20")
    fun observeActiveRecallSchedules(): Flow<List<RecallScheduleEntity>>

    @Query("SELECT * FROM recall_schedules WHERE recallId = :recallId LIMIT 1")
    suspend fun loadRecallSchedule(recallId: Long): RecallScheduleEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecallSchedule(schedule: RecallScheduleEntity): Long

    @Query(
        """
        UPDATE recall_schedules
        SET dueAtMillis = :dueAtMillis,
            intervalDays = :intervalDays,
            status = :status,
            lastAction = :lastAction,
            lastReviewedAtMillis = :lastReviewedAtMillis,
            updatedAtMillis = :updatedAtMillis
        WHERE recallId = :recallId
        """
    )
    suspend fun updateRecallSchedule(
        recallId: Long,
        dueAtMillis: Long,
        intervalDays: Int,
        status: String,
        lastAction: String,
        lastReviewedAtMillis: Long?,
        updatedAtMillis: Long
    ): Int

    @Query(
        """
        UPDATE recall_schedules
        SET status = 'degraded',
            lastAction = 'degraded',
            updatedAtMillis = :updatedAtMillis
        WHERE recallId = :recallId
        """
    )
    suspend fun markRecallScheduleDegraded(recallId: Long, updatedAtMillis: Long): Int

    @Query(
        """
        SELECT * FROM memory_links
        WHERE (sourceId = :nodeId AND sourceType = :nodeType)
           OR (targetId = :nodeId AND targetType = :nodeType)
        ORDER BY strength DESC, createdAtMillis DESC
        LIMIT :limit
        """
    )
    fun observeMemoryLinksForNode(nodeId: String, nodeType: String, limit: Int): Flow<List<MemoryLinkEntity>>

    @Query("SELECT * FROM memory_links ORDER BY createdAtMillis DESC LIMIT :limit")
    fun observeRecentMemoryLinks(limit: Int): Flow<List<MemoryLinkEntity>>

    @Query("SELECT * FROM memory_links WHERE verificationState != 'orphaned'")
    suspend fun loadMemoryLinksForReconciliation(): List<MemoryLinkEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMemoryLink(link: MemoryLinkEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMemoryLinks(links: List<MemoryLinkEntity>): List<Long>

    @Query("UPDATE memory_links SET verificationState = 'orphaned' WHERE linkId IN (:linkIds)")
    suspend fun markMemoryLinksOrphaned(linkIds: List<String>): Int

    @Query("SELECT * FROM recall_schedules WHERE status = 'active'")
    suspend fun loadActiveRecallSchedulesForReconciliation(): List<RecallScheduleEntity>

    @Query("SELECT * FROM knowledge_capsules WHERE status = 'active' AND sourceEventHash IS NOT NULL")
    suspend fun loadKnowledgeCapsulesWithSourceEvents(): List<KnowledgeCapsuleEntity>

    @Query("SELECT * FROM migration_records ORDER BY createdAtMillis DESC LIMIT :limit")
    fun observeRecentMigrationRecords(limit: Int): Flow<List<MigrationRecordEntity>>

    @Query("SELECT * FROM migration_records WHERE status IN ('planned', 'approved', 'completed')")
    suspend fun loadMigrationRecordsForReconciliation(): List<MigrationRecordEntity>

    @Query("SELECT * FROM migration_records WHERE migrationId = :migrationId LIMIT 1")
    suspend fun loadMigrationRecord(migrationId: String): MigrationRecordEntity?

    @Query(
        """
        SELECT * FROM migration_records
        WHERE migrationType = :migrationType AND status = :status
        ORDER BY createdAtMillis DESC
        LIMIT 1
        """
    )
    suspend fun loadLatestMigrationRecordByTypeAndStatus(
        migrationType: String,
        status: String
    ): MigrationRecordEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMigrationRecord(record: MigrationRecordEntity)

    @Query(
        """
        UPDATE migration_records
        SET approvedByUser = 1,
            approvalId = :approvalId,
            status = :status,
            updatedAtMillis = :updatedAtMillis
        WHERE migrationId = :migrationId
        """
    )
    suspend fun approveMigrationRecord(
        migrationId: String,
        approvalId: String,
        status: String,
        updatedAtMillis: Long
    ): Int

    @Query(
        """
        UPDATE migration_records
        SET status = :status,
            postSnapshotId = :postSnapshotId,
            errorsJson = :errorsJson,
            updatedAtMillis = :updatedAtMillis
        WHERE migrationId = :migrationId
        """
    )
    suspend fun updateMigrationRecordResult(
        migrationId: String,
        status: String,
        postSnapshotId: String?,
        errorsJson: String,
        updatedAtMillis: Long
    ): Int

    @Query("SELECT * FROM orchestrator_devices ORDER BY authorizationStatus ASC, updatedAtMillis DESC")
    fun observeOrchestratorDevices(): Flow<List<OrchestratorDeviceEntity>>

    @Query("SELECT COUNT(*) FROM orchestrator_devices")
    suspend fun countOrchestratorDevices(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrchestratorDevices(devices: List<OrchestratorDeviceEntity>): List<Long>

    @Query("SELECT * FROM agent_profiles ORDER BY riskLevel DESC, role ASC")
    fun observeAgentProfiles(): Flow<List<AgentProfileEntity>>

    @Query("SELECT COUNT(*) FROM agent_profiles")
    suspend fun countAgentProfiles(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAgentProfiles(agents: List<AgentProfileEntity>): List<Long>

    @Query("SELECT * FROM delegated_tasks ORDER BY createdAtMillis DESC LIMIT 30")
    fun observeDelegatedTasks(): Flow<List<DelegatedTaskEntity>>

    @Query("SELECT * FROM delegated_tasks WHERE taskId = :taskId LIMIT 1")
    suspend fun loadDelegatedTask(taskId: String): DelegatedTaskEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDelegatedTask(task: DelegatedTaskEntity)

    @Query(
        """
        UPDATE delegated_tasks
        SET approvalId = :approvalId,
            status = :status,
            updatedAtMillis = :updatedAtMillis
        WHERE taskId = :taskId
        """
    )
    suspend fun approveDelegatedTask(
        taskId: String,
        approvalId: String,
        status: String,
        updatedAtMillis: Long
    ): Int

    @Query(
        """
        UPDATE delegated_tasks
        SET status = :status,
            errorSummary = :errorSummary,
            updatedAtMillis = :updatedAtMillis,
            completedAtMillis = :completedAtMillis
        WHERE taskId = :taskId
        """
    )
    suspend fun rejectDelegatedTask(
        taskId: String,
        status: String,
        errorSummary: String,
        updatedAtMillis: Long,
        completedAtMillis: Long
    ): Int
}
