package com.morimil.app.core.memory

import com.morimil.app.data.local.MemoryEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryIntegrityVerifierTest {
    private val eventIntegrity = MemoryEventIntegrity()
    private val verifier = MemoryIntegrityVerifier(eventIntegrity)

    @Test
    fun tailInspectionReturnsAppendHashForTrustedTail() {
        val checkpointHash = "sha256:trusted-checkpoint"
        val first = sampleEvent(previousEventHash = checkpointHash)
        val second = sampleEvent(
            previousEventHash = first.eventHash,
            body = "Morimil appends after the trusted tail.",
            createdAtMillis = SAMPLE_CREATED_AT_MILLIS + 1
        )

        val result = verifier.inspectMemoryEventTail(
            events = listOf(first, second),
            fallbackPreviousHash = checkpointHash
        )

        assertTrue(result.trusted)
        assertEquals(second.eventHash, result.appendPreviousEventHash)
        assertEquals(second.eventHash, result.lastTrustedEventHash)
    }

    @Test
    fun tailInspectionStopsAtFirstUntrustedEvent() {
        val checkpointHash = "sha256:trusted-checkpoint"
        val first = sampleEvent(previousEventHash = checkpointHash)
        val second = sampleEvent(
            previousEventHash = first.eventHash,
            body = "This event is valid before tampering.",
            createdAtMillis = SAMPLE_CREATED_AT_MILLIS + 1
        )
        val tamperedSecond = second.copy(body = "Tampered body.")

        val result = verifier.inspectMemoryEventTail(
            events = listOf(first, tamperedSecond),
            fallbackPreviousHash = checkpointHash
        )

        assertFalse(result.trusted)
        assertEquals(first.eventHash, result.appendPreviousEventHash)
        assertEquals(first.eventHash, result.lastTrustedEventHash)
        assertEquals(second.eventHash, result.firstUntrustedHash)
        assertEquals("event_hash_mismatch", result.reason)
    }

    private fun sampleEvent(
        previousEventHash: String?,
        body: String = SAMPLE_BODY,
        createdAtMillis: Long = SAMPLE_CREATED_AT_MILLIS
    ): MemoryEventEntity {
        val eventHash = eventIntegrity.hashMemoryEventV3(
            genesisCoreId = SAMPLE_GENESIS_CORE_ID,
            genesisCoreHash = SAMPLE_GENESIS_CORE_HASH,
            previousEventHash = previousEventHash,
            eventType = "conversation.user_message",
            actor = "user",
            source = "chat",
            contextTag = "local_runtime",
            privacyVisibility = "private_local",
            memoryKind = "decision",
            tagsJson = "[\"memory\",\"decision\"]",
            evidenceJson = SAMPLE_EVIDENCE_JSON,
            confidence = 94,
            userConfirmed = true,
            body = body,
            importance = 92,
            createdAtMillis = createdAtMillis
        )
        return MemoryEventEntity(
            genesisCoreId = SAMPLE_GENESIS_CORE_ID,
            genesisCoreHash = SAMPLE_GENESIS_CORE_HASH,
            previousEventHash = previousEventHash,
            eventHash = eventHash,
            hashAlgorithm = "sha256",
            canonicalization = MemoryEventIntegrity.MEMORY_EVENT_CANONICALIZATION_V3,
            signatureAlgorithm = "unsigned_runtime_v1",
            eventSignature = null,
            eventType = "conversation.user_message",
            actor = "user",
            source = "chat",
            contextTag = "local_runtime",
            privacyVisibility = "private_local",
            memoryKind = "decision",
            tagsJson = "[\"memory\",\"decision\"]",
            evidenceJson = SAMPLE_EVIDENCE_JSON,
            confidence = 94,
            userConfirmed = true,
            body = body,
            importance = 92,
            createdAtMillis = createdAtMillis
        )
    }

    companion object {
        private const val SAMPLE_GENESIS_CORE_ID = "primary_genesis"
        private const val SAMPLE_GENESIS_CORE_HASH = "sha256:genesis-core-test"
        private const val SAMPLE_BODY = "Morimil keeps memory integrity in one core verifier."
        private const val SAMPLE_EVIDENCE_JSON = "{\"schema\":\"morimil.memory_evidence.v1\",\"classifier\":\"test\"}"
        private const val SAMPLE_CREATED_AT_MILLIS = 1_720_000_000_000L
    }
}
