package com.morimil.app.data.repository

import com.morimil.app.core.memory.MemoryIntegrityCore
import com.morimil.app.data.local.AutobiographicalSnapshotEntity
import com.morimil.app.data.local.KnowledgeCapsuleEntity
import com.morimil.app.data.local.MemoryOrganDatabase
import kotlinx.coroutines.flow.Flow

class MemoryOrganRepository(
    database: MemoryOrganDatabase,
    private val memoryIntegrityCore: MemoryIntegrityCore
) {
    private val dao = database.memoryOrganDao()

    val selfSnapshot: Flow<AutobiographicalSnapshotEntity?> = dao.observeCurrentSelfSnapshot()
    val knowledgeCapsules: Flow<List<KnowledgeCapsuleEntity>> = dao.observeRecentKnowledgeCapsules()

    suspend fun auditKnowledgeCapsuleChain(): Boolean {
        return memoryIntegrityCore.verifyCapsuleChain(dao.loadKnowledgeCapsuleChain())
    }

    suspend fun updateSelfSnapshot(
        genesisCoreId: String,
        alias: String,
        selfSummary: String,
        stableTraits: String,
        activeGoals: String,
        importantConstraints: String,
        sourceEventHash: String?
    ) {
        dao.upsertSelfSnapshot(
            AutobiographicalSnapshotEntity(
                snapshotId = "current",
                genesisCoreId = genesisCoreId,
                alias = alias,
                selfSummary = selfSummary.trim(),
                stableTraits = stableTraits.trim(),
                activeGoals = activeGoals.trim(),
                importantConstraints = importantConstraints.trim(),
                sourceEventHash = sourceEventHash,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun captureKnowledgeCapsuleFromText(
        genesisCoreId: String,
        text: String,
        sourceEventHash: String? = null
    ): KnowledgeCapsuleEntity? {
        val clean = text.trim()
        if (!KnowledgeIntakeClassifier.hasExplicitCapsuleIntent(clean)) return null

        val title = KnowledgeIntakeClassifier.inferTitle(clean)
        val category = KnowledgeIntakeClassifier.inferCategory(clean)
        val tags = KnowledgeIntakeClassifier.inferTags(clean, category)
        val claims = KnowledgeIntakeClassifier.inferClaims(clean)
        if (claims.isEmpty() && clean.length < 120) return null

        return appendKnowledgeCapsule(
            genesisCoreId = genesisCoreId,
            capsuleCategory = category,
            capsuleType = "knowledge_capsule",
            title = title,
            source = "user_approved_notes",
            privacyVisibility = "private_local",
            summary = clean.take(1600),
            claims = claims,
            tags = tags,
            confidence = 92,
            sourceEventHash = sourceEventHash
        )
    }

    suspend fun appendKnowledgeCapsule(
        genesisCoreId: String,
        capsuleCategory: String,
        capsuleType: String = "knowledge_capsule",
        title: String,
        source: String = "user_approved_notes",
        privacyVisibility: String = "private_local",
        summary: String,
        claims: List<String> = emptyList(),
        tags: List<String> = emptyList(),
        confidence: Int,
        sourceEventHash: String?
    ): KnowledgeCapsuleEntity {
        val cleanTitle = title.trim().ifBlank { "untitled" }
        val now = System.currentTimeMillis()
        val existingChain = dao.loadKnowledgeCapsuleChain()
        require(memoryIntegrityCore.verifyCapsuleChain(existingChain)) {
            "Knowledge capsule chain integrity failed. Refusing to write a new capsule."
        }

        val versionsForTitle = dao.loadCapsulesByTitle(cleanTitle)
        val nextVersion = (versionsForTitle.maxOfOrNull { it.capsuleVersion } ?: 0) + 1
        val previousCapsuleHash = existingChain.lastOrNull()?.capsuleHash
        val cleanClaims = KnowledgeIntakeClassifier.buildClaimsJson(claims)
        val cleanTags = KnowledgeIntakeClassifier.buildTagsJson(tags)
        val cleanSummary = summary.trim()
        val cleanConfidence = confidence.coerceIn(1, 100)
        val capsuleId = "${KnowledgeIntakeClassifier.slug(cleanTitle)}-v$nextVersion"
        val evidenceJson = KnowledgeIntakeClassifier.buildEvidenceJson(
            source = source,
            sourceEventHash = sourceEventHash,
            summary = cleanSummary
        )
        val capsuleHash = memoryIntegrityCore.hashCapsuleV2(
            genesisCoreId = genesisCoreId,
            capsuleId = capsuleId,
            capsuleVersion = nextVersion,
            capsuleCategory = capsuleCategory,
            capsuleType = capsuleType,
            status = "active",
            title = cleanTitle,
            source = source,
            privacyVisibility = privacyVisibility,
            summary = cleanSummary,
            claimsJson = cleanClaims,
            tags = cleanTags,
            evidenceJson = evidenceJson,
            confidence = cleanConfidence,
            sourceEventHash = sourceEventHash,
            previousCapsuleHash = previousCapsuleHash,
            createdAtMillis = now
        )

        if (versionsForTitle.any { it.status == "active" }) {
            dao.markActiveCapsulesSuperseded(cleanTitle, now)
        }
        val capsule = KnowledgeCapsuleEntity(
            capsuleId = capsuleId,
            genesisCoreId = genesisCoreId,
            capsuleVersion = nextVersion,
            capsuleCategory = capsuleCategory,
            capsuleType = capsuleType,
            status = "active",
            title = cleanTitle,
            source = source,
            privacyVisibility = privacyVisibility,
            summary = cleanSummary,
            claimsJson = cleanClaims,
            tags = cleanTags,
            evidenceJson = evidenceJson,
            confidence = cleanConfidence,
            sourceEventHash = sourceEventHash,
            previousCapsuleHash = previousCapsuleHash,
            capsuleHash = capsuleHash,
            hashAlgorithm = MemoryIntegrityCore.HASH_ALGORITHM_SHA256,
            canonicalization = MemoryIntegrityCore.CAPSULE_CANONICALIZATION_V2,
            createdAtMillis = now,
            updatedAtMillis = now
        )
        dao.insertKnowledgeCapsule(capsule)
        return capsule
    }

    suspend fun buildKnowledgeCapsuleContext(limit: Int = 8): String {
        val capsules = dao.loadKnowledgeCapsules(limit)
        if (capsules.isEmpty()) {
            return "No knowledge capsules yet."
        }
        return capsules.joinToString("\n") { capsule ->
            "- [${capsule.capsuleCategory}/${capsule.status}/v${capsule.capsuleVersion}/${capsule.privacyVisibility}/c${capsule.confidence}/${capsule.capsuleHash.take(19)}] " +
                "${capsule.title}: ${capsule.summary.take(500)} claims=${capsule.claimsJson.take(420)} tags=${capsule.tags} evidence=${capsule.evidenceJson.take(320)}"
        }
    }
}
