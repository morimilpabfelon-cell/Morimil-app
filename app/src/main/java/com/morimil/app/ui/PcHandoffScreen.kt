package com.morimil.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morimil.app.data.local.AgentInstanceEntity
import com.morimil.app.data.local.DelegatedTaskEntity
import com.morimil.app.data.local.OrchestratorDeviceEntity
import com.morimil.app.data.local.ProjectVaultEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun PcHandoffScreen(viewModel: PcHandoffViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedVaultId by remember { mutableStateOf<String?>(null) }
    val selectedVault = uiState.vaults.firstOrNull { it.vaultId == selectedVaultId }

    if (selectedVault != null) {
        ProjectVaultDetailScreen(
            vault = selectedVault,
            uiState = uiState,
            onBack = { selectedVaultId = null },
            onComplete = { viewModel.completeVault(selectedVault.vaultId, selectedVault.roadmapSummary) },
            onArchive = { viewModel.archiveVault(selectedVault.vaultId) },
            onApproveTask = viewModel::approveTask,
            onRejectTask = viewModel::rejectTask,
            onCreateAgent = viewModel::createAgentForVault,
            onAssignAgentTask = viewModel::assignAgentTask,
            onSubmitAgentResult = viewModel::submitAgentResult,
            onMarkAgentWorking = viewModel::markAgentWorking,
            onMarkAgentThinking = viewModel::markAgentThinking,
            onMarkAgentAwaitingReview = viewModel::markAgentAwaitingReview,
            onRetireAgent = viewModel::retireAgent,
            onQuarantineAgent = viewModel::quarantineAgent,
            onPromoteAgent = viewModel::promoteAgent
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Bovedas", style = MaterialTheme.typography.headlineMedium)
        Text("Morimil crea bovedas solo cuando hay una intencion real. Cada boveda conserva roadmap, enjambre, avances, decisiones y cierre.")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::seedOrchestration) { Text("Inicializar entornos") }
        }

        ProjectCard(
            title = "Regla operativa",
            description = "Morimil es la mente maestra. Los agentes trabajan dentro de una boveda, con memoria de trabajo local; solo lo util y aprobado se consolida en memoria viva.",
            status = "protected"
        )

        val activeVaults = uiState.vaults.filter { it.status == "active" }
        val completedVaults = uiState.vaults.filter { it.status != "active" }

        Text("Bovedas activas", style = MaterialTheme.typography.titleMedium)
        if (activeVaults.isEmpty()) {
            ProjectCard("Sin bovedas activas", "Cuando le digas a Morimil que inicie una empresa o proyecto, aparecera aqui como boton.", "empty")
        } else {
            VaultGrid(vaults = activeVaults, uiState = uiState, onSelect = { selectedVaultId = it.vaultId })
        }

        Text("Proyectos finalizados", style = MaterialTheme.typography.titleMedium)
        if (completedVaults.isEmpty()) {
            ProjectCard("Finalizados", "Los proyectos completados o archivados quedan aqui sin mezclarse con el trabajo activo.", "empty")
        } else {
            completedVaults.forEach { vault ->
                CompactVaultRow(vault = vault, onSelect = { selectedVaultId = vault.vaultId })
            }
        }

        Text("Entornos autorizables", style = MaterialTheme.typography.titleMedium)
        if (uiState.devices.isEmpty()) {
            ProjectCard("Dispositivos", "Aun no se inicializo el registro de entornos.", "empty")
        } else {
            uiState.devices.forEach { device -> DeviceCard(device) }
        }
    }
}

@Composable
private fun ProjectVaultDetailScreen(
    vault: ProjectVaultEntity,
    uiState: PcHandoffUiState,
    onBack: () -> Unit,
    onComplete: () -> Unit,
    onArchive: () -> Unit,
    onApproveTask: (String) -> Unit,
    onRejectTask: (String) -> Unit,
    onCreateAgent: (String) -> Unit,
    onAssignAgentTask: (String, String) -> Unit,
    onSubmitAgentResult: (String, String) -> Unit,
    onMarkAgentWorking: (String) -> Unit,
    onMarkAgentThinking: (String) -> Unit,
    onMarkAgentAwaitingReview: (String) -> Unit,
    onRetireAgent: (String) -> Unit,
    onQuarantineAgent: (String) -> Unit,
    onPromoteAgent: (String) -> Unit
) {
    val relatedAgents = uiState.agentInstances.filter { agent -> agent.projectVaultId == vault.vaultId }
    val relatedTasks = relatedTasksForVault(vault, relatedAgents, uiState.tasks)
    val signal = vaultSwarmSignal(relatedAgents, relatedTasks)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(onClick = onBack) { Text("< Volver") }
        Text(vault.displayName, style = MaterialTheme.typography.headlineMedium)
        Text(vault.mission)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusDot(signal.color)
            AssistChip(onClick = {}, label = { Text(signal.label) })
            AssistChip(onClick = {}, label = { Text(vault.status) })
            AssistChip(onClick = {}, label = { Text("${vault.progressPercent}%") })
            AssistChip(onClick = {}, label = { Text(vault.projectType) })
        }

        ProjectCard("Roadmap", vault.roadmapSummary, vault.healthStatus)
        ProjectCard(
            "Memoria de trabajo",
            "El enjambre puede recordar su tarea en PC/dispositivo autorizado, pero no escribe directo la memoria principal. Morimil consolida solo decisiones, errores y aprendizajes utiles.",
            "local_working_memory"
        )

        ProjectVaultSwarmGraph(vault = vault, agents = relatedAgents, tasks = relatedTasks)

        Text("Enjambre", style = MaterialTheme.typography.titleMedium)
        Button(onClick = { onCreateAgent(vault.vaultId) }, enabled = vault.status == "active") {
            Text("Crear agente temporal")
        }
        if (relatedAgents.isEmpty()) {
            ProjectCard("Sin agentes activos", "Crea agentes temporales solo cuando la boveda tenga trabajo concreto.", "planning")
        } else {
            relatedAgents.forEach { agent ->
                AgentInstanceCard(
                    vault = vault,
                    agent = agent,
                    onAssignTask = onAssignAgentTask,
                    onSubmitResult = onSubmitAgentResult,
                    onMarkWorking = onMarkAgentWorking,
                    onMarkThinking = onMarkAgentThinking,
                    onMarkAwaitingReview = onMarkAgentAwaitingReview,
                    onRetire = onRetireAgent,
                    onQuarantine = onQuarantineAgent,
                    onPromote = onPromoteAgent
                )
            }
        }

        Text("Tareas relacionadas", style = MaterialTheme.typography.titleMedium)
        if (relatedTasks.isEmpty()) {
            ProjectCard("Sin tareas", "Aun no hay tareas delegadas asociadas a esta boveda.", "empty")
        } else {
            relatedTasks.forEach { task ->
                TaskCard(
                    task = task,
                    onApprove = { onApproveTask(task.taskId) },
                    onReject = { onRejectTask(task.taskId) }
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onComplete, enabled = vault.status == "active") { Text("Marcar completado") }
            Button(onClick = onArchive, enabled = vault.status != "archived") { Text("Archivar") }
        }
    }
}

@Composable
private fun VaultGrid(
    vaults: List<ProjectVaultEntity>,
    uiState: PcHandoffUiState,
    onSelect: (ProjectVaultEntity) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        vaults.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { vault ->
                    val agents = uiState.agentInstances.filter { it.projectVaultId == vault.vaultId }
                    val tasks = relatedTasksForVault(vault, agents, uiState.tasks)
                    Box(modifier = Modifier.weight(1f)) {
                        VaultButton(vault = vault, agents = agents, tasks = tasks, onClick = { onSelect(vault) })
                    }
                }
                if (row.size == 1) Box(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun VaultButton(
    vault: ProjectVaultEntity,
    agents: List<AgentInstanceEntity>,
    tasks: List<DelegatedTaskEntity>,
    onClick: () -> Unit
) {
    val signal = vaultSwarmSignal(agents, tasks)
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(min = 148.dp)
            .clickable(role = Role.Button, onClick = onClick),
        colors = CardDefaults.elevatedCardColors(containerColor = signal.color.copy(alpha = 0.10f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusDot(signal.color)
                Text(vault.displayName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }
            Text(vault.projectType)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                AssistChip(onClick = onClick, label = { Text(signal.label) })
                AssistChip(onClick = onClick, label = { Text("agentes=${agents.size}") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = onClick, label = { Text("tareas=${tasks.size}") })
                AssistChip(onClick = onClick, label = { Text("${vault.progressPercent}%") })
            }
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text("Abrir panel") }
            Text(vault.mission, maxLines = 3)
        }
    }
}

@Composable
private fun CompactVaultRow(vault: ProjectVaultEntity, onSelect: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onSelect)) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(vault.displayName, style = MaterialTheme.typography.titleMedium)
                Text(vault.roadmapSummary, maxLines = 2)
            }
            AssistChip(onClick = onSelect, label = { Text(vault.status) })
        }
    }
}

@Composable
private fun ProjectVaultSwarmGraph(
    vault: ProjectVaultEntity,
    agents: List<AgentInstanceEntity>,
    tasks: List<DelegatedTaskEntity>
) {
    val signal = vaultSwarmSignal(agents, tasks)
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = signal.color.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusDot(signal.color)
                Text("Panel operativo", style = MaterialTheme.typography.titleMedium)
                AssistChip(onClick = {}, label = { Text(signal.label) })
            }
            Canvas(modifier = Modifier.fillMaxWidth().height(240.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = min(size.width, size.height) * 0.34f
                val visibleAgents = agents.take(10)
                drawCircle(color = Color(0xFF263238), radius = 30f, center = center)
                drawCircle(color = signal.color, radius = 36f, center = center, style = Stroke(width = 5f))

                visibleAgents.forEachIndexed { index, agent ->
                    val angle = (2.0 * PI * index / maxOf(visibleAgents.size, 1)) - (PI / 2.0)
                    val node = Offset(
                        x = center.x + cos(angle).toFloat() * radius,
                        y = center.y + sin(angle).toFloat() * radius
                    )
                    drawLine(color = Color(0xFF90A4AE), start = center, end = node, strokeWidth = 2f)
                    drawCircle(color = agentStatusColor(agent.status), radius = 18f, center = node)
                    if (agent.currentTaskId != null) {
                        drawCircle(color = Color(0xFFFFFFFF), radius = 24f, center = node, style = Stroke(width = 3f))
                    }
                }

                tasks.take(8).forEachIndexed { index, task ->
                    val x = size.width * (index + 1) / (min(tasks.size, 8) + 1)
                    val y = size.height - 22f
                    val taskNode = Offset(x, y)
                    drawLine(color = Color(0xFFB0BEC5), start = center, end = taskNode, strokeWidth = 1.5f)
                    drawCircle(color = taskStatusColor(task.status), radius = 10f, center = taskNode)
                }
            }
            Text("Centro: ${vault.displayName}. Nodos grandes: agentes. Nodos inferiores: tareas. Aro blanco: agente con tarea asignada.")
            SwarmLegend()
        }
    }
}

@Composable
private fun SwarmLegend() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LegendRow("verde", "trabajando bien", SWARM_GREEN)
        LegendRow("azul", "pensando/investigando", SWARM_BLUE)
        LegendRow("amarillo", "aprobacion pendiente", SWARM_YELLOW)
        LegendRow("ambar", "esperando revision", SWARM_AMBER)
        LegendRow("rojo", "error/cuarentena", SWARM_RED)
        LegendRow("gris", "retirado/pausado", SWARM_GRAY)
        LegendRow("dorado", "promovido/especializado", SWARM_GOLD)
    }
}

@Composable
private fun LegendRow(label: String, meaning: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        StatusDot(color)
        Text("$label = $meaning")
    }
}

@Composable
private fun AgentInstanceCard(
    vault: ProjectVaultEntity,
    agent: AgentInstanceEntity,
    onAssignTask: (String, String) -> Unit,
    onSubmitResult: (String, String) -> Unit,
    onMarkWorking: (String) -> Unit,
    onMarkThinking: (String) -> Unit,
    onMarkAwaitingReview: (String) -> Unit,
    onRetire: (String) -> Unit,
    onQuarantine: (String) -> Unit,
    onPromote: (String) -> Unit
) {
    val operational = agent.status != "error_quarantined" && agent.status != "retired"
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusDot(agentStatusColor(agent.status))
                Text(agent.displayName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(agentStatusLabel(agent.status)) })
                AssistChip(onClick = {}, label = { Text("calidad=${agent.qualityScore}") })
                AssistChip(onClick = {}, label = { Text("errores=${agent.errorCount}") })
            }
            Text(agent.briefing, maxLines = 3)
            Text("plantilla=${agent.templateAgentId}; tarea=${agent.currentTaskId ?: "ninguna"}")
            Text("creado=${formatTimestamp(agent.createdAtMillis)}; inicio=${formatTimestamp(agent.lastHeartbeatAtMillis ?: agent.createdAtMillis)}; vida=${lifetimeLabel(agent.createdAtMillis)}")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onAssignTask(
                            agent.agentInstanceId,
                            "Preparar avance verificable para ${vault.displayName}: riesgos, siguiente paso y artefacto esperado."
                        )
                    },
                    enabled = operational
                ) { Text("Asignar tarea") }
                Button(
                    onClick = {
                        onSubmitResult(
                            agent.agentInstanceId,
                            "Resultado registrado desde UI; pendiente de revision humana."
                        )
                    },
                    enabled = operational
                ) { Text("Enviar resultado") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onMarkWorking(agent.agentInstanceId) }, enabled = operational) { Text("Verde") }
                Button(onClick = { onMarkThinking(agent.agentInstanceId) }, enabled = operational) { Text("Azul") }
                Button(onClick = { onMarkAwaitingReview(agent.agentInstanceId) }, enabled = operational) { Text("Amarillo") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onQuarantine(agent.agentInstanceId) }, enabled = agent.status != "error_quarantined") { Text("Rojo") }
                Button(onClick = { onRetire(agent.agentInstanceId) }, enabled = agent.status != "retired") { Text("Gris") }
                Button(onClick = { onPromote(agent.agentInstanceId) }, enabled = operational) { Text("Dorado") }
            }
        }
    }
}

private fun relatedTasksForVault(
    vault: ProjectVaultEntity,
    agents: List<AgentInstanceEntity>,
    tasks: List<DelegatedTaskEntity>
): List<DelegatedTaskEntity> {
    val agentIds = agents.map { it.agentInstanceId }.toSet()
    return tasks.filter { task ->
        task.goal.contains(vault.displayName, ignoreCase = true) ||
            task.contextSummary.contains(vault.displayName, ignoreCase = true) ||
            task.assignedAgentId in agentIds
    }
}

private data class SwarmSignal(
    val label: String,
    val color: Color
)

private fun vaultSwarmSignal(
    agents: List<AgentInstanceEntity>,
    tasks: List<DelegatedTaskEntity>
): SwarmSignal {
    return when {
        agents.any { it.status == "error_quarantined" } -> SwarmSignal("rojo: cuarentena", SWARM_RED)
        tasks.any { it.status == "awaiting_approval" } -> SwarmSignal("amarillo: aprobar", SWARM_YELLOW)
        agents.any { it.status == "awaiting_review" } -> SwarmSignal("ambar: revisar", SWARM_AMBER)
        agents.any { it.status == "thinking" } -> SwarmSignal("azul: investigando", SWARM_BLUE)
        agents.any { it.status == "working" || it.status == "promoted" } -> SwarmSignal("verde: operativo", SWARM_GREEN)
        else -> SwarmSignal("gris: sin enjambre", SWARM_GRAY)
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(modifier = Modifier.size(12.dp).background(color = color, shape = CircleShape))
}

private fun agentStatusLabel(status: String): String {
    return when (status) {
        "working" -> "verde: trabajando"
        "thinking" -> "azul: pensando"
        "awaiting_review" -> "ambar: revision"
        "error_quarantined" -> "rojo: cuarentena"
        "retired" -> "gris: retirado"
        "promoted" -> "dorado: promovido"
        else -> status
    }
}

private fun agentStatusColor(status: String): Color {
    return when (status) {
        "working" -> SWARM_GREEN
        "thinking" -> SWARM_BLUE
        "awaiting_review" -> SWARM_AMBER
        "error_quarantined" -> SWARM_RED
        "retired" -> SWARM_GRAY
        "promoted" -> SWARM_GOLD
        else -> SWARM_GRAY
    }
}

private fun taskStatusColor(status: String): Color {
    return when (status) {
        "awaiting_approval" -> SWARM_YELLOW
        "approved" -> SWARM_GREEN
        "rejected" -> SWARM_RED
        "awaiting_review" -> SWARM_AMBER
        else -> SWARM_GRAY
    }
}

private fun formatTimestamp(millis: Long): String {
    return SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(millis))
}

private fun lifetimeLabel(createdAtMillis: Long): String {
    val ageMillis = (System.currentTimeMillis() - createdAtMillis).coerceAtLeast(0L)
    val hours = TimeUnit.MILLISECONDS.toHours(ageMillis)
    if (hours > 0) return "${hours}h"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ageMillis)
    return "${minutes}m"
}

@Composable
private fun DeviceCard(device: OrchestratorDeviceEntity) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(device.displayName, style = MaterialTheme.typography.titleMedium)
            Text("${device.deviceType} / owner=${device.trustedOwner}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(device.authorizationStatus) })
                AssistChip(onClick = {}, label = { Text(device.pairingState) })
                AssistChip(onClick = {}, label = { Text("riesgo=${device.riskLevel}") })
            }
            Text("transportes=${device.allowedTransportsJson}")
        }
    }
}

@Composable
private fun TaskCard(
    task: DelegatedTaskEntity,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(task.goal, style = MaterialTheme.typography.titleMedium)
            Text(task.contextSummary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(task.assignedAgentId) })
                AssistChip(onClick = {}, label = { Text(task.status) })
                AssistChip(onClick = {}, label = { Text("riesgo=${task.riskLevel}") })
            }
            Text("acciones=${task.allowedActionsJson}")
            Text("transportes=${task.allowedTransportsJson}")
            if (task.status == "awaiting_approval") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onApprove) { Text("Aprobar") }
                    Button(onClick = onReject) { Text("Rechazar") }
                }
            }
        }
    }
}

private val SWARM_GREEN = Color(0xFF2E7D32)
private val SWARM_BLUE = Color(0xFF1565C0)
private val SWARM_YELLOW = Color(0xFFFDD835)
private val SWARM_AMBER = Color(0xFFFF8F00)
private val SWARM_RED = Color(0xFFC62828)
private val SWARM_GRAY = Color(0xFF757575)
private val SWARM_GOLD = Color(0xFFB8860B)
