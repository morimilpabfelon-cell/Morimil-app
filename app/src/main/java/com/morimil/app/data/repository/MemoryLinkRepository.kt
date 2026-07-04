package com.morimil.app.data.repository

import com.morimil.app.data.local.MemoryEventEntity
import com.morimil.app.data.local.MemoryLinkEntity
import com.morimil.app.data.local.MemoryOrganDatabase
import kotlinx.coroutines.flow.Flow
import kotlin.math.roundToInt

class MemoryLinkRepository(organDatabase: MemoryOrganDatabase) {
    private val organDao = organDatabase.memoryOrganDao()

    val recentMemoryLinks: Flow<List<MemoryLinkEntity>> = organDao.observeRecentMemoryLinks(RECENT_LINK_LIMIT)

    fun observeMemoryLinksForEvent(eventHash: String): Flow<List<MemoryLinkEntity>> {
        return organDao.observeMemoryLinksForNode(
            nodeId = eventHash,
            nodeType = MEMORY_EVENT_NODE_TYPE,
            limit = RECENT_LINK_LIMIT
        )
    }

    suspend fun createMemoryLink(
        instanceId: String,
        genesisCoreHash: String,
        sourceId: String,
        sourceType: String,
        targetId: String,
        targetType: String,
        relation: String,
        strength: Double,
        reason: String,
        createdBy: String = CREATED_BY_LOCAL_RUNTIME,
        createdAtMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val insertedId = organDao.insertMemoryLink(
            MemoryLinkEntity(
                linkId = buildMemoryLinkId(createdAtMillis, sourceId, targetId, relation),
                instanceId = instanceId,
                genesisCoreHash = genesisCoreHash,
                sourceId = sourceId,
                sourceType = sourceType,
                targetId = targetId,
                targetType = targetType,
                relation = relation,
                strength = strength.coerceIn(0.0, 1.0),
                reason = reason.ifBlank { "local_memory_link_v1" },
                createdBy = createdBy,
                privacyVisibility = PRIVATE_LOCAL,
                cloudSyncAllowed = false,
                exportAllowed = false,
                verificationState = VERIFICATION_VALID,
                createdAtMillis = createdAtMillis
            )
        )
        return insertedId > 0
    }

    suspend fun linkRestCycleToEvents(
        instanceId: String,
        genesisCoreHash: String,
        restCycleEventHash: String,
        sourceEvents: List<MemoryEventEntity>,
        createdAtMillis: Long = System.currentTimeMillis()
    ): Int {
        val links = sourceEvents
            .filter { event -> event.eventHash != restCycleEventHash }
            .distinctBy { event -> event.eventHash }
            .take(REST_CYCLE_LINK_LIMIT)
            .mapIndexed { index, event ->
                MemoryLinkEntity(
                    linkId = buildMemoryLinkId(
                        createdAtMillis = createdAtMillis + index,
                        sourceId = restCycleEventHash,
                        targetId = event.eventHash,
                        relation = RELATION_DERIVED_FROM
                    ),
                    instanceId = instanceId,
                    genesisCoreHash = genesisCoreHash,
                    sourceId = restCycleEventHash,
                    sourceType = MEMORY_EVENT_NODE_TYPE,
                    targetId = event.eventHash,
                    targetType = MEMORY_EVENT_NODE_TYPE,
                    relation = RELATION_DERIVED_FROM,
                    strength = event.linkStrength(),
                    reason = "rest_cycle_consolidated:${event.memoryKind}/i${event.importance}/c${event.confidence}",
                    createdBy = CREATED_BY_REST_CYCLE,
                    privacyVisibility = PRIVATE_LOCAL,
                    cloudSyncAllowed = false,
                    exportAllowed = false,
                    verificationState = VERIFICATION_VALID,
                    createdAtMillis = createdAtMillis + index
                )
            }

        return organDao.insertMemoryLinks(links).count { rowId -> rowId > 0 }
    }

    private fun MemoryEventEntity.linkStrength(): Double {
        val weighted = ((importance.coerceIn(0, 100) * 0.6) + (confidence.coerceIn(0, 100) * 0.4)) / 100.0
        return (weighted * 100.0).roundToInt() / 100.0
    }

    companion object {
        const val MEMORY_EVENT_NODE_TYPE = "memory_event"
        const val RELATION_DERIVED_FROM = "derived_from"

        private const val CREATED_BY_LOCAL_RUNTIME = "local_runtime"
        private const val CREATED_BY_REST_CYCLE = "rest_cycle"
        private const val PRIVATE_LOCAL = "private_local"
        private const val VERIFICATION_VALID = "valid"
        private const val RECENT_LINK_LIMIT = 50
        private const val REST_CYCLE_LINK_LIMIT = 24

        fun buildMemoryLinkId(
            createdAtMillis: Long,
            sourceId: String,
            targetId: String,
            relation: String
        ): String {
            val suffix = "$sourceId|$targetId|$relation"
                .fold(0L) { acc, char -> ((acc * 31) + char.code.toLong()).and(0x7FFFFFFFL) }
                .toString()
                .padStart(10, '0')
            return "mlink_$createdAtMillis$suffix"
        }
    }
}
