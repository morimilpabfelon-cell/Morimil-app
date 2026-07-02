package com.morimil.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.morimil.app.ai.ReasoningConfigStore
import com.morimil.app.ai.ReasoningPreset
import com.morimil.app.ai.ReasoningProviderConfig
import com.morimil.app.ai.ReasoningRuntimeState

@Composable
fun RuntimeNote() {
    val context = LocalContext.current
    val store = remember { ReasoningConfigStore(context) }

    var config by remember { mutableStateOf(store.load()) }
    var endpointDraft by remember(config) { mutableStateOf(config.baseUrl) }
    var modelDraft by remember(config) { mutableStateOf(config.model) }
    var note by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(config) {
        ReasoningRuntimeState.set(config)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Motor", style = MaterialTheme.typography.titleMedium)
            Text("Provider = transporte temporal. Morimil conserva identidad y memoria local.")

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReasoningPreset.entries.forEach { preset ->
                    AssistChip(
                        onClick = {
                            val selected = ReasoningProviderConfig.fromPreset(preset)
                            store.save(selected)
                                .onSuccess {
                                    config = selected
                                    endpointDraft = selected.baseUrl
                                    modelDraft = selected.model
                                    note = "Provider actualizado."
                                }
                                .onFailure { note = it.message }
                        },
                        label = { Text(if (preset == config.preset) "✓ ${preset.displayName}" else preset.displayName) }
                    )
                }
            }

            TextField(
                value = endpointDraft,
                onValueChange = { endpointDraft = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Endpoint") }
            )
            TextField(
                value = modelDraft,
                onValueChange = { modelDraft = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Modelo") }
            )
            Button(onClick = {
                val updated = config.copy(baseUrl = endpointDraft.trim(), model = modelDraft.trim())
                store.save(updated)
                    .onSuccess {
                        config = updated
                        note = "Endpoint y modelo guardados."
                    }
                    .onFailure { note = it.message }
            }) {
                Text("Guardar motor")
            }

            note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
