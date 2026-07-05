package com.morimil.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
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
    Text("Canvas navegable de recuerdos conectados por memory_links.")

    if (selectedEventHash == null) {
        MemoryGraphEmptyCard()
        return
    }

    val snapshot = remember(selectedEventHash, graphEvents, links) {
        MemoryGraphSnapshotBuilder.build(
            selectedEventHash = selectedEventHash,
            events = graphEvents,
            links = links
        )
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Recuerdo central", style = MaterialTheme.typography.titleMedium)
            Text(selectedEvent?.body?.take(220) ?: selectedEventHash.take(32))
            Text("nodes=${snapshot.nodes.size} edges=${snapshot.edges.size} hash=${selectedEventHash.take(24)}")
            Button(onClick = onClearSelection) { Text("Cerrar grafo") }

            MemoryGraphCanvas(
                snapshot = snapshot,
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
            Text("Selecciona un recuerdo para ver sus nodos conectados.")
            AssistChip(onClick = {}, label = { Text("empty") })
        }
    }
}

@Composable
private fun MemoryGraphCanvas(
    snapshot: MemoryGraphSnapshot,
    onSelectEventHash: (String) -> Unit
) {
    val density = LocalDensity.current
    val canvasHeight = 280.dp

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
                .pointerInput(snapshot, positions) {
                    detectTapGestures { tapOffset ->
                        val nearest = positions.minByOrNull { (_, offset) -> distance(offset, tapOffset) }
                        if (nearest != null && distance(nearest.value, tapOffset) <= TAP_RADIUS_PX) {
                            val node = snapshot.nodes.firstOrNull { graphNode -> graphNode.nodeId == nearest.key }
                            if (node?.nodeType == MemoryGraphSnapshotBuilder.MEMORY_EVENT_NODE_TYPE) {
                                onSelectEventHash(node.nodeId)
                            }
                        }
                    }
                }
        ) {
            snapshot.edges.forEach { edge ->
                val source = positions[edge.sourceId]
                val target = positions[edge.targetId]
                if (source != null && target != null) {
                    drawLine(
                        color = Color(0xFF64748B).copy(alpha = (0.25f + (edge.strength.toFloat() * 0.5f)).coerceAtMost(0.85f)),
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

@Composable
private fun MemoryGraphNodeList(
    snapshot: MemoryGraphSnapshot,
    onSelectEventHash: (String) -> Unit
) {
    if (snapshot.isEmpty) {
        Text("No hay nodos visibles todavia.")
        return
    }

    Text("Nodos conectados", style = MaterialTheme.typography.titleMedium)
    snapshot.nodes.take(10).forEach { node ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(if (node.selected) "central" else node.nodeType) })
                AssistChip(onClick = {}, label = { Text("peso=${"%.2f".format(node.weight)}") })
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
        node.memoryKind.contains("decision", ignoreCase = true) -> Color(0xFF7C3AED)
        node.memoryKind.contains("project", ignoreCase = true) -> Color(0xFF0F766E)
        node.memoryKind.contains("preference", ignoreCase = true) -> Color(0xFF059669)
        node.memoryKind.contains("correction", ignoreCase = true) -> Color(0xFFD97706)
        else -> Color(0xFF334155)
    }
}

private fun nodeRadius(node: MemoryGraphNode): Float {
    val base = 10f + (node.weight.coerceIn(0.0, 1.0).toFloat() * 12f)
    return if (node.selected) base + 5f else base
}

private fun distance(a: Offset, b: Offset): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return sqrt((dx * dx) + (dy * dy))
}

private const val TAP_RADIUS_PX = 42f
