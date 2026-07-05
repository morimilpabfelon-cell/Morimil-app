package com.morimil.app.data.repository

import com.morimil.app.data.local.MemoryEventEntity
import com.morimil.app.data.local.MemoryOrganDatabase
import com.morimil.app.data.local.MigrationRecordEntity
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
        val events = memoryDao.loadMemoryContext(40)
            .filter { event -> event.memoryKind != "chat_noise" }
            .sortedWith(
                compareByDescending<MemoryEventEntity> { it.userConfirmed }
                    .thenByDescending { it.importance }
                    .thenByDescending { it.confidence }
                    .thenByDescending { it.createdAtMillis }
            )
            .take(12)
        if (events.isEmpty()) return null

        val chainVerified = memoryRepository.auditLivingMemoryChain()
        return migrationRecordRepository.planMigration(
            instanceId = localIdentity?.instanceId ?: "local_instance_pending",
            genesisCoreHash = genesisCore.contentSha256,
            proposalId = "cognitive_refinement:${System.currentTimeMillis()}",
            migrationType = COGNITIVE_MIGRATION_TYPE,
            fromVersion = "living_memory_current",
            toVersion = "living_memory_refined_v1",
            affectedArtifacts = events.map { event -> event.eventHash },
            preSnapshotId = snapshot?.updatedAtMillis?.let { "snapshot:$it" } ?: "snapshot:none",
            chainVerified = chainVerified,
            backupRequired = true,
            steps = listOf(
                "review_high_value_memory_events",
                "append_cognitive_migration_execution_event",
                "preserve_original_events",
                "allow_append_only_rollback_marker"
            ),
            expectedEffect = buildExpectedEffect(events),
            riskLevel = if (events.any { event -> event.importance >= 90 || event.userConfirmed }) "medium" else "low",
            approvalRequired = true,
            rollbackAvailable = true,
            rollbackStrategy = "append_only: original memories stay untouched; rollback appends a compensating cognitive_migration.rollback event",
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
                body = buildExecutionBody(record),
                importance = if (record.riskLevel == "medium") 92 else 82
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
            body = buildRollbackBody(record),
            importance = 95
        )
        migrationRecordRepository.markMigrationRolledBack(
            migrationId = migrationId,
            rollbackEventHash = eventHash,
            notes = listOf("rollback_requested_by_user", "append_only_compensation")
        )
        return true
    }

    private fun buildExpectedEffect(events: List<MemoryEventEntity>): String {
        val digest = events.take(6).joinToString("\n") { event ->
            "- ${event.memoryKind}/i${event.importance}/c${event.confidence}/${event.eventHash.take(19)}: " +
                event.body.replace("\n", " ").take(220)
        }
        return "Propuesta de migracion cognitiva local: revisar y consolidar memoria de alto valor sin reescribir eventos originales.\n$digest"
    }

    private fun buildExecutionBody(record: MigrationRecordEntity): String {
        return "MIGRACION_COGNITIVA_EJECUTADA: migration_id=${record.migrationId}; " +
            "risk=${record.riskLevel}; approved=${record.approvedByUser}; approval_id=${record.approvalId}; " +
            "affected=${record.affectedArtifactsJson}; expected_effect=${record.expectedEffect.take(500)}"
    }

    private fun buildRollbackBody(record: MigrationRecordEntity): String {
        return "ROLLBACK_MIGRACION_COGNITIVA: migration_id=${record.migrationId}; " +
            "previous_status=${record.status}; strategy=${record.rollbackStrategy}; " +
            "affected=${record.affectedArtifactsJson}; note=append_only_compensation"
    }

    companion object {
        const val COGNITIVE_MIGRATION_TYPE = "cognitive.memory_refinement"
        private const val STATUS_PLANNED = "planned"
        private const val STATUS_APPROVED = "approved"
        private const val STATUS_COMPLETED = "completed"
        private const val STATUS_FAILED = "failed"
    }
}
