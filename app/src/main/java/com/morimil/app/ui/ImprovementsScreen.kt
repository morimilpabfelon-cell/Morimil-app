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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.morimil.app.improvements.ImprovementDecision
import com.morimil.app.improvements.ImprovementProposal
import com.morimil.app.improvements.ImprovementProposalStore

@Composable
fun ImprovementsScreen() {
    val context = LocalContext.current
    val store = remember(context) { ImprovementProposalStore(context) }
    var proposals by remember { mutableStateOf(store.loadProposals()) }

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

        proposals.forEach { proposal ->
            ImprovementProposalCard(
                proposal = proposal,
                onApprove = {
                    store.approve(proposal.id)
                    proposals = store.loadProposals()
                },
                onDeny = {
                    store.deny(proposal.id)
                    proposals = store.loadProposals()
                }
            )
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
        }
    }
}

private fun ImprovementDecision.toUiLabel(): String {
    return when (this) {
        ImprovementDecision.PENDING -> "Pendiente"
        ImprovementDecision.APPROVED -> "Aprobada"
        ImprovementDecision.DENIED -> "Negada"
    }
}
