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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morimil.app.data.genesis.GenesisIdentitySource
import kotlinx.coroutines.launch

/**
 * Shown once, on first install, before the main tab UI. Matches:
 * "el Bloque Genesis es la semilla, el celular es la tierra". Genesis is
 * bundled in the app; the user names the local instance exactly once.
 */
@Composable
fun OnboardingScreen(viewModel: MorimilViewModel) {
    val genesisResult by viewModel.genesisResult.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

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
            null -> OnboardingCard("Genesis", "Leyendo semilla local empaquetada...", "loading")
            else -> result.fold(
                onSuccess = { source -> GenesisPreview(source) },
                onFailure = { error -> OnboardingCard("Genesis", error.message.orEmpty(), "error") }
            )
        }

        Text("Paso 1: nombra tu instancia. Este nombre se define una sola vez.")
        OutlinedTextField(
            value = alias,
            onValueChange = { alias = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Como quieres llamar a tu instancia?") },
            singleLine = true
        )

        if (working) {
            CircularProgressIndicator()
            Text("Copiando Genesis y creando tu instancia local...")
        }

        Button(
            enabled = genesisResult?.isSuccess == true && alias.isNotBlank() && !working,
            onClick = {
                working = true
                scope.launch {
                    viewModel.bornInstance(alias.trim())
                        .onSuccess {
                            status = "Instancia creada. Genesis local copiado al telefono."
                        }
                        .onFailure { error ->
                            status = error.message.orEmpty()
                            working = false
                        }
                }
            }
        ) {
            Text("Crear mi instancia")
        }

        status?.let { OnboardingCard("Estado", it, "pending") }
    }
}

@Composable
private fun GenesisPreview(source: GenesisIdentitySource) {
    val originLabel = "semilla local empaquetada (${source.origin.label})"
    OnboardingCard(source.identity.alias, "${source.identity.role} / ${source.identity.riskTier}", originLabel)
    OnboardingCard("Genesis verificado", "${source.manifest.fileCount} archivos / ${source.manifest.genesisCoreHash}", source.manifest.manifestId)
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
