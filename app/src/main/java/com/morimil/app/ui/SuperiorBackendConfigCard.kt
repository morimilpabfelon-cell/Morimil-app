package com.morimil.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun SuperiorBackendConfigCard(
    endpoint: String,
    model: String,
    saveStatus: String,
    runtimeKey: String,
    keyStatus: String,
    onEndpointChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onRuntimeKeyChange: (String) -> Unit,
    onSave: () -> Unit,
    onSaveRuntimeKey: () -> Unit,
    onClearRuntimeKey: () -> Unit
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

            TextField(
                value = runtimeKey,
                onValueChange = onRuntimeKeyChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Llave superior") },
                visualTransformation = PasswordVisualTransformation()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSaveRuntimeKey, enabled = runtimeKey.isNotBlank()) {
                    Text("Guardar llave")
                }
                Button(onClick = onClearRuntimeKey) {
                    Text("Borrar llave")
                }
            }
            Text(keyStatus, style = MaterialTheme.typography.bodySmall)
        }
    }
}