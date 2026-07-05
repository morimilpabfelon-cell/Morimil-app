package com.morimil.app.core.memory

import com.morimil.app.data.local.KnowledgeCapsuleEntity
import com.morimil.app.data.local.MemoryLinkEntity
import com.morimil.app.data.local.MigrationRecordEntity
import com.morimil.app.data.local.RecallScheduleEntity
import org.json.JSONArray

class MemoryOrganReconciliation {
    fun buildReport(
        validMemoryEventHashes: Set<String>,
        links: List<MemoryLinkEntity>,
        recalls: List<RecallScheduleEntity>,
        capsules: List<KnowledgeCapsuleEntity>,
        migrations: List<MigrationRecordEntity>,
        capsuleChainVerified: Boolean = true
    ): MemoryOrganReconciliationReport {
        val orphanedLinks = links.filter { link ->
            link.referencesMissingMemoryEvent(validMemoryEventHashes)
        }
        val orphanedRecalls = recalls.filter { recall ->
            recall.targetEventHash !in validMemoryEventHashes
        }
        val orphanedCapsules = capsules.filter { capsule ->
            val sourceEventHash = capsule.sourceEventHash
            sourceEventHash != null && sourceEventHash !in validMemoryEventHashes
        }
        val migrationMissingRefs = migrations.associate { record ->
            record.migrationId to record.referencedMemoryEventHashes()
                .filter { eventHash -> eventHash !in validMemoryEventHashes }
                .distinct()
        }.filterValues { missingRefs -> missingRefs.isNotEmpty() }

        return MemoryOrganReconciliationReport(
            scannedLinks = links.size,
            orphanedLinkIds = orphanedLinks.map { link -> link.linkId },
            scannedRecalls = recalls.size,
            orphanedRecallIds = orphanedRecalls.map { recall -> recall.recallId },
            scannedCapsules = capsules.size,
            orphanedCapsuleIds = orphanedCapsules.map { capsule -> capsule.capsuleId },
            capsuleChainVerified = capsuleChainVerified,
            scannedMigrations = migrations.size,
            migrationMissingRefs = migrationMissingRefs
        )
    }

    private fun MemoryLinkEntity.referencesMissingMemoryEvent(validMemoryEventHashes: Set<String>): Boolean {
        val sourceMissing = sourceType == MEMORY_EVENT_NODE_TYPE && sourceId !in validMemoryEventHashes
        val targetMissing = targetType == MEMORY_EVENT_NODE_TYPE && targetId !in validMemoryEventHashes
        return sourceMissing || targetMissing
    }

    private fun MigrationRecordEntity.referencedMemoryEventHashes(): List<String> {
        return parseJsonArray(affectedArtifactsJson) +
            listOfNotNull(preSnapshotId, postSnapshotId).filter { value -> value.isMemoryEventHashCandidate() }
    }

    private fun parseJsonArray(value: String): List<String> {
        return runCatching {
            val array = JSONArray(value)
            (0 until array.length()).mapNotNull { index ->
                array.optString(index).takeIf { item -> item.isMemoryEventHashCandidate() }
            }
        }.getOrDefault(emptyList())
    }

    private fun String.isMemoryEventHashCandidate(): Boolean {
        return startsWith("sha256:")
    }

    companion object {
        private const val MEMORY_EVENT_NODE_TYPE = "memory_event"
    }
}

data class MemoryOrganReconciliationReport(
    val scannedLinks: Int,
    val orphanedLinkIds: List<String>,
    val scannedRecalls: Int,
    val orphanedRecallIds: List<Long>,
    val scannedCapsules: Int,
    val orphanedCapsuleIds: List<String>,
    val capsuleChainVerified: Boolean = true,
    val scannedMigrations: Int,
    val migrationMissingRefs: Map<String, List<String>>,
    val markedOrphanedLinks: Int = 0,
    val degradedRecalls: Int = 0
) {
    val hasIssues: Boolean
        get() = orphanedLinkIds.isNotEmpty() ||
            orphanedRecallIds.isNotEmpty() ||
            orphanedCapsuleIds.isNotEmpty() ||
            !capsuleChainVerified ||
            migrationMissingRefs.isNotEmpty()

    fun toAuditNotes(): List<String> {
        return listOf(
            "organ_reconciliation:completed",
            "organ_reconciliation_scanned_links:$scannedLinks",
            "organ_reconciliation_orphaned_links:${orphanedLinkIds.size}",
            "organ_reconciliation_marked_orphaned_links:$markedOrphanedLinks",
            "organ_reconciliation_scanned_recalls:$scannedRecalls",
            "organ_reconciliation_orphaned_recalls:${orphanedRecallIds.size}",
            "organ_reconciliation_degraded_recalls:$degradedRecalls",
            "organ_reconciliation_scanned_capsules:$scannedCapsules",
            "organ_reconciliation_orphaned_capsules:${orphanedCapsuleIds.size}",
            "organ_reconciliation_capsule_chain_verified:$capsuleChainVerified",
            "organ_reconciliation_scanned_migrations:$scannedMigrations",
            "organ_reconciliation_migrations_with_missing_refs:${migrationMissingRefs.size}"
        )
    }
}
