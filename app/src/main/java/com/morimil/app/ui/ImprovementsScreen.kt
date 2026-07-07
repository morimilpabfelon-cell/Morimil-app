package com.morimil.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morimil.app.improvements.ImprovementDecision
import com.morimil.app.improvements.ImprovementDecisionHistoryEntry
import com.morimil.app.improvements.ImprovementProposal
import com.morimil.app.improvements.ImprovementProposalStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun ImprovementsScreen(viewModel: MorimilViewModel) {
    val context = LocalContext.current
    val store = remember(context) { ImprovementProposalStore(context) }
    val scope = rememberCoroutineScope()
    val chatError by viewModel.chatError.collectAsStateWithLifecycle()
    val chatStatus by viewModel.chatOrganismStatus.collectAsStateWithLifecycle()
    val internalIssue by viewModel.internalRuntimeIssue.collectAsStateWithLifecycle()

    var proposals by remember { mutableStateOf(store.loadProposals()) }
    var decisionHistory by remember { mutableStateOf<List<ImprovementDecisionHistoryEntry>>(emptyList()) }
    var scanMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(store) {
        decisionHistory = store.loadDecisionHistory()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Mejoras", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Morimil puede proponer mejoras reales, pero no las aplica por cuenta propia. Tu decision queda registrada localmente.",
            style = MaterialTheme.typography.bodyMedium
        )

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Analisis de seÃƒÂ±ales actuales", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Esto revisa errores de chat, issues internos y estado de memoria. Si encuentra algo util, crea una propuesta para que tu la apruebes o niegues.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Text("Memoria: ${chatStatus.memoryIntegrityLabel}", style = MaterialTheme.typography.bodySmall)

                internalIssue?.let { issue ->
                    Text(
                        "Issue interno: ${issue.component} / ${issue.message}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                chatError?.let { error ->
                    Text(
                        "Error de chat: ${error.take(140)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Button(
                    onClick = {
                        val captured = store.refreshObservedSignals(
                            chatError = chatError,
                            internalComponent = internalIssue?.component,
                            internalMessage = internalIssue?.message,
                            memoryNeedsAttention = chatStatus.memoryNeedsAttention
                        )
                        proposals = store.loadProposals()
                        scanMessage = if (captured == 0) {
                            "Sin seÃƒÂ±ales nuevas. Morimil no encontro un fallo actual que proponer."
                        } else {
                            "Se capturaron $captured seÃƒÂ±al(es) como propuesta(s) revisables."
                        }
                    }
                ) {
                    Text("Analizar seÃƒÂ±ales actuales")
                }

                scanMessage?.let { message ->
                    Text(message, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        DecisionHistoryCard(decisionHistory)

        proposals.forEach { proposal ->
            ImprovementProposalCard(
                proposal = proposal,
                onApprove = {
                    scope.launch {
                        store.approveWithAudit(proposal.id)
                        proposals = store.loadProposals()
                        decisionHistory = store.loadDecisionHistory()
                    }
                },
                onDeny = {
                    scope.launch {
                        store.denyWithAudit(proposal.id)
                        proposals = store.loadProposals()
                        decisionHistory = store.loadDecisionHistory()
                    }
                }
            )
        }
    }
}

@Composable
private fun DecisionHistoryCard(history: List<ImprovementDecisionHistoryEntry>) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Historial de decisiones", style = MaterialTheme.typography.titleMedium)

            if (history.isEmpty()) {
                Text(
                    "Todavia no hay decisiones registradas.",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                history.take(8).forEach { entry ->
                    Text(
                        "${entry.decision.toUiLabel()} Ã‚Â· ${entry.proposalTitle}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        entry.decidedAtMillis.toDecisionTimeLabel(),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun ImprovementProposalCard(
    proposal: ImprovementProposal,
    onApprove: () -> Unit,
    onDeny: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(proposal.title, style = MaterialTheme.typography.titleMedium)
            Text("Estado: ${proposal.decision.toUiLabel()}", style = MaterialTheme.typography.bodyMedium)

            Text("Problema", style = MaterialTheme.typography.titleSmall)
            Text(proposal.problem, style = MaterialTheme.typography.bodyMedium)

            Text("Propuesta", style = MaterialTheme.typography.titleSmall)
            Text(proposal.proposal, style = MaterialTheme.typography.bodyMedium)

            Text("Riesgo", style = MaterialTheme.typography.titleSmall)
            Text(proposal.risk, style = MaterialTheme.typography.bodyMedium)

            Text("Areas afectadas: ${proposal.affectedAreas.joinToString()}", style = MaterialTheme.typography.bodySmall)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = proposal.decision != ImprovementDecision.APPROVED,
                    onClick = onApprove
                ) {
                    Text("Aprobar")
                }
                OutlinedButton(
                    enabled = proposal.decision != ImprovementDecision.DENIED,
                    onClick = onDeny
                ) {
                    Text("Negar")
                }
            }

            when (proposal.decision) {
                ImprovementDecision.APPROVED -> ApprovedActionPlan(proposal)
                ImprovementDecision.PENDING -> Text(
                    "Si apruebas esta mejora, Morimil solo mostrara un plan de accion verificable. No aplicara cambios automaticos.",
                    style = MaterialTheme.typography.bodySmall
                )
                ImprovementDecision.DENIED -> Text(
                    "Propuesta negada. No se genera plan activo ni se aplica ningun cambio.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun ApprovedActionPlan(proposal: ImprovementProposal) {
    if (proposal.actionPlan.isNotEmpty()) {
        Text("Plan de accion verificable", style = MaterialTheme.typography.titleSmall)
        proposal.actionPlan.forEachIndexed { index, step ->
            Text("${index + 1}. $step", style = MaterialTheme.typography.bodyMedium)
        }
    }

    if (proposal.validationChecks.isNotEmpty()) {
        Text("Validacion requerida", style = MaterialTheme.typography.titleSmall)
        proposal.validationChecks.forEachIndexed { index, check ->
            Text("${index + 1}. $check", style = MaterialTheme.typography.bodyMedium)
        }
    }

    Text(
        "Estado: aprobado para planificacion. No ejecutado automaticamente.",
        style = MaterialTheme.typography.bodySmall
    )
}

private fun Long.toDecisionTimeLabel(): String {
    val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return formatter.format(Date(this))
}

private fun ImprovementDecision.toUiLabel(): String {
    return when (this) {
        ImprovementDecision.PENDING -> "Pendiente"
        ImprovementDecision.APPROVED -> "Aprobada"
        ImprovementDecision.DENIED -> "Negada"
    }
}
