package com.morimil.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.morimil.app.ai.ReasoningRuntimeState

@Composable
fun RuntimeNote() {
    val context = LocalContext.current
    val store = remember { ReasoningConfigStore(context) }
    var config by remember { mutableStateOf(store.load()) }
    var endpoint by remember(config) { mutableStateOf(config.baseUrl) }
    var model by remember(config) { mutableStateOf(config.model) }
    var note by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(config) {
        ReasoningRuntimeState.set(config)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Motor", style = MaterialTheme.typography.titleMedium)
            Text("Razonamiento configurable. Morimil conserva identidad y memoria local.")
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
            Button(onClick = {
                val updated = config.copy(baseUrl = endpoint, model = model)
                store.save(updated)
                    .onSuccess {
                        config = updated
                        note = "Motor guardado."
                    }
                    .onFailure { note = it.message }
            }) {
                Text("Guardar motor")
            }
            note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
