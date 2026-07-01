package com.morimil.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morimil.app.data.genesis.GenesisIdentitySource
import com.morimil.app.data.genesis.GenesisOrigin
import kotlinx.coroutines.launch

/**
 * Shown once, on first install, before the main tab UI. Matches:
 * "el Bloque Genesis es la semilla, el celular es la tierra" -- reads
 * Genesis, forks it under the user's own GitHub account (so this instance's
 * memory will live in that fork, never the shared Genesis repo), then names
 * the local instance.
 */
@Composable
fun OnboardingScreen(viewModel: MorimilViewModel) {
    val genesisResult by viewModel.genesisResult.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var tokenDraft by remember { mutableStateOf("") }
    var hasToken by remember { mutableStateOf(viewModel.hasGitHubToken()) }
    var alias by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Bienvenido a Morimil", style = MaterialTheme.typography.headlineMedium)
        Text("El Bloque Genesis es la semilla. Tu celular es la tierra donde crece tu instancia.")

        when (val result = genesisResult) {
            null -> OnboardingCard("Genesis", "Leyendo reglas base desde GitHub...", "loading")
            else -> result.fold(
                onSuccess = { source -> GenesisPreview(source) },
                onFailure = { error -> OnboardingCard("Genesis", error.message.orEmpty(), "error") }
            )
        }

        if (!hasToken) {
            Text("Paso 1: token de GitHub, para que la app cree tu fork de Morimil.")
            OutlinedTextField(
                value = tokenDraft,
                onValueChange = { tokenDraft = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("GitHub token") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )
            Button(
                enabled = tokenDraft.isNotBlank(),
                onClick = {
                    viewModel.saveGitHubToken(tokenDraft)
                        .onSuccess {
                            tokenDraft = ""
                            hasToken = true
                            status = null
                        }
                        .onFailure { error -> status = error.message.orEmpty() }
                }
            ) {
                Text("Guardar token")
            }
        } else {
            Text("Paso 2: nombra tu instancia. La app forkeara Genesis bajo tu cuenta.")
            OutlinedTextField(
                value = alias,
                onValueChange = { alias = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Como quieres llamar a tu instancia?") },
                singleLine = true
            )

            if (working) {
                CircularProgressIndicator()
                Text("Creando tu fork y tu instancia...")
            }

            Button(
                enabled = genesisResult?.isSuccess == true && alias.isNotBlank() && !working,
                onClick = {
                    working = true
                    scope.launch {
                        viewModel.bornInstance(alias.trim())
                            .onSuccess {
                                status = "Instancia creada. Fork listo bajo tu cuenta de GitHub."
                            }
                            .onFailure { error ->
                                status = error.message.orEmpty()
                                working = false
                            }
                    }
                }
            ) {
                Text("Fork Genesis y crear mi instancia")
            }
        }

        status?.let { OnboardingCard("Estado", it, "pending") }
    }
}

@Composable
private fun GenesisPreview(source: GenesisIdentitySource) {
    val originLabel = when (source.origin) {
        GenesisOrigin.GITHUB_LIVE -> "leido en vivo desde GitHub"
        GenesisOrigin.BUNDLED_FALLBACK -> "sin red -- usando copia local empaquetada"
    }
    OnboardingCard(source.identity.alias, "${source.identity.role} / ${source.identity.riskTier}", originLabel)
    OnboardingCard("Acciones permitidas", source.identity.allowedActions.joinToString(", "), "bounded")
    OnboardingCard("Acciones prohibidas", source.identity.disallowedActions.joinToString(", "), "protected")
}

@Composable
private fun OnboardingCard(title: String, description: String, status: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description)
            AssistChip(onClick = {}, label = { Text(status) })
        }
    }
}
