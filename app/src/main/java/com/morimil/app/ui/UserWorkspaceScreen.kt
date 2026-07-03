package com.morimil.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Shows this instance's own local chain. Genesis is the seed; the phone is
 * where the living memory grows after birth.
 */
@Composable
fun UserWorkspaceScreen(viewModel: MorimilViewModel) {
    val workspace by viewModel.activeWorkspace.collectAsStateWithLifecycle()
    val localIdentity by viewModel.localIdentity.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Workspace", style = MaterialTheme.typography.headlineMedium)
        Text("Genesis es la semilla. Tu celular guarda la cadena local de esta instancia.")

        WorkspaceCard("Genesis", "Solo lectura. Nunca se escribe en Genesis.", "read-only")
        WorkspaceCard("Phone", "Room/SQLite guarda memoria e identidad local.", "primary")

        localIdentity?.let { identity ->
            WorkspaceCard(
                "Tu cadena local",
                identity.localMemoryUri,
                "on-device"
            )
            WorkspaceCard(
                "Nombre de instancia",
                "${identity.alias}. El nombre solo se define una vez.",
                "locked"
            )
        } ?: WorkspaceCard("Tu cadena", "Aun no se ha creado tu instancia local.", "pending")

        workspace?.let {
            WorkspaceCard("Workspace activo", it.displayName, "local")
        }
    }
}

@Composable
private fun WorkspaceCard(title: String, description: String, status: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description)
            AssistChip(onClick = {}, label = { Text(status) })
        }
    }
}
