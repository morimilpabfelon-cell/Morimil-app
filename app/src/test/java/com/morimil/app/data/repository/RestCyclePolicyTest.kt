package com.morimil.app.data.repository

import com.morimil.app.data.local.MemoryEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestCyclePolicyTest {
    @Test
    fun lowRiskEventsDoNotRequireHumanApproval() {
        val events = listOf(
            event(memoryKind = "learning", importance = 62),
            event(memoryKind = "preference", importance = 70)
        )

        assertFalse(RestCyclePolicy.requiresHumanApproval(events))
        assertEquals("low", RestCyclePolicy.riskLevel(events))
    }

    @Test
    fun confirmedCriticalEventRequiresHumanApproval() {
        val events = listOf(
            event(memoryKind = "decision", importance = 86, userConfirmed = true)
        )

        assertTrue(RestCyclePolicy.requiresHumanApproval(events))
        assertEquals("medium", RestCyclePolicy.riskLevel(events))
    }

    @Test
    fun highImpactBatchRequiresHumanApproval() {
        val events = listOf(
            event(memoryKind = "learning", importance = 81),
            event(memoryKind = "preference", importance = 83),
            event(memoryKind = "correction", importance = 82)
        )

        assertTrue(RestCyclePolicy.requiresHumanApproval(events))
    }

    private fun event(
        memoryKind: String,
        importance: Int,
        userConfirmed: Boolean = false
    ): MemoryEventEntity {
        return MemoryEventEntity(
            genesisCoreId = "primary_genesis",
            genesisCoreHash = "sha256:genesis",
            previousEventHash = null,
            eventHash = "sha256:$memoryKind:$importance:$userConfirmed",
            hashAlgorithm = "sha256",
            canonicalization = "morimil.memory_event_hash.v3",
            signatureAlgorithm = "unsigned_runtime_v1",
            eventSignature = null,
            eventType = "test.event",
            actor = "user",
            source = "test",
            contextTag = "test",
            privacyVisibility = "private_local",
            memoryKind = memoryKind,
            tagsJson = "[]",
            evidenceJson = "{}",
            confidence = 90,
            userConfirmed = userConfirmed,
            body = "test memory",
            importance = importance,
            createdAtMillis = 123L
        )
    }
}
