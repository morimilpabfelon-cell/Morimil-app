package com.morimil.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.morimil.app.ai.DiscoveredReasoningModel
import com.morimil.app.ai.ReasoningConfigStore
import com.morimil.app.ai.ReasoningModelDiscoveryClient
import com.morimil.app.ai.ReasoningProviderConfig
import com.morimil.app.ai.ReasoningRuntimeState
import com.morimil.app.security.SecretVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RuntimeNote() {
    val context = LocalContext.current
    val store = remember { ReasoningConfigStore(context) }
    val vault = remember { SecretVault(context) }
    val discoveryClient = remember { ReasoningModelDiscoveryClient() }
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf(store.load()) }
    var endpoint by remember(config) { mutableStateOf(config.baseUrl) }
    var model by remember(config) { mutableStateOf(config.model) }
    var keyDraft by remember { mutableStateOf("") }
    var providerLabel by remember { mutableStateOf<String?>(null) }
    var discoveredModels by remember { mutableStateOf<List<DiscoveredReasoningModel>>(emptyList()) }
    var discoveredConfig by remember { mutableStateOf<ReasoningProviderConfig?>(null) }
    var isDiscovering by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(config) {
        ReasoningRuntimeState.set(config)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Motor", style = MaterialTheme.typography.titleMedium)
            Text("Razonamiento configurable. Morimil conserva identidad y memoria local.")
            TextField(
                value = keyDraft,
                onValueChange = { keyDraft = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Pega tu API key") },
                visualTransformation = PasswordVisualTransformation()
            )
            TextField(
                value = endpoint,
                onValueChange = { endpoint = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Endpoint") }
            )
            TextField(
                value = model,
                onValueChange = { model = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Modelo") }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !isDiscovering,
                    onClick = {
                        scope.launch {
                            isDiscovering = true
                            note = "Detectando modelos..."
                            val result = withContext(Dispatchers.IO) {
                                discoveryClient.discover(keyDraft, endpoint)
                            }
                            result
                                .onSuccess { discovery ->
                                    providerLabel = discovery.providerLabel
                                    discoveredModels = discovery.models
                                    discoveredConfig = ReasoningProviderConfig.fromPreset(discovery.preset)
                                        .copy(baseUrl = discovery.endpoint)
                                    endpoint = discovery.endpoint
                                    discovery.bestModel?.let { best ->
                                        model = best.id
                                    }
                                    note = discovery.note
                                }
                                .onFailure { error ->
                                    note = error.message ?: "No se pudo detectar el motor."
                                }
                            isDiscovering = false
                        }
                    }
                ) {
                    Text(if (isDiscovering) "Detectando..." else "Detectar motores")
                }
                Button(onClick = {
                    val base = discoveredConfig ?: config
                    val updated = base.copy(baseUrl = endpoint.trim(), model = model.trim())
                    store.save(updated)
                        .onSuccess {
                            config = updated
                            if (keyDraft.isNotBlank()) {
                                vault.saveReasoningKey(keyDraft)
                            }
                            keyDraft = ""
                            note = "Motor guardado."
                        }
                        .onFailure { note = it.message }
                }) {
                    Text("Guardar motor")
                }
            }
            providerLabel?.let {
                Text("Proveedor detectado: $it", style = MaterialTheme.typography.bodySmall)
            }
            if (discoveredModels.isNotEmpty()) {
                Text("Modelos disponibles", style = MaterialTheme.typography.bodySmall)
                discoveredModels.take(8).forEach { candidate ->
                    Button(
                        modifier = Modifier.wrapContentWidth(),
                        onClick = {
                            model = candidate.id
                            val base = discoveredConfig ?: config
                            val updated = base.copy(baseUrl = endpoint.trim(), model = candidate.id)
                            store.save(updated)
                                .onSuccess {
                                    config = updated
                                    if (keyDraft.isNotBlank()) vault.saveReasoningKey(keyDraft)
                                    note = "Modelo seleccionado: ${candidate.label}"
                                }
                                .onFailure { note = it.message }
                        }
                    ) {
                        Text(candidate.label)
                    }
                }
            }
            note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}