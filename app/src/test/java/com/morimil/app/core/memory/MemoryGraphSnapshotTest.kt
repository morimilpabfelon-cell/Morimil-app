package com.morimil.app.core.memory

import com.morimil.app.data.local.MemoryEventEntity
import com.morimil.app.data.local.MemoryLinkEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryGraphSnapshotTest {
    @Test
    fun buildsSelectedMemoryGraphWithIncomingAndOutgoingEdges() {
        val selected = event("selected", "decision", "Selected memory", 90, 80)
        val project = event("project", "project", "Project memory", 70, 75)
        val design = event("design", "observation", "Design memory", 50, 60)
        val links = listOf(
            link("selected", "project", "supports", 0.9),
            link("design", "selected", "mentions", 0.7)
        )

        val snapshot = MemoryGraphSnapshotBuilder.build(
            selectedEventHash = "selected",
            events = listOf(selected, project, design),
            links = links
        )

        assertFalse(snapshot.isEmpty)
        assertEquals("selected", snapshot.selectedNodeId)
        assertEquals(listOf("selected", "project", "design"), snapshot.nodes.map { node -> node.nodeId })
        assertTrue(snapshot.nodes.first().selected)
        assertEquals(listOf("supports", "mentions"), snapshot.edges.map { edge -> edge.relation })
    }

    @Test
    fun keepsExternalLinkedNodesVisibleWithoutMemoryEventRow() {
        val selected = event("selected", "decision", "Selected memory", 80, 70)
        val links = listOf(
            link(
                sourceId = "selected",
                targetId = "capsule-1",
                relation = "consolidates_into",
                strength = 0.8,
                targetType = "knowledge_capsule"
            )
        )

        val snapshot = MemoryGraphSnapshotBuilder.build(
            selectedEventHash = "selected",
            events = listOf(selected),
            links = links
        )

        val capsuleNode = snapshot.nodes.single { node -> node.nodeId == "capsule-1" }
        assertEquals("knowledge_capsule", capsuleNode.nodeType)
        assertEquals("knowledge_capsule", capsuleNode.title)
        assertEquals("capsule-1", capsuleNode.subtitle)
        assertEquals(1, snapshot.edges.size)
    }

    @Test
    fun respectsMaxNodeLimitAndDropsEdgesOutsideTheVisibleGraph() {
        val selected = event("selected", "decision", "Selected memory", 80, 70)
        val links = listOf(
            link("selected", "one", "mentions", 0.8),
            link("selected", "two", "mentions", 0.7),
            link("selected", "three", "mentions", 0.6)
        )

        val snapshot = MemoryGraphSnapshotBuilder.build(
            selectedEventHash = "selected",
            events = listOf(selected, event("one"), event("two"), event("three")),
            links = links,
            maxNodes = 3
        )

        assertEquals(listOf("selected", "one", "two"), snapshot.nodes.map { node -> node.nodeId })
        assertEquals(2, snapshot.edges.size)
        assertTrue(snapshot.edges.none { edge -> edge.targetId == "three" })
    }

    private fun event(
        hash: String,
        memoryKind: String = "observation",
        body: String = "Body for $hash",
        importance: Int = 50,
        confidence: Int = 70
    ): MemoryEventEntity {
        return MemoryEventEntity(
            genesisCoreId = "primary_genesis",
            genesisCoreHash = "sha256:genesis",
            previousEventHash = null,
            eventHash = hash,
            hashAlgorithm = "sha256",
            canonicalization = "morimil.memory_event_hash.v3",
            signatureAlgorithm = "unsigned_runtime_v1",
            eventSignature = null,
            eventType = "test.event",
            actor = "test",
            source = "test",
            contextTag = "test",
            privacyVisibility = "private_local",
            memoryKind = memoryKind,
            tagsJson = "[]",
            evidenceJson = "{}",
            confidence = confidence,
            userConfirmed = false,
            body = body,
            importance = importance,
            createdAtMillis = 1_000L
        )
    }

    private fun link(
        sourceId: String,
        targetId: String,
        relation: String,
        strength: Double,
        sourceType: String = MemoryGraphSnapshotBuilder.MEMORY_EVENT_NODE_TYPE,
        targetType: String = MemoryGraphSnapshotBuilder.MEMORY_EVENT_NODE_TYPE
    ): MemoryLinkEntity {
        return MemoryLinkEntity(
            linkId = "$sourceId-$targetId-$relation",
            instanceId = "instance",
            genesisCoreHash = "sha256:genesis",
            sourceId = sourceId,
            sourceType = sourceType,
            targetId = targetId,
            targetType = targetType,
            relation = relation,
            strength = strength,
            reason = "test",
            createdBy = "test",
            privacyVisibility = "private_local",
            cloudSyncAllowed = false,
            exportAllowed = false,
            verificationState = "valid",
            createdAtMillis = 1_000L
        )
    }
}
