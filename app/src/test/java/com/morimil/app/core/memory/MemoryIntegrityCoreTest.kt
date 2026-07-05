package com.morimil.app.core.memory

import com.morimil.app.data.local.KnowledgeCapsuleEntity
import com.morimil.app.data.local.MemoryEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryIntegrityCoreTest {
    private val core = MemoryIntegrityCore()

    @Test
    fun corePreservesMemoryEventHashContract() {
        val event = sampleEvent(previousEventHash = null)

        assertEquals("sha256:a69fcd21d68505ea6504a9c6a4466268b2b7ccd271f49c0aac2d5ba89398ce1d", event.eventHash)
        assertNull(core.memoryEventIntegrityFailure(event, expectedPreviousHash = null))
        assertTrue(core.verifyMemoryEventChain(listOf(event)))
    }

    @Test
    fun corePreservesCapsuleHashContract() {
        val capsule = sampleCapsule(previousCapsuleHash = null)

        assertTrue(capsule.capsuleHash.startsWith("sha256:"))
        assertNull(core.capsuleIntegrityFailure(capsule, expectedPreviousHash = null))
        assertTrue(core.verifyCapsuleChain(listOf(capsule)))
    }

    @Test
    fun coreOwnsSharedIntegrityConstants() {
        assertEquals("sha256", MemoryIntegrityCore.HASH_ALGORITHM_SHA256)
        assertEquals("morimil.memory_event_hash.v3", MemoryIntegrityCore.MEMORY_EVENT_CANONICALIZATION_V3)
        assertEquals("morimil.knowledge_capsule_hash.v2", MemoryIntegrityCore.CAPSULE_CANONICALIZATION_V2)
        assertEquals("unsigned_runtime_v1", MemoryIntegrityCore.MEMORY_EVENT_SIGNATURE_ALGORITHM_UNSIGNED)
    }

    private fun sampleEvent(previousEventHash: String?): MemoryEventEntity {
        val eventHash = core.hashMemoryEventV3(
            genesisCoreId = SAMPLE_GENESIS_CORE_ID,
            genesisCoreHash = SAMPLE_GENESIS_CORE_HASH,
            previousEventHash = previousEventHash,
            eventType = "conversation.user_message",
            actor = "user",
            source = "chat",
            contextTag = SAMPLE_CONTEXT_TAG,
            privacyVisibility = SAMPLE_PRIVACY,
            memoryKind = "decision",
            tagsJson = "[\"memory\",\"decision\"]",
            evidenceJson = SAMPLE_EVIDENCE_JSON,
            confidence = 94,
            userConfirmed = true,
            body = SAMPLE_BODY,
            importance = 92,
            createdAtMillis = SAMPLE_CREATED_AT_MILLIS
        )
        return MemoryEventEntity(
            genesisCoreId = SAMPLE_GENESIS_CORE_ID,
            genesisCoreHash = SAMPLE_GENESIS_CORE_HASH,
            previousEventHash = previousEventHash,
            eventHash = eventHash,
            hashAlgorithm = MemoryIntegrityCore.HASH_ALGORITHM_SHA256,
            canonicalization = MemoryIntegrityCore.MEMORY_EVENT_CANONICALIZATION_V3,
            signatureAlgorithm = MemoryIntegrityCore.MEMORY_EVENT_SIGNATURE_ALGORITHM_UNSIGNED,
            eventSignature = null,
            eventType = "conversation.user_message",
            actor = "user",
            source = "chat",
            contextTag = SAMPLE_CONTEXT_TAG,
            privacyVisibility = SAMPLE_PRIVACY,
            memoryKind = "decision",
            tagsJson = "[\"memory\",\"decision\"]",
            evidenceJson = SAMPLE_EVIDENCE_JSON,
            confidence = 94,
            userConfirmed = true,
            body = SAMPLE_BODY,
            importance = 92,
            createdAtMillis = SAMPLE_CREATED_AT_MILLIS
        )
    }

    private fun sampleCapsule(previousCapsuleHash: String?): KnowledgeCapsuleEntity {
        val capsuleHash = core.hashCapsuleV2(
            genesisCoreId = SAMPLE_GENESIS_CORE_ID,
            capsuleId = "memory-architecture-v1",
            capsuleVersion = 1,
            capsuleCategory = "memory_architecture",
            capsuleType = "knowledge_capsule",
            status = "active",
            title = "Memory Architecture",
            source = "user_approved_notes",
            privacyVisibility = SAMPLE_PRIVACY,
            summary = "Knowledge capsules preserve stable learned procedures.",
            claimsJson = SAMPLE_CLAIMS_JSON,
            tags = SAMPLE_TAGS_JSON,
            evidenceJson = SAMPLE_CAPSULE_EVIDENCE_JSON,
            confidence = 92,
            sourceEventHash = "sha256:source-event",
            previousCapsuleHash = previousCapsuleHash,
            createdAtMillis = SAMPLE_CREATED_AT_MILLIS
        )
        return KnowledgeCapsuleEntity(
            capsuleId = "memory-architecture-v1",
            genesisCoreId = SAMPLE_GENESIS_CORE_ID,
            capsuleVersion = 1,
            capsuleCategory = "memory_architecture",
            capsuleType = "knowledge_capsule",
            status = "active",
            title = "Memory Architecture",
            source = "user_approved_notes",
            privacyVisibility = SAMPLE_PRIVACY,
            summary = "Knowledge capsules preserve stable learned procedures.",
            claimsJson = SAMPLE_CLAIMS_JSON,
            tags = SAMPLE_TAGS_JSON,
            evidenceJson = SAMPLE_CAPSULE_EVIDENCE_JSON,
            confidence = 92,
            sourceEventHash = "sha256:source-event",
            previousCapsuleHash = previousCapsuleHash,
            capsuleHash = capsuleHash,
            hashAlgorithm = MemoryIntegrityCore.HASH_ALGORITHM_SHA256,
            canonicalization = MemoryIntegrityCore.CAPSULE_CANONICALIZATION_V2,
            createdAtMillis = SAMPLE_CREATED_AT_MILLIS,
            updatedAtMillis = SAMPLE_CREATED_AT_MILLIS
        )
    }

    companion object {
        private const val SAMPLE_GENESIS_CORE_ID = "primary_genesis"
        private const val SAMPLE_GENESIS_CORE_HASH = "sha256:genesis-core-test"
        private const val SAMPLE_CONTEXT_TAG = "local_runtime"
        private const val SAMPLE_PRIVACY = "private_local"
        private const val SAMPLE_BODY = "Morimil recuerda que la memoria local vive en el telefono."
        private const val SAMPLE_EVIDENCE_JSON = "{\"schema\":\"morimil.memory_evidence.v1\",\"classifier\":\"test\"}"
        private const val SAMPLE_CLAIMS_JSON = "[{\"claim\":\"Capsules are stable procedural knowledge.\"}]"
        private const val SAMPLE_TAGS_JSON = "[\"memory\",\"capsule\"]"
        private const val SAMPLE_CAPSULE_EVIDENCE_JSON = "{\"schema\":\"morimil.knowledge_capsule_evidence.v1\"}"
        private const val SAMPLE_CREATED_AT_MILLIS = 1_720_000_000_000L
    }
}
