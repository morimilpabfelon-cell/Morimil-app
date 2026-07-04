package com.morimil.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "migration_records",
    indices = [
        Index(value = ["migrationType"], name = "index_migration_records_migrationType"),
        Index(value = ["status"], name = "index_migration_records_status"),
        Index(value = ["approvedByUser"], name = "index_migration_records_approvedByUser"),
        Index(value = ["createdAtMillis"], name = "index_migration_records_createdAtMillis")
    ]
)
data class MigrationRecordEntity(
    @PrimaryKey
    val migrationId: String,
    val instanceId: String,
    val genesisCoreHash: String,
    val proposalId: String?,
    val migrationType: String,
    val fromVersion: String,
    val toVersion: String,
    val affectedArtifactsJson: String,
    val preSnapshotId: String,
    val chainVerified: Boolean,
    val backupRequired: Boolean,
    val stepsJson: String,
    val expectedEffect: String,
    val riskLevel: String,
    val approvalRequired: Boolean,
    val approvedByUser: Boolean,
    val approvalId: String?,
    val status: String,
    val postSnapshotId: String?,
    val errorsJson: String,
    val rollbackAvailable: Boolean,
    val rollbackStrategy: String,
    val createdBy: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)
