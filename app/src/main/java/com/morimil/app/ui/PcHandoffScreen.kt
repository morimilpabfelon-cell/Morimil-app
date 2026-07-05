package com.morimil.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morimil.app.data.local.AgentProfileEntity
import com.morimil.app.data.local.DelegatedTaskEntity
import com.morimil.app.data.local.OrchestratorDeviceEntity

@Composable
fun PcHandoffScreen(viewModel: PcHandoffViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Orquestacion", style = MaterialTheme.typography.headlineMedium)
        Text("Morimil registra entornos propios, agentes especializados y tareas delegadas. La ejecucion real sigue bloqueada hasta autorizacion.")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::seedOrchestration) { Text("Inicializar") }
            Button(onClick = viewModel::proposeAndroidBuildTask) { Text("Tarea Android") }
            Button(onClick = viewModel::proposeRepoReviewTask) { Text("Tarea GitHub") }
        }

        ProjectCard(
            title = "Limite actual",
            description = "Wi-Fi, Bluetooth, USB e internet quedan registrados como transportes permitibles, pero no ejecutan nada hasta el protocolo y aprobacion.",
            status = "protected"
        )

        Text("Entornos autorizables", style = MaterialTheme.typography.titleMedium)
        if (uiState.devices.isEmpty()) {
            ProjectCard("Dispositivos", "Aun no se inicializo el registro de entornos.", "empty")
        } else {
            uiState.devices.forEach { device -> DeviceCard(device) }
        }

        Text("Agentes disponibles", style = MaterialTheme.typography.titleMedium)
        if (uiState.agents.isEmpty()) {
            ProjectCard("Agentes", "Aun no hay perfiles de agente.", "empty")
        } else {
            uiState.agents.forEach { agent -> AgentCard(agent) }
        }

        Text("Tareas delegadas", style = MaterialTheme.typography.titleMedium)
        if (uiState.tasks.isEmpty()) {
            ProjectCard("Delegacion", "Morimil todavia no propuso tareas.", "empty")
        } else {
            uiState.tasks.forEach { task ->
                TaskCard(
                    task = task,
                    onApprove = { viewModel.approveTask(task.taskId) },
                    onReject = { viewModel.rejectTask(task.taskId) }
                )
            }
        }
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
private fun AgentCard(agent: AgentProfileEntity) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(agent.displayName, style = MaterialTheme.typography.titleMedium)
            Text(agent.description)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(agent.role) })
                AssistChip(onClick = {}, label = { Text("riesgo=${agent.riskLevel}") })
                AssistChip(onClick = {}, label = { Text(if (agent.requiresHumanApproval) "requiere_aprobacion" else "auto") })
            }
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