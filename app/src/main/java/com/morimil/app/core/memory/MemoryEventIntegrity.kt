package com.morimil.app.core.memory

import com.morimil.app.data.local.MemoryEventEntity

class MemoryEventIntegrity(
    private val hasher: MemoryHasher = MemoryHasher(),
    private val signatureVerifier: MemoryEventSignatureVerifier = UnsignedOnlyMemoryEventSignatureVerifier,
    private val signatureEpochPolicy: MemorySignatureEpochPolicy = NoopMemorySignatureEpochPolicy
) {
    fun verifyMemoryEventChain(
        events: List<MemoryEventEntity>,
        requireGenesisStart: Boolean = true
    ): Boolean {
        var expectedPreviousHash = if (requireGenesisStart) null else events.firstOrNull()?.previousEventHash
        val requiredSignatureEpochHash = signatureEpochPolicy.signedEpochEventHash()
        val signingKeyExists = signatureEpochPolicy.signingKeyExists()
        var signatureEpochReached = false
        var signatureEpochFound = requiredSignatureEpochHash == null
        var signedEventFound = false
        events.forEach { event ->
            if (event.eventHash == LEGACY_EVENT_HASH) {
                if (signatureEpochReached) return false
                expectedPreviousHash = event.eventHash
                return@forEach
            }
            if (memoryEventIntegrityFailure(event, expectedPreviousHash) != null) return false
            val eventIsSigned = event.hasKeystoreSignature()
            if (eventIsSigned) signedEventFound = true
            when {
                requiredSignatureEpochHash != null && event.eventHash == requiredSignatureEpochHash -> {
                    if (!eventIsSigned) return false
                    signatureEpochReached = true
                    signatureEpochFound = true
                }
                signatureEpochReached && !eventIsSigned -> return false
                requiredSignatureEpochHash == null && eventIsSigned -> {
                    signatureEpochReached = true
                    signatureEpochFound = true
                }
            }
            expectedPreviousHash = event.eventHash
        }
        if (requireGenesisStart && signingKeyExists && requiredSignatureEpochHash == null && !signedEventFound) {
            return false
        }
        return signatureEpochFound || !requireGenesisStart
    }

    fun memoryEventIntegrityFailure(
        event: MemoryEventEntity,
        expectedPreviousHash: String?
    ): String? {
        if (event.previousEventHash != expectedPreviousHash) return "previous_hash_mismatch"
        if (event.hashAlgorithm != "sha256") return "unsupported_hash_algorithm:${event.hashAlgorithm}"
        val expectedHash = expectedMemoryEventHash(event) ?: return "unknown_canonicalization:${event.canonicalization}"
        if (event.eventHash != expectedHash) return "event_hash_mismatch"
        val signatureFailure = signatureVerifier.signatureIntegrityFailure(
            eventHash = event.eventHash,
            signatureAlgorithm = event.signatureAlgorithm,
            eventSignature = event.eventSignature
        )
        if (signatureFailure != null) return signatureFailure
        return null
    }

    fun expectedMemoryEventHash(event: MemoryEventEntity): String? {
        return when (event.canonicalization) {
            MEMORY_EVENT_CANONICALIZATION_V1 -> hashMemoryEventV1(
                genesisCoreId = event.genesisCoreId,
                genesisCoreHash = event.genesisCoreHash,
                previousEventHash = event.previousEventHash,
                eventType = event.eventType,
                actor = event.actor,
                body = event.body,
                importance = event.importance,
                createdAtMillis = event.createdAtMillis
            )
            MEMORY_EVENT_CANONICALIZATION_V2 -> hashMemoryEventV2(
                genesisCoreId = event.genesisCoreId,
                genesisCoreHash = event.genesisCoreHash,
                previousEventHash = event.previousEventHash,
                eventType = event.eventType,
                actor = event.actor,
                source = event.source,
                contextTag = event.contextTag,
                privacyVisibility = event.privacyVisibility,
                body = event.body,
                importance = event.importance,
                createdAtMillis = event.createdAtMillis
            )
            MEMORY_EVENT_CANONICALIZATION_V3 -> hashMemoryEventV3(
                genesisCoreId = event.genesisCoreId,
                genesisCoreHash = event.genesisCoreHash,
                previousEventHash = event.previousEventHash,
                eventType = event.eventType,
                actor = event.actor,
                source = event.source,
                contextTag = event.contextTag,
                privacyVisibility = event.privacyVisibility,
                memoryKind = event.memoryKind,
                tagsJson = event.tagsJson,
                evidenceJson = event.evidenceJson,
                confidence = event.confidence,
                userConfirmed = event.userConfirmed,
                body = event.body,
                importance = event.importance,
                createdAtMillis = event.createdAtMillis
            )
            else -> null
        }
    }

    fun hashMemoryEventV1(
        genesisCoreId: String,
        genesisCoreHash: String,
        previousEventHash: String?,
        eventType: String,
        actor: String,
        body: String,
        importance: Int,
        createdAtMillis: Long
    ): String {
        return hashFields(
            mapOf(
                "actor" to actor,
                "body" to body,
                "canonicalization" to MEMORY_EVENT_CANONICALIZATION_V1,
                "createdAtMillis" to createdAtMillis,
                "eventType" to eventType,
                "genesisCoreId" to genesisCoreId,
                "genesisCoreHash" to genesisCoreHash,
                "hashAlgorithm" to "sha256",
                "importance" to importance,
                "previousEventHash" to previousEventHash
            )
        )
    }

    fun hashMemoryEventV2(
        genesisCoreId: String,
        genesisCoreHash: String,
        previousEventHash: String?,
        eventType: String,
        actor: String,
        source: String,
        contextTag: String,
        privacyVisibility: String,
        body: String,
        importance: Int,
        createdAtMillis: Long
    ): String {
        return hashFields(
            mapOf(
                "actor" to actor,
                "body" to body,
                "canonicalization" to MEMORY_EVENT_CANONICALIZATION_V2,
                "contextTag" to contextTag,
                "createdAtMillis" to createdAtMillis,
                "eventType" to eventType,
                "genesisCoreId" to genesisCoreId,
                "genesisCoreHash" to genesisCoreHash,
                "hashAlgorithm" to "sha256",
                "importance" to importance,
                "previousEventHash" to previousEventHash,
                "privacyVisibility" to privacyVisibility,
                "source" to source
            )
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
        return hashFields(
            mapOf(
                "actor" to actor,
                "body" to body,
                "canonicalization" to MEMORY_EVENT_CANONICALIZATION_V3,
                "confidence" to confidence,
                "contextTag" to contextTag,
                "createdAtMillis" to createdAtMillis,
                "eventType" to eventType,
                "evidenceJson" to evidenceJson,
                "genesisCoreId" to genesisCoreId,
                "genesisCoreHash" to genesisCoreHash,
                "hashAlgorithm" to "sha256",
                "importance" to importance,
                "memoryKind" to memoryKind,
                "previousEventHash" to previousEventHash,
                "privacyVisibility" to privacyVisibility,
                "source" to source,
                "tagsJson" to tagsJson,
                "userConfirmed" to userConfirmed
            )
        )
    }

    private fun hashFields(fields: Map<String, Any?>): String = hasher.hash(fields)

    companion object {
        const val LEGACY_EVENT_HASH = "sha256:legacy-unverified"
        const val MEMORY_EVENT_CANONICALIZATION_V1 = "morimil.memory_event_hash.v1"
        const val MEMORY_EVENT_CANONICALIZATION_V2 = "morimil.memory_event_hash.v2"
        const val MEMORY_EVENT_CANONICALIZATION_V3 = "morimil.memory_event_hash.v3"
    }

    private fun MemoryEventEntity.hasKeystoreSignature(): Boolean {
        return signatureAlgorithm == MemoryIntegrityCore.MEMORY_EVENT_SIGNATURE_ALGORITHM_ANDROID_KEYSTORE_EC &&
            !eventSignature.isNullOrBlank()
    }
}