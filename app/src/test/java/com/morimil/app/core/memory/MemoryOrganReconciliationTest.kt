package com.morimil.app.core.memory

import com.morimil.app.data.local.KnowledgeCapsuleEntity
import com.morimil.app.data.local.MemoryLinkEntity
import com.morimil.app.data.local.MigrationRecordEntity
import com.morimil.app.data.local.RecallScheduleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryOrganReconciliationTest {
    private val reconciler = MemoryOrganReconciliation()

    @Test
    fun detectsOrphanedOrganReferencesAgainstMemoryEvents() {
        val validHash = "sha256:valid"
        val missingHash = "sha256:missing"

        val report = reconciler.buildReport(
            validMemoryEventHashes = setOf(validHash),
            links = listOf(
                memoryLink("valid-link", validHash, validHash),
                memoryLink("orphan-link", validHash, missingHash)
            ),
            recalls = listOf(
                recall(1, validHash),
                recall(2, missingHash)
            ),
            capsules = listOf(
                capsule("capsule-valid", validHash),
                capsule("capsule-orphan", missingHash)
            ),
            migrations = listOf(
                migration("migration-with-gap", """["$validHash","$missingHash"]""")
            )
        )

        assertEquals(listOf("orphan-link"), report.orphanedLinkIds)
        assertEquals(listOf(2L), report.orphanedRecallIds)
        assertEquals(listOf("capsule-orphan"), report.orphanedCapsuleIds)
        assertEquals(listOf(missingHash), report.migrationMissingRefs["migration-with-gap"])
        assertTrue(report.hasIssues)
    }

    private fun memoryLink(linkId: String, sourceId: String, targetId: String): MemoryLinkEntity {
        return MemoryLinkEntity(
            linkId = linkId,
            instanceId = "local",
            genesisCoreHash = "genesis",
            sourceId = sourceId,
            sourceType = "memory_event",
            targetId = targetId,
            targetType = "memory_event",
            relation = "derived_from",
            strength = 1.0,
            reason = "test",
            createdBy = "test",
            privacyVisibility = "private_local",
            cloudSyncAllowed = false,
            exportAllowed = false,
            verificationState = "valid",
            createdAtMillis = 1L
        )
    }

    private fun recall(recallId: Long, targetEventHash: String): RecallScheduleEntity {
        return RecallScheduleEntity(
            recallId = recallId,
            genesisCoreId = "primary_genesis",
            targetEventHash = targetEventHash,
            targetMemoryKind = "decision",
            prompt = "prompt",
            reason = "test",
            priority = 80,
            intervalDays = 7,
            dueAtMillis = 1L,
            status = "active",
            lastAction = "created",
            source = "test",
            createdAtMillis = 1L,
            updatedAtMillis = 1L,
            lastReviewedAtMillis = null
        )
    }

    private fun capsule(capsuleId: String, sourceEventHash: String): KnowledgeCapsuleEntity {
        return KnowledgeCapsuleEntity(
            capsuleId = capsuleId,
            genesisCoreId = "primary_genesis",
            capsuleVersion = 1,
            capsuleCategory = "test",
            capsuleType = "knowledge_capsule",
            status = "active",
            title = capsuleId,
            source = "test",
            privacyVisibility = "private_local",
            summary = "summary",
            claimsJson = "[]",
            tags = "[]",
            evidenceJson = "{}",
            confidence = 80,
            sourceEventHash = sourceEventHash,
            previousCapsuleHash = null,
            capsuleHash = "sha256:$capsuleId",
            hashAlgorithm = "sha256",
            canonicalization = "morimil.knowledge_capsule_hash.v2",
            createdAtMillis = 1L,
            updatedAtMillis = 1L
        )
    }

    private fun migration(migrationId: String, affectedArtifactsJson: String): MigrationRecordEntity {
        return MigrationRecordEntity(
            migrationId = migrationId,
            instanceId = "local",
            genesisCoreHash = "genesis",
            proposalId = null,
            migrationType = "test",
            fromVersion = "from",
            toVersion = "to",
            affectedArtifactsJson = affectedArtifactsJson,
            preSnapshotId = "none",
            chainVerified = true,
            backupRequired = false,
            stepsJson = "[]",
            expectedEffect = "test",
            riskLevel = "low",
            approvalRequired = false,
            approvedByUser = false,
            approvalId = null,
            status = "planned",
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
