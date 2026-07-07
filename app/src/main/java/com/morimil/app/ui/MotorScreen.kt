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
    val initialSuperior = remember(context) { ReasoningProfileRuntimeStore.loadSuperior(context) }
    var endpoint by remember { mutableStateOf(initialConfig.baseUrl) }
    var model by remember { mutableStateOf(initialConfig.model.ifBlank { "llama3.2" }) }
    var pcIp by remember { mutableStateOf("") }
    var saveStatus by remember { mutableStateOf("Config actual cargada.") }
    var superiorEndpoint by remember { mutableStateOf(initialSuperior.baseUrl) }
    var superiorModel by remember { mutableStateOf(initialSuperior.model) }
    var superiorSaveStatus by remember { mutableStateOf("Motor superior runtime sin configurar.") }
    var superiorRuntimeKey by remember { mutableStateOf("") }
    var superiorKeyStatus by remember {
        mutableStateOf(
            if (secretVault.hasReasoningKey(2)) "Llave superior guardada." else "Llave superior no guardada."
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(uiState.title, style = MaterialTheme.typography.headlineMedium)
        Text("Aqui configuras el razonamiento temporal. Morimil mantiene identidad y memoria local en el celular.")
        RuntimeNote()
        ProjectCard(
            "Separacion correcta",
            "Chat conversa y usa voz. Motor/API guarda llave, endpoint, proveedor detectado y modelo.",
            "configured"
        )
        ProjectCard(
            "Privacidad",
            "La API razona con el contexto que entrega la app; la memoria viva sigue siendo local.",
            "private_local"
        )
        LocalBackendConfigCard(
            endpoint = endpoint,
            model = model,
            pcIp = pcIp,
            saveStatus = saveStatus,
            onEndpointChange = { endpoint = it },
            onModelChange = { model = it },
            onPcIpChange = { pcIp = it },
            onUseEmulator = {
                endpoint = "http://10.0.2.2:11434/v1/chat/completions"
                if (model.isBlank()) model = "llama3.2"
            },
            onUsePhysicalDevice = {
                val cleanIp = pcIp.trim()
                if (cleanIp.isNotBlank()) {
                    endpoint = "http://$cleanIp:11434/v1/chat/completions"
                    if (model.isBlank()) model = "llama3.2"
                }
            },
            onSave = {
                val result = configStore.save(
                    ReasoningProviderConfig(
                        preset = ReasoningPreset.LOCAL_COMPATIBLE,
                        baseUrl = endpoint,
                        model = model
                    )
                )
                saveStatus = result.fold(
                    onSuccess = { "Motor local guardado. El siguiente mensaje del chat usara esta configuracion." },
                    onFailure = { error -> "No se pudo guardar: ${error.message ?: error::class.java.simpleName}" }
                )
            }
        )
        SuperiorBackendConfigCard(
            endpoint = superiorEndpoint,
            model = superiorModel,
            saveStatus = superiorSaveStatus,
            runtimeKey = superiorRuntimeKey,
            keyStatus = superiorKeyStatus,
            onEndpointChange = { superiorEndpoint = it },
            onModelChange = { superiorModel = it },
            onRuntimeKeyChange = { superiorRuntimeKey = it },
            onSaveRuntimeKey = {
                val result = secretVault.saveReasoningKey(2, superiorRuntimeKey)
                superiorKeyStatus = result.fold(
                    onSuccess = {
                        superiorRuntimeKey = ""
                        "Llave superior guardada en SecretVault slot 2."
                    },
                    onFailure = { error -> "No se pudo guardar llave superior: ${error.message ?: error::class.java.simpleName}" }
                )
            },
            onClearRuntimeKey = {
                secretVault.clearReasoningKey(2)
                superiorRuntimeKey = ""
                superiorKeyStatus = "Llave superior borrada."
            },
            onSave = {
                val result = ReasoningProfileRuntimeStore.saveSuperior(
                    context,
                    ReasoningProviderConfig(
                        preset = ReasoningPreset.CUSTOM,
                        baseUrl = superiorEndpoint,
                        model = superiorModel
                    )
                )
                superiorSaveStatus = result.fold(
                    onSuccess = { "Motor superior guardado de forma persistente." },
                    onFailure = { error -> "No se pudo guardar superior: ${error.message ?: error::class.java.simpleName}" }
                )
            }
        )
        NativeBrowserScreen()
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
    onUseEmulator: () -> Unit,
    onUsePhysicalDevice: () -> Unit,
    onSave: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Motor local Ollama-compatible", style = MaterialTheme.typography.titleMedium)
            Text(
                "Para emulador usa 10.0.2.2. Para celular fisico usa la IP local de tu PC, por ejemplo 192.168.1.28.",
                style = MaterialTheme.typography.bodySmall
            )
            TextField(
                value = pcIp,
                onValueChange = onPcIpChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("IP de la PC para celular fisico") },
                placeholder = { Text("192.168.1.28") }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onUseEmulator) {
                    Text("Emulador")
                }
                Button(enabled = pcIp.trim().isNotBlank(), onClick = onUsePhysicalDevice) {
                    Text("Celular fisico")
                }
            }
            TextField(
                value = endpoint,
                onValueChange = onEndpointChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Endpoint") }
            )
            TextField(
                value = model,
                onValueChange = onModelChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Modelo") }
            )
            Button(onClick = onSave, enabled = endpoint.isNotBlank() && model.isNotBlank()) {
                Text("Guardar motor local")
            }
            Text(saveStatus, style = MaterialTheme.typography.bodySmall)
        }
    }
}
