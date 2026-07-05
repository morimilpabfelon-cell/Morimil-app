package com.morimil.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.morimil.app.core.memory.MemoryGraphExplorer
import com.morimil.app.core.memory.MemoryGraphExplorerEdge
import com.morimil.app.core.memory.MemoryGraphExplorerNode
import com.morimil.app.core.memory.MemoryGraphExplorerSnapshot
import com.morimil.app.data.local.DecisionLogEntity
import com.morimil.app.data.local.KnowledgeCapsuleEntity
import com.morimil.app.data.local.MemoryEventEntity
import com.morimil.app.data.local.MemoryLinkEntity
import com.morimil.app.data.local.MigrationRecordEntity
import com.morimil.app.data.local.ProjectStateEntity
import com.morimil.app.data.local.RecallScheduleEntity
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun MemoryGraphCanvasPanel(
    selectedEventHash: String?,
    selectedEvent: MemoryEventEntity?,
    graphEvents: List<MemoryEventEntity>,
    allEvents: List<MemoryEventEntity>,
    links: List<MemoryLinkEntity>,
    knowledgeCapsules: List<KnowledgeCapsuleEntity>,
    recalls: List<RecallScheduleEntity>,
    migrations: List<MigrationRecordEntity>,
    projects: List<ProjectStateEntity>,
    decisions: List<DecisionLogEntity>,
    onSelectEventHash: (String) -> Unit,
    onClearSelection: () -> Unit
) {
    var mode by remember { mutableStateOf(MemoryGraphExplorer.MODE_GLOBAL) }
    var activeLayers by remember { mutableStateOf(MemoryGraphExplorer.defaultLayers) }
    var selectedGraphNodeId by remember(selectedEventHash) { mutableStateOf(selectedEventHash) }

    val canvasEvents = remember(mode, selectedEventHash, graphEvents, allEvents) {
        when (mode) {
            MemoryGraphExplorer.MODE_FOCUS -> (graphEvents + allEvents.filter { event -> event.eventHash == selectedEventHash })
                .distinctBy { event -> event.eventHash }
            else -> allEvents
        }
    }
    val snapshot = remember(mode, selectedEventHash, canvasEvents, links, knowledgeCapsules, recalls, migrations, projects, decisions, activeLayers) {
        MemoryGraphExplorer.build(
            mode = mode,
            selectedEventHash = selectedEventHash,
            events = canvasEvents,
            links = links,
            capsules = knowledgeCapsules,
            recalls = recalls,
            migrations = migrations,
            projects = projects,
            decisions = decisions,
            activeLayers = activeLayers
        )
    }
    val selectedGraphNode = selectedGraphNodeId?.let { id -> snapshot.nodes.firstOrNull { node -> node.nodeId == id } }

    Text("Grafo de memoria v2", style = MaterialTheme.typography.titleMedium)
    Text("Mapa global, foco y huecos. No dibuja toda la base cruda: prioriza organos, decisiones, capsulas, recalls y recuerdos relevantes.")

    if (snapshot.isEmpty) {
        MemoryGraphEmptyCard()
        return
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MemoryGraphModeRow(mode = mode, onModeChange = { nextMode ->
                mode = nextMode
                selectedGraphNodeId = selectedEventHash
            })
            MemoryGraphLayerFilters(activeLayers = activeLayers, onToggleLayer = { layer ->
                activeLayers = if (layer in activeLayers) activeLayers - layer else activeLayers + layer
            })
            MemoryGraphStatsRow(snapshot)
            MemoryGraphNarrativePath(snapshot)
            if (snapshot.focused) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onClearSelection) { Text("Cerrar foco") }
                    Button(onClick = { mode = MemoryGraphExplorer.MODE_GLOBAL }) { Text("Mapa global") }
                }
            }

            MemoryGraphCanvas(
                snapshot = snapshot,
                selectedGraphNodeId = selectedGraphNodeId,
                onSelectNode = { node ->
                    selectedGraphNodeId = node.nodeId
                    if (node.nodeType == MemoryGraphExplorer.MEMORY_EVENT_NODE_TYPE) {
                        onSelectEventHash(node.nodeId)
                        mode = MemoryGraphExplorer.MODE_FOCUS
                    }
                }
            )

            MemoryGraphNodeInspector(
                snapshot = snapshot,
                node = selectedGraphNode,
                selectedEvent = selectedEvent,
                onSelectEventHash = onSelectEventHash
            )
            MemoryGraphGapPanel(snapshot)
            MemoryGraphNodeList(snapshot = snapshot, onSelectNode = { node ->
                selectedGraphNodeId = node.nodeId
                if (node.nodeType == MemoryGraphExplorer.MEMORY_EVENT_NODE_TYPE) {
                    onSelectEventHash(node.nodeId)
                    mode = MemoryGraphExplorer.MODE_FOCUS
                }
            })
        }
    }
}

@Composable
private fun MemoryGraphModeRow(mode: String, onModeChange: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MemoryGraphChip("global", attention = mode == MemoryGraphExplorer.MODE_GLOBAL, onClick = { onModeChange(MemoryGraphExplorer.MODE_GLOBAL) })
        MemoryGraphChip("foco", attention = mode == MemoryGraphExplorer.MODE_FOCUS, onClick = { onModeChange(MemoryGraphExplorer.MODE_FOCUS) })
        MemoryGraphChip("huecos", attention = mode == MemoryGraphExplorer.MODE_GAPS, onClick = { onModeChange(MemoryGraphExplorer.MODE_GAPS) })
    }
}

@Composable
private fun MemoryGraphLayerFilters(activeLayers: Set<String>, onToggleLayer: (String) -> Unit) {
    val layers = listOf(
        MemoryGraphExplorer.LAYER_PROJECTS to "proyectos",
        MemoryGraphExplorer.LAYER_DECISIONS to "decisiones",
        MemoryGraphExplorer.LAYER_CAPSULES to "capsulas",
        MemoryGraphExplorer.LAYER_RECALLS to "recalls",
        MemoryGraphExplorer.LAYER_MIGRATIONS to "migraciones",
        MemoryGraphExplorer.LAYER_EVENTS to "recuerdos",
        MemoryGraphExplorer.LAYER_INTEGRITY to "integridad"
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Capas", style = MaterialTheme.typography.labelLarge)
        layers.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (layer, label) ->
                    MemoryGraphChip(label, attention = layer in activeLayers, onClick = { onToggleLayer(layer) })
                }
            }
        }
    }
}

@Composable
private fun MemoryGraphStatsRow(snapshot: MemoryGraphExplorerSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MemoryGraphChip("modo=${snapshot.mode}")
            MemoryGraphChip("nodos=${snapshot.nodes.size}")
            MemoryGraphChip("lineas=${snapshot.edges.size}")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MemoryGraphChip("huecos=${snapshot.gaps.size}", attention = snapshot.gaps.isNotEmpty())
            MemoryGraphChip("alertas=${snapshot.issueCount}", attention = snapshot.issueCount > 0)
        }
    }
}

@Composable
private fun MemoryGraphNarrativePath(snapshot: MemoryGraphExplorerSnapshot) {
    if (snapshot.narrativePath.isEmpty()) return
    Text("Camino narrativo", style = MaterialTheme.typography.labelLarge)
    Text(snapshot.narrativePath.joinToString(" -> "))
}

@Composable
private fun MemoryGraphEmptyCard() {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Canvas de memoria", style = MaterialTheme.typography.titleMedium)
            Text("La semilla aun no tiene recuerdos conectados aqui.")
            AssistChip(onClick = {}, label = { Text("esperando_links") })
        }
    }
}

@Composable
private fun MemoryGraphChip(
    label: String,
    attention: Boolean = false,
    color: Color? = null,
    onClick: (() -> Unit)? = null
) {
    val containerColor = when {
        color != null -> color
        attention -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val labelColor = when {
        color != null -> Color.White
        attention -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    AssistChip(
        onClick = onClick ?: {},
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = containerColor,
            labelColor = labelColor
        )
    )
}

@Composable
private fun MemoryGraphCanvas(
    snapshot: MemoryGraphExplorerSnapshot,
    selectedGraphNodeId: String?,
    onSelectNode: (MemoryGraphExplorerNode) -> Unit
) {
    val density = LocalDensity.current
    val canvasHeight = 340.dp
    var zoom by remember(snapshot.mode) { mutableStateOf(1f) }
    var panOffset by remember(snapshot.mode) { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        zoom = (zoom * zoomChange).coerceIn(0.65f, 3.2f)
        panOffset += panChange
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MemoryGraphChip("zoom=${"%.1f".format(zoom)}x")
            MemoryGraphChip("arrastre=activo")
        }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(canvasHeight)
        ) {
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { canvasHeight.toPx() }
            val positions = remember(snapshot, widthPx, heightPx) {
                layoutGraphNodes(snapshot.nodes, widthPx, heightPx)
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics {
                        contentDescription = "Grafo de memoria v2. Pellizcar para zoom, arrastrar para mover, tocar nodo para inspeccionar."
                    }
                    .transformable(transformState)
                    .pointerInput(snapshot, positions, zoom, panOffset) {
                        detectTapGestures { tapOffset ->
                            val graphTap = screenToGraphOffset(
                                tap = tapOffset,
                                panOffset = panOffset,
                                zoom = zoom,
                                pivot = Offset(widthPx / 2f, heightPx / 2f)
                            )
                            val nearest = positions.minByOrNull { (_, offset) -> distance(offset, graphTap) }
                            if (nearest != null && distance(nearest.value, graphTap) <= TAP_RADIUS_PX) {
                                snapshot.nodes.firstOrNull { node -> node.nodeId == nearest.key }?.let(onSelectNode)
                            }
                        }
                    }
            ) {
                val pivot = Offset(size.width / 2f, size.height / 2f)
                withTransform({
                    translate(left = panOffset.x, top = panOffset.y)
                    scale(scaleX = zoom, scaleY = zoom, pivot = pivot)
                }) {
                    snapshot.edges.forEach { edge ->
                        val source = positions[edge.sourceId]
                        val target = positions[edge.targetId]
                        if (source != null && target != null) {
                            drawLine(
                                color = graphEdgeColor(edge).copy(alpha = (0.25f + (edge.strength.toFloat() * 0.5f)).coerceAtMost(0.9f)),
                                start = source,
                                end = target,
                                strokeWidth = 2f + (edge.strength.toFloat() * 4f)
                            )
                        }
                    }

                    snapshot.nodes.forEach { node ->
                        val center = positions[node.nodeId]
                        if (center != null) {
                            val radius = nodeRadius(node)
                            if (node.health == "critical") {
                                drawCircle(
                                    color = Color(0xFFD97706).copy(alpha = 0.25f),
                                    radius = radius + 24f,
                                    center = center
                                )
                            }
                            if (node.nodeId == selectedGraphNodeId || node.selected) {
                                drawCircle(
                                    color = Color(0xFF2563EB).copy(alpha = 0.18f),
                                    radius = radius + 18f,
                                    center = center
                                )
                            }
                            drawCircle(
                                color = graphNodeColor(node).copy(alpha = 0.18f),
                                radius = radius + 8f,
                                center = center
                            )
                            drawCircle(
                                color = graphNodeColor(node),
                                radius = radius,
                                center = center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryGraphNodeInspector(
    snapshot: MemoryGraphExplorerSnapshot,
    node: MemoryGraphExplorerNode?,
    selectedEvent: MemoryEventEntity?,
    onSelectEventHash: (String) -> Unit
) {
    val inspectedNode = node ?: snapshot.nodes.firstOrNull { it.selected } ?: snapshot.nodes.firstOrNull()
    if (inspectedNode == null) return
    val connections = MemoryGraphExplorer.connectionsForNode(snapshot, inspectedNode.nodeId)

    Text("Inspector de nodo", style = MaterialTheme.typography.titleMedium)
    ElevatedCard {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MemoryGraphChip(inspectedNode.nodeType, color = graphNodeColor(inspectedNode))
                MemoryGraphChip(inspectedNode.layer)
                MemoryGraphChip(inspectedNode.health, attention = inspectedNode.health != "ok")
            }
            Text(inspectedNode.title, style = MaterialTheme.typography.titleMedium)
            Text(inspectedNode.subtitle.take(260))
            Text("peso=${"%.2f".format(inspectedNode.weight)} conexiones=${connections.size}")
            if (selectedEvent != null && inspectedNode.nodeId == selectedEvent.eventHash) {
                Text("recuerdo=${selectedEvent.body.take(260)}")
            }
            inspectedNode.sourceEventHash?.let { hash ->
                Button(onClick = { onSelectEventHash(hash) }) { Text("Abrir recuerdo fuente") }
            }
            connections.take(6).forEach { edge ->
                Text("${edge.relation} -> ${otherEnd(edge, inspectedNode.nodeId).take(28)} / ${edge.health} / ${edge.reason.take(120)}")
            }
        }
    }
}

@Composable
private fun MemoryGraphGapPanel(snapshot: MemoryGraphExplorerSnapshot) {
    if (snapshot.gaps.isEmpty()) return
    Text("Huecos detectados", style = MaterialTheme.typography.titleMedium)
    snapshot.gaps.take(6).forEach { gap ->
        ElevatedCard {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MemoryGraphChip(gap.severity, attention = gap.severity != "ok")
                    MemoryGraphChip(gap.gapId.take(24))
                }
                Text(gap.title, style = MaterialTheme.typography.titleMedium)
                Text(gap.detail.take(220))
            }
        }
    }
}

@Composable
private fun MemoryGraphNodeList(
    snapshot: MemoryGraphExplorerSnapshot,
    onSelectNode: (MemoryGraphExplorerNode) -> Unit
) {
    if (snapshot.isEmpty) {
        Text("La semilla aun no tiene nodos visibles aqui.")
        return
    }

    Text("Nodos visibles", style = MaterialTheme.typography.titleMedium)
    snapshot.nodes.take(12).forEach { node ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MemoryGraphChip(node.layer, color = graphNodeColor(node), onClick = { onSelectNode(node) })
                MemoryGraphChip("salud=${node.health}", attention = node.health != "ok")
                MemoryGraphChip("peso=${"%.2f".format(node.weight)}")
            }
            Text(node.title)
            Text(node.subtitle.take(160), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun layoutGraphNodes(
    nodes: List<MemoryGraphExplorerNode>,
    widthPx: Float,
    heightPx: Float
): Map<String, Offset> {
    if (nodes.isEmpty()) return emptyMap()

    val center = Offset(widthPx / 2f, heightPx / 2f)
    val positions = linkedMapOf<String, Offset>()
    val identity = nodes.firstOrNull { it.layer == MemoryGraphExplorer.LAYER_IDENTITY } ?: nodes.first()
    positions[identity.nodeId] = center

    val grouped = nodes.filter { it.nodeId != identity.nodeId }.groupBy { it.layer }
    val rings = listOf(
        MemoryGraphExplorer.LAYER_PROJECTS,
        MemoryGraphExplorer.LAYER_DECISIONS,
        MemoryGraphExplorer.LAYER_CAPSULES,
        MemoryGraphExplorer.LAYER_RECALLS,
        MemoryGraphExplorer.LAYER_MIGRATIONS,
        MemoryGraphExplorer.LAYER_INTEGRITY,
        MemoryGraphExplorer.LAYER_EVENTS,
        MemoryGraphExplorer.LAYER_EXTERNAL
    )
    val maxRadius = (minOf(widthPx, heightPx) * 0.43f).coerceAtLeast(90f)
    rings.forEachIndexed { ringIndex, layer ->
        val layerNodes = grouped[layer].orEmpty()
        if (layerNodes.isEmpty()) return@forEachIndexed
        val ringRadius = (maxRadius * ((ringIndex + 1).coerceAtMost(4) / 4f)).coerceAtLeast(72f)
        layerNodes.forEachIndexed { index, node ->
            val angle = (-PI / 2.0) + ((2.0 * PI * index) / layerNodes.size.toDouble()) + (ringIndex * 0.22)
            positions[node.nodeId] = Offset(
                x = center.x + (cos(angle) * ringRadius).toFloat(),
                y = center.y + (sin(angle) * ringRadius).toFloat()
            )
        }
    }
    return positions
}

private fun graphNodeColor(node: MemoryGraphExplorerNode): Color {
    if (node.health == "critical") return Color(0xFFD97706)
    return when (node.layer) {
        MemoryGraphExplorer.LAYER_IDENTITY -> Color(0xFF166534)
        MemoryGraphExplorer.LAYER_PROJECTS -> Color(0xFF0F766E)
        MemoryGraphExplorer.LAYER_DECISIONS -> Color(0xFF7C3AED)
        MemoryGraphExplorer.LAYER_CAPSULES -> Color(0xFF2563EB)
        MemoryGraphExplorer.LAYER_RECALLS -> Color(0xFFB7791F)
        MemoryGraphExplorer.LAYER_MIGRATIONS -> Color(0xFF9333EA)
        MemoryGraphExplorer.LAYER_INTEGRITY -> Color(0xFFB45309)
        MemoryGraphExplorer.LAYER_EVENTS -> memoryKindColor(node.memoryKind)
        else -> Color(0xFF64748B)
    }
}

private fun memoryKindColor(memoryKind: String): Color {
    return when {
        memoryKind.contains("identity", ignoreCase = true) -> Color(0xFF2563EB)
        memoryKind.contains("decision", ignoreCase = true) -> Color(0xFF7C3AED)
        memoryKind.contains("project", ignoreCase = true) -> Color(0xFF0F766E)
        memoryKind.contains("preference", ignoreCase = true) -> Color(0xFF059669)
        memoryKind.contains("conversation", ignoreCase = true) -> Color(0xFF475569)
        else -> Color(0xFF334155)
    }
}

private fun graphEdgeColor(edge: MemoryGraphExplorerEdge): Color {
    return when (edge.health) {
        "critical" -> Color(0xFFD97706)
        "watch" -> Color(0xFFEAB308)
        else -> Color(0xFF64748B)
    }
}

private fun nodeRadius(node: MemoryGraphExplorerNode): Float {
    val base = 10f + (node.weight.coerceIn(0.0, 1.0).toFloat() * 12f)
    return if (node.layer == MemoryGraphExplorer.LAYER_IDENTITY) base + 7f else base
}

private fun screenToGraphOffset(tap: Offset, panOffset: Offset, zoom: Float, pivot: Offset): Offset {
    return Offset(
        x = ((tap.x - panOffset.x - pivot.x) / zoom) + pivot.x,
        y = ((tap.y - panOffset.y - pivot.y) / zoom) + pivot.y
    )
}

private fun otherEnd(edge: MemoryGraphExplorerEdge, nodeId: String): String {
    return if (edge.sourceId == nodeId) edge.targetId else edge.sourceId
}

private fun distance(a: Offset, b: Offset): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return sqrt((dx * dx) + (dy * dy))
}

private const val TAP_RADIUS_PX = 50f