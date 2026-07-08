package com.morimil.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.morimil.app.ai.ReasoningConfigStore
import com.morimil.app.ai.ReasoningProfileRuntimeStore
import com.morimil.app.ai.ReasoningProviderConfig
import com.morimil.app.ai.ReasoningPreset
import com.morimil.app.core.memory.MemoryBacklink
import com.morimil.app.core.memory.MemoryBacklinkGraphBuilder
import com.morimil.app.data.genesis.CurrentMobileAppCapabilities
import com.morimil.app.data.genesis.GenesisIdentitySource
import com.morimil.app.data.local.MemoryEventEntity
import com.morimil.app.data.local.MemoryLinkEntity
import com.morimil.app.data.local.MigrationRecordEntity
import com.morimil.app.data.local.RecallScheduleEntity
import com.morimil.app.runtime.RestCycleScheduleStatus
import com.morimil.app.security.SecretVault
import java.util.Locale

@Composable
fun MotorScreen(viewModel: MotorViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val configStore = remember(context) { ReasoningConfigStore(context) }
    val secretVault = remember(context) { SecretVault(context) }
    val initialConfig = remember(configStore) { configStore.load() }
    val initialThirdMotor = remember(context) { ReasoningProfileRuntimeStore.loadSuperior(context) }
    var endpoint by remember { mutableStateOf(initialConfig.baseUrl) }
    var model by remember { mutableStateOf(initialConfig.model.ifBlank { "llama3.2" }) }
    var pcIp by remember { mutableStateOf("") }
    var saveStatus by remember { mutableStateOf("Config actual cargada.") }
    var thirdMotorEndpoint by remember { mutableStateOf(initialThirdMotor.baseUrl) }
    var thirdMotorModel by remember { mutableStateOf(initialThirdMotor.model) }
    var thirdMotorSaveStatus by remember { mutableStateOf("Motor auxiliar remoto API sin configurar.") }
    var thirdMotorRuntimeKey by remember { mutableStateOf("") }
    var thirdMotorKeyStatus by remember {
        mutableStateOf(
            if (secretVault.hasReasoningKey(2)) "Llave API guardada." else "Llave API no guardada."
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(uiState.title, style = MaterialTheme.typography.headlineMedium)
        Text("Aqui configuras motores auxiliares. Morimil mantiene identidad, memoria y nucleo local en el celular.")
        RuntimeNote()
        ReasoningKernelGatesCard(
            localConfigured = endpoint.isNotBlank() && model.isNotBlank(),
            remoteProfileConfigured = thirdMotorEndpoint.isNotBlank() && thirdMotorModel.isNotBlank(),
            remoteKeyStored = secretVault.hasReasoningKey(2),
            externalWebIsContextOnly = true
        )
        LocalBackendConfigCard(
            endpoint = endpoint,
            model = model,
            pcIp = pcIp,
            saveStatus = saveStatus,
            onEndpointChange = { endpoint = it },
            onModelChange = { model = it },
            onPcIpChange = { pcIp = it },
            onUseUsb = {
                endpoint = "http://127.0.0.1:11434/v1/chat/completions"
                if (model.isBlank()) model = "llama3.2"
            },
            onUseLan = {
                val cleanIp = pcIp.trim()
                if (cleanIp.isNotBlank()) {
                    endpoint = "http://$cleanIp:11434/v1/chat/completions"
                    if (model.isBlank()) model = "llama3.2"
                }
            },
            onSave = {
                val result = configStore.save(
                    ReasoningProviderConfig(
                        preset = localHelperPresetFor(endpoint),
                        baseUrl = endpoint,
                        model = model
                    )
                )
                saveStatus = result.fold(
                    onSuccess = { "Motor auxiliar local guardado. El siguiente mensaje del chat usara esta configuracion." },
                    onFailure = { error -> "No se pudo guardar: ${error.message ?: error::class.java.simpleName}" }
                )
            }
        )
        RemoteApiBackendConfigCard(
            endpoint = thirdMotorEndpoint,
            model = thirdMotorModel,
            saveStatus = thirdMotorSaveStatus,
            runtimeKey = thirdMotorRuntimeKey,
            keyStatus = thirdMotorKeyStatus,
            onEndpointChange = { thirdMotorEndpoint = it },
            onModelChange = { thirdMotorModel = it },
            onRuntimeKeyChange = { thirdMotorRuntimeKey = it },
            onSaveRuntimeKey = {
                val result = secretVault.saveReasoningKey(2, thirdMotorRuntimeKey)
                thirdMotorKeyStatus = result.fold(
                    onSuccess = {
                        thirdMotorRuntimeKey = ""
                        "Llave API guardada en SecretVault slot 2."
                    },
                    onFailure = { error -> "No se pudo guardar llave API: ${error.message ?: error::class.java.simpleName}" }
                )
            },
            onClearRuntimeKey = {
                secretVault.clearReasoningKey(2)
                thirdMotorRuntimeKey = ""
                thirdMotorKeyStatus = "Llave API borrada."
            },
            onSave = {
                val result = ReasoningProfileRuntimeStore.saveSuperior(
                    context,
                    ReasoningProviderConfig(
                        preset = ReasoningPreset.CUSTOM,
                        baseUrl = thirdMotorEndpoint,
                        model = thirdMotorModel
                    )
                )
                thirdMotorSaveStatus = result.fold(
                    onSuccess = { "Motor auxiliar remoto API guardado de forma persistente." },
                    onFailure = { error -> "No se pudo guardar API remota: ${error.message ?: error::class.java.simpleName}" }
                )
            }
        )
    }
}

@Composable
private fun ReasoningKernelGatesCard(
    localConfigured: Boolean,
    remoteProfileConfigured: Boolean,
    remoteKeyStored: Boolean,
    externalWebIsContextOnly: Boolean
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Topologia de motores", style = MaterialTheme.typography.titleMedium)
            Text(
                "Morimil conserva el nucleo. Los modelos local/remoto son motores auxiliares de computo.",
                style = MaterialTheme.typography.bodySmall
            )
            GateRow("Motor nucleo de Morimil activo", true)
            GateRow("Motor auxiliar local configurado", localConfigured)
            GateRow("Motor auxiliar remoto API configurado", remoteProfileConfigured)
            GateRow("Llave API en SecretVault slot 2", remoteKeyStored)
            GateRow("Web externa entra como contexto", externalWebIsContextOnly)
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
    pcIp: String,
    saveStatus: String,
    onEndpointChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onPcIpChange: (String) -> Unit,
    onUseUsb: () -> Unit,
    onUseLan: () -> Unit,
    onSave: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Motor auxiliar local Ollama", style = MaterialTheme.typography.titleMedium)
            Text(
                "USB usa 127.0.0.1 con ADB reverse. LAN usa la IP local de tu PC, por ejemplo 192.168.1.28.",
                style = MaterialTheme.typography.bodySmall
            )
            TextField(
                value = pcIp,
                onValueChange = onPcIpChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("IP de la PC para LAN") },
                placeholder = { Text("192.168.1.28") }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onUseUsb) {
                    Text("USB")
                }
                Button(enabled = pcIp.trim().isNotBlank(), onClick = onUseLan) {
                    Text("LAN")
                }
            }
            TextField(
                value = endpoint,
                onValueChange = onEndpointChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Endpoint local") }
            )
            TextField(
                value = model,
                onValueChange = onModelChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Modelo local") }
            )
            Button(onClick = onSave, enabled = endpoint.isNotBlank() && model.isNotBlank()) {
                Text("Guardar motor auxiliar local")
            }
            Text(saveStatus, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RemoteApiBackendConfigCard(
    endpoint: String,
    model: String,
    saveStatus: String,
    runtimeKey: String,
    keyStatus: String,
    onEndpointChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onRuntimeKeyChange: (String) -> Unit,
    onSaveRuntimeKey: () -> Unit,
    onClearRuntimeKey: () -> Unit,
    onSave: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Motor auxiliar remoto API", style = MaterialTheme.typography.titleMedium)
            Text(
                "Panel limpio para OpenAI, Claude/Messages, Responses-compatible u otro proveedor remoto. No uses Ollama aqui.",
                style = MaterialTheme.typography.bodySmall
            )
            TextField(
                value = endpoint,
                onValueChange = onEndpointChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Endpoint API remoto") },
                placeholder = { Text("https://api.openai.com/v1/responses") }
            )
            TextField(
                value = model,
                onValueChange = onModelChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Modelo remoto") },
                placeholder = { Text("modelo de razonamiento del proveedor") }
            )
            TextField(
                value = runtimeKey,
                onValueChange = onRuntimeKeyChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Llave API remota") }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSaveRuntimeKey, enabled = runtimeKey.isNotBlank()) {
                    Text("Guardar llave API")
                }
                Button(onClick = onClearRuntimeKey) {
                    Text("Borrar llave API")
                }
            }
            Text(keyStatus, style = MaterialTheme.typography.bodySmall)
            Button(onClick = onSave, enabled = endpoint.isNotBlank() && model.isNotBlank()) {
                Text("Guardar motor auxiliar remoto API")
            }
            Text(saveStatus, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun localHelperPresetFor(endpoint: String): ReasoningPreset {
    val clean = endpoint.trim().lowercase(Locale.ROOT)
    return if (clean.startsWith("http://127.0.0.1") || clean.startsWith("http://localhost")) {
        ReasoningPreset.LOCAL_USB_HELPER
    } else {
        ReasoningPreset.LOCAL_LAN_HELPER
    }
}
