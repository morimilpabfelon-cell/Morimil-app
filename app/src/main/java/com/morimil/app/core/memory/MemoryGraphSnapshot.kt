package com.morimil.app.core.memory

import com.morimil.app.data.local.MemoryEventEntity
import com.morimil.app.data.local.MemoryLinkEntity

data class MemoryGraphSnapshot(
    val selectedNodeId: String?,
    val nodes: List<MemoryGraphNode>,
    val edges: List<MemoryGraphEdge>
) {
    val isEmpty: Boolean = nodes.isEmpty()
    val memoryEventNodeCount: Int =
        nodes.count { node -> node.nodeType == MemoryGraphSnapshotBuilder.MEMORY_EVENT_NODE_TYPE }
    val externalNodeCount: Int = nodes.size - memoryEventNodeCount
    val orphanedEdgeCount: Int = edges.count { edge -> edge.verificationState == "orphaned" }
    val focused: Boolean = selectedNodeId != null
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
    val sourceType: String,
    val targetId: String,
    val targetType: String,
    val relation: String,
    val strength: Double,
    val reason: String,
    val verificationState: String
)

object MemoryGraphSnapshotBuilder {
    const val MEMORY_EVENT_NODE_TYPE = "memory_event"

    fun build(
        selectedEventHash: String?,
        events: List<MemoryEventEntity>,
        links: List<MemoryLinkEntity>,
        maxNodes: Int = 48
    ): MemoryGraphSnapshot {
        val eventsByHash = events.associateBy { event -> event.eventHash }
        val selectedHash = selectedEventHash?.takeIf { hash -> hash.isNotBlank() }
        val nodeKeys = linkedSetOf<NodeKey>()
        if (selectedHash != null) {
            nodeKeys += NodeKey(selectedHash, MEMORY_EVENT_NODE_TYPE)
        }
        links.forEach { link ->
            nodeKeys += NodeKey(link.sourceId, link.sourceType)
            nodeKeys += NodeKey(link.targetId, link.targetType)
        }
        if (selectedHash == null && nodeKeys.isEmpty()) {
            events.sortedByDescending { event -> event.createdAtMillis }
                .take(maxNodes)
                .forEach { event -> nodeKeys += NodeKey(event.eventHash, MEMORY_EVENT_NODE_TYPE) }
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
                selected = key.id == selectedHash && key.type == MEMORY_EVENT_NODE_TYPE
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
                    sourceType = link.sourceType,
                    targetId = link.targetId,
                    targetType = link.targetType,
                    relation = link.relation,
                    strength = link.strength.coerceIn(0.0, 1.0),
                    reason = link.reason,
                    verificationState = link.verificationState
                )
            }
            .sortedWith(
                compareByDescending<MemoryGraphEdge> { edge -> if (edge.verificationState == "orphaned") 1 else 0 }
                    .thenByDescending { edge -> edge.strength }
                    .thenBy { edge -> edge.relation }
            )

        return MemoryGraphSnapshot(
            selectedNodeId = selectedHash,
            nodes = nodes,
            edges = edges
        )
    }

    fun connectionsForNode(snapshot: MemoryGraphSnapshot, nodeId: String): List<MemoryGraphEdge> {
        return snapshot.edges
            .filter { edge -> edge.sourceId == nodeId || edge.targetId == nodeId }
            .sortedWith(
                compareByDescending<MemoryGraphEdge> { edge -> edge.strength }
                    .thenBy { edge -> edge.relation }
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
