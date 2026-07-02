package com.morimil.app.data.repository

import com.morimil.app.data.local.AutobiographicalSnapshotEntity
import com.morimil.app.data.local.KnowledgeCapsuleEntity
import com.morimil.app.data.local.MemoryOrganDatabase
import kotlinx.coroutines.flow.Flow

class MemoryOrganRepository(database: MemoryOrganDatabase) {
    private val dao = database.memoryOrganDao()

    val selfSnapshot: Flow<AutobiographicalSnapshotEntity?> = dao.observeCurrentSelfSnapshot()
    val knowledgeCapsules: Flow<List<KnowledgeCapsuleEntity>> = dao.observeKnowledgeCapsules()

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

    suspend fun upsertKnowledgeCapsule(
        genesisCoreId: String,
        title: String,
        summary: String,
        tags: String,
        confidence: Int,
        sourceEventHash: String?
    ) {
        val cleanTitle = title.trim().ifBlank { "untitled" }
        val now = System.currentTimeMillis()
        dao.upsertKnowledgeCapsule(
            KnowledgeCapsuleEntity(
                capsuleId = cleanTitle.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-'),
                genesisCoreId = genesisCoreId,
                title = cleanTitle,
                summary = summary.trim(),
                tags = tags.trim(),
                confidence = confidence.coerceIn(1, 100),
                sourceEventHash = sourceEventHash,
                createdAtMillis = now,
                updatedAtMillis = now
            )
        )
    }
}
