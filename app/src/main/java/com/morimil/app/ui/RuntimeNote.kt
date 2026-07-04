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
import com.morimil.app.ai.ReasoningMotorSlot
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
    var activeSlotId by remember { mutableStateOf(store.loadActiveSlotId()) }
    var slotId by remember { mutableStateOf(activeSlotId) }
    var slot by remember(slotId) { mutableStateOf(store.loadSlot(slotId)) }
    var config by remember(slot) { mutableStateOf(slot.config) }
    var label by remember(slot) { mutableStateOf(slot.displayName) }
    var endpoint by remember(config) { mutableStateOf(config.baseUrl) }
    var model by remember(config) { mutableStateOf(config.model) }
    var keyDraft by remember { mutableStateOf("") }
    var formatLabel by remember { mutableStateOf<String?>(null) }
    var discoveredModels by remember { mutableStateOf<List<DiscoveredReasoningModel>>(emptyList()) }
    var discoveredConfig by remember { mutableStateOf<ReasoningProviderConfig?>(null) }
    var isDiscovering by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(activeSlotId) {
        ReasoningRuntimeState.set(store.loadSlot(activeSlotId).config)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Motor", style = MaterialTheme.typography.titleMedium)
            Text("Hasta ${ReasoningConfigStore.MAX_PROVIDER_SLOTS} APIs de razonamiento. Morimil conserva identidad y memoria local.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = slotId > 1,
                    onClick = {
                        slotId -= 1
                        keyDraft = ""
                        discoveredModels = emptyList()
                        discoveredConfig = null
                        formatLabel = null
                        note = null
                    }
                ) {
                    Text("<")
                }
                Text(
                    text = "Slot $slotId/${ReasoningConfigStore.MAX_PROVIDER_SLOTS}" +
                        if (slotId == activeSlotId) " activo" else "",
                    modifier = Modifier.padding(top = 12.dp)
                )
                Button(
                    enabled = slotId < ReasoningConfigStore.MAX_PROVIDER_SLOTS,
                    onClick = {
                        slotId += 1
                        keyDraft = ""
                        discoveredModels = emptyList()
                        discoveredConfig = null
                        formatLabel = null
                        note = null
                    }
                ) {
                    Text(">")
                }
            }
            TextField(
                value = label,
                onValueChange = { label = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Nombre del motor") }
            )
            TextField(
                value = keyDraft,
                onValueChange = { keyDraft = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("API key del slot (opcional para local)") },
                visualTransformation = PasswordVisualTransformation()
            )
            TextField(
                value = endpoint,
                onValueChange = { endpoint = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Endpoint; vacio detecta API Responses por llave") }
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
	                                    formatLabel = discovery.formatLabel
	                                    discoveredModels = discovery.models
	                                    val bestModel = discovery.bestModel
	                                    val detectedConfig = ReasoningProviderConfig.fromPreset(discovery.preset)
	                                        .copy(
	                                            baseUrl = discovery.endpoint,
                                            model = bestModel?.id ?: model.trim()
                                        )
                                    discoveredConfig = detectedConfig
                                    endpoint = detectedConfig.baseUrl
	                                    if (bestModel != null) model = bestModel.id

	                                    if (bestModel != null) {
                                            val updatedSlot = ReasoningMotorSlot(
                                                id = slotId,
                                                label = label.ifBlank { "Motor $slotId ${discovery.formatLabel}" },
                                                config = detectedConfig,
                                                enabled = true
                                            )
	                                        val keyResult = if (keyDraft.isNotBlank()) {
                                                withContext(Dispatchers.IO) { vault.saveReasoningKey(slotId, keyDraft) }
                                            } else {
                                                Result.success(Unit)
                                            }
	                                        val configResult = store.saveSlot(updatedSlot)
	                                        if (keyResult.isSuccess && configResult.isSuccess) {
                                                store.setActiveSlot(slotId)
                                                activeSlotId = slotId
                                                slot = updatedSlot
	                                            config = detectedConfig
	                                            keyDraft = ""
	                                            note = "${discovery.note} Slot $slotId guardado como motor activo."
	                                        } else {
	                                            note = keyResult.exceptionOrNull()?.message
	                                                ?: configResult.exceptionOrNull()?.message
                                                ?: "No se pudo guardar el motor detectado."
                                        }
                                    } else {
                                        note = discovery.note
                                    }
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
                        val updatedSlot = slot.copy(
                            label = label.ifBlank { "Motor $slotId" },
                            config = updated,
                            enabled = true
                        )
	                    store.saveSlot(updatedSlot)
	                        .onSuccess {
                                store.setActiveSlot(slotId)
                                activeSlotId = slotId
                                slot = updatedSlot
	                            config = updated
	                            if (keyDraft.isNotBlank()) {
	                                vault.saveReasoningKey(slotId, keyDraft)
	                                keyDraft = ""
	                            }
	                            note = "Slot $slotId guardado como motor activo."
	                        }
	                        .onFailure { note = it.message }
	                }) {
	                    Text("Guardar motor")
	                }
	            }
                if (slotId != activeSlotId) {
                    Button(onClick = {
                        store.setActiveSlot(slotId)
                        activeSlotId = slotId
                        ReasoningRuntimeState.set(store.loadSlot(slotId).config)
                        note = "Motor activo cambiado a slot $slotId."
                    }) {
                        Text("Usar este motor")
                    }
                }
	            formatLabel?.let {
	                Text("Formato detectado: $it", style = MaterialTheme.typography.bodySmall)
	            }
	            if (discoveredModels.isNotEmpty()) {
	                Text("Modelos disponibles", style = MaterialTheme.typography.bodySmall)
	                discoveredModels.take(10).forEach { candidate ->
	                    Button(
	                        modifier = Modifier.wrapContentWidth(),
	                        onClick = {
	                            model = candidate.id
	                            val base = discoveredConfig ?: config
	                            val updated = base.copy(baseUrl = endpoint.trim(), model = candidate.id)
                                val updatedSlot = slot.copy(
                                    label = label.ifBlank { "Motor $slotId" },
                                    config = updated,
                                    enabled = true
                                )
	                            store.saveSlot(updatedSlot)
	                                .onSuccess {
                                        store.setActiveSlot(slotId)
                                        activeSlotId = slotId
                                        slot = updatedSlot
	                                    config = updated
	                                    if (keyDraft.isNotBlank()) {
	                                        vault.saveReasoningKey(slotId, keyDraft)
	                                        keyDraft = ""
	                                    }
	                                    note = "Modelo seleccionado en slot $slotId: ${candidate.label}"
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
