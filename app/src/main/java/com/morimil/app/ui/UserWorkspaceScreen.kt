package com.morimil.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morimil.app.github.UserRepoProposalValidator

@Composable
fun UserWorkspaceScreen(viewModel: MorimilViewModel) {
    val workspace by viewModel.activeWorkspace.collectAsStateWithLifecycle()
    val validator = remember { UserRepoProposalValidator() }
    var displayName by remember { mutableStateOf(workspace?.displayName ?: "Local Morimil Workspace") }
    var repoOwner by remember { mutableStateOf(workspace?.optionalRepoOwner.orEmpty()) }
    var repoName by remember { mutableStateOf(workspace?.optionalRepoName ?: "my-morimil-workspace") }
    var repoPrivate by remember { mutableStateOf(workspace?.optionalRepoPrivate ?: true) }
    var approved by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Workspace local activo. Repo opcional no validado.") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Workspace", style = MaterialTheme.typography.headlineMedium)
        Text("Phase 5D: Genesis read-only, phone-local primary, optional user repo proposal.")

        WorkspaceCard("Genesis", "Starting seed only. No runtime writes to Genesis.", "read-only")
        WorkspaceCard("Phone", "Room/SQLite stores local memory and workspace state.", "primary")
        WorkspaceCard("GitHub repo", "Optional user-owned repo proposal. No creation is executed.", "proposal-only")

        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Workspace name") },
            singleLine = true
        )

        OutlinedTextField(
            value = repoOwner,
            onValueChange = { repoOwner = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Optional GitHub owner") },
            singleLine = true
        )

        OutlinedTextField(
            value = repoName,
            onValueChange = { repoName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Optional GitHub repo name") },
            singleLine = true
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(checked = repoPrivate, onCheckedChange = { repoPrivate = it })
            Text("Propose private repo")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(checked = approved, onCheckedChange = { approved = it })
            Text("Founder approves this repo proposal for a later execution gate.")
        }

        Button(
            onClick = {
                val errors = validator.validate(repoOwner.trim(), repoName.trim(), approved)
                if (errors.isEmpty()) {
                    viewModel.saveWorkspaceProposal(
                        displayName = displayName,
                        repoOwner = repoOwner,
                        repoName = repoName,
                        repoPrivate = repoPrivate,
                        approved = approved
                    )
                    status = "Repo proposal valid and saved locally. No GitHub repo created."
                } else {
                    status = errors.joinToString("\n")
                }
            }
        ) {
            Text("Validate workspace")
        }

        workspace?.let {
            WorkspaceCard(
                title = it.displayName,
                description = "repo=${it.optionalRepoOwner.orEmpty()}/${it.optionalRepoName.orEmpty()} localPrimary=${it.localPrimary}",
                status = if (it.repoProposalApproved) "approved proposal" else "local-only"
            )
        }
        WorkspaceCard("Validation", status, "phase_5d")
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
