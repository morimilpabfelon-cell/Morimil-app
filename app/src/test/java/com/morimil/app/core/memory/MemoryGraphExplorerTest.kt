package com.morimil.app.core.memory

import com.morimil.app.data.local.DecisionLogEntity
import com.morimil.app.data.local.KnowledgeCapsuleEntity
import com.morimil.app.data.local.MemoryEventEntity
import com.morimil.app.data.local.MemoryLinkEntity
import com.morimil.app.data.local.MigrationRecordEntity
import com.morimil.app.data.local.ProjectStateEntity
import com.morimil.app.data.local.RecallScheduleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryGraphExplorerTest {
    @Test
    fun globalGraphBuildsTypedNodesAndDetectsGaps() {
        val validHash = "sha256:valid"
        val missingHash = "sha256:missing"
        val snapshot = MemoryGraphExplorer.build(
            mode = MemoryGraphExplorer.MODE_GLOBAL,
            selectedEventHash = null,
            events = listOf(event(validHash, "decision", "Fundador decide grafo global")),
            links = listOf(link("l1", validHash, missingHash, verificationState = "orphaned")),
            capsules = listOf(capsule("c1", "Capsula con hueco", missingHash)),
            recalls = listOf(recall(1L, missingHash)),
            migrations = listOf(migration("m1", """["$missingHash"]""")),
            projects = listOf(ProjectStateEntity("p1", "Morimil", "active", 1L)),
            decisions = listOf(DecisionLogEntity(1L, "Crear grafo v2", "accepted", 1L)),
            nowMillis = 10L
        )

        assertTrue(snapshot.nodes.any { node -> node.nodeType == "knowledge_capsule" })
        assertTrue(snapshot.nodes.any { node -> node.nodeType == "project" })
        assertTrue(snapshot.nodes.any { node -> node.nodeType == "decision" })
        assertTrue(snapshot.gaps.any { gap -> gap.title.contains("Capsula") })
        assertTrue(snapshot.issueCount > 0)
        assertTrue(snapshot.narrativePath.first() == "Morimil")
    }

    @Test
    fun focusGraphKeepsSelectedMemoryEvent() {
        val selectedHash = "sha256:selected"
        val snapshot = MemoryGraphExplorer.build(
            mode = MemoryGraphExplorer.MODE_FOCUS,
            selectedEventHash = selectedHash,
            events = listOf(event(selectedHash, "identity", "Recuerdo central")),
            links = emptyList(),
            capsules = emptyList(),
            recalls = emptyList(),
            migrations = emptyList(),
            projects = emptyList(),
            decisions = emptyList()
        )

        assertEquals(selectedHash, snapshot.selectedNodeId)
        assertTrue(snapshot.nodes.any { node -> node.nodeId == selectedHash && node.selected })
    }

    private fun event(hash: String, memoryKind: String, body: String): MemoryEventEntity {
        return MemoryEventEntity(
            id = 1L,
            genesisCoreId = "primary_genesis",
            genesisCoreHash = "sha256:genesis",
            previousEventHash = null,
            eventHash = hash,
            hashAlgorithm = "sha256",
            canonicalization = "morimil.memory_event_hash.v3",
            signatureAlgorithm = null,
            eventSignature = null,
            eventType = "test.event",
            actor = "test",
            source = "test",
            contextTag = "test",
            privacyVisibility = "private_local",
            memoryKind = memoryKind,
            tagsJson = "[]",
            evidenceJson = "{}",
            confidence = 80,
            userConfirmed = true,
            body = body,
            importance = 90,
            createdAtMillis = 1L
        )
    }

    private fun link(linkId: String, sourceHash: String, targetHash: String, verificationState: String = "valid"): MemoryLinkEntity {
        return MemoryLinkEntity(
            linkId = linkId,
            instanceId = "local",
            genesisCoreHash = "sha256:genesis",
            sourceId = sourceHash,
            sourceType = "memory_event",
            targetId = targetHash,
            targetType = "memory_event",
            relation = "supports",
            strength = 0.8,
            reason = "test",
            createdBy = "test",
            privacyVisibility = "private_local",
            cloudSyncAllowed = false,
            exportAllowed = false,
            verificationState = verificationState,
            createdAtMillis = 1L
        )
    }

    private fun capsule(capsuleId: String, title: String, sourceEventHash: String): KnowledgeCapsuleEntity {
        return KnowledgeCapsuleEntity(
            capsuleId = capsuleId,
            genesisCoreId = "primary_genesis",
            capsuleVersion = 1,
            capsuleCategory = "test",
            capsuleType = "knowledge_capsule",
            status = "active",
            title = title,
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

    private fun recall(recallId: Long, targetEventHash: String): RecallScheduleEntity {
        return RecallScheduleEntity(
            recallId = recallId,
            genesisCoreId = "primary_genesis",
            targetEventHash = targetEventHash,
            targetMemoryKind = "decision",
            prompt = "recordar",
            reason = "test",
            priority = 90,
            intervalDays = 1,
            dueAtMillis = 1L,
            status = "active",
            lastAction = "created",
            source = "test",
            createdAtMillis = 1L,
            updatedAtMillis = 1L,
            lastReviewedAtMillis = null
        )
    }

    private fun migration(migrationId: String, affectedArtifactsJson: String): MigrationRecordEntity {
        return MigrationRecordEntity(
            migrationId = migrationId,
            instanceId = "local",
            genesisCoreHash = "sha256:genesis",
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