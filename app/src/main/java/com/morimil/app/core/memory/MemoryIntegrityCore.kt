package com.morimil.app.core.memory

import com.morimil.app.data.local.KnowledgeCapsuleEntity
import com.morimil.app.data.local.MemoryEventEntity

class MemoryIntegrityCore(
    signatureVerifier: MemoryEventSignatureVerifier = UnsignedOnlyMemoryEventSignatureVerifier,
    signatureEpochPolicy: MemorySignatureEpochPolicy = NoopMemorySignatureEpochPolicy,
    private val eventIntegrity: MemoryEventIntegrity = MemoryEventIntegrity(
        signatureVerifier = signatureVerifier,
        signatureEpochPolicy = signatureEpochPolicy
    ),
    private val capsuleIntegrity: CapsuleHasher = CapsuleHasher(),
    private val verifier: MemoryIntegrityVerifier = MemoryIntegrityVerifier(eventIntegrity)
) {
    fun verifyMemoryEventChain(
        events: List<MemoryEventEntity>,
        requireGenesisStart: Boolean = true
    ): Boolean {
        return verifier.verifyMemoryEventChain(
            events = events,
            requireGenesisStart = requireGenesisStart
        )
    }

    fun inspectMemoryEventTail(
        events: List<MemoryEventEntity>,
        fallbackPreviousHash: String?
    ): MemoryTailIntegrity {
        return verifier.inspectMemoryEventTail(
            events = events,
            fallbackPreviousHash = fallbackPreviousHash
        )
    }

    fun memoryEventIntegrityFailure(
        event: MemoryEventEntity,
        expectedPreviousHash: String?
    ): String? {
        return eventIntegrity.memoryEventIntegrityFailure(
            event = event,
            expectedPreviousHash = expectedPreviousHash
        )
    }

    fun hashMemoryEventV3(
        genesisCoreId: String,
        genesisCoreHash: String,
        previousEventHash: String?,
        eventType: String,
        actor: String,
        source: String,
        contextTag: String,
        privacyVisibility: String,
        memoryKind: String,
        tagsJson: String,
        evidenceJson: String,
        confidence: Int,
        userConfirmed: Boolean,
        body: String,
        importance: Int,
        createdAtMillis: Long
    ): String {
        return eventIntegrity.hashMemoryEventV3(
            genesisCoreId = genesisCoreId,
            genesisCoreHash = genesisCoreHash,
            previousEventHash = previousEventHash,
            eventType = eventType,
            actor = actor,
            source = source,
            contextTag = contextTag,
            privacyVisibility = privacyVisibility,
            memoryKind = memoryKind,
            tagsJson = tagsJson,
            evidenceJson = evidenceJson,
            confidence = confidence,
            userConfirmed = userConfirmed,
            body = body,
            importance = importance,
            createdAtMillis = createdAtMillis
        )
    }

    fun verifyCapsuleChain(capsules: List<KnowledgeCapsuleEntity>): Boolean {
        return capsuleIntegrity.verifyCapsuleChain(capsules)
    }

    fun capsuleIntegrityFailure(
        capsule: KnowledgeCapsuleEntity,
        expectedPreviousHash: String?
    ): String? {
        return capsuleIntegrity.capsuleIntegrityFailure(
            capsule = capsule,
            expectedPreviousHash = expectedPreviousHash
        )
    }

    fun hashCapsuleV2(
        genesisCoreId: String,
        capsuleId: String,
        capsuleVersion: Int,
        capsuleCategory: String,
        capsuleType: String,
        status: String,
        title: String,
        source: String,
        privacyVisibility: String,
        summary: String,
        claimsJson: String,
        tags: String,
        evidenceJson: String,
        confidence: Int,
        sourceEventHash: String?,
        previousCapsuleHash: String?,
        createdAtMillis: Long
    ): String {
        return capsuleIntegrity.hashCapsuleV2(
            genesisCoreId = genesisCoreId,
            capsuleId = capsuleId,
            capsuleVersion = capsuleVersion,
            capsuleCategory = capsuleCategory,
            capsuleType = capsuleType,
            status = status,
            title = title,
            source = source,
            privacyVisibility = privacyVisibility,
            summary = summary,
            claimsJson = claimsJson,
            tags = tags,
            evidenceJson = evidenceJson,
            confidence = confidence,
            sourceEventHash = sourceEventHash,
            previousCapsuleHash = previousCapsuleHash,
            createdAtMillis = createdAtMillis
        )
    }

    companion object {
        const val HASH_ALGORITHM_SHA256 = "sha256"
        const val LEGACY_EVENT_HASH = "sha256:legacy-unverified"
        const val LEGACY_CAPSULE_HASH = "sha256:legacy-unverified"
        const val MEMORY_EVENT_CANONICALIZATION_V3 = "morimil.memory_event_hash.v3"
        const val CAPSULE_CANONICALIZATION_V2 = "morimil.knowledge_capsule_hash.v2"
        const val MEMORY_EVENT_SIGNATURE_ALGORITHM_UNSIGNED = "unsigned_runtime_v1"
        const val MEMORY_EVENT_SIGNATURE_ALGORITHM_ANDROID_KEYSTORE_EC =
            "android_keystore_ec_p256_sha256_ecdsa_v1"
    }
}
