package com.morimil.app.core.memory

import com.morimil.app.data.local.KnowledgeCapsuleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapsuleHasherTest {
    private val hasher = CapsuleHasher()

    @Test
    fun v2HashIsDeterministic() {
        val first = sampleCapsule(previousCapsuleHash = null).capsuleHash
        val second = sampleCapsule(previousCapsuleHash = null).capsuleHash

        assertEquals(first, second)
        assertTrue(first.startsWith("sha256:"))
    }

    @Test
    fun tamperedSummaryFailsIntegrity() {
        val capsule = sampleCapsule(previousCapsuleHash = null)
        val tampered = capsule.copy(summary = "Different stable knowledge.")

        assertNull(hasher.capsuleIntegrityFailure(capsule, expectedPreviousHash = null))
        assertEquals("capsule_hash_mismatch", hasher.capsuleIntegrityFailure(tampered, expectedPreviousHash = null))
    }

    @Test
    fun chainVerificationAcceptsValidChainAndRejectsBrokenLink() {
        val first = sampleCapsule(previousCapsuleHash = null)
        val second = sampleCapsule(
            capsuleId = "memory-architecture-v2",
            capsuleVersion = 2,
            previousCapsuleHash = first.capsuleHash,
            title = "Memory Architecture",
            summary = "Knowledge capsules are chained after memory events."
        )
        val brokenSecond = second.copy(previousCapsuleHash = "sha256:wrong-previous")

        assertTrue(hasher.verifyCapsuleChain(listOf(first, second)))
        assertFalse(hasher.verifyCapsuleChain(listOf(first, brokenSecond)))
    }

    private fun sampleCapsule(
        capsuleId: String = "memory-architecture-v1",
        capsuleVersion: Int = 1,
        previousCapsuleHash: String?,
        title: String = "Memory Architecture",
        summary: String = "Knowledge capsules preserve stable learned procedures.",
        createdAtMillis: Long = 1_720_000_000_000L
    ): KnowledgeCapsuleEntity {
        val capsuleHash = hasher.hashCapsuleV2(
            genesisCoreId = SAMPLE_GENESIS_CORE_ID,
            capsuleId = capsuleId,
            capsuleVersion = capsuleVersion,
            capsuleCategory = "memory_architecture",
            capsuleType = "knowledge_capsule",
            status = "active",
            title = title,
            source = "user_approved_notes",
            privacyVisibility = "private_local",
            summary = summary,
            claimsJson = SAMPLE_CLAIMS_JSON,
            tags = SAMPLE_TAGS_JSON,
            evidenceJson = SAMPLE_EVIDENCE_JSON,
            confidence = 92,
            sourceEventHash = SAMPLE_SOURCE_EVENT_HASH,
            previousCapsuleHash = previousCapsuleHash,
            createdAtMillis = createdAtMillis
        )
        return KnowledgeCapsuleEntity(
            capsuleId = capsuleId,
            genesisCoreId = SAMPLE_GENESIS_CORE_ID,
            capsuleVersion = capsuleVersion,
            capsuleCategory = "memory_architecture",
            capsuleType = "knowledge_capsule",
            status = "active",
            title = title,
            source = "user_approved_notes",
            privacyVisibility = "private_local",
            summary = summary,
            claimsJson = SAMPLE_CLAIMS_JSON,
            tags = SAMPLE_TAGS_JSON,
            evidenceJson = SAMPLE_EVIDENCE_JSON,
            confidence = 92,
            sourceEventHash = SAMPLE_SOURCE_EVENT_HASH,
            previousCapsuleHash = previousCapsuleHash,
            capsuleHash = capsuleHash,
            hashAlgorithm = "sha256",
            canonicalization = CapsuleHasher.CAPSULE_CANONICALIZATION_V2,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = createdAtMillis
        )
    }

    companion object {
        private const val SAMPLE_GENESIS_CORE_ID = "primary_genesis"
        private const val SAMPLE_SOURCE_EVENT_HASH = "sha256:source-event"
        private const val SAMPLE_CLAIMS_JSON = "[{\"claim\":\"Capsules are stable procedural knowledge.\"}]"
        private const val SAMPLE_TAGS_JSON = "[\"memory\",\"capsule\"]"
        private const val SAMPLE_EVIDENCE_JSON = "{\"schema\":\"morimil.knowledge_capsule_evidence.v1\"}"
    }
}
