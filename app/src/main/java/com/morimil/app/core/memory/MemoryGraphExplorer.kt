package com.morimil.app.core.memory

import com.morimil.app.data.local.DecisionLogEntity
import com.morimil.app.data.local.KnowledgeCapsuleEntity
import com.morimil.app.data.local.MemoryEventEntity
import com.morimil.app.data.local.MemoryLinkEntity
import com.morimil.app.data.local.MigrationRecordEntity
import com.morimil.app.data.local.ProjectStateEntity
import com.morimil.app.data.local.RecallScheduleEntity
import org.json.JSONArray

data class MemoryGraphExplorerSnapshot(
    val mode: String,
    val selectedNodeId: String?,
    val nodes: List<MemoryGraphExplorerNode>,
    val edges: List<MemoryGraphExplorerEdge>,
    val gaps: List<MemoryGraphGap>,
    val narrativePath: List<String>
) {
    val isEmpty: Boolean = nodes.isEmpty()
    val issueCount: Int = gaps.count { gap -> gap.severity != "ok" } +
        edges.count { edge -> edge.health == "critical" } +
        nodes.count { node -> node.health == "critical" }
    val focused: Boolean = selectedNodeId != null
}

data class MemoryGraphExplorerNode(
    val nodeId: String,
    val nodeType: String,
    val layer: String,
    val title: String,
    val subtitle: String,
    val memoryKind: String,
    val weight: Double,
    val health: String,
    val selected: Boolean,
    val sourceEventHash: String?
)

data class MemoryGraphExplorerEdge(
    val sourceId: String,
    val targetId: String,
    val relation: String,
    val strength: Double,
    val health: String,
    val reason: String
)

data class MemoryGraphGap(
    val gapId: String,
    val severity: String,
    val title: String,
    val detail: String
)

object MemoryGraphExplorer {
    const val MODE_GLOBAL = "global"
    const val MODE_FOCUS = "focus"
    const val MODE_GAPS = "gaps"

    const val LAYER_IDENTITY = "identity"
    const val LAYER_PROJECTS = "projects"
    const val LAYER_DECISIONS = "decisions"
    const val LAYER_CAPSULES = "capsules"
    const val LAYER_RECALLS = "recalls"
    const val LAYER_MIGRATIONS = "migrations"
    const val LAYER_EVENTS = "events"
    const val LAYER_INTEGRITY = "integrity"
    const val LAYER_EXTERNAL = "external"

    const val MEMORY_EVENT_NODE_TYPE = "memory_event"

    val defaultLayers: Set<String> = setOf(
        LAYER_IDENTITY,
        LAYER_PROJECTS,
        LAYER_DECISIONS,
        LAYER_CAPSULES,
        LAYER_RECALLS,
        LAYER_MIGRATIONS,
        LAYER_EVENTS,
        LAYER_INTEGRITY,
        LAYER_EXTERNAL
    )

    fun build(
        mode: String,
        selectedEventHash: String?,
        events: List<MemoryEventEntity>,
        links: List<MemoryLinkEntity>,
        capsules: List<KnowledgeCapsuleEntity>,
        recalls: List<RecallScheduleEntity>,
        migrations: List<MigrationRecordEntity>,
        projects: List<ProjectStateEntity>,
        decisions: List<DecisionLogEntity>,
        activeLayers: Set<String> = defaultLayers,
        nowMillis: Long = System.currentTimeMillis(),
        maxEventNodes: Int = 64,
        maxTotalNodes: Int = 96
    ): MemoryGraphExplorerSnapshot {
        val normalizedMode = mode.takeIf { it in setOf(MODE_GLOBAL, MODE_FOCUS, MODE_GAPS) } ?: MODE_GLOBAL
        val selectedHash = selectedEventHash?.takeIf { hash -> hash.isNotBlank() }
        val eventsByHash = events.associateBy { event -> event.eventHash }
        val selectedNodeId = selectedHash
        val nodes = linkedMapOf<String, MemoryGraphExplorerNode>()
        val edges = mutableListOf<MemoryGraphExplorerEdge>()
        val gaps = mutableListOf<MemoryGraphGap>()

        fun layerEnabled(layer: String): Boolean = layer in activeLayers

        fun addNode(node: MemoryGraphExplorerNode) {
            if (node.layer == LAYER_IDENTITY || layerEnabled(node.layer)) {
                nodes.putIfAbsent(node.nodeId, node)
            }
        }

        fun addEdge(edge: MemoryGraphExplorerEdge) {
            if (edge.sourceId in nodes && edge.targetId in nodes) {
                edges += edge
            }
        }

        addNode(
            MemoryGraphExplorerNode(
                nodeId = "morimil:identity",
                nodeType = "identity",
                layer = LAYER_IDENTITY,
                title = "Morimil",
                subtitle = "Identidad local, Genesis y cuerpo de memoria",
                memoryKind = "identity",
                weight = 1.0,
                health = "ok",
                selected = false,
                sourceEventHash = null
            )
        )

        if (layerEnabled(LAYER_PROJECTS)) {
            projects.take(12).forEach { project ->
                val nodeId = "project:${project.projectId}"
                addNode(
                    MemoryGraphExplorerNode(
                        nodeId = nodeId,
                        nodeType = "project",
                        layer = LAYER_PROJECTS,
                        title = project.title,
                        subtitle = "status=${project.status}",
                        memoryKind = "project",
                        weight = 0.72,
                        health = "ok",
                        selected = false,
                        sourceEventHash = null
                    )
                )
                addEdge(edge("morimil:identity", nodeId, "owns_project", 0.7, "ok", "Proyecto local registrado"))
            }
        }

        if (layerEnabled(LAYER_DECISIONS)) {
            decisions.sortedByDescending { decision -> decision.createdAtMillis }.take(16).forEach { decision ->
                val nodeId = "decision:${decision.id}:${decision.createdAtMillis}"
                addNode(
                    MemoryGraphExplorerNode(
                        nodeId = nodeId,
                        nodeType = "decision",
                        layer = LAYER_DECISIONS,
                        title = decision.title,
                        subtitle = "status=${decision.status}",
                        memoryKind = "decision",
                        weight = 0.76,
                        health = "ok",
                        selected = false,
                        sourceEventHash = null
                    )
                )
                addEdge(edge("morimil:identity", nodeId, "decided", 0.68, "ok", "Decision persistida"))
            }
        }

        val eventHashesFromLinks = links.flatMap { link ->
            listOfNotNull(
                link.sourceId.takeIf { link.sourceType == MEMORY_EVENT_NODE_TYPE },
                link.targetId.takeIf { link.targetType == MEMORY_EVENT_NODE_TYPE }
            )
        }.toSet()

        val focusHashes = if (selectedHash == null) {
            emptySet()
        } else {
            buildSet {
                add(selectedHash)
                links.forEach { link ->
                    if (link.sourceId == selectedHash && link.targetType == MEMORY_EVENT_NODE_TYPE) add(link.targetId)
                    if (link.targetId == selectedHash && link.sourceType == MEMORY_EVENT_NODE_TYPE) add(link.sourceId)
                }
            }
        }

        val eventCandidates = when (normalizedMode) {
            MODE_FOCUS -> events.filter { event -> event.eventHash in focusHashes || event.eventHash == selectedHash }
            MODE_GAPS -> events.filter { event ->
                event.memoryKind.contains("quarantine", ignoreCase = true) ||
                    event.memoryKind.contains("cuarentena", ignoreCase = true) ||
                    event.eventType.contains("quarantine", ignoreCase = true) ||
                    event.eventHash in eventHashesFromLinks
            }
            else -> events.sortedWith(
                compareByDescending<MemoryEventEntity> { event -> event.userConfirmed }
                    .thenByDescending { event -> event.importance }
                    .thenByDescending { event -> event.confidence }
                    .thenByDescending { event -> event.createdAtMillis }
            )
        }.take(maxEventNodes)

        if (layerEnabled(LAYER_EVENTS)) {
            eventCandidates.forEach { event ->
                addNode(memoryEventNode(event, selectedHash))
            }
        }

        if (selectedHash != null && selectedHash !in nodes && layerEnabled(LAYER_EVENTS)) {
            val event = eventsByHash[selectedHash]
            if (event != null) {
                addNode(memoryEventNode(event, selectedHash))
            } else {
                addNode(externalEventNode(selectedHash, selected = true))
            }
        }

        if (layerEnabled(LAYER_CAPSULES)) {
            capsules.sortedByDescending { capsule -> capsule.confidence }.take(24).forEach { capsule ->
                val nodeId = "capsule:${capsule.capsuleId}"
                val sourceHash = capsule.sourceEventHash
                val sourceMissing = sourceHash != null && sourceHash !in eventsByHash
                if (sourceMissing) {
                    gaps += MemoryGraphGap(
                        gapId = "capsule:${capsule.capsuleId}:source_missing",
                        severity = "critical",
                        title = "Capsula sin recuerdo fuente visible",
                        detail = "${capsule.title} apunta a ${sourceHash?.take(24)}"
                    )
                }
                addNode(
                    MemoryGraphExplorerNode(
                        nodeId = nodeId,
                        nodeType = "knowledge_capsule",
                        layer = LAYER_CAPSULES,
                        title = capsule.title,
                        subtitle = "${capsule.capsuleCategory} / confidence=${capsule.confidence}",
                        memoryKind = "knowledge_capsule",
                        weight = capsule.confidence.coerceIn(0, 100) / 100.0,
                        health = if (sourceMissing) "critical" else "ok",
                        selected = false,
                        sourceEventHash = sourceHash
                    )
                )
                addEdge(edge("morimil:identity", nodeId, "knows", 0.52, if (sourceMissing) "critical" else "ok", "Capsula consolidada"))
                sourceHash?.let { hash ->
                    ensureEventEndpoint(hash, eventsByHash, nodes, activeLayers, selectedHash)
                    addEdge(edge(nodeId, hash, "based_on", 0.82, if (sourceMissing) "critical" else "ok", "Fuente de conocimiento"))
                }
            }
        }

        if (layerEnabled(LAYER_RECALLS)) {
            recalls.sortedWith(compareBy<RecallScheduleEntity> { it.dueAtMillis }.thenByDescending { it.priority }).take(24).forEach { recall ->
                val nodeId = "recall:${recall.recallId}"
                val missing = recall.targetEventHash !in eventsByHash
                val overdue = recall.dueAtMillis <= nowMillis
                if (missing) {
                    gaps += MemoryGraphGap(
                        gapId = "recall:${recall.recallId}:target_missing",
                        severity = "critical",
                        title = "Recall sin recuerdo objetivo visible",
                        detail = recall.targetEventHash.take(24)
                    )
                }
                addNode(
                    MemoryGraphExplorerNode(
                        nodeId = nodeId,
                        nodeType = "recall",
                        layer = LAYER_RECALLS,
                        title = recall.prompt.take(80),
                        subtitle = "priority=${recall.priority} due=${recall.dueAtMillis}",
                        memoryKind = "recall",
                        weight = recall.priority.coerceIn(0, 100) / 100.0,
                        health = when {
                            missing -> "critical"
                            overdue -> "watch"
                            else -> "ok"
                        },
                        selected = false,
                        sourceEventHash = recall.targetEventHash
                    )
                )
                ensureEventEndpoint(recall.targetEventHash, eventsByHash, nodes, activeLayers, selectedHash)
                addEdge(edge(nodeId, recall.targetEventHash, "recalls", 0.74, if (missing) "critical" else "ok", recall.reason))
            }
        }

        if (layerEnabled(LAYER_MIGRATIONS)) {
            migrations.sortedByDescending { migration -> migration.createdAtMillis }.take(18).forEach { migration ->
                val nodeId = "migration:${migration.migrationId}"
                val refs = migration.memoryEventRefs()
                val missingRefs = refs.filter { ref -> ref !in eventsByHash }
                if (missingRefs.isNotEmpty()) {
                    gaps += MemoryGraphGap(
                        gapId = "migration:${migration.migrationId}:missing_refs",
                        severity = "critical",
                        title = "Migracion con referencias no visibles",
                        detail = missingRefs.take(3).joinToString(", ") { it.take(24) }
                    )
                }
                val health = when {
                    migration.status == "failed" -> "critical"
                    missingRefs.isNotEmpty() -> "critical"
                    migration.approvalRequired && !migration.approvedByUser -> "watch"
                    else -> "ok"
                }
                addNode(
                    MemoryGraphExplorerNode(
                        nodeId = nodeId,
                        nodeType = "migration",
                        layer = LAYER_MIGRATIONS,
                        title = migration.migrationType,
                        subtitle = "status=${migration.status} risk=${migration.riskLevel}",
                        memoryKind = "migration",
                        weight = if (migration.riskLevel == "high") 0.86 else 0.58,
                        health = health,
                        selected = false,
                        sourceEventHash = refs.firstOrNull()
                    )
                )
                addEdge(edge("morimil:identity", nodeId, "changes_memory", 0.62, health, "Migracion cognitiva o runtime"))
                refs.take(8).forEach { ref ->
                    ensureEventEndpoint(ref, eventsByHash, nodes, activeLayers, selectedHash)
                    addEdge(edge(nodeId, ref, "references", 0.58, if (ref in missingRefs) "critical" else "ok", "Artifact referenciado"))
                }
            }
        }

        links.take(120).forEach { link ->
            val sourceId = link.sourceId
            val targetId = link.targetId
            ensureEndpointForLink(sourceId, link.sourceType, eventsByHash, nodes, activeLayers, selectedHash)
            ensureEndpointForLink(targetId, link.targetType, eventsByHash, nodes, activeLayers, selectedHash)
            val health = if (link.verificationState == "orphaned") "critical" else "ok"
            if (health == "critical") {
                gaps += MemoryGraphGap(
                    gapId = "link:${link.linkId}:orphaned",
                    severity = "critical",
                    title = "Link huerfano",
                    detail = "${link.sourceType}:${sourceId.take(18)} -> ${link.targetType}:${targetId.take(18)}"
                )
            }
            addEdge(edge(sourceId, targetId, link.relation, link.strength, health, link.reason))
        }

        val finalNodes = nodes.values
            .sortedWith(
                compareByDescending<MemoryGraphExplorerNode> { node -> node.selected }
                    .thenBy { node -> node.layer.orderRank() }
                    .thenByDescending { node -> if (node.health == "critical") 1 else 0 }
                    .thenByDescending { node -> node.weight }
                    .thenBy { node -> node.title }
            )
            .take(maxTotalNodes)
        val includedNodeIds = finalNodes.map { node -> node.nodeId }.toSet()
        val finalEdges = edges
            .filter { edge -> edge.sourceId in includedNodeIds && edge.targetId in includedNodeIds }
            .distinctBy { edge -> "${edge.sourceId}|${edge.targetId}|${edge.relation}" }
            .sortedWith(
                compareByDescending<MemoryGraphExplorerEdge> { edge -> if (edge.health == "critical") 1 else 0 }
                    .thenByDescending { edge -> edge.strength }
                    .thenBy { edge -> edge.relation }
            )

        return MemoryGraphExplorerSnapshot(
            mode = normalizedMode,
            selectedNodeId = selectedHash,
            nodes = finalNodes,
            edges = finalEdges,
            gaps = gaps.distinctBy { gap -> gap.gapId },
            narrativePath = buildNarrativePath(finalNodes, selectedHash)
        )
    }

    fun connectionsForNode(snapshot: MemoryGraphExplorerSnapshot, nodeId: String): List<MemoryGraphExplorerEdge> {
        return snapshot.edges
            .filter { edge -> edge.sourceId == nodeId || edge.targetId == nodeId }
            .sortedWith(
                compareByDescending<MemoryGraphExplorerEdge> { edge -> if (edge.health == "critical") 1 else 0 }
                    .thenByDescending { edge -> edge.strength }
            )
    }

    private fun memoryEventNode(event: MemoryEventEntity, selectedHash: String?): MemoryGraphExplorerNode {
        val isQuarantine = event.memoryKind.contains("quarantine", ignoreCase = true) ||
            event.memoryKind.contains("cuarentena", ignoreCase = true) ||
            event.eventType.contains("quarantine", ignoreCase = true)
        return MemoryGraphExplorerNode(
            nodeId = event.eventHash,
            nodeType = MEMORY_EVENT_NODE_TYPE,
            layer = if (isQuarantine) LAYER_INTEGRITY else LAYER_EVENTS,
            title = "${event.memoryKind} / ${event.eventType}",
            subtitle = event.body.singleLinePreview(140),
            memoryKind = event.memoryKind,
            weight = event.importanceConfidenceWeight(),
            health = if (isQuarantine) "critical" else "ok",
            selected = event.eventHash == selectedHash,
            sourceEventHash = event.eventHash
        )
    }

    private fun externalEventNode(hash: String, selected: Boolean): MemoryGraphExplorerNode {
        return MemoryGraphExplorerNode(
            nodeId = hash,
            nodeType = MEMORY_EVENT_NODE_TYPE,
            layer = LAYER_EXTERNAL,
            title = "Recuerdo no cargado",
            subtitle = hash.take(32),
            memoryKind = "external_memory_event",
            weight = 0.28,
            health = "watch",
            selected = selected,
            sourceEventHash = hash
        )
    }

    private fun ensureEventEndpoint(
        hash: String,
        eventsByHash: Map<String, MemoryEventEntity>,
        nodes: MutableMap<String, MemoryGraphExplorerNode>,
        activeLayers: Set<String>,
        selectedHash: String?
    ) {
        if (hash in nodes) return
        val event = eventsByHash[hash]
        val node = if (event != null) memoryEventNode(event, selectedHash) else externalEventNode(hash, selected = hash == selectedHash)
        if (node.layer in activeLayers || node.selected) nodes[hash] = node
    }

    private fun ensureEndpointForLink(
        id: String,
        type: String,
        eventsByHash: Map<String, MemoryEventEntity>,
        nodes: MutableMap<String, MemoryGraphExplorerNode>,
        activeLayers: Set<String>,
        selectedHash: String?
    ) {
        if (id in nodes) return
        if (type == MEMORY_EVENT_NODE_TYPE) {
            ensureEventEndpoint(id, eventsByHash, nodes, activeLayers, selectedHash)
        } else if (LAYER_EXTERNAL in activeLayers) {
            nodes[id] = MemoryGraphExplorerNode(
                nodeId = id,
                nodeType = type,
                layer = LAYER_EXTERNAL,
                title = type,
                subtitle = id.take(32),
                memoryKind = type,
                weight = 0.24,
                health = "watch",
                selected = false,
                sourceEventHash = null
            )
        }
    }

    private fun edge(
        sourceId: String,
        targetId: String,
        relation: String,
        strength: Double,
        health: String,
        reason: String
    ): MemoryGraphExplorerEdge {
        return MemoryGraphExplorerEdge(
            sourceId = sourceId,
            targetId = targetId,
            relation = relation,
            strength = strength.coerceIn(0.0, 1.0),
            health = health,
            reason = reason
        )
    }

    private fun MigrationRecordEntity.memoryEventRefs(): List<String> {
        return parseJsonArray(affectedArtifactsJson) +
            listOfNotNull(preSnapshotId, postSnapshotId).filter { value -> value.startsWith("sha256:") }
    }

    private fun parseJsonArray(value: String): List<String> {
        return runCatching {
            val array = JSONArray(value)
            (0 until array.length()).mapNotNull { index ->
                array.optString(index).takeIf { item -> item.startsWith("sha256:") }
            }
        }.getOrDefault(emptyList())
    }

    private fun buildNarrativePath(nodes: List<MemoryGraphExplorerNode>, selectedHash: String?): List<String> {
        val selected = selectedHash?.let { hash -> nodes.firstOrNull { node -> node.nodeId == hash } }
        val project = nodes.firstOrNull { node -> node.layer == LAYER_PROJECTS }
        val decision = nodes.firstOrNull { node -> node.layer == LAYER_DECISIONS }
        val capsule = nodes.firstOrNull { node -> node.layer == LAYER_CAPSULES }
        return listOfNotNull(
            "Morimil",
            project?.title,
            decision?.title,
            capsule?.title,
            selected?.title
        ).distinct()
    }

    private fun MemoryEventEntity.importanceConfidenceWeight(): Double {
        val importanceScore = importance.coerceIn(0, 100) / 100.0
        val confidenceScore = confidence.coerceIn(0, 100) / 100.0
        val confirmationBoost = if (userConfirmed) 0.15 else 0.0
        return ((importanceScore * 0.58) + (confidenceScore * 0.27) + confirmationBoost).coerceIn(0.0, 1.0)
    }

    private fun String.singleLinePreview(maxLength: Int): String {
        return replace('\n', ' ').replace('\r', ' ').take(maxLength)
    }

    private fun String.orderRank(): Int {
        return when (this) {
            LAYER_IDENTITY -> 0
            LAYER_PROJECTS -> 1
            LAYER_DECISIONS -> 2
            LAYER_CAPSULES -> 3
            LAYER_RECALLS -> 4
            LAYER_MIGRATIONS -> 5
            LAYER_INTEGRITY -> 6
            LAYER_EVENTS -> 7
            else -> 8
        }
    }
}