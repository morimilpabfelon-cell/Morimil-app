package com.morimil.app.data.repository

import com.morimil.app.core.identity.StableIdDigest
import com.morimil.app.data.local.MemoryOrganDatabase
import com.morimil.app.data.local.MigrationRecordEntity
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

class MigrationRecordRepository(organDatabase: MemoryOrganDatabase) {
    private val organDao = organDatabase.memoryOrganDao()

    val recentMigrationRecords: Flow<List<MigrationRecordEntity>> = organDao.observeRecentMigrationRecords(RECENT_MIGRATION_LIMIT)

    suspend fun loadLatestPlannedMigration(migrationType: String): MigrationRecordEntity? {
        return organDao.loadLatestMigrationRecordByTypeAndStatus(
            migrationType = migrationType,
            status = STATUS_PLANNED
        )
    }

    suspend fun loadLatestCompletedMigration(migrationType: String): MigrationRecordEntity? {
        return organDao.loadLatestMigrationRecordByTypeAndStatus(
            migrationType = migrationType,
            status = STATUS_COMPLETED
        )
    }

    suspend fun planMigration(
        instanceId: String,
        genesisCoreHash: String,
        proposalId: String?,
        migrationType: String,
        fromVersion: String,
        toVersion: String,
        affectedArtifacts: List<String>,
        preSnapshotId: String,
        chainVerified: Boolean,
        backupRequired: Boolean,
        steps: List<String>,
        expectedEffect: String,
        riskLevel: String,
        approvalRequired: Boolean = true,
        rollbackAvailable: Boolean,
        rollbackStrategy: String,
        approvedByUser: Boolean,
        approvalId: String?
    ): String {
        val now = System.currentTimeMillis()
        val migrationId = buildMigrationId(now, migrationType, fromVersion, toVersion)
        organDao.insertMigrationRecord(
            MigrationRecordEntity(
                migrationId = migrationId,
                instanceId = instanceId,
                genesisCoreHash = genesisCoreHash,
                proposalId = proposalId,
                migrationType = migrationType,
                fromVersion = fromVersion,
                toVersion = toVersion,
                affectedArtifactsJson = JSONArray(affectedArtifacts).toString(),
                preSnapshotId = preSnapshotId,
                chainVerified = chainVerified,
                backupRequired = backupRequired,
                stepsJson = JSONArray(steps).toString(),
                expectedEffect = expectedEffect,
                riskLevel = riskLevel,
                approvalRequired = approvalRequired,
                approvedByUser = approvedByUser,
                approvalId = approvalId,
                status = STATUS_PLANNED,
                postSnapshotId = null,
                errorsJson = "[]",
                rollbackAvailable = rollbackAvailable,
                rollbackStrategy = rollbackStrategy,
                createdBy = CREATED_BY_LOCAL_RUNTIME,
                createdAtMillis = now,
                updatedAtMillis = now
            )
        )
        return migrationId
    }

    suspend fun loadMigration(migrationId: String): MigrationRecordEntity? {
        return organDao.loadMigrationRecord(migrationId)
    }

    suspend fun markMigrationCompleted(
        migrationId: String,
        postSnapshotId: String?,
        resultNotes: List<String> = emptyList()
    ) {
        updateMigrationResult(
            migrationId = migrationId,
            status = STATUS_COMPLETED,
            postSnapshotId = postSnapshotId,
            errors = resultNotes
        )
    }

    suspend fun markMigrationApproved(migrationId: String, approvalId: String) {
        val rows = organDao.approveMigrationRecord(
            migrationId = migrationId,
            approvalId = approvalId,
            status = STATUS_APPROVED,
            updatedAtMillis = System.currentTimeMillis()
        )
        require(rows > 0) { "Migration approval update failed." }
    }

    suspend fun markMigrationFailed(
        migrationId: String,
        errors: List<String>,
        postSnapshotId: String? = null
    ) {
        updateMigrationResult(
            migrationId = migrationId,
            status = STATUS_FAILED,
            postSnapshotId = postSnapshotId,
            errors = errors
        )
    }

    suspend fun markMigrationRolledBack(
        migrationId: String,
        rollbackEventHash: String?,
        notes: List<String>
    ) {
        updateMigrationResult(
            migrationId = migrationId,
            status = STATUS_ROLLED_BACK,
            postSnapshotId = rollbackEventHash,
            errors = notes
        )
    }

    private suspend fun updateMigrationResult(
        migrationId: String,
        status: String,
        postSnapshotId: String?,
        errors: List<String>
    ) {
        val rows = organDao.updateMigrationRecordResult(
            migrationId = migrationId,
            status = status,
            postSnapshotId = postSnapshotId,
            errorsJson = JSONArray(errors).toString(),
            updatedAtMillis = System.currentTimeMillis()
        )
        require(rows > 0) { "Migration record update failed." }
    }

    companion object {
        private const val CREATED_BY_LOCAL_RUNTIME = "local_runtime"
        private const val STATUS_PLANNED = "planned"
        private const val STATUS_APPROVED = "approved"
        private const val STATUS_COMPLETED = "completed"
        private const val STATUS_FAILED = "failed"
        private const val STATUS_ROLLED_BACK = "rolled_back"
        private const val RECENT_MIGRATION_LIMIT = 20

        fun buildMigrationId(
            createdAtMillis: Long,
            migrationType: String,
            fromVersion: String,
            toVersion: String
        ): String {
            val suffix = StableIdDigest.shortSha256Hex(
                namespace = "migration_record",
                parts = listOf(
                    createdAtMillis.toString(),
                    migrationType,
                    fromVersion,
                    toVersion
                )
            )
            return "mig_${createdAtMillis}_$suffix"
        }
    }
}
