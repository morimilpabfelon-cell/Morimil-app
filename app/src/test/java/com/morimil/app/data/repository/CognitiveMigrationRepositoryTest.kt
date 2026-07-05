package com.morimil.app.data.repository

import com.morimil.app.core.memory.CognitiveMigrationPlanner
import com.morimil.app.core.memory.MemoryIntegrityCore
import com.morimil.app.data.local.MemoryEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CognitiveMigrationRepositoryTest {
    @Test
    fun cognitiveMigrationTypeIsStableForAuditFilters() {
        assertEquals(
            "cognitive.memory_refinement",
            CognitiveMigrationRepository.COGNITIVE_MIGRATION_TYPE
        )
    }

    @Test
    fun cognitiveMigrationRepositoryRequiresSharedSignedMemoryRepository() {
        val constructorParameterTypes = CognitiveMigrationRepository::class.java
            .constructors
            .flatMap { constructor -> constructor.parameterTypes.toList() }

        assertTrue(constructorParameterTypes.contains(MemoryRepository::class.java))
        assertFalse(constructorParameterTypes.contains(MemoryIntegrityCore::class.java))
    }

    @Test
    fun plannerBuildsCapsuleAndBacklinkProposalsWithoutRewritingMemory() {
        val events = listOf(
            memoryEvent(
                eventHash = "hash_decision_reasoning_001",
                previousEventHash = null,
                memoryKind = "decision",
                tagsJson = "[\"memory\",\"reasoning\",\"decision\"]",
                body = "Decision: el motor de razonamiento debe ser neutral y admitir varios proveedores.",
                importance = 96,
                confidence = 94,
                userConfirmed = true,
                createdAtMillis = 1000L
            ),
            memoryEvent(
                eventHash = "hash_preference_reasoning_002",
                previousEventHash = "hash_decision_reasoning_001",
                memoryKind = "preference",
                tagsJson = "[\"memory\",\"reasoning\",\"preference\"]",
                body = "Preferencia: Morimil debe delegar trabajo al proveedor que mejor haga cada tarea.",
                importance = 90,
                confidence = 88,
                userConfirmed = true,
                createdAtMillis = 2000L
            ),
            memoryEvent(
                eventHash = "hash_project_memory_003",
                previousEventHash = "hash_preference_reasoning_002",
                memoryKind = "learning",
                tagsJson = "[\"memory\",\"project\"]",
                body = "Aprendizaje: las migraciones cognitivas deben quedar auditadas y aprobadas por el usuario.",
                importance = 84,
                confidence = 86,
                userConfirmed = false,
                createdAtMillis = 3000L
            )
        )

        val plan = CognitiveMigrationPlanner.buildPlan(
            events = events,
            chainVerified = true,
            preSnapshotId = "snapshot:1000",
            createdAtMillis = 1234L
        )

        assertEquals(CognitiveMigrationPlanner.PLAN_SCHEMA, plan.schema)
        assertEquals("cognitive_refinement_v2:1234", plan.proposalId)
        assertEquals("medium", plan.riskLevel)
        assertTrue(plan.affectedArtifacts.any { artifact -> artifact.startsWith("memory_event:") })
        assertTrue(plan.affectedArtifacts.any { artifact -> artifact.startsWith("capsule_proposal:reasoning_motor") })
        assertTrue(plan.affectedArtifacts.any { artifact -> artifact.startsWith("link_proposal:") })
        assertTrue(plan.steps.contains("draft_capsule_proposals:2"))
        assertTrue(plan.steps.contains("audit_chain_after_execution"))
        assertTrue(plan.expectedEffect.contains("diff_logico:"))
        assertTrue(plan.expectedEffect.contains("capsulas_propuestas:"))
        assertTrue(plan.expectedEffect.contains("backlinks_propuestos:"))
        assertTrue(plan.expectedEffect.contains("policy=append_only"))
        assertTrue(plan.rollbackStrategy.contains("Append cognitive_migration.rollback"))
    }

    @Test
    fun plannerEscalatesRiskWhenChainAuditFails() {
        val plan = CognitiveMigrationPlanner.buildPlan(
            events = listOf(memoryEvent(eventHash = "hash_low_risk", body = "Contexto tecnico menor.")),
            chainVerified = false,
            preSnapshotId = "snapshot:none",
            createdAtMillis = 1L
        )

        assertEquals("high", plan.riskLevel)
        assertTrue(plan.steps.contains("audit_chain:needs_quarantine_review"))
        assertTrue(plan.rollbackStrategy.contains("Re-run explicit memory audit"))
    }

    @Test
    fun plannerBuildsPostExecutionAuditNotes() {
        val record = com.morimil.app.data.local.MigrationRecordEntity(
            migrationId = "mig_123",
            instanceId = "instance",
            genesisCoreHash = "sha256:genesis",
            proposalId = "proposal",
            migrationType = CognitiveMigrationRepository.COGNITIVE_MIGRATION_TYPE,
            fromVersion = "living_memory_current",
            toVersion = "living_memory_refined_v2",
            affectedArtifactsJson = "[]",
            preSnapshotId = "snapshot:1",
            chainVerified = true,
            backupRequired = true,
            stepsJson = "[]",
            expectedEffect = "plan",
            riskLevel = "medium",
            approvalRequired = true,
            approvedByUser = true,
            approvalId = "user_approved:1",
            status = "approved",
            postSnapshotId = null,
            errorsJson = "[]",
            rollbackAvailable = true,
            rollbackStrategy = "rollback",
            createdBy = "test",
            createdAtMillis = 1L,
            updatedAtMillis = 1L
        )

        val notes = CognitiveMigrationPlanner.buildPostExecutionAuditNotes(
            record = record,
            executionEventHash = "event_hash",
            chainVerified = true,
            checkedAtMillis = 2L
        )

        assertTrue(notes.contains("post_execution_audit:verified"))
        assertTrue(notes.contains("execution_event_hash:event_hash"))
        assertTrue(notes.contains("migration_id:mig_123"))
        assertTrue(notes.contains("policy:append_only_original_memory_unchanged"))
    }

    private fun memoryEvent(
        eventHash: String,
        previousEventHash: String? = null,
        memoryKind: String = "conversation",
        tagsJson: String = "[\"memory\"]",
        body: String,
        importance: Int = 60,
        confidence: Int = 70,
        userConfirmed: Boolean = false,
        createdAtMillis: Long = 100L
    ): MemoryEventEntity {
        return MemoryEventEntity(
            id = 0,
            genesisCoreId = "primary_genesis",
            genesisCoreHash = "sha256:genesis",
            previousEventHash = previousEventHash,
            eventHash = eventHash,
            hashAlgorithm = "sha256",
            canonicalization = "morimil.memory_event_hash.v3",
            signatureAlgorithm = "unsigned_runtime_v1",
            eventSignature = null,
            eventType = "conversation.user_message",
            actor = "user",
            source = "chat",
            contextTag = "local_runtime",
            privacyVisibility = "private_local",
            memoryKind = memoryKind,
            tagsJson = tagsJson,
            evidenceJson = "{}",
            confidence = confidence,
            userConfirmed = userConfirmed,
            body = body,
            importance = importance,
            createdAtMillis = createdAtMillis
        )
    }
}
