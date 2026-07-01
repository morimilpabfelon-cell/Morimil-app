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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.morimil.app.security.SecretVault

@Composable
fun GitHubSyncGateScreen() {
    val context = LocalContext.current
    val vault = remember { SecretVault(context) }
    var tokenDraft by remember { mutableStateOf("") }
    var hasToken by remember { mutableStateOf(vault.hasGitHubToken()) }
    var status by remember {
        mutableStateOf(
            if (hasToken) {
                "Credential gate ready. Real sync remains blocked."
            } else {
                "No credential stored. Real sync remains blocked."
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("GitHub Sync Gate", style = MaterialTheme.typography.headlineMedium)
        Text("Phase 5A: encrypted local credential gate only. No GitHub network calls yet.")

        GateCard("Android Keystore", "AES/GCM key stored in Android Keystore. Token value is not hardcoded.", "active")
        GateCard("Network", "INTERNET permission is not enabled in Phase 5A.", "blocked")
        GateCard("Sync", "No repo creation, file upload, PR, merge, or automation yet.", "blocked")

        OutlinedTextField(
            value = tokenDraft,
            onValueChange = { tokenDraft = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("GitHub token for future approved sync") },
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
                            status = "Token stored locally behind Keystore encryption. Sync still blocked."
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
                    status = "Credential cleared."
                }
            ) {
                Text("Clear")
            }
        }

        GateCard("Credential state", if (hasToken) "Token present, hidden, and local." else "No token present.", status)
        GateCard("Next gate", "Only after review: controlled GitHub read/sync preview.", "MORIMIL_APP_PHASE5A_KEYSTORE_GATE_REVIEW")
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
