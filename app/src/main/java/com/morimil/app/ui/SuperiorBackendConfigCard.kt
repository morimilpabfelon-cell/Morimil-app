package com.morimil.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SuperiorBackendConfigCard(
    endpoint: String,
    model: String,
    saveStatus: String,
    onEndpointChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onSave: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Motor superior", style = MaterialTheme.typography.titleMedium)
            Text(
                "Perfil separado para tareas fuertes. No se usa automaticamente; requiere autorizacion.",
                style = MaterialTheme.typography.bodySmall
            )
            TextField(
                value = endpoint,
                onValueChange = onEndpointChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Endpoint superior") }
            )
            TextField(
                value = model,
                onValueChange = onModelChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Modelo superior") }
            )
            Button(onClick = onSave, enabled = endpoint.isNotBlank() && model.isNotBlank()) {
                Text("Guardar motor superior")
            }
            Text(saveStatus, style = MaterialTheme.typography.bodySmall)
        }
    }
}
