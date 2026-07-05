package com.morimil.app.ui

import com.morimil.app.data.local.KnowledgeCapsuleEntity
import com.morimil.app.data.local.MemoryLinkEntity
import com.morimil.app.data.local.MigrationRecordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryOrgansUiStateTest {
    @Test
    fun stableOrgansReportCapsulesLinksAndMigrations() {
        val state = MemoryOrgansUiStateBuilder.build(
            capsules = listOf(capsule("c1", "fintech", 90), capsule("c2", "fintech", 80)),
            links = listOf(link("l1", "valid")),
            migrations = listOf(migration("m1", "completed", "low"))
        )

        assertEquals(HealthStatusLevel.Stable, state.level)
        assertEquals(2, state.capsuleCount)
        assertEquals(1, state.capsuleCategoryCount)
        assertEquals(85, state.averageCapsuleConfidence)
        assertEquals(1, state.linkCount)
        assertEquals(0, state.orphanedLinkCount)
        assertEquals("accion: continuar", state.actionLabel)
    }

    @Test
    fun orphanedLinksAreCritical() {
        val state = MemoryOrgansUiStateBuilder.build(
            capsules = listOf(capsule("c1", "fintech", 90)),
            links = listOf(link("l1", "orphaned")),
            migrations = emptyList()
        )

        assertEquals(HealthStatusLevel.Critical, state.level)
        assertEquals(1, state.orphanedLinkCount)
        assertEquals("accion: reconciliar links", state.actionLabel)
    }

    @Test
    fun pendingMigrationsRequireAttention() {
        val state = MemoryOrgansUiStateBuilder.build(
            capsules = listOf(capsule("c1", "fintech", 90)),
            links = listOf(link("l1", "valid")),
            migrations = listOf(migration("m1", "planned", "medium"))
        )

        assertEquals(HealthStatusLevel.Attention, state.level)
        assertEquals(1, state.pendingMigrationCount)
        assertEquals("accion: aprobar o ejecutar", state.actionLabel)
        assertTrue(state.summary.contains("migraciones=1"))
    }

    private fun capsule(id: String, category: String, confidence: Int): KnowledgeCapsuleEntity {
        return KnowledgeCapsuleEntity(
            capsuleId = id,
            genesisCoreId = "primary_genesis",
            capsuleVersion = 1,
            capsuleCategory = category,
            capsuleType = "knowledge_capsule",
            status = "active",
            title = id,
            source = "test",
            privacyVisibility = "private_local",
            summary = "summary",
            claimsJson = "[]",
            tags = "[]",
            evidenceJson = "{}",
            confidence = confidence,
            sourceEventHash = "sha256:event",
            previousCapsuleHash = null,
            capsuleHash = "sha256:$id",
            hashAlgorithm = "sha256",
            canonicalization = "morimil.knowledge_capsule_hash.v2",
            createdAtMillis = 1L,
            updatedAtMillis = 1L
        )
    }

    private fun link(id: String, verificationState: String): MemoryLinkEntity {
        return MemoryLinkEntity(
            linkId = id,
            instanceId = "local",
            genesisCoreHash = "genesis",
            sourceId = "sha256:source",
            sourceType = "memory_event",
            targetId = "sha256:target",
            targetType = "memory_event",
            relation = "derived_from",
            strength = 1.0,
            reason = "test",
            createdBy = "test",
            privacyVisibility = "private_local",
            cloudSyncAllowed = false,
            exportAllowed = false,
            verificationState = verificationState,
            createdAtMillis = 1L
        )
    }

    private fun migration(id: String, status: String, risk: String): MigrationRecordEntity {
        return MigrationRecordEntity(
            migrationId = id,
            instanceId = "local",
            genesisCoreHash = "genesis",
            proposalId = null,
            migrationType = "test",
            fromVersion = "from",
            toVersion = "to",
            affectedArtifactsJson = "[]",
            preSnapshotId = "none",
            chainVerified = true,
            backupRequired = false,
            stepsJson = "[]",
            expectedEffect = "effect",
            riskLevel = risk,
            approvalRequired = status == "planned",
            approvedByUser = false,
            approvalId = null,
            status = status,
            postSnapshotId = null,
            errorsJson = "[]",
            rollbackAvailable = true,
            rollbackStrategy = "append_only",
            createdBy = "test",
            createdAtMillis = 1L,
            updatedAtMillis = 1L
        )
    }
}
