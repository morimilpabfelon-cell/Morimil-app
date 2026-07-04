package com.morimil.app.core.memory

import com.morimil.app.data.local.MemoryLinkEntity

data class MemoryBacklinkGraph(
    val nodeId: String,
    val nodeType: String,
    val outgoing: List<MemoryBacklink>,
    val incoming: List<MemoryBacklink>
) {
    val isEmpty: Boolean = outgoing.isEmpty() && incoming.isEmpty()
}

data class MemoryBacklink(
    val link: MemoryLinkEntity,
    val linkedNodeId: String,
    val linkedNodeType: String,
    val direction: MemoryBacklinkDirection
)

enum class MemoryBacklinkDirection {
    Outgoing,
    Incoming
}

object MemoryBacklinkGraphBuilder {
    fun buildForNode(
        nodeId: String,
        nodeType: String,
        links: List<MemoryLinkEntity>
    ): MemoryBacklinkGraph {
        val outgoing = links
            .filter { link -> link.sourceId == nodeId && link.sourceType == nodeType }
            .map { link ->
                MemoryBacklink(
                    link = link,
                    linkedNodeId = link.targetId,
                    linkedNodeType = link.targetType,
                    direction = MemoryBacklinkDirection.Outgoing
                )
            }
            .sortedByBacklinkWeight()

        val incoming = links
            .filter { link -> link.targetId == nodeId && link.targetType == nodeType }
            .map { link ->
                MemoryBacklink(
                    link = link,
                    linkedNodeId = link.sourceId,
                    linkedNodeType = link.sourceType,
                    direction = MemoryBacklinkDirection.Incoming
                )
            }
            .sortedByBacklinkWeight()

        return MemoryBacklinkGraph(
            nodeId = nodeId,
            nodeType = nodeType,
            outgoing = outgoing,
            incoming = incoming
        )
    }

    private fun List<MemoryBacklink>.sortedByBacklinkWeight(): List<MemoryBacklink> {
        return sortedWith(
            compareByDescending<MemoryBacklink> { backlink -> backlink.link.strength }
                .thenByDescending { backlink -> backlink.link.createdAtMillis }
                .thenBy { backlink -> backlink.link.linkId }
        )
    }
}
