package com.morimil.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.morimil.app.core.memory.MemoryBacklink
import com.morimil.app.core.memory.MemoryBacklinkGraphBuilder
import com.morimil.app.data.genesis.CurrentMobileAppCapabilities
import com.morimil.app.data.genesis.GenesisIdentitySource
import com.morimil.app.data.local.MemoryEventEntity
import com.morimil.app.data.local.MemoryLinkEntity
import com.morimil.app.data.local.MigrationRecordEntity
import java.util.Locale

private const val MEMORY_EVENT_NODE_TYPE = "memory_event"
private const val REST_CYCLE_MIGRATION_TYPE = "rest_cycle.local_consolidation"

private enum class MorimilTab(val label: String, val icon: String) {
    Chat("Chat", "Ch"),
    Voice("Motor", "API"),
    Genesis("Genesis", "Ge"),
    Workspace("Workspace", "Ws"),
    Projects("Projects", "Pj"),
    Memory("Memory", "Me"),
    Handoff("PC", "Ha")
}

@Composable
fun MorimilApp(viewModel: MorimilViewModel = viewModel()) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val localIdentity by viewModel.localIdentity.collectAsStateWithLifecycle()

            if (localIdentity == null) {
                OnboardingScreen(viewModel)
            } else {
                MainTabsScaffold(viewModel)
            }
        }
    }
}

@Composable
private fun MainTabsScaffold(viewModel: MorimilViewModel) {
    var selectedTab by remember { mutableStateOf(MorimilTab.Chat) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                MorimilTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        label = { Text(tab.label) },
                        icon = { Text(tab.icon) }
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab) {
                MorimilTab.Chat -> ChatScreen(viewModel)
                MorimilTab.Voice -> MotorScreen(viewModel)
                MorimilTab.Genesis -> GenesisScreen(viewModel)
                MorimilTab.Workspace -> UserWorkspaceScreen(viewModel)
                MorimilTab.Projects -> ProjectsScreen(viewModel)
                MorimilTab.Memory -> MemoryScreen(viewModel)
                MorimilTab.Handoff -> PcHandoffScreen()
            }
        }
    }
}

@Composable
private fun ChatScreen(viewModel: MorimilViewModel) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()
    val chatError by viewModel.chatError.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, isSending) {
        val target = when {
            isSending -> messages.size
            messages.isNotEmpty() -> messages.lastIndex
            else -> null
        }
        target?.let { listState.animateScrollToItem(it) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Morimil", style = MaterialTheme.typography.headlineMedium)
        Text("Conversacion real, con memoria local como contexto", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))

        ChatVoiceControls(viewModel)
        Spacer(Modifier.height(16.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { message ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Text("${message.author}: ${message.body}", modifier = Modifier.padding(12.dp))
                }
            }
            if (isSending) {
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Morimil esta escribiendo...", modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }

        chatError?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Escribe a Morimil...") },
                enabled = !isSending
            )
            Button(
                enabled = !isSending && draft.isNotBlank(),
                onClick = {
                    viewModel.sendMessage(draft)
                    draft = ""
                }
            ) {
                Text("Enviar")
            }
        }
    }
}

@Composable
private fun ChatVoiceControls(viewModel: MorimilViewModel) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    var voiceStatus by remember { mutableStateOf("Voz lista.") }
    var ttsReady by remember { mutableStateOf(false) }

    val textToSpeech = remember {
        TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
        }
    }

    DisposableEffect(textToSpeech) {
        textToSpeech.language = Locale.getDefault()
        onDispose {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
    }

    val speechRecognizer = remember {
        SpeechRecognizer.createSpeechRecognizer(context)
    }

    DisposableEffect(speechRecognizer) {
        speechRecognizer.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {
                    voiceStatus = "Escuchando..."
                }

                override fun onBeginningOfSpeech() {
                    voiceStatus = "Voz detectada."
                }

                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    voiceStatus = "Procesando voz..."
                }

                override fun onError(error: Int) {
                    voiceStatus = "No se pudo reconocer voz. Codigo: $error"
                }

                override fun onResults(results: android.os.Bundle?) {
                    val bestMatch = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        .orEmpty()
                        .firstOrNull()
                        .orEmpty()

                    if (bestMatch.isBlank()) {
                        voiceStatus = "No se recibio texto."
                    } else {
                        voiceStatus = "Texto reconocido y enviado."
                        viewModel.sendMessage(bestMatch)
                    }
                }

                override fun onPartialResults(partialResults: android.os.Bundle?) = Unit
                override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
            }
        )

        onDispose {
            speechRecognizer.destroy()
        }
    }

    fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla con Morimil")
        }
        speechRecognizer.startListening(intent)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startListening()
        } else {
            voiceStatus = "Permiso de microfono denegado."
        }
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Voz integrada", style = MaterialTheme.typography.titleMedium)
            Text(voiceStatus, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED

                        if (granted) {
                            startListening()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                ) {
                    Text("Hablar")
                }

                Button(
                    enabled = ttsReady && messages.isNotEmpty(),
                    onClick = {
                        val lastMessage = messages.lastOrNull { it.author != "user" }?.body
                            ?: messages.lastOrNull()?.body.orEmpty()
                        textToSpeech.speak(
                            lastMessage,
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            "morimil-chat-last-message"
                        )
                    }
                ) {
                    Text("Leer ultimo")
                }
            }
        }
    }
}
@Composable
private fun MotorScreen(viewModel: MorimilViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Motor/API", style = MaterialTheme.typography.headlineMedium)
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
    }
}
@Composable
private fun VoiceScreen(viewModel: MorimilViewModel) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    var voiceStatus by remember { mutableStateOf("Push-to-talk listo. No escucha en background.") }
    var ttsReady by remember { mutableStateOf(false) }

    val textToSpeech = remember {
        TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
        }
    }

    DisposableEffect(textToSpeech) {
        textToSpeech.language = Locale.getDefault()
        onDispose {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
    }

    val speechRecognizer = remember {
        SpeechRecognizer.createSpeechRecognizer(context)
    }

    DisposableEffect(speechRecognizer) {
        speechRecognizer.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {
                    voiceStatus = "Escuchando..."
                }

                override fun onBeginningOfSpeech() {
                    voiceStatus = "Voz detectada."
                }

                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    voiceStatus = "Procesando voz..."
                }

                override fun onError(error: Int) {
                    voiceStatus = "No se pudo reconocer voz. Codigo: $error"
                }

                override fun onResults(results: android.os.Bundle?) {
                    val matches = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        .orEmpty()
                    val bestMatch = matches.firstOrNull().orEmpty()
                    if (bestMatch.isBlank()) {
                        voiceStatus = "No se recibio texto."
                    } else {
                        voiceStatus = "Texto reconocido y guardado."
                        viewModel.sendMessage(bestMatch)
                    }
                }

                override fun onPartialResults(partialResults: android.os.Bundle?) = Unit
                override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
            }
        )

        onDispose {
            speechRecognizer.destroy()
        }
    }

    fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla con Morimil")
        }
        speechRecognizer.startListening(intent)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startListening()
        } else {
            voiceStatus = "Permiso de microfono denegado."
        }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Voice", style = MaterialTheme.typography.headlineMedium)
        Text("Genesis movil v1 mantiene voz push-to-talk y TTS manual.")
        ProjectCard("SpeechRecognizer", voiceStatus, "controlled")
        ProjectCard("TextToSpeech", if (ttsReady) "Motor TTS listo." else "Inicializando TTS.", "manual")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED

                    if (granted) {
                        startListening()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            ) {
                Text("Hablar")
            }

            Button(
                enabled = ttsReady && messages.isNotEmpty(),
                onClick = {
                    val lastMessage = messages.lastOrNull()?.body.orEmpty()
                    textToSpeech.speak(
                        lastMessage,
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        "morimil-last-message"
                    )
                }
            ) {
                Text("Leer ultimo")
            }
        }

        ProjectCard("Boundary", "Sin sincronizacion externa, sin ejecucion de PC, sin escucha en background.", "protected")
    }
}

@Composable
private fun GenesisScreen(viewModel: MorimilViewModel) {
    val genesisResult by viewModel.genesisResult.collectAsStateWithLifecycle()
    val localIdentity by viewModel.localIdentity.collectAsStateWithLifecycle()
    val genesisCore by viewModel.genesisCore.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Genesis", style = MaterialTheme.typography.headlineMedium)
        Text("Fase Genesis local: lee la semilla empaquetada en la app y la copia al telefono.")

        localIdentity?.let {
            ProjectCard(
                "Esta instancia: ${it.alias}",
                "${it.genesisRole} / ${it.genesisRiskTier}",
                "instance_id=${it.instanceId}"
            )
            ProjectCard(
                "Memoria local",
                "cadena en el dispositivo",
                it.localMemoryUri
            )
        }

        genesisCore?.let {
            ProjectCard(
                "Genesis Core copiado",
                "Origen=${it.sourceOrigin}; nacido=${it.copiedAtMillis}; sha256=${it.contentSha256.take(16)}...",
                "immutable"
            )
        } ?: ProjectCard("Genesis Core", "Aun no hay copia local nacida.", "pending")

        when (val result = genesisResult) {
            null -> ProjectCard("Genesis Reader", "Cargando...", "loading")
            else -> result.fold(
                onSuccess = { source -> GenesisContent(source) },
                onFailure = { error -> ProjectCard("Genesis Reader", error.message.orEmpty(), "error") }
            )
        }
    }
}

@Composable
private fun ProjectsScreen(viewModel: MorimilViewModel) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Projects", style = MaterialTheme.typography.headlineMedium)
        if (projects.isEmpty()) {
            ProjectCard("Morimil_app", "Genesis movil v1: esperando memoria local.", "loading")
        } else {
            projects.forEach { project ->
                ProjectCard(project.title, "Persisted project state in Room.", project.status)
            }
        }
        ProjectCard("Genesis", "Bundled Genesis seed visible in Genesis tab.", "read-only")
    }
}

@Composable
private fun GenesisContent(source: GenesisIdentitySource) {
    val genesis = source.identity
    val capabilities = CurrentMobileAppCapabilities.value
    val originLabel = source.origin.label
    ProjectCard(genesis.alias, "${genesis.role} / ${genesis.riskTier}", originLabel)
    ProjectCard("Genesis verificado", "${source.manifest.fileCount} archivos", source.manifest.genesisCoreHash)
    ProjectCard("Owner", genesis.owner, genesis.schemaVersion)
    ProjectCard("Allowed actions", genesis.allowedActions.joinToString(", "), "bounded")
    ProjectCard("Disallowed actions", genesis.disallowedActions.joinToString(", "), "protected")
    ProjectCard(
            "Mobile app capabilities",
            "memory=${capabilities.localMemory}, voice=${capabilities.voicePushToTalk}, " +
            "external_sync=${capabilities.externalReadOnlySync}, external_write=${capabilities.externalWriteExecution}, " +
            "pc_execution=${capabilities.pcExecution}",
            capabilities.currentAppPhase
        )
}

@Composable
private fun MemoryScreen(viewModel: MorimilViewModel) {
    val decisions by viewModel.decisions.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val snapshot by viewModel.livingMemorySnapshot.collectAsStateWithLifecycle()
    val events by viewModel.recentMemoryEvents.collectAsStateWithLifecycle()
    val recalls by viewModel.activeRecallSchedules.collectAsStateWithLifecycle()
    val migrations by viewModel.recentMigrationRecords.collectAsStateWithLifecycle()
    val selectedMemoryEventHash by viewModel.selectedMemoryEventHash.collectAsStateWithLifecycle()
    val selectedMemoryLinks by viewModel.selectedMemoryLinks.collectAsStateWithLifecycle()
    val eventsByHash = events.associateBy { event -> event.eventHash }
    val selectedMemoryEvent = selectedMemoryEventHash?.let { hash -> eventsByHash[hash] }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Living Memory", style = MaterialTheme.typography.headlineMedium)
        Text("Genesis Core inmutable + eventos append-only + snapshot local.")
        ProjectCard("Conversation memory", "${messages.size} mensajes persistidos.", "connected")
        snapshot?.let {
            ProjectCard(
                "Living snapshot",
                "${it.eventCount} eventos / ${it.messageCount} mensajes. ${it.summary.take(260)}",
                "updated=${it.updatedAtMillis}"
            )
        } ?: ProjectCard("Living snapshot", "Todavia no existe snapshot vivo.", "pending")
        ProjectCard("Project state", "${projects.size} proyectos persistidos.", "connected")
        if (decisions.isEmpty()) {
            ProjectCard("Decision log", "Sin decisiones registradas todavia.", "empty")
        } else {
            decisions.take(3).forEach { decision ->
                ProjectCard(decision.title, "Decision persisted locally.", decision.status)
            }
        }
        Text("Recalls pendientes", style = MaterialTheme.typography.titleMedium)
        Text("Recuerdos que conviene repasar para mantenerlos utiles sin convertir ruido en memoria fuerte.")
        if (recalls.isEmpty()) {
            ProjectCard("Recall schedule", "No hay recalls activos todavia.", "empty")
            Button(onClick = { viewModel.seedRecallScheduleIfNeeded() }) {
                Text("Crear recalls")
            }
        } else {
            recalls.take(8).forEach { recall ->
                ElevatedCard {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("${recall.targetMemoryKind} / priority=${recall.priority}", style = MaterialTheme.typography.titleMedium)
                        Text(recall.prompt.take(360))
                        Text("due=${recall.dueAtMillis} interval=${recall.intervalDays}d action=${recall.lastAction}")
                        Text("reason=${recall.reason.take(180)}")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { viewModel.reinforceRecall(recall.recallId) }) {
                                Text("Reforzar")
                            }
                            Button(onClick = { viewModel.postponeRecall(recall.recallId) }) {
                                Text("Posponer")
                            }
                        }
                        Button(onClick = { viewModel.degradeRecall(recall.recallId) }) {
                            Text("Degradar")
                        }
                    }
                }
            }
        }
        Text("Memory review queue", style = MaterialTheme.typography.titleMedium)
        Text("Los recuerdos son append-only. Aprobar, corregir o degradar crea una revision local nueva; no modifica el evento original.")
        if (events.isEmpty()) {
            ProjectCard("Memory events", "Sin eventos vivos todavia.", "empty")
        } else {
            events.take(12).forEach { event ->
                ElevatedCard {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("${event.memoryKind} / ${event.eventType}", style = MaterialTheme.typography.titleMedium)
                        Text(event.body.take(340))
                        Text(
                            "actor=${event.actor} importance=${event.importance} confidence=${event.confidence} " +
                                "hash=${event.eventHash.take(24)}"
                        )
                        Text("tags=${event.tagsJson.take(160)}")
                        if (selectedMemoryEventHash == event.eventHash) {
                            AssistChip(onClick = {}, label = { Text("Backlinks abiertos") })
                        } else {
                            Button(onClick = { viewModel.selectMemoryEvent(event.eventHash) }) {
                                Text("Ver backlinks")
                            }
                        }
                        Button(onClick = { viewModel.approveMemoryEvent(event) }) {
                            Text("Aprobar")
                        }
                        Button(onClick = { viewModel.degradeMemoryEvent(event) }) {
                            Text("Degradar ruido")
                        }
                        Button(onClick = { viewModel.requestMemoryCorrection(event) }) {
                            Text("Pedir correccion")
                        }
                    }
                }
            }
        }
        RestCycleHistoryPanel(
            migrations = migrations,
            onApproveRestCycle = viewModel::approveRestCycleConsolidation,
            onRunRestCycleNow = viewModel::runRestCycleNow
        )
        MemoryBacklinksPanel(
            selectedEventHash = selectedMemoryEventHash,
            selectedEvent = selectedMemoryEvent,
            selectedLinks = selectedMemoryLinks,
            eventsByHash = eventsByHash,
            onSelectEventHash = viewModel::selectMemoryEvent,
            onClearSelection = viewModel::clearSelectedMemoryEvent
        )
        ProjectCard("Scope guardian", "Sin sincronizacion externa y sin ejecucion de PC.", "protected")
    }
}

@Composable
private fun RestCycleHistoryPanel(
    migrations: List<MigrationRecordEntity>,
    onApproveRestCycle: (String) -> Unit,
    onRunRestCycleNow: () -> Unit
) {
    val restCycles = migrations
        .filter { migration -> migration.migrationType == REST_CYCLE_MIGRATION_TYPE }
        .take(8)

    Text("Rest cycle", style = MaterialTheme.typography.titleMedium)
    Text("Consolidaciones locales programadas, con aprobacion para cambios importantes.")
    Button(onClick = onRunRestCycleNow) {
        Text("Ejecutar descanso ahora")
    }

    if (restCycles.isEmpty()) {
        ProjectCard("Historial de descanso", "Todavia no hay consolidaciones registradas.", "empty")
        return
    }

    restCycles.forEach { migration ->
        ElevatedCard {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("${migration.status} / risk=${migration.riskLevel}", style = MaterialTheme.typography.titleMedium)
                Text(migration.expectedEffect.take(260))
                Text(
                    "approval_required=${migration.approvalRequired} approved=${migration.approvedByUser} " +
                        "rollback=${migration.rollbackAvailable}"
                )
                Text("strategy=${migration.rollbackStrategy.take(220)}")
                if (migration.status == "planned" && migration.approvalRequired && !migration.approvedByUser) {
                    Button(onClick = { onApproveRestCycle(migration.migrationId) }) {
                        Text("Aprobar consolidacion")
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryBacklinksPanel(
    selectedEventHash: String?,
    selectedEvent: MemoryEventEntity?,
    selectedLinks: List<MemoryLinkEntity>,
    eventsByHash: Map<String, MemoryEventEntity>,
    onSelectEventHash: (String) -> Unit,
    onClearSelection: () -> Unit
) {
    Text("Backlinks de memoria", style = MaterialTheme.typography.titleMedium)
    if (selectedEventHash == null) {
        ProjectCard("Grafo de recuerdos", "Selecciona un recuerdo para ver que apunta a el y a que apunta.", "empty")
        return
    }

    val graph = MemoryBacklinkGraphBuilder.buildForNode(
        nodeId = selectedEventHash,
        nodeType = MEMORY_EVENT_NODE_TYPE,
        links = selectedLinks
    )

    ElevatedCard {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Recuerdo seleccionado", style = MaterialTheme.typography.titleMedium)
            Text(selectedEvent?.body?.take(260) ?: selectedEventHash.take(32))
            Text("hash=${selectedEventHash.take(32)} links=${selectedLinks.size}")
            Button(onClick = onClearSelection) {
                Text("Cerrar")
            }
            MemoryBacklinkSection(
                title = "Este recuerdo apunta a...",
                emptyText = "Este recuerdo todavia no apunta a otros nodos.",
                backlinks = graph.outgoing,
                eventsByHash = eventsByHash,
                onSelectEventHash = onSelectEventHash
            )
            MemoryBacklinkSection(
                title = "Este recuerdo es mencionado por...",
                emptyText = "Ningun nodo reciente apunta todavia a este recuerdo.",
                backlinks = graph.incoming,
                eventsByHash = eventsByHash,
                onSelectEventHash = onSelectEventHash
            )
        }
    }
}

@Composable
private fun MemoryBacklinkSection(
    title: String,
    emptyText: String,
    backlinks: List<MemoryBacklink>,
    eventsByHash: Map<String, MemoryEventEntity>,
    onSelectEventHash: (String) -> Unit
) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    if (backlinks.isEmpty()) {
        Text(emptyText)
        return
    }

    backlinks.take(8).forEach { backlink ->
        val linkedEvent = eventsByHash[backlink.linkedNodeId]
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${backlink.link.relation} / strength=${backlink.link.strength} / ${backlink.link.reason.take(120)}")
            if (backlink.linkedNodeType == MEMORY_EVENT_NODE_TYPE) {
                Button(onClick = { onSelectEventHash(backlink.linkedNodeId) }) {
                    Text(memoryNodeLabel(linkedEvent, backlink.linkedNodeId, backlink.linkedNodeType).take(90))
                }
            } else {
                AssistChip(
                    onClick = {},
                    label = { Text(memoryNodeLabel(linkedEvent, backlink.linkedNodeId, backlink.linkedNodeType).take(90)) }
                )
            }
        }
    }
}

private fun memoryNodeLabel(
    event: MemoryEventEntity?,
    nodeId: String,
    nodeType: String
): String {
    if (event == null) return "$nodeType ${nodeId.take(24)}"
    return "${event.memoryKind} / ${event.eventType}: ${event.body.take(90)}"
}

@Composable
private fun PcHandoffScreen() {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("PC Handoff", style = MaterialTheme.typography.headlineMedium)
        Text("Fase 6 placeholder. Aqui estaran los comandos aprobados para PC.")
        ProjectCard("Pending handoff", "No hay comandos reales todavia.", "empty")
        ProjectCard("Boundary", "La app movil no ejecuta comandos de PC.", "protected")
    }
}

@Composable
private fun ProjectCard(title: String, description: String, status: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description)
            AssistChip(onClick = {}, label = { Text(status) })
        }
    }
}
