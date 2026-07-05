package com.morimil.app.ui

import com.morimil.app.data.local.KnowledgeCapsuleEntity
import com.morimil.app.data.local.MemoryLinkEntity
import com.morimil.app.data.local.MigrationRecordEntity

data class MemoryOrgansUiState(
    val level: HealthStatusLevel,
    val summary: String,
    val capsuleCount: Int,
    val capsuleCategoryCount: Int,
    val averageCapsuleConfidence: Int,
    val linkCount: Int,
    val orphanedLinkCount: Int,
    val migrationCount: Int,
    val pendingMigrationCount: Int,
    val failedMigrationCount: Int,
    val actionLabel: String
)

object MemoryOrgansUiStateBuilder {
    fun build(
        capsules: List<KnowledgeCapsuleEntity>,
        links: List<MemoryLinkEntity>,
        migrations: List<MigrationRecordEntity>
    ): MemoryOrgansUiState {
        val activeCapsules = capsules.filter { capsule -> capsule.status == "active" }
        val categories = activeCapsules.map { capsule -> capsule.capsuleCategory }.distinct()
        val averageConfidence = if (activeCapsules.isEmpty()) {
            0
        } else {
            activeCapsules.sumOf { capsule -> capsule.confidence } / activeCapsules.size
        }
        val orphanedLinks = links.count { link -> link.verificationState == "orphaned" }
        val pendingMigrations = migrations.count { migration ->
            migration.status == "planned" || migration.status == "approved"
        }
        val failedMigrations = migrations.count { migration ->
            migration.status == "failed" || migration.status == "rolled_back"
        }
        val level = when {
            failedMigrations > 0 || orphanedLinks > 0 -> HealthStatusLevel.Critical
            pendingMigrations > 0 -> HealthStatusLevel.Attention
            activeCapsules.isEmpty() || links.isEmpty() -> HealthStatusLevel.Watch
            else -> HealthStatusLevel.Stable
        }
        val actionLabel = when {
            failedMigrations > 0 -> "accion: revisar migraciones"
            orphanedLinks > 0 -> "accion: reconciliar links"
            pendingMigrations > 0 -> "accion: aprobar o ejecutar"
            activeCapsules.isEmpty() -> "accion: crear capsulas"
            links.isEmpty() -> "accion: crear backlinks"
            else -> "accion: continuar"
        }
        val summary = "capsulas=${activeCapsules.size}, categorias=${categories.size}, " +
            "links=${links.size}, migraciones=${migrations.size}, confianza=$averageConfidence"

        return MemoryOrgansUiState(
            level = level,
            summary = summary,
            capsuleCount = activeCapsules.size,
            capsuleCategoryCount = categories.size,
            averageCapsuleConfidence = averageConfidence,
            linkCount = links.size,
            orphanedLinkCount = orphanedLinks,
            migrationCount = migrations.size,
            pendingMigrationCount = pendingMigrations,
            failedMigrationCount = failedMigrations,
            actionLabel = actionLabel
        )
    }
}
