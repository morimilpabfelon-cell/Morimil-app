package com.morimil.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/**
 * Shows this instance's own local chain. Genesis is the seed; the phone is
 * where the living memory grows after birth.
 */
@Composable
fun UserWorkspaceScreen(viewModel: MorimilViewModel) {
    val workspace by viewModel.activeWorkspace.collectAsStateWithLifecycle()
    val localIdentity by viewModel.localIdentity.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var displayName by remember(workspace?.displayName) {
        mutableStateOf(workspace?.displayName ?: "")
    }
    var status by remember { mutableStateOf<String?>(null) }

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
        } ?: WorkspaceCard("Tu cadena", "Aun no se ha creado tu instancia local.", "pending")

        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Nombre para mostrar (solo local)") },
            singleLine = true
        )

        Button(
            enabled = displayName.isNotBlank(),
            onClick = {
                scope.launch {
                    val errors = viewModel.renameWorkspace(displayName)
                    status = if (errors.isEmpty()) "Nombre actualizado." else errors.joinToString("\n")
                }
            }
        ) {
            Text("Guardar nombre")
        }

        status?.let { WorkspaceCard("Estado", it, "local") }
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
