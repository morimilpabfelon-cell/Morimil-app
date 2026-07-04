package com.morimil.app.core.memory

import com.morimil.app.data.local.KnowledgeCapsuleEntity

class CapsuleHasher(private val hasher: MemoryHasher = MemoryHasher()) {
    fun verifyCapsuleChain(capsules: List<KnowledgeCapsuleEntity>): Boolean {
        var expectedPreviousHash: String? = null
        capsules.forEach { capsule ->
            if (capsule.capsuleHash == LEGACY_CAPSULE_HASH) {
                expectedPreviousHash = capsule.capsuleHash
                return@forEach
            }
            if (capsuleIntegrityFailure(capsule, expectedPreviousHash) != null) return false
            expectedPreviousHash = capsule.capsuleHash
        }
        return true
    }

    fun capsuleIntegrityFailure(
        capsule: KnowledgeCapsuleEntity,
        expectedPreviousHash: String?
    ): String? {
        if (capsule.previousCapsuleHash != expectedPreviousHash) return "previous_capsule_hash_mismatch"
        if (capsule.hashAlgorithm != "sha256") return "unsupported_hash_algorithm:${capsule.hashAlgorithm}"
        val expectedHash = expectedCapsuleHash(capsule) ?: return "unknown_canonicalization:${capsule.canonicalization}"
        if (capsule.capsuleHash != expectedHash) return "capsule_hash_mismatch"
        return null
    }

    fun expectedCapsuleHash(capsule: KnowledgeCapsuleEntity): String? {
        return when (capsule.canonicalization) {
            CAPSULE_CANONICALIZATION_V1 -> hashCapsuleV1(capsule)
            CAPSULE_CANONICALIZATION_V2 -> hashCapsuleV2(
                genesisCoreId = capsule.genesisCoreId,
                capsuleId = capsule.capsuleId,
                capsuleVersion = capsule.capsuleVersion,
                capsuleCategory = capsule.capsuleCategory,
                capsuleType = capsule.capsuleType,
                status = capsule.status,
                title = capsule.title,
                source = capsule.source,
                privacyVisibility = capsule.privacyVisibility,
                summary = capsule.summary,
                claimsJson = capsule.claimsJson,
                tags = capsule.tags,
                evidenceJson = capsule.evidenceJson,
                confidence = capsule.confidence,
                sourceEventHash = capsule.sourceEventHash,
                previousCapsuleHash = capsule.previousCapsuleHash,
                createdAtMillis = capsule.createdAtMillis
            )
            else -> null
        }
    }

    fun hashCapsuleV1(capsule: KnowledgeCapsuleEntity): String {
        return hashFields(
            mapOf(
                "canonicalization" to CAPSULE_CANONICALIZATION_V1,
                "capsuleId" to capsule.capsuleId,
                "capsuleType" to capsule.capsuleType,
                "claimsJson" to capsule.claimsJson,
                "confidence" to capsule.confidence,
                "createdAtMillis" to capsule.createdAtMillis,
                "evidenceJson" to capsule.evidenceJson,
                "genesisCoreId" to capsule.genesisCoreId,
                "hashAlgorithm" to "sha256",
                "previousCapsuleHash" to capsule.previousCapsuleHash,
                "privacyVisibility" to capsule.privacyVisibility,
                "source" to capsule.source,
                "sourceEventHash" to capsule.sourceEventHash,
                "summary" to capsule.summary,
                "tags" to capsule.tags,
                "title" to capsule.title
            )
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
        return hashFields(
            mapOf(
                "canonicalization" to CAPSULE_CANONICALIZATION_V2,
                "capsuleCategory" to capsuleCategory,
                "capsuleId" to capsuleId,
                "capsuleType" to capsuleType,
                "capsuleVersion" to capsuleVersion,
                "claimsJson" to claimsJson,
                "confidence" to confidence,
                "createdAtMillis" to createdAtMillis,
                "evidenceJson" to evidenceJson,
                "genesisCoreId" to genesisCoreId,
                "hashAlgorithm" to "sha256",
                "previousCapsuleHash" to previousCapsuleHash,
                "privacyVisibility" to privacyVisibility,
                "source" to source,
                "sourceEventHash" to sourceEventHash,
                "status" to status,
                "summary" to summary,
                "tags" to tags,
                "title" to title
            )
        )
    }

    private fun hashFields(fields: Map<String, Any?>): String = hasher.hash(fields)

    companion object {
        const val LEGACY_CAPSULE_HASH = "sha256:legacy-unverified"
        const val CAPSULE_CANONICALIZATION_V1 = "morimil.knowledge_capsule_hash.v1"
        const val CAPSULE_CANONICALIZATION_V2 = "morimil.knowledge_capsule_hash.v2"
    }
}
