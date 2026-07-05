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
import com.morimil.app.core.memory.MemoryGraphEdge
import com.morimil.app.core.memory.MemoryGraphNode
import com.morimil.app.core.memory.MemoryGraphSnapshot
import com.morimil.app.core.memory.MemoryGraphSnapshotBuilder
import com.morimil.app.data.local.MemoryEventEntity
import com.morimil.app.data.local.MemoryLinkEntity
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun MemoryGraphCanvasPanel(
    selectedEventHash: String?,
    selectedEvent: MemoryEventEntity?,
    graphEvents: List<MemoryEventEntity>,
    links: List<MemoryLinkEntity>,
    onSelectEventHash: (String) -> Unit,
    onClearSelection: () -> Unit
) {
    Text("Grafo visual", style = MaterialTheme.typography.titleMedium)
    Text("Canvas navegable de recuerdos conectados por memory_links. Pellizca para acercar y arrastra para explorar.")

    val snapshot = remember(selectedEventHash, graphEvents, links) {
        MemoryGraphSnapshotBuilder.build(
            selectedEventHash = selectedEventHash,
            events = graphEvents,
            links = links
        )
    }

    if (snapshot.isEmpty) {
        MemoryGraphEmptyCard()
        return
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (snapshot.focused) "Recuerdo central" else "Mapa reciente", style = MaterialTheme.typography.titleMedium)
            Text(
                if (snapshot.focused) {
                    selectedEvent?.body?.take(220) ?: selectedEventHash?.take(32).orEmpty()
                } else {
                    "Vista general de nodos recientes. Abre un recuerdo para entrar en modo foco."
                }
            )
            MemoryGraphStatsRow(snapshot)
            MemoryGraphLegend()
            if (snapshot.focused) {
                Button(onClick = onClearSelection) { Text("Cerrar foco") }
            }

            MemoryGraphCanvas(
                snapshot = snapshot,
                onSelectEventHash = onSelectEventHash
            )

            MemoryGraphConnectionPanel(
                snapshot = snapshot,
                selectedEventHash = selectedEventHash,
                onSelectEventHash = onSelectEventHash
            )
            MemoryGraphNodeList(
                snapshot = snapshot,
                onSelectEventHash = onSelectEventHash
            )
        }
    }
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
private fun MemoryGraphStatsRow(snapshot: MemoryGraphSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MemoryGraphChip(if (snapshot.focused) "modo=foco" else "modo=reciente")
            MemoryGraphChip("nodos=${snapshot.nodes.size}")
            MemoryGraphChip("lineas=${snapshot.edges.size}", attention = snapshot.orphanedEdgeCount > 0)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MemoryGraphChip("eventos=${snapshot.memoryEventNodeCount}")
            MemoryGraphChip("externos=${snapshot.externalNodeCount}")
            MemoryGraphChip("huerfanos=${snapshot.orphanedEdgeCount}", attention = snapshot.orphanedEdgeCount > 0)
        }
    }
}

@Composable
private fun MemoryGraphLegend() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Leyenda", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MemoryGraphChip("identidad", color = Color(0xFF2563EB))
            MemoryGraphChip("decision", color = Color(0xFF7C3AED))
            MemoryGraphChip("proyecto", color = Color(0xFF0F766E))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MemoryGraphChip("preferencia", color = Color(0xFF059669))
            MemoryGraphChip("cuarentena", attention = true)
            MemoryGraphChip("externo", color = Color(0xFF64748B))
        }
    }
}

@Composable
private fun MemoryGraphChip(
    label: String,
    attention: Boolean = false,
    color: Color? = null
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
        onClick = {},
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = containerColor,
            labelColor = labelColor
        )
    )
}

@Composable
private fun MemoryGraphCanvas(
    snapshot: MemoryGraphSnapshot,
    onSelectEventHash: (String) -> Unit
) {
    val density = LocalDensity.current
    val canvasHeight = 300.dp
    var zoom by remember(snapshot) { mutableStateOf(1f) }
    var panOffset by remember(snapshot) { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        zoom = (zoom * zoomChange).coerceIn(0.7f, 2.8f)
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
                        contentDescription = "Grafo visual de memoria. Pellizcar para zoom, arrastrar para mover, tocar nodo para abrir."
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
                                val node = snapshot.nodes.firstOrNull { graphNode -> graphNode.nodeId == nearest.key }
                                if (node?.nodeType == MemoryGraphSnapshotBuilder.MEMORY_EVENT_NODE_TYPE) {
                                    onSelectEventHash(node.nodeId)
                                }
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
                            val quarantineHalo = nodeNeedsAttention(node, snapshot.edges)
                            if (quarantineHalo) {
                                drawCircle(
                                    color = Color(0xFFD97706).copy(alpha = 0.24f),
                                    radius = radius + 22f,
                                    center = center
                                )
                            }
                            if (node.selected) {
                                drawCircle(
                                    color = Color(0xFF2563EB).copy(alpha = 0.16f),
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
private fun MemoryGraphConnectionPanel(
    snapshot: MemoryGraphSnapshot,
    selectedEventHash: String?,
    onSelectEventHash: (String) -> Unit
) {
    val focusNodeId = selectedEventHash ?: snapshot.nodes.firstOrNull()?.nodeId
    if (focusNodeId == null) return

    val nodesById = snapshot.nodes.associateBy { node -> node.nodeId }
    val connections = MemoryGraphSnapshotBuilder.connectionsForNode(snapshot, focusNodeId)
    Text(if (snapshot.focused) "Conexiones del recuerdo" else "Conexiones destacadas", style = MaterialTheme.typography.titleMedium)
    if (connections.isEmpty()) {
        Text("La semilla aun no enlazo este recuerdo con otros nodos.")
        return
    }

    connections.take(8).forEach { edge ->
        val linkedId = if (edge.sourceId == focusNodeId) edge.targetId else edge.sourceId
        val linkedNode = nodesById[linkedId]
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MemoryGraphChip(edge.relation)
                MemoryGraphChip("fuerza=${"%.2f".format(edge.strength)}")
                MemoryGraphChip(edge.verificationState, attention = edge.verificationState == "orphaned")
            }
            Text(edge.reason.take(180), style = MaterialTheme.typography.bodySmall)
            if (linkedNode?.nodeType == MemoryGraphSnapshotBuilder.MEMORY_EVENT_NODE_TYPE) {
                Button(onClick = { onSelectEventHash(linkedNode.nodeId) }) {
                    Text("Abrir ${linkedNode.title.take(72)}")
                }
            } else {
                Text("${edge.sourceType}:${edge.sourceId.take(20)} -> ${edge.targetType}:${edge.targetId.take(20)}")
            }
        }
    }
}

@Composable
private fun MemoryGraphNodeList(
    snapshot: MemoryGraphSnapshot,
    onSelectEventHash: (String) -> Unit
) {
    if (snapshot.isEmpty) {
        Text("La semilla aun no tiene nodos visibles aqui.")
        return
    }

    Text("Nodos conectados", style = MaterialTheme.typography.titleMedium)
    snapshot.nodes.take(10).forEach { node ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MemoryGraphChip(if (node.selected) "central" else node.nodeType, color = graphNodeColor(node))
                MemoryGraphChip("peso=${"%.2f".format(node.weight)}", attention = nodeNeedsAttention(node, snapshot.edges))
            }
            Text(node.title)
            Text(node.subtitle.take(160), style = MaterialTheme.typography.bodySmall)
            if (!node.selected && node.nodeType == MemoryGraphSnapshotBuilder.MEMORY_EVENT_NODE_TYPE) {
                Button(onClick = { onSelectEventHash(node.nodeId) }) {
                    Text("Abrir nodo")
                }
            }
        }
    }
}

private fun layoutGraphNodes(
    nodes: List<MemoryGraphNode>,
    widthPx: Float,
    heightPx: Float
): Map<String, Offset> {
    if (nodes.isEmpty()) return emptyMap()

    val center = Offset(widthPx / 2f, heightPx / 2f)
    if (nodes.size == 1) return mapOf(nodes.first().nodeId to center)

    val radius = (minOf(widthPx, heightPx) * 0.36f).coerceAtLeast(78f)
    val outerNodes = nodes.drop(1)
    val positions = linkedMapOf(nodes.first().nodeId to center)
    outerNodes.forEachIndexed { index, node ->
        val angle = (-PI / 2.0) + ((2.0 * PI * index) / outerNodes.size.toDouble())
        positions[node.nodeId] = Offset(
            x = center.x + (cos(angle) * radius).toFloat(),
            y = center.y + (sin(angle) * radius).toFloat()
        )
    }
    return positions
}

private fun graphNodeColor(node: MemoryGraphNode): Color {
    if (node.selected) return Color(0xFF2563EB)
    if (node.nodeType != MemoryGraphSnapshotBuilder.MEMORY_EVENT_NODE_TYPE) return Color(0xFF64748B)
    return when {
        node.memoryKind.contains("identity", ignoreCase = true) ||
            node.memoryKind.contains("identidad", ignoreCase = true) ||
            node.memoryKind.contains("autobiography", ignoreCase = true) -> Color(0xFF2563EB)
        node.memoryKind.contains("decision", ignoreCase = true) -> Color(0xFF7C3AED)
        node.memoryKind.contains("project", ignoreCase = true) ||
            node.memoryKind.contains("proyecto", ignoreCase = true) -> Color(0xFF0F766E)
        node.memoryKind.contains("preference", ignoreCase = true) ||
            node.memoryKind.contains("preferencia", ignoreCase = true) -> Color(0xFF059669)
        node.memoryKind.contains("correction", ignoreCase = true) ||
            node.memoryKind.contains("cuarentena", ignoreCase = true) ||
            node.memoryKind.contains("quarantine", ignoreCase = true) -> Color(0xFFD97706)
        node.memoryKind.contains("conversation", ignoreCase = true) -> Color(0xFF475569)
        else -> Color(0xFF334155)
    }
}

private fun graphEdgeColor(edge: MemoryGraphEdge): Color {
    if (edge.verificationState == "orphaned") return Color(0xFFD97706)
    return Color(0xFF64748B)
}

private fun nodeRadius(node: MemoryGraphNode): Float {
    val base = 10f + (node.weight.coerceIn(0.0, 1.0).toFloat() * 12f)
    return if (node.selected) base + 5f else base
}

private fun nodeNeedsAttention(node: MemoryGraphNode, edges: List<MemoryGraphEdge>): Boolean {
    val quarantined = node.memoryKind.contains("quarantine", ignoreCase = true) ||
        node.memoryKind.contains("cuarentena", ignoreCase = true) ||
        node.title.contains("quarantine", ignoreCase = true) ||
        node.title.contains("cuarentena", ignoreCase = true)
    val orphanedEdge = edges.any { edge ->
        edge.verificationState == "orphaned" && (edge.sourceId == node.nodeId || edge.targetId == node.nodeId)
    }
    return quarantined || orphanedEdge
}

private fun screenToGraphOffset(tap: Offset, panOffset: Offset, zoom: Float, pivot: Offset): Offset {
    return Offset(
        x = ((tap.x - panOffset.x - pivot.x) / zoom) + pivot.x,
        y = ((tap.y - panOffset.y - pivot.y) / zoom) + pivot.y
    )
}

private fun distance(a: Offset, b: Offset): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return sqrt((dx * dx) + (dy * dy))
}

private const val TAP_RADIUS_PX = 48f