package com.morimil.app.core.memory

import com.morimil.app.data.local.MemoryEventEntity
import com.morimil.app.data.local.MemoryLinkEntity

data class MemoryGraphSnapshot(
    val selectedNodeId: String?,
    val nodes: List<MemoryGraphNode>,
    val edges: List<MemoryGraphEdge>
) {
    val isEmpty: Boolean = nodes.isEmpty()
}

data class MemoryGraphNode(
    val nodeId: String,
    val nodeType: String,
    val title: String,
    val subtitle: String,
    val memoryKind: String,
    val weight: Double,
    val selected: Boolean
)

data class MemoryGraphEdge(
    val sourceId: String,
    val targetId: String,
    val relation: String,
    val strength: Double,
    val reason: String
)

object MemoryGraphSnapshotBuilder {
    const val MEMORY_EVENT_NODE_TYPE = "memory_event"

    fun build(
        selectedEventHash: String?,
        events: List<MemoryEventEntity>,
        links: List<MemoryLinkEntity>,
        maxNodes: Int = 48
    ): MemoryGraphSnapshot {
        if (selectedEventHash.isNullOrBlank()) {
            return MemoryGraphSnapshot(
                selectedNodeId = null,
                nodes = emptyList(),
                edges = emptyList()
            )
        }

        val eventsByHash = events.associateBy { event -> event.eventHash }
        val nodeKeys = linkedSetOf(NodeKey(selectedEventHash, MEMORY_EVENT_NODE_TYPE))
        links.forEach { link ->
            nodeKeys += NodeKey(link.sourceId, link.sourceType)
            nodeKeys += NodeKey(link.targetId, link.targetType)
        }

        val limitedNodeKeys = nodeKeys
            .filter { key -> key.id.isNotBlank() && key.type.isNotBlank() }
            .take(maxNodes)
        val includedIds = limitedNodeKeys.map { key -> key.id }.toSet()
        val nodes = limitedNodeKeys.map { key ->
            val event = if (key.type == MEMORY_EVENT_NODE_TYPE) eventsByHash[key.id] else null
            MemoryGraphNode(
                nodeId = key.id,
                nodeType = key.type,
                title = event?.let { memoryEventTitle(it) } ?: key.type,
                subtitle = event?.body?.singleLinePreview(120) ?: key.id.take(32),
                memoryKind = event?.memoryKind ?: key.type,
                weight = event?.importanceConfidenceWeight() ?: EXTERNAL_NODE_WEIGHT,
                selected = key.id == selectedEventHash && key.type == MEMORY_EVENT_NODE_TYPE
            )
        }.sortedWith(
            compareByDescending<MemoryGraphNode> { node -> node.selected }
                .thenByDescending { node -> node.weight }
                .thenBy { node -> node.title }
        )

        val edges = links
            .filter { link -> link.sourceId in includedIds && link.targetId in includedIds }
            .distinctBy { link -> "${link.sourceType}|${link.sourceId}|${link.targetType}|${link.targetId}|${link.relation}" }
            .map { link ->
                MemoryGraphEdge(
                    sourceId = link.sourceId,
                    targetId = link.targetId,
                    relation = link.relation,
                    strength = link.strength.coerceIn(0.0, 1.0),
                    reason = link.reason
                )
            }
            .sortedWith(
                compareByDescending<MemoryGraphEdge> { edge -> edge.strength }
                    .thenBy { edge -> edge.relation }
            )

        return MemoryGraphSnapshot(
            selectedNodeId = selectedEventHash,
            nodes = nodes,
            edges = edges
        )
    }

    private fun memoryEventTitle(event: MemoryEventEntity): String {
        return "${event.memoryKind} / ${event.eventType}"
    }

    private fun MemoryEventEntity.importanceConfidenceWeight(): Double {
        val importanceScore = importance.coerceIn(0, 100) / 100.0
        val confidenceScore = confidence.coerceIn(0, 100) / 100.0
        return (importanceScore * 0.65) + (confidenceScore * 0.35)
    }

    private fun String.singleLinePreview(maxLength: Int): String {
        return replace('\n', ' ').replace('\r', ' ').take(maxLength)
    }

    private data class NodeKey(
        val id: String,
        val type: String
    )

    private const val EXTERNAL_NODE_WEIGHT = 0.35
}
