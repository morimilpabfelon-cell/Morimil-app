package com.morimil.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morimil.app.ai.ReasoningConfigStore
import com.morimil.app.ai.ReasoningEndpointPolicy
import com.morimil.app.ai.ReasoningProfileRuntimeStore
import com.morimil.app.ai.ReasoningProviderConfig
import com.morimil.app.ai.ReasoningPreset
import com.morimil.app.security.SecretVault

@Composable
fun MotorScreen(viewModel: MotorViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val configStore = remember(context) { ReasoningConfigStore(context) }
    val secretVault = remember(context) { SecretVault(context) }
    val initialConfig = remember(configStore) { configStore.load() }
    val initialSuperior = remember(context) { ReasoningProfileRuntimeStore.loadSuperior(context) }
    var endpoint by remember { mutableStateOf(initialConfig.baseUrl) }
    var model by remember { mutableStateOf(initialConfig.model.ifBlank { "llama3.2" }) }
    var saveStatus by remember { mutableStateOf("Config actual cargada.") }
    var superiorEndpoint by remember { mutableStateOf(initialSuperior.baseUrl) }
    var superiorModel by remember { mutableStateOf(initialSuperior.model) }
    var allowPrivateContextToRemote by remember {
        mutableStateOf(initialSuperior.allowPrivateContextToRemote)
    }
    var superiorSaveStatus by remember {
        mutableStateOf("Motor auxiliar remoto API sin configurar.")
    }
    var superiorRuntimeKey by remember { mutableStateOf("") }
    var superiorKeyStatus by remember {
        mutableStateOf(remoteKeyStatus(secretVault, superiorEndpoint, 2))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(uiState.title, style = MaterialTheme.typography.headlineMedium)
        Text(
            "Orden real: 1 Morimil nucleo, 2 auxiliar local Ollama por USB, " +
                "3 auxiliar remoto API. Morimil conserva identidad, memoria y autoridad local."
        )
        MorimilCoreMotorCard()
        ReasoningKernelGatesCard(
            localConfigured = endpoint.isNotBlank() && model.isNotBlank(),
            remoteProfileConfigured = superiorEndpoint.isNotBlank() && superiorModel.isNotBlank(),
            remoteKeyStored = secretVault.hasReasoningKey(
                slotId = 2,
                endpoint = superiorEndpoint
            ),
            remotePrivateContextAllowed = allowPrivateContextToRemote,
            externalWebIsContextOnly = true
        )
        LocalBackendConfigCard(
            endpoint = endpoint,
            model = model,
            saveStatus = saveStatus,
            onEndpointChange = { endpoint = it },
            onModelChange = { model = it },
            onUseUsb = {
                endpoint = "http://127.0.0.1:11434/v1/chat/completions"
                if (model.isBlank()) model = "llama3.2"
            },
            onSave = {
                val result = configStore.save(
                    ReasoningProviderConfig(
                        preset = ReasoningPreset.LOCAL_USB_HELPER,
                        baseUrl = endpoint,
                        model = model
                    )
                )
                saveStatus = result.fold(
                    onSuccess = {
                        "Motor 2 auxiliar local Ollama por USB guardado. " +
                            "El siguiente mensaje usara esta configuracion."
                    },
                    onFailure = { error ->
                        "No se pudo guardar: ${error.message ?: error::class.java.simpleName}"
                    }
                )
            }
        )
        SuperiorBackendConfigCard(
            endpoint = superiorEndpoint,
            model = superiorModel,
            saveStatus = superiorSaveStatus,
            runtimeKey = superiorRuntimeKey,
            keyStatus = superiorKeyStatus,
            allowPrivateContextToRemote = allowPrivateContextToRemote,
            onEndpointChange = { value ->
                superiorEndpoint = value
                superiorKeyStatus = remoteKeyStatus(secretVault, value, 2)
            },
            onModelChange = { superiorModel = it },
            onRuntimeKeyChange = { superiorRuntimeKey = it },
            onAllowPrivateContextChange = { allowPrivateContextToRemote = it },
            onSaveRuntimeKey = {
                val result = secretVault.saveReasoningKey(
                    slotId = 2,
                    endpoint = superiorEndpoint,
                    key = superiorRuntimeKey
                )
                superiorKeyStatus = result.fold(
                    onSuccess = {
                        superiorRuntimeKey = ""
                        remoteKeyStatus(secretVault, superiorEndpoint, 2)
                    },
                    onFailure = { error ->
                        "No se pudo guardar llave API: " +
                            (error.message ?: error::class.java.simpleName)
                    }
                )
            },
            onClearRuntimeKey = {
                if (ReasoningEndpointPolicy.isSecureRemoteEndpoint(superiorEndpoint)) {
                    secretVault.clearReasoningKey(2, superiorEndpoint)
                } else {
                    secretVault.clearAllReasoningKeys(2)
                }
                superiorRuntimeKey = ""
                superiorKeyStatus = "Llave API borrada."
            },
            onSave = {
                val result = ReasoningProfileRuntimeStore.saveSuperior(
                    context,
                    ReasoningProviderConfig(
                        preset = ReasoningPreset.CUSTOM,
                        baseUrl = superiorEndpoint,
                        model = superiorModel,
                        allowPrivateContextToRemote = allowPrivateContextToRemote
                    )
                )
                superiorSaveStatus = result.fold(
                    onSuccess = {
                        "Motor 3 auxiliar remoto guardado. Contexto privado remoto: " +
                            if (allowPrivateContextToRemote) "AUTORIZADO" else "BLOQUEADO"
                    },
                    onFailure = { error ->
                        "No se pudo guardar motor 3 API: " +
                            (error.message ?: error::class.java.simpleName)
                    }
                )
                superiorKeyStatus = remoteKeyStatus(secretVault, superiorEndpoint, 2)
            }
        )
    }
}

private fun remoteKeyStatus(vault: SecretVault, endpoint: String, slotId: Int): String {
    if (endpoint.isBlank()) return "Configura primero un endpoint HTTPS remoto."
    if (!ReasoningEndpointPolicy.isSecureRemoteEndpoint(endpoint)) {
        return "La llave solo puede ligarse a un origen HTTPS remoto valido."
    }
    if (vault.hasReasoningKey(slotId, endpoint)) {
        return "Llave API guardada y ligada al origen HTTPS actual."
    }
    if (vault.hasLegacyUnboundReasoningKey(slotId)) {
        return "Existe una llave antigua sin host. Por seguridad no se usara; vuelve a guardarla para este origen."
    }
    return "Llave API no guardada para este origen."
}

@Composable
private fun MorimilCoreMotorCard() {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Motor 1 — Morimil nucleo", style = MaterialTheme.typography.titleMedium)
            Text(
                "Este motor no se configura como API. Es el kernel local que arma contexto, usa memoria viva, aplica identidad y decide que motor auxiliar consultar.",
                style = MaterialTheme.typography.bodySmall
            )
            GateRow("Identidad local activa", true)
            GateRow("Memoria viva local activa", true)
            GateRow("Gobernanza local activa", true)
            GateRow("No requiere endpoint externo", true)
        }
    }
}

@Composable
private fun ReasoningKernelGatesCard(
    localConfigured: Boolean,
    remoteProfileConfigured: Boolean,
    remoteKeyStored: Boolean,
    remotePrivateContextAllowed: Boolean,
    externalWebIsContextOnly: Boolean
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Estado general de motores", style = MaterialTheme.typography.titleMedium)
            Text(
                "Morimil conserva el nucleo. Ollama y API son auxiliares temporales sin autoridad de memoria.",
                style = MaterialTheme.typography.bodySmall
            )
            GateRow("Motor 1 Morimil nucleo activo", true)
            GateRow("Motor 2 auxiliar local Ollama USB configurado", localConfigured)
            GateRow("Motor 3 auxiliar remoto API configurado", remoteProfileConfigured)
            GateRow("Llave remota ligada al host exacto", remoteKeyStored)
            GateRow("Contexto privado remoto bloqueado por defecto", !remotePrivateContextAllowed)
            GateRow("Web externa entra como contexto local", externalWebIsContextOnly)
        }
    }
}

@Composable
private fun GateRow(label: String, ok: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (ok) "OK" else "PENDIENTE", style = MaterialTheme.typography.bodyMedium)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LocalBackendConfigCard(
    endpoint: String,
    model: String,
    saveStatus: String,
    onEndpointChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onUseUsb: () -> Unit,
    onSave: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Motor 2 — auxiliar local Ollama", style = MaterialTheme.typography.titleMedium)
            Text(
                "USB usa 127.0.0.1 con ADB reverse hacia el puerto 11434 de la PC. No usa la red LAN.",
                style = MaterialTheme.typography.bodySmall
            )
            Button(onClick = onUseUsb) {
                Text("Configurar USB")
            }
            TextField(
                value = endpoint,
                onValueChange = onEndpointChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Endpoint local USB") }
            )
            TextField(
                value = model,
                onValueChange = onModelChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Modelo local") }
            )
            Button(onClick = onSave, enabled = endpoint.isNotBlank() && model.isNotBlank()) {
                Text("Guardar motor 2 auxiliar local")
            }
            Text(saveStatus, style = MaterialTheme.typography.bodySmall)
        }
    }
}
