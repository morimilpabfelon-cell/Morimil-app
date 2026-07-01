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
import com.morimil.app.github.GitHubWriteProposal
import com.morimil.app.github.GitHubWriteProposalValidator

@Composable
fun GitHubWriteProposalScreen() {
    val validator = remember { GitHubWriteProposalValidator() }
    var path by remember { mutableStateOf("docs/proposals/morimil-app-phase5c-proposal.md") }
    var commitMessage by remember { mutableStateOf("docs: add approved Morimil app proposal") }
    var content by remember {
        mutableStateOf(
            "# Morimil App Proposal\n\nThis is a local write proposal generated inside the app.\n\nNo GitHub write has been executed.\n"
        )
    }
    var humanApproved by remember { mutableStateOf(false) }
    var validationResult by remember { mutableStateOf("No proposal validated yet.") }
    var approvedPreview by remember { mutableStateOf<GitHubWriteProposal?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("GitHub Write Proposal", style = MaterialTheme.typography.headlineMedium)
        Text("Phase 5C: validates a human-approved write proposal. It does not execute GitHub writes.")

        ProposalCard("Allowed target", "morimilpabfelon-cell/Morimil-app main docs/proposals/*", "fixed")
        ProposalCard("Execution", "No PUT, POST, PATCH, DELETE, PR, merge, or upload is executed in Phase 5C.", "blocked")

        OutlinedTextField(
            value = path,
            onValueChange = { path = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Proposed path") },
            singleLine = true
        )

        OutlinedTextField(
            value = commitMessage,
            onValueChange = { commitMessage = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Commit message") },
            singleLine = true
        )

        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Proposed content") },
            minLines = 5
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(
                checked = humanApproved,
                onCheckedChange = { humanApproved = it }
            )
            Text("Founder reviewed and approves this proposal for future execution gate.")
        }

        Button(
            onClick = {
                val proposal = GitHubWriteProposal(
                    owner = GitHubWriteProposalValidator.ALLOWED_OWNER,
                    repo = GitHubWriteProposalValidator.ALLOWED_REPO,
                    branch = GitHubWriteProposalValidator.ALLOWED_BRANCH,
                    path = path.trim(),
                    commitMessage = commitMessage.trim(),
                    content = content,
                    humanApproved = humanApproved
                )
                val errors = validator.validate(proposal)
                if (errors.isEmpty()) {
                    approvedPreview = proposal
                    validationResult = "Proposal valid for next review gate. No GitHub write executed."
                } else {
                    approvedPreview = null
                    validationResult = errors.joinToString("\n")
                }
            }
        ) {
            Text("Validate proposal")
        }

        ProposalCard("Validation", validationResult, if (approvedPreview == null) "pending" else "valid")
        approvedPreview?.let { proposal ->
            ProposalCard("Approved preview", proposal.target, "proposal-only")
        }
    }
}

@Composable
private fun ProposalCard(title: String, description: String, status: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description)
            AssistChip(onClick = {}, label = { Text(status) })
        }
    }
}
