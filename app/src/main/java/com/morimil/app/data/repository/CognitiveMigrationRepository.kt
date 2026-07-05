package com.morimil.app.data.repository

import com.morimil.app.core.memory.CognitiveMigrationPlanner
import com.morimil.app.data.local.MemoryOrganDatabase
import com.morimil.app.data.local.MorimilDatabase

class CognitiveMigrationRepository(
    organDatabase: MemoryOrganDatabase,
    memoryDatabase: MorimilDatabase
) {
    private val memoryDao = memoryDatabase.memoryDao()
    private val memoryRepository = MemoryRepository(memoryDatabase)
    private val migrationRecordRepository = MigrationRecordRepository(organDatabase)

    suspend fun proposeCognitiveMigration(): String? {
        val genesisCore = memoryDao.loadGenesisCore() ?: return null
        val localIdentity = memoryDao.loadLocalIdentity()
        val snapshot = memoryDao.getLivingMemorySnapshot()
        val events = memoryDao.loadMemoryContext(60)
            .filter { event -> event.memoryKind != "chat_noise" }
            .sortedWith(
                compareByDescending<com.morimil.app.data.local.MemoryEventEntity> { it.userConfirmed }
                    .thenByDescending { it.importance }
                    .thenByDescending { it.confidence }
                    .thenByDescending { it.createdAtMillis }
            )
            .take(16)
        if (events.isEmpty()) return null

        val chainVerified = memoryRepository.auditLivingMemoryChain()
        val preSnapshotId = snapshot?.updatedAtMillis?.let { "snapshot:$it" } ?: "snapshot:none"
        val plan = CognitiveMigrationPlanner.buildPlan(
            events = events,
            chainVerified = chainVerified,
            preSnapshotId = preSnapshotId,
            createdAtMillis = System.currentTimeMillis()
        )

        return migrationRecordRepository.planMigration(
            instanceId = localIdentity?.instanceId ?: "local_instance_pending",
            genesisCoreHash = genesisCore.contentSha256,
            proposalId = plan.proposalId,
            migrationType = COGNITIVE_MIGRATION_TYPE,
            fromVersion = "living_memory_current",
            toVersion = "living_memory_refined_v2",
            affectedArtifacts = plan.affectedArtifacts,
            preSnapshotId = preSnapshotId,
            chainVerified = chainVerified,
            backupRequired = true,
            steps = plan.steps,
            expectedEffect = plan.expectedEffect,
            riskLevel = plan.riskLevel,
            approvalRequired = true,
            rollbackAvailable = true,
            rollbackStrategy = plan.rollbackStrategy,
            approvedByUser = false,
            approvalId = null
        )
    }

    suspend fun approveCognitiveMigration(migrationId: String): Boolean {
        val record = migrationRecordRepository.loadMigration(migrationId) ?: return false
        if (record.migrationType != COGNITIVE_MIGRATION_TYPE || record.status != STATUS_PLANNED) return false
        migrationRecordRepository.markMigrationApproved(
            migrationId = migrationId,
            approvalId = "user_approved:${System.currentTimeMillis()}"
        )
        return true
    }

    suspend fun executeCognitiveMigration(migrationId: String): Boolean {
        val record = migrationRecordRepository.loadMigration(migrationId) ?: return false
        if (record.migrationType != COGNITIVE_MIGRATION_TYPE || record.status != STATUS_APPROVED) return false

        return runCatching {
            val eventHash = memoryRepository.recordSystemMemoryEvent(
                eventType = "cognitive_migration.executed",
                body = CognitiveMigrationPlanner.buildExecutionBody(record),
                importance = when (record.riskLevel) {
                    "high" -> 96
                    "medium" -> 92
                    else -> 82
                }
            )
            migrationRecordRepository.markMigrationCompleted(
                migrationId = migrationId,
                postSnapshotId = eventHash
            )
            true
        }.getOrElse { error ->
            migrationRecordRepository.markMigrationFailed(
                migrationId = migrationId,
                errors = listOf(error.message ?: error::class.java.simpleName)
            )
            false
        }
    }

    suspend fun rollbackCognitiveMigration(migrationId: String): Boolean {
        val record = migrationRecordRepository.loadMigration(migrationId) ?: return false
        if (record.migrationType != COGNITIVE_MIGRATION_TYPE || !record.rollbackAvailable) return false
        if (record.status !in setOf(STATUS_COMPLETED, STATUS_APPROVED, STATUS_FAILED)) return false

        val eventHash = memoryRepository.recordSystemMemoryEvent(
            eventType = "cognitive_migration.rollback",
            body = CognitiveMigrationPlanner.buildRollbackBody(record),
            importance = 95
        )
        migrationRecordRepository.markMigrationRolledBack(
            migrationId = migrationId,
            rollbackEventHash = eventHash,
            notes = listOf("rollback_requested_by_user", "append_only_compensation", "cognitive_migration_plan_v2")
        )
        return true
    }

    companion object {
        const val COGNITIVE_MIGRATION_TYPE = "cognitive.memory_refinement"
        private const val STATUS_PLANNED = "planned"
        private const val STATUS_APPROVED = "approved"
        private const val STATUS_COMPLETED = "completed"
        private const val STATUS_FAILED = "failed"
    }
}
