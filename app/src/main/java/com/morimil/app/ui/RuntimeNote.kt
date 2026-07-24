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
    var formatLabel by remember { mutableStateOf<String?>(null) }
    var discoveredModels by remember {
        mutableStateOf<List<DiscoveredReasoningModel>>(emptyList())
    }
    var discoveredConfig by remember { mutableStateOf<ReasoningProviderConfig?>(null) }
    var isDiscovering by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(config) {
        ReasoningRuntimeState.set(config)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Auxiliar temporal predeterminado", style = MaterialTheme.typography.titleMedium)
            Text(
                "No es un motor de Morimil. Sea local o remoto, recibe solamente la tarea actual del usuario y su salida se identifica como consultiva externa."
            )

            TextField(
                value = keyDraft,
                onValueChange = { keyDraft = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Llave del auxiliar remoto; vacia para Ollama local") },
                visualTransformation = PasswordVisualTransformation()
            )
            TextField(
                value = endpoint,
                onValueChange = { endpoint = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Endpoint temporal; vacio detecta Responses por llave") }
            )
            TextField(
                value = model,
                onValueChange = { model = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Modelo del auxiliar") }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !isDiscovering,
                    onClick = {
                        scope.launch {
                            isDiscovering = true
                            note = "Detectando modelos auxiliares..."
                            val result = withContext(Dispatchers.IO) {
                                discoveryClient.discover(keyDraft, endpoint)
                            }
                            result
                                .onSuccess { discovery ->
                                    formatLabel = discovery.formatLabel
                                    discoveredModels = discovery.models
                                    val bestModel = discovery.bestModel
                                    val detectedConfig = ReasoningProviderConfig
                                        .fromPreset(discovery.preset)
                                        .copy(
                                            baseUrl = discovery.endpoint,
                                            model = bestModel?.id ?: model.trim()
                                        )
                                    discoveredConfig = detectedConfig
                                    endpoint = detectedConfig.baseUrl
                                    if (bestModel != null) model = bestModel.id

                                    if (bestModel != null) {
                                        val keyResult = withContext(Dispatchers.IO) {
                                            saveKeyForConfig(vault, detectedConfig, keyDraft)
                                        }
                                        val configResult = withContext(Dispatchers.IO) {
                                            store.save(detectedConfig)
                                        }
                                        if (keyResult.isSuccess && configResult.isSuccess) {
                                            config = detectedConfig
                                            keyDraft = ""
                                            note = "${discovery.note} Auxiliar temporal guardado con llave ligada al origen."
                                        } else {
                                            note = keyResult.exceptionOrNull()?.message
                                                ?: configResult.exceptionOrNull()?.message
                                                ?: "No se pudo guardar el auxiliar detectado."
                                        }
                                    } else {
                                        note = discovery.note
                                    }
                                }
                                .onFailure { error ->
                                    note = error.message ?: "No se pudo detectar el auxiliar."
                                }
                            isDiscovering = false
                        }
                    }
                ) {
                    Text(if (isDiscovering) "Detectando..." else "Detectar modelos")
                }
                Button(onClick = {
                    scope.launch {
                        val updated = (discoveredConfig ?: config).copy(
                            baseUrl = endpoint.trim(),
                            model = model.trim()
                        )
                        val configResult = withContext(Dispatchers.IO) { store.save(updated) }
                        val keyResult = if (configResult.isSuccess) {
                            withContext(Dispatchers.IO) {
                                saveKeyForConfig(vault, updated, keyDraft)
                            }
                        } else {
                            Result.success(Unit)
                        }
                        if (configResult.isSuccess && keyResult.isSuccess) {
                            keyDraft = ""
                            config = updated
                            discoveredConfig = updated
                            note = "Auxiliar temporal guardado; la llave remota esta ligada al origen exacto."
                        } else {
                            note = configResult.exceptionOrNull()?.message
                                ?: keyResult.exceptionOrNull()?.message
                                ?: "No se pudo guardar el auxiliar."
                        }
                    }
                }) {
                    Text("Guardar auxiliar")
                }
            }

            formatLabel?.let {
                Text("Formato detectado: $it", style = MaterialTheme.typography.bodySmall)
            }
            if (discoveredModels.isNotEmpty()) {
                Text("Modelos auxiliares disponibles", style = MaterialTheme.typography.bodySmall)
                discoveredModels.take(10).forEach { candidate ->
                    Button(
                        modifier = Modifier.wrapContentWidth(),
                        onClick = {
                            scope.launch {
                                model = candidate.id
                                val updated = (discoveredConfig ?: config).copy(
                                    baseUrl = endpoint.trim(),
                                    model = candidate.id
                                )
                                val configResult = withContext(Dispatchers.IO) { store.save(updated) }
                                val keyResult = if (configResult.isSuccess) {
                                    withContext(Dispatchers.IO) {
                                        saveKeyForConfig(vault, updated, keyDraft)
                                    }
                                } else {
                                    Result.success(Unit)
                                }
                                if (configResult.isSuccess && keyResult.isSuccess) {
                                    keyDraft = ""
                                    config = updated
                                    discoveredConfig = updated
                                    note = "Modelo seleccionado para el auxiliar: ${candidate.label}"
                                } else {
                                    note = configResult.exceptionOrNull()?.message
                                        ?: keyResult.exceptionOrNull()?.message
                                        ?: "No se pudo guardar el modelo auxiliar."
                                }
                            }
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

private fun saveKeyForConfig(
    vault: SecretVault,
    config: ReasoningProviderConfig,
    keyDraft: String
): Result<Unit> {
    if (keyDraft.isBlank() || !config.requiresRuntimeKey) return Result.success(Unit)
    return vault.saveReasoningKey(
        endpoint = config.baseUrl,
        key = keyDraft
    )
}
