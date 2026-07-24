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
    val initialRemoteHelper = remember(context) {
        ReasoningProfileRuntimeStore.loadRemoteHelper(context)
    }
    var localEndpoint by remember { mutableStateOf(initialConfig.baseUrl) }
    var localModel by remember { mutableStateOf(initialConfig.model.ifBlank { "llama3.2" }) }
    var localSaveStatus by remember { mutableStateOf("Configuracion auxiliar cargada.") }
    var remoteEndpoint by remember { mutableStateOf(initialRemoteHelper.baseUrl) }
    var remoteModel by remember { mutableStateOf(initialRemoteHelper.model) }
    var remoteSaveStatus by remember {
        mutableStateOf("Auxiliar remoto temporal sin configurar.")
    }
    var remoteRuntimeKey by remember { mutableStateOf("") }
    var remoteKeyStatus by remember {
        mutableStateOf(remoteKeyStatus(secretVault, remoteEndpoint, REMOTE_HELPER_SLOT_ID))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Arquitectura cognitiva y auxiliares",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            "Los tres motores de Morimil son intuitivo, deliberativo y metacognitivo. " +
                "Ollama y las APIs son proveedores temporales de computo; nunca son motores de Morimil."
        )
        Text(uiState.boundary, style = MaterialTheme.typography.bodySmall)
        MorimilCognitiveArchitectureCard()
        TemporaryHelperBoundaryCard(
            localConfigured = localEndpoint.isNotBlank() && localModel.isNotBlank(),
            remoteConfigured = remoteEndpoint.isNotBlank() && remoteModel.isNotBlank(),
            remoteKeyStored = secretVault.hasReasoningKey(
                slotId = REMOTE_HELPER_SLOT_ID,
                endpoint = remoteEndpoint
            )
        )
        LocalHelperConfigCard(
            endpoint = localEndpoint,
            model = localModel,
            saveStatus = localSaveStatus,
            onEndpointChange = { localEndpoint = it },
            onModelChange = { localModel = it },
            onUseUsb = {
                localEndpoint = "http://127.0.0.1:11434/v1/chat/completions"
                if (localModel.isBlank()) localModel = "llama3.2"
            },
            onSave = {
                val result = configStore.save(
                    ReasoningProviderConfig(
                        preset = ReasoningPreset.LOCAL_USB_HELPER,
                        baseUrl = localEndpoint,
                        model = localModel
                    )
                )
                localSaveStatus = result.fold(
                    onSuccess = {
                        "Auxiliar temporal local guardado. Solo recibira la tarea actual del usuario."
                    },
                    onFailure = { error ->
                        "No se pudo guardar: ${error.message ?: error::class.java.simpleName}"
                    }
                )
            }
        )
        RemoteHelperConfigCard(
            endpoint = remoteEndpoint,
            model = remoteModel,
            saveStatus = remoteSaveStatus,
            runtimeKey = remoteRuntimeKey,
            keyStatus = remoteKeyStatus,
            onEndpointChange = { value ->
                remoteEndpoint = value
                remoteKeyStatus = remoteKeyStatus(
                    secretVault,
                    value,
                    REMOTE_HELPER_SLOT_ID
                )
            },
            onModelChange = { remoteModel = it },
            onRuntimeKeyChange = { remoteRuntimeKey = it },
            onSaveRuntimeKey = {
                val result = secretVault.saveReasoningKey(
                    slotId = REMOTE_HELPER_SLOT_ID,
                    endpoint = remoteEndpoint,
                    key = remoteRuntimeKey
                )
                remoteKeyStatus = result.fold(
                    onSuccess = {
                        remoteRuntimeKey = ""
                        remoteKeyStatus(
                            secretVault,
                            remoteEndpoint,
                            REMOTE_HELPER_SLOT_ID
                        )
                    },
                    onFailure = { error ->
                        "No se pudo guardar llave API: " +
                            (error.message ?: error::class.java.simpleName)
                    }
                )
            },
            onClearRuntimeKey = {
                if (ReasoningEndpointPolicy.isSecureRemoteEndpoint(remoteEndpoint)) {
                    secretVault.clearReasoningKey(REMOTE_HELPER_SLOT_ID, remoteEndpoint)
                } else {
                    secretVault.clearAllReasoningKeys(REMOTE_HELPER_SLOT_ID)
                }
                remoteRuntimeKey = ""
                remoteKeyStatus = "Llave API borrada."
            },
            onSave = {
                val result = ReasoningProfileRuntimeStore.saveRemoteHelper(
                    context,
                    ReasoningProviderConfig(
                        preset = ReasoningPreset.CUSTOM,
                        baseUrl = remoteEndpoint,
                        model = remoteModel
                    )
                )
                remoteSaveStatus = result.fold(
                    onSuccess = {
                        "Auxiliar remoto temporal guardado. Sin acceso a identidad, doctrina, memoria, Genesis ni historial privado."
                    },
                    onFailure = { error ->
                        "No se pudo guardar el auxiliar remoto: " +
                            (error.message ?: error::class.java.simpleName)
                    }
                )
                remoteKeyStatus = remoteKeyStatus(
                    secretVault,
                    remoteEndpoint,
                    REMOTE_HELPER_SLOT_ID
                )
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
        return "Existe una llave antigua sin host. No se usara; vuelve a guardarla para este origen."
    }
    return "Llave API no guardada para este origen."
}

@Composable
private fun MorimilCognitiveArchitectureCard() {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Arquitectura cognitiva de Morimil", style = MaterialTheme.typography.titleMedium)
            Text(
                "El kernel coordina identidad, memoria, doctrina y autoridad. Los motores cognitivos intrinsecos son roles estables de Morimil, no endpoints configurables.",
                style = MaterialTheme.typography.bodySmall
            )
            GateRow("Motor intuitivo intrinseco", true)
            GateRow("Motor deliberativo normal permanece bloqueado", true)
            GateRow("Motor metacognitivo normal permanece bloqueado", true)
            GateRow("Ollama y APIs no cuentan como motores", true)
        }
    }
}

@Composable
private fun TemporaryHelperBoundaryCard(
    localConfigured: Boolean,
    remoteConfigured: Boolean,
    remoteKeyStored: Boolean
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Frontera de auxiliares temporales", style = MaterialTheme.typography.titleMedium)
            Text(
                "Los auxiliares aportan calculo reemplazable. No poseen identidad, memoria, autoridad ni voz intrinseca.",
                style = MaterialTheme.typography.bodySmall
            )
            GateRow("Auxiliar local Ollama USB configurado", localConfigured)
            GateRow("Auxiliar remoto API configurado", remoteConfigured)
            GateRow("Llave remota ligada al host exacto", remoteKeyStored)
            GateRow("Solo reciben la tarea actual del usuario", true)
            GateRow("Sin acceso a memoria, doctrina, Genesis o historial", true)
            GateRow("Salida externa separada de la voz de Morimil", true)
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
private fun LocalHelperConfigCard(
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
            Text(
                "Auxiliar temporal local — Ollama por USB",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "ADB reverse conecta el telefono con el proceso externo en la PC. Aunque use loopback, sigue fuera de Morimil y recibe solo la tarea actual.",
                style = MaterialTheme.typography.bodySmall
            )
            Button(onClick = onUseUsb) {
                Text("Configurar USB")
            }
            TextField(
                value = endpoint,
                onValueChange = onEndpointChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Endpoint auxiliar USB") }
            )
            TextField(
                value = model,
                onValueChange = onModelChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Modelo del auxiliar") }
            )
            Button(onClick = onSave, enabled = endpoint.isNotBlank() && model.isNotBlank()) {
                Text("Guardar auxiliar local")
            }
            Text(saveStatus, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private const val REMOTE_HELPER_SLOT_ID = 2
