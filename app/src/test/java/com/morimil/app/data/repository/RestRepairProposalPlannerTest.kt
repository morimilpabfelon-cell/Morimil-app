package com.morimil.app.data.repository

import com.morimil.app.data.local.MemoryEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestRepairProposalPlannerTest {
    @Test
    fun duplicateMemoriesBecomeRepairCandidates() {
        val text = "Morimil debe guardar decisiones importantes solo cuando el usuario las confirma claramente."
        val report = RestRepairProposalPlanner.build(
            listOf(
                event(hash = "sha256:a", body = text, memoryKind = "decision", importance = 80),
                event(hash = "sha256:b", body = text, memoryKind = "decision", importance = 76)
            )
        )

        assertTrue(report.hasCandidates)
        assertTrue(report.candidates.any { candidate -> candidate.kind == "duplicate_candidate" })
        assertTrue(report.expectedEffect().contains("automatic_changes=false"))
    }

    @Test
    fun importantUnconfirmedMemoryRequiresReview() {
        val report = RestRepairProposalPlanner.build(
            listOf(
                event(
                    hash = "sha256:decision",
                    body = "La arquitectura aprobada exige aprobacion humana antes de acciones externas.",
                    memoryKind = "decision",
                    importance = 92,
                    userConfirmed = false
                )
            )
        )

        assertEquals("medium", report.riskLevel)
        assertTrue(report.candidates.any { candidate -> candidate.kind == "important_unconfirmed_memory" })
        assertTrue(report.migrationSteps().contains("wait_for_human_approval_before_any_repair"))
    }

    @Test
    fun correctionOverlappingStableMemoryCreatesHighRiskContradictionCandidate() {
        val report = RestRepairProposalPlanner.build(
            listOf(
                event(
                    hash = "sha256:old",
                    body = "IonPay sera una empresa billetera digital con agentes activos en la boveda.",
                    memoryKind = "decision",
                    importance = 88,
                    createdAtMillis = 10L
                ),
                event(
                    hash = "sha256:correction",
                    body = "Correccion: IonPay no sera una empresa billetera digital todavia; solo es una idea por revisar.",
                    memoryKind = "correction",
                    eventType = "memory_review.correction",
                    importance = 90,
                    createdAtMillis = 20L
                )
            )
        )

        assertEquals("high", report.riskLevel)
        assertTrue(report.candidates.any { candidate -> candidate.kind == "possible_contradiction" })
    }

    @Test
    fun cleanLowImportanceMemoriesDoNotCreateRepairProposal() {
        val report = RestRepairProposalPlanner.build(
            listOf(
                event(hash = "sha256:one", body = "Hola, conversacion casual.", memoryKind = "conversation", importance = 20),
                event(hash = "sha256:two", body = "Nota simple sin importancia estable.", memoryKind = "learning", importance = 45)
            )
        )

        assertFalse(report.hasCandidates)
        assertTrue(report.candidates.isEmpty())
    }

    @Test
    fun evidenceJsonDeclaresProposalOnlyMode() {
        val report = RestRepairProposalPlanner.build(
            listOf(
                event(
                    hash = "sha256:decision",
                    body = "Una decision muy importante debe revisarse antes de consolidarse como verdad estable.",
                    memoryKind = "decision",
                    importance = 95,
                    userConfirmed = false
                )
            )
        )

        val evidence = report.evidenceJson("mig_test")

        assertTrue(evidence.contains("morimil.rest_repair_proposal.v1"))
        assertTrue(evidence.contains("proposal_only"))
        assertTrue(evidence.contains("approval_required"))
    }

    private fun event(
        hash: String,
        body: String,
        memoryKind: String,
        importance: Int,
        eventType: String = "test.event",
        confidence: Int = 90,
        userConfirmed: Boolean = false,
        createdAtMillis: Long = 123L
    ): MemoryEventEntity {
        return MemoryEventEntity(
            genesisCoreId = "primary_genesis",
            genesisCoreHash = "sha256:genesis",
            previousEventHash = null,
            eventHash = hash,
            hashAlgorithm = "sha256",
            canonicalization = "morimil.memory_event_hash.v3",
            signatureAlgorithm = "unsigned_runtime_v1",
            eventSignature = null,
            eventType = eventType,
            actor = "user",
            source = "test",
            contextTag = "test",
            privacyVisibility = "private_local",
            memoryKind = memoryKind,
            tagsJson = "[]",
            evidenceJson = "{}",
            confidence = confidence,
            userConfirmed = userConfirmed,
            body = body,
            importance = importance,
            createdAtMillis = createdAtMillis
        )
    }
}
