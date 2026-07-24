package com.morimil.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
    allowPrivateContextToRemote: Boolean,
    onEndpointChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onRuntimeKeyChange: (String) -> Unit,
    onAllowPrivateContextChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onSaveRuntimeKey: () -> Unit,
    onClearRuntimeKey: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Motor 3 — auxiliar remoto API", style = MaterialTheme.typography.titleMedium)
            Text(
                "Perfil remoto separado. No reemplaza el nucleo de Morimil y solo se usa despues de una autorizacion de escalamiento.",
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
                label = { Text("Modelo API") }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Permitir contexto privado completo")
                    Text(
                        "Desactivado por defecto. Al activarlo, doctrina, memoria, capsulas e historial pueden enviarse a este proveedor remoto.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = allowPrivateContextToRemote,
                    onCheckedChange = onAllowPrivateContextChange
                )
            }
            Button(onClick = onSave, enabled = endpoint.isNotBlank() && model.isNotBlank()) {
                Text("Guardar perfil remoto")
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
