package com.morimil.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morimil.app.data.local.AgentInstanceEntity
import com.morimil.app.data.local.DelegatedTaskEntity
import com.morimil.app.data.local.OrchestratorDeviceEntity
import com.morimil.app.data.local.ProjectVaultEntity

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
            description = "Los agentes se crean por boveda y tarea. Morimil coordina, evalua, aprueba y archiva; no ejecuta acciones reales sin autorizacion.",
            status = "protected"
        )

        val activeVaults = uiState.vaults.filter { it.status == "active" }
        val completedVaults = uiState.vaults.filter { it.status != "active" }

        Text("Bovedas activas", style = MaterialTheme.typography.titleMedium)
        if (activeVaults.isEmpty()) {
            ProjectCard("Sin bovedas activas", "Cuando le digas a Morimil que inicie una empresa o proyecto, aparecera aqui como boton.", "empty")
        } else {
            VaultGrid(vaults = activeVaults, onSelect = { selectedVaultId = it.vaultId })
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
    val relatedTasks = uiState.tasks.filter { task ->
        task.goal.contains(vault.displayName, ignoreCase = true) ||
            task.contextSummary.contains(vault.displayName, ignoreCase = true) ||
            relatedAgents.any { agent -> task.assignedAgentId == agent.agentInstanceId }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(onClick = onBack) { Text("< Volver") }
        Text(vault.displayName, style = MaterialTheme.typography.headlineMedium)
        Text(vault.mission)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = {}, label = { Text(vault.status) })
            AssistChip(onClick = {}, label = { Text(vault.healthStatus) })
            AssistChip(onClick = {}, label = { Text("${vault.progressPercent}%") })
            AssistChip(onClick = {}, label = { Text(vault.projectType) })
        }

        ProjectCard("Roadmap", vault.roadmapSummary, vault.healthStatus)

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
private fun VaultGrid(vaults: List<ProjectVaultEntity>, onSelect: (ProjectVaultEntity) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        vaults.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { vault ->
                    Box(modifier = Modifier.weight(1f)) {
                        VaultButton(vault = vault, onClick = { onSelect(vault) })
                    }
                }
                if (row.size == 1) Box(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun VaultButton(vault: ProjectVaultEntity, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(min = 148.dp)
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(vault.displayName, style = MaterialTheme.typography.titleMedium)
            Text(vault.projectType)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = onClick, label = { Text(vault.healthStatus) })
                AssistChip(onClick = onClick, label = { Text("${vault.progressPercent}%") })
            }
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
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(agent.displayName, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(agentStatusLabel(agent.status)) })
                AssistChip(onClick = {}, label = { Text("calidad=${agent.qualityScore}") })
                AssistChip(onClick = {}, label = { Text("errores=${agent.errorCount}") })
            }
            Text(agent.briefing, maxLines = 3)
            Text("plantilla=${agent.templateAgentId}; tarea=${agent.currentTaskId ?: "ninguna"}")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    onAssignTask(
                        agent.agentInstanceId,
                        "Preparar avance verificable para ${vault.displayName}: riesgos, siguiente paso y artefacto esperado."
                    )
                }) { Text("Asignar tarea") }
                Button(onClick = {
                    onSubmitResult(
                        agent.agentInstanceId,
                        "Resultado registrado desde UI; pendiente de revision humana."
                    )
                }) { Text("Enviar resultado") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onMarkWorking(agent.agentInstanceId) }) { Text("Verde") }
                Button(onClick = { onMarkThinking(agent.agentInstanceId) }) { Text("Azul") }
                Button(onClick = { onMarkAwaitingReview(agent.agentInstanceId) }) { Text("Amarillo") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onQuarantine(agent.agentInstanceId) }) { Text("Rojo") }
                Button(onClick = { onRetire(agent.agentInstanceId) }) { Text("Gris") }
                Button(onClick = { onPromote(agent.agentInstanceId) }) { Text("Dorado") }
            }
        }
    }
}

private fun agentStatusLabel(status: String): String {
    return when (status) {
        "working" -> "verde: trabajando"
        "thinking" -> "azul: pensando"
        "awaiting_review" -> "amarillo: revision"
        "error_quarantined" -> "rojo: cuarentena"
        "retired" -> "gris: retirado"
        "promoted" -> "dorado: promovido"
        else -> status
    }
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
