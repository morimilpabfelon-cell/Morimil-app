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
fun RemoteHelperConfigCard(
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
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Auxiliar temporal remoto — API",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "No es un motor de Morimil ni una autoridad superior. Recibe solamente la tarea actual del usuario y devuelve una salida consultiva identificada como externa.",
                style = MaterialTheme.typography.bodySmall
            )
            TextField(
                value = endpoint,
                onValueChange = onEndpointChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Endpoint HTTPS remoto") }
            )
            TextField(
                value = model,
                onValueChange = onModelChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Modelo del auxiliar") }
            )
            Button(onClick = onSave, enabled = endpoint.isNotBlank() && model.isNotBlank()) {
                Text("Guardar auxiliar remoto")
            }
            Text(saveStatus, style = MaterialTheme.typography.bodySmall)

            TextField(
                value = runtimeKey,
                onValueChange = onRuntimeKeyChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Llave API para este origen") },
                visualTransformation = PasswordVisualTransformation()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSaveRuntimeKey,
                    enabled = runtimeKey.isNotBlank() && endpoint.isNotBlank()
                ) {
                    Text("Guardar llave ligada al host")
                }
                Button(onClick = onClearRuntimeKey) {
                    Text("Borrar llave")
                }
            }
            Text(keyStatus, style = MaterialTheme.typography.bodySmall)
        }
    }
}
