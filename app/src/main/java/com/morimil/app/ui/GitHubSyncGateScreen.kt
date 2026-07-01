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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.morimil.app.github.GitHubReadOnlyClient
import com.morimil.app.github.GitHubRepoStatus
import com.morimil.app.security.SecretVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GitHubSyncGateScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vault = remember { SecretVault(context) }
    val client = remember { GitHubReadOnlyClient() }
    var tokenDraft by remember { mutableStateOf("") }
    var hasToken by remember { mutableStateOf(vault.hasGitHubToken()) }
    var status by remember {
        mutableStateOf(
            if (hasToken) {
                "Credential stored. Read-only preview available."
            } else {
                "No credential stored. Store token before preview."
            }
        )
    }
    var repoStatus by remember { mutableStateOf<GitHubRepoStatus?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("GitHub Sync Preview", style = MaterialTheme.typography.headlineMedium)
        Text("Phase 5B: explicit read-only metadata check. No writes, PRs, merges, or file uploads.")

        GateCard("Android Keystore", "Token is stored encrypted locally through SecretVault.", "active")
        GateCard("Network", "INTERNET enabled only for HTTPS GET repo metadata.", "read-only")
        GateCard("Writes", "Repo creation, file upload, PR and merge remain blocked.", "blocked")

        OutlinedTextField(
            value = tokenDraft,
            onValueChange = { tokenDraft = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("GitHub token") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    vault.saveGitHubToken(tokenDraft)
                        .onSuccess {
                            tokenDraft = ""
                            hasToken = vault.hasGitHubToken()
                            status = "Token stored locally. Read-only preview allowed."
                        }
                        .onFailure { error ->
                            status = error.message.orEmpty()
                        }
                }
            ) {
                Text("Store")
            }

            Button(
                onClick = {
                    vault.clearGitHubToken()
                    tokenDraft = ""
                    hasToken = false
                    repoStatus = null
                    status = "Credential cleared."
                }
            ) {
                Text("Clear")
            }
        }

        Button(
            enabled = hasToken,
            onClick = {
                scope.launch {
                    status = "Checking Morimil-app on GitHub..."
                    val token = vault.readGitHubTokenForApprovedSync().getOrNull()
                    if (token.isNullOrBlank()) {
                        status = "Stored token could not be read."
                        return@launch
                    }

                    val result = withContext(Dispatchers.IO) {
                        client.getRepositoryStatus(
                            owner = "morimilpabfelon-cell",
                            repo = "Morimil-app",
                            token = token
                        )
                    }

                    result
                        .onSuccess {
                            repoStatus = it
                            status = "Read-only GitHub preview complete."
                        }
                        .onFailure { error ->
                            status = error.message.orEmpty()
                        }
                }
            }
        ) {
            Text("Check repo read-only")
        }

        repoStatus?.let { repo ->
            GateCard(repo.fullName, "branch=${repo.defaultBranch}, visibility=${repo.visibility}, private=${repo.privateRepo}", "read-only")
        }

        GateCard("Current state", if (hasToken) "Credential present." else "No credential present.", status)
        GateCard("Next gate", "Only after review: controlled write proposal, still no autonomous mutation.", "MORIMIL_APP_PHASE5B_GITHUB_READONLY_SYNC_REVIEW")
    }
}

@Composable
private fun GateCard(title: String, description: String, status: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description)
            AssistChip(onClick = {}, label = { Text(status) })
        }
    }
}
