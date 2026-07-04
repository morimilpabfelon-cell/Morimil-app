package com.morimil.app.core.memory

import com.morimil.app.data.local.MemoryLinkEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MemoryBacklinkGraphTest {
    @Test
    fun separatesOutgoingAndIncomingLinksForSelectedMemoryNode() {
        val links = listOf(
            link("selected", "project", "derived_from", 0.8, 10L),
            link("decision", "selected", "mentions", 0.9, 11L),
            link("other", "project", "unrelated", 1.0, 12L)
        )

        val graph = MemoryBacklinkGraphBuilder.buildForNode(
            nodeId = "selected",
            nodeType = "memory_event",
            links = links
        )

        assertFalse(graph.isEmpty)
        assertEquals(listOf("project"), graph.outgoing.map { backlink -> backlink.linkedNodeId })
        assertEquals(listOf("decision"), graph.incoming.map { backlink -> backlink.linkedNodeId })
        assertEquals(MemoryBacklinkDirection.Outgoing, graph.outgoing.single().direction)
        assertEquals(MemoryBacklinkDirection.Incoming, graph.incoming.single().direction)
    }

    @Test
    fun ordersBacklinksByStrengthThenNewest() {
        val links = listOf(
            link("selected", "weak", "mentions", 0.2, 30L),
            link("selected", "oldStrong", "mentions", 0.9, 10L),
            link("selected", "newStrong", "mentions", 0.9, 20L)
        )

        val graph = MemoryBacklinkGraphBuilder.buildForNode(
            nodeId = "selected",
            nodeType = "memory_event",
            links = links
        )

        assertEquals(
            listOf("newStrong", "oldStrong", "weak"),
            graph.outgoing.map { backlink -> backlink.linkedNodeId }
        )
    }

    private fun link(
        sourceId: String,
        targetId: String,
        relation: String,
        strength: Double,
        createdAtMillis: Long
    ): MemoryLinkEntity {
        return MemoryLinkEntity(
            linkId = "$sourceId-$targetId-$relation",
            instanceId = "instance",
            genesisCoreHash = "sha256:genesis",
            sourceId = sourceId,
            sourceType = "memory_event",
            targetId = targetId,
            targetType = "memory_event",
            relation = relation,
            strength = strength,
            reason = "test",
            createdBy = "test",
            privacyVisibility = "private_local",
            cloudSyncAllowed = false,
            exportAllowed = false,
            verificationState = "valid",
            createdAtMillis = createdAtMillis
        )
    }
}
