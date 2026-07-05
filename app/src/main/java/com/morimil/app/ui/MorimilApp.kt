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
import com.morimil.app.core.memory.MemoryBacklink
import com.morimil.app.core.memory.MemoryBacklinkGraphBuilder
import com.morimil.app.data.genesis.CurrentMobileAppCapabilities
import com.morimil.app.data.genesis.GenesisIdentitySource
import com.morimil.app.data.local.MemoryEventEntity
import com.morimil.app.data.local.MemoryLinkEntity
import com.morimil.app.data.local.MigrationRecordEntity
import com.morimil.app.data.local.RecallScheduleEntity
import java.util.Locale

private const val MEMORY_EVENT_NODE_TYPE = "memory_event"
private const val REST_CYCLE_MIGRATION_TYPE = "rest_cycle.local_consolidation"
private const val COGNITIVE_MIGRATION_TYPE = "cognitive.memory_refinement"

private val MorimilLightColors = lightColorScheme(
    primary = Color(0xFF245C37),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8EACF),
    onPrimaryContainer = Color(0xFF06210F),
    secondary = Color(0xFF6B5B1E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1E2A6),
    onSecondaryContainer = Color(0xFF211A02),
    tertiary = Color(0xFFB7791F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDAA),
    onTertiaryContainer = Color(0xFF2A1700),
    background = Color(0xFFFAF7EF),
    onBackground = Color(0xFF1C1B17),
    surface = Color(0xFFFFFBF4),
    onSurface = Color(0xFF1C1B17),
    surfaceVariant = Color(0xFFE0E4D7),
    onSurfaceVariant = Color(0xFF44483F),
    error = Color(0xFFBA1A1A)
)

private val MorimilDarkColors = darkColorScheme(
    primary = Color(0xFF9DD7A6),
    onPrimary = Color(0xFF00391A),
    primaryContainer = Color(0xFF0D4A25),
    onPrimaryContainer = Color(0xFFC8EACF),
    secondary = Color(0xFFD8C77C),
    onSecondary = Color(0xFF393000),
    secondaryContainer = Color(0xFF514700),
    onSecondaryContainer = Color(0xFFF5E6A4),
    tertiary = Color(0xFFFFC36F),
    onTertiary = Color(0xFF452B00),
    tertiaryContainer = Color(0xFF624000),
    onTertiaryContainer = Color(0xFFFFDDAA),
    background = Color(0xFF11140F),
    onBackground = Color(0xFFE3E3D8),
    surface = Color(0xFF171A14),
    onSurface = Color(0xFFE3E3D8),
    surfaceVariant = Color(0xFF3F453C),
    onSurfaceVariant = Color(0xFFC5C9BD),
    error = Color(0xFFFFB4AB)
)

private enum class MorimilTab(val label: String, val icon: String) {
    Chat("Chat", "C"),
    Voice("Motor", "M"),
    Genesis("Genesis", "G"),
    Workspace("Workspace", "W"),
    Projects("Projects", "P"),
    Memory("Memory", "R"),
    Handoff("PC", "PC")
}

@Composable
fun MorimilApp(viewModel: MorimilViewModel = viewModel()) {
    MorimilTheme {
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
private fun MorimilTheme(
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = isSystemInDarkTheme()
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> MorimilDarkColors
        else -> MorimilLightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
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
                MorimilTab.Voice -> MotorScreen()
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
    val organismStatus by viewModel.chatOrganismStatus.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.refreshChatOrganismStatus()
    }

    LaunchedEffect(messages.size, isSending) {
        val target = when {
            isSending -> messages.size
            messages.isNotEmpty() -> messages.lastIndex
            else -> null
        }
        target?.let { listState.animateScrollToItem(it) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        ChatOrganismHeader(organismStatus)
        Spacer(Modifier.height(16.dp))
        ChatVoiceControls(viewModel)
        Spacer(Modifier.height(16.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { message ->
                ChatMessageBubble(author = message.author, body = message.body)
            }
            if (isSending) {
                item {
                    ThinkingBubble()
                }
            }
        }

        chatError?.let { error ->
            Spacer(Modifier.height(8.dp))
            Text(error, style = MaterialTheme.typography.bodySmall)
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
private fun ChatOrganismHeader(status: ChatOrganismStatusUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Morimil", style = MaterialTheme.typography.headlineMedium)
        Text("Conversacion real, con memoria local como contexto", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip("motor: ${status.motorLabel}")
            StatusChip(status.modelLabel.take(36))
            StatusChip(status.memoryIntegrityLabel, attention = status.memoryNeedsAttention)
        }
    }
}

@Composable
private fun StatusChip(label: String, attention: Boolean = false) {
    AssistChip(
        onClick = {},
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (attention) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
            labelColor = if (attention) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
        )
    )
}

@Composable
private fun ChatMessageBubble(author: String, body: String) {
    val isUser = author == "user"
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val maxBubbleWidth = maxWidth * 0.85f
        Surface(
            modifier = Modifier
                .align(if (isUser) Alignment.CenterEnd else Alignment.CenterStart)
                .widthIn(max = maxBubbleWidth),
            shape = if (isUser) {
                RoundedCornerShape(topStart = 18.dp, topEnd = 6.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
            } else {
                RoundedCornerShape(topStart = 6.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
            },
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (isUser) "Tu" else "Morimil",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    body,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ThinkingBubble() {
    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.align(Alignment.CenterStart),
            shape = RoundedCornerShape(topStart = 6.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Morimil piensa", style = MaterialTheme.typography.labelMedium)
                ThinkingDots()
            }
        }
    }
}

@Composable
private fun ThinkingDots() {
    val transition = rememberInfiniteTransition(label = "thinking")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "thinkingPhase"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val alpha = 0.35f + (((phase + index * 0.24f) % 1f) * 0.65f)
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
            )
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

    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    DisposableEffect(speechRecognizer) {
        speechRecognizer.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) { voiceStatus = "Escuchando..." }
                override fun onBeginningOfSpeech() { voiceStatus = "Voz detectada." }
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() { voiceStatus = "Procesando voz..." }
                override fun onError(error: Int) { voiceStatus = "No se pudo reconocer voz. Codigo: $error" }
                override fun onPartialResults(partialResults: android.os.Bundle?) = Unit
                override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
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
            }
        )
        onDispose { speechRecognizer.destroy() }
    }

    fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla con Morimil")
        }
        speechRecognizer.startListening(intent)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening() else voiceStatus = "Permiso de microfono denegado."
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
                        if (granted) startListening() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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
private fun MotorScreen() {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
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
private fun GenesisScreen(viewModel: MorimilViewModel) {
    val genesisResult by viewModel.genesisResult.collectAsStateWithLifecycle()
    val localIdentity by viewModel.localIdentity.collectAsStateWithLifecycle()
    val genesisCore by viewModel.genesisCore.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Genesis", style = MaterialTheme.typography.headlineMedium)
        Text("Fase Genesis local: lee la semilla empaquetada en la app y la copia al telefono.")
        localIdentity?.let { identity ->
            ProjectCard("Esta instancia: ${identity.alias}", "${identity.genesisRole} / ${identity.genesisRiskTier}", "instance_id=${identity.instanceId}")
            ProjectCard("Memoria local", "cadena en el dispositivo", identity.localMemoryUri)
        }
        genesisCore?.let { core ->
            ProjectCard(
                "Genesis Core copiado",
                "Origen=${core.sourceOrigin}; nacido=${core.copiedAtMillis}; sha256=${core.contentSha256.take(16)}...",
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
            projects.forEach { project -> ProjectCard(project.title, "Persisted project state in Room.", project.status) }
        }
        ProjectCard("Genesis", "Bundled Genesis seed visible in Genesis tab.", "read-only")
    }
}

@Composable
private fun GenesisContent(source: GenesisIdentitySource) {
    val genesis = source.identity
    val capabilities = CurrentMobileAppCapabilities.value
    ProjectCard(genesis.alias, "${genesis.role} / ${genesis.riskTier}", source.origin.label)
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
    val selfSnapshot by viewModel.selfSnapshot.collectAsStateWithLifecycle()
    val knowledgeCapsules by viewModel.knowledgeCapsules.collectAsStateWithLifecycle()
    val recentLinks by viewModel.recentMemoryLinks.collectAsStateWithLifecycle()
    val integrityAudit by viewModel.memoryIntegrityAudit.collectAsStateWithLifecycle()
    val selectedMemoryEventHash by viewModel.selectedMemoryEventHash.collectAsStateWithLifecycle()
    val selectedMemoryLinks by viewModel.selectedMemoryLinks.collectAsStateWithLifecycle()
    val selectedGraphEvents by viewModel.selectedGraphEvents.collectAsStateWithLifecycle()
    val eventsByHash = events.associateBy { event -> event.eventHash }
    val selectedMemoryEvent = selectedMemoryEventHash?.let { hash ->
        eventsByHash[hash] ?: selectedGraphEvents.firstOrNull { event -> event.eventHash == hash }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Living Memory", style = MaterialTheme.typography.headlineMedium)
        Text("Genesis Core inmutable + eventos append-only + organos locales de memoria.")
        ProjectCard("Conversation memory", "${messages.size} mensajes persistidos.", "connected")
        snapshot?.let { liveSnapshot ->
            ProjectCard(
                "Living snapshot",
                "${liveSnapshot.eventCount} eventos / ${liveSnapshot.messageCount} mensajes. ${liveSnapshot.summary.take(260)}",
                "updated=${liveSnapshot.updatedAtMillis}"
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

        MemoryOrgansPanel(
            selfSnapshot = selfSnapshot,
            knowledgeCapsules = knowledgeCapsules,
            links = recentLinks,
            migrations = migrations,
            audit = integrityAudit,
            onRunIntegrityAudit = viewModel::runMemoryIntegrityAudit,
            onOpenMemory = viewModel::selectMemoryEvent
        )

        RecallSchedulePanel(
            recalls = recalls,
            eventsByHash = eventsByHash,
            onSeedRecalls = viewModel::seedRecallScheduleIfNeeded,
            onReinforceRecall = viewModel::reinforceRecall,
            onPostponeRecall = viewModel::postponeRecall,
            onDegradeRecall = viewModel::degradeRecall,
            onOpenMemory = viewModel::selectMemoryEvent
        )

        Text("Memory review queue", style = MaterialTheme.typography.titleMedium)
        Text("Los recuerdos son append-only. Aprobar, corregir o degradar crea una revision local nueva; no modifica el evento original.")
        if (events.isEmpty()) {
            ProjectCard("Memory events", "Sin eventos vivos todavia.", "empty")
        } else {
            events.take(12).forEach { event ->
                MemoryEventReviewCard(
                    event = event,
                    selected = selectedMemoryEventHash == event.eventHash,
                    onSelect = viewModel::selectMemoryEvent,
                    onApprove = viewModel::approveMemoryEvent,
                    onDegrade = viewModel::degradeMemoryEvent,
                    onRequestCorrection = viewModel::requestMemoryCorrection
                )
            }
        }

        RestCycleHistoryPanel(
            migrations = migrations,
            onApproveRestCycle = viewModel::approveRestCycleConsolidation,
            onRunRestCycleNow = viewModel::runRestCycleNow
        )
        CognitiveMigrationPanel(
            migrations = migrations,
            onProposeMigration = viewModel::proposeCognitiveMigration,
            onApproveMigration = viewModel::approveCognitiveMigration,
            onExecuteMigration = viewModel::executeCognitiveMigration,
            onRollbackMigration = viewModel::rollbackCognitiveMigration
        )
        MemoryGraphCanvasPanel(
            selectedEventHash = selectedMemoryEventHash,
            selectedEvent = selectedMemoryEvent,
            graphEvents = selectedGraphEvents,
            links = selectedMemoryLinks,
            onSelectEventHash = viewModel::selectMemoryEvent,
            onClearSelection = viewModel::clearSelectedMemoryEvent
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
private fun MemoryEventReviewCard(
    event: MemoryEventEntity,
    selected: Boolean,
    onSelect: (String) -> Unit,
    onApprove: (MemoryEventEntity) -> Unit,
    onDegrade: (MemoryEventEntity) -> Unit,
    onRequestCorrection: (MemoryEventEntity) -> Unit
) {
    val isQuarantine = event.memoryKind == "integrity_quarantine" || event.eventType == "memory_integrity.quarantine"
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isQuarantine) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                MemoryKindChip(event.memoryKind)
                if (isQuarantine) StatusChip("cuarentena", attention = true)
            }
            Text(event.eventType, style = MaterialTheme.typography.titleMedium)
            Text(event.body.take(340))
            Text("actor=${event.actor} importance=${event.importance} confidence=${event.confidence} hash=${event.eventHash.take(24)}")
            Text("tags=${event.tagsJson.take(160)}")
            if (selected) {
                AssistChip(onClick = {}, label = { Text("Backlinks abiertos") })
            } else {
                Button(onClick = { onSelect(event.eventHash) }) { Text("Ver backlinks") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onApprove(event) }) { Text("Aprobar") }
                Button(onClick = { onDegrade(event) }) { Text("Degradar ruido") }
            }
            Button(onClick = { onRequestCorrection(event) }) { Text("Pedir correccion") }
        }
    }
}

@Composable
private fun MemoryKindChip(memoryKind: String) {
    val container = memoryKindContainerColor(memoryKind)
    val labelColor = if (memoryKind == "chat_noise") {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        Color.White
    }
    AssistChip(
        onClick = {},
        label = { Text(memoryKind.ifBlank { "memory" }) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = container,
            labelColor = labelColor
        )
    )
}

@Composable
private fun memoryKindContainerColor(memoryKind: String): Color {
    return when (memoryKind) {
        "identity" -> Color(0xFF166534)
        "decision" -> Color(0xFF0F766E)
        "preference" -> Color(0xFF15803D)
        "correction", "error_detected" -> Color(0xFFB45309)
        "approval" -> Color(0xFF2563EB)
        "rejection" -> Color(0xFF991B1B)
        "learning" -> Color(0xFF6D4C41)
        "integrity_quarantine" -> MaterialTheme.colorScheme.tertiary
        "chat_noise" -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.secondary
    }
}

@Composable
private fun RecallSchedulePanel(
    recalls: List<RecallScheduleEntity>,
    eventsByHash: Map<String, MemoryEventEntity>,
    onSeedRecalls: () -> Unit,
    onReinforceRecall: (Long) -> Unit,
    onPostponeRecall: (Long) -> Unit,
    onDegradeRecall: (Long) -> Unit,
    onOpenMemory: (String) -> Unit
) {
    val now = System.currentTimeMillis()
    val overdue = recalls
        .filter { recall -> recall.dueAtMillis <= now }
        .sortedWith(compareByDescending<RecallScheduleEntity> { it.priority }.thenBy { it.dueAtMillis })
    val future = recalls
        .filter { recall -> recall.dueAtMillis > now }
        .sortedWith(compareBy<RecallScheduleEntity> { it.dueAtMillis }.thenByDescending { it.priority })

    Text("Recalls pendientes", style = MaterialTheme.typography.titleMedium)
    Text("Recuerdos que conviene repasar para mantenerlos utiles sin convertir ruido en memoria fuerte.")
    Button(onClick = onSeedRecalls) { Text(if (recalls.isEmpty()) "Crear recalls" else "Actualizar recalls") }

    if (recalls.isEmpty()) {
        ProjectCard("Recall schedule", "No hay recalls activos todavia.", "empty")
        return
    }

    RecallScheduleSection("Vencidos ahora", "No hay recalls vencidos.", overdue.take(8), eventsByHash, onReinforceRecall, onPostponeRecall, onDegradeRecall, onOpenMemory)
    RecallScheduleSection("Futuros", "No hay recalls futuros.", future.take(8), eventsByHash, onReinforceRecall, onPostponeRecall, onDegradeRecall, onOpenMemory)
}

@Composable
private fun RecallScheduleSection(
    title: String,
    emptyText: String,
    recalls: List<RecallScheduleEntity>,
    eventsByHash: Map<String, MemoryEventEntity>,
    onReinforceRecall: (Long) -> Unit,
    onPostponeRecall: (Long) -> Unit,
    onDegradeRecall: (Long) -> Unit,
    onOpenMemory: (String) -> Unit
) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    if (recalls.isEmpty()) {
        Text(emptyText)
        return
    }

    recalls.forEach { recall ->
        val targetEvent = eventsByHash[recall.targetEventHash]
        ElevatedCard {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${recall.targetMemoryKind} / priority=${recall.priority}", style = MaterialTheme.typography.titleMedium)
                Text(recall.prompt.take(360))
                Text("due=${recall.dueAtMillis} interval=${recall.intervalDays}d action=${recall.lastAction}")
                Text("organ=${recall.source} link=${recall.targetEventHash.take(24)}")
                targetEvent?.let { event -> Text("recuerdo=${event.memoryKind}: ${event.body.take(180)}") }
                Text("reason=${recall.reason.take(180)}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onReinforceRecall(recall.recallId) }) { Text("Reforzar") }
                    Button(onClick = { onPostponeRecall(recall.recallId) }) { Text("Posponer") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onOpenMemory(recall.targetEventHash) }) { Text("Abrir recuerdo") }
                    Button(onClick = { onDegradeRecall(recall.recallId) }) { Text("Degradar") }
                }
            }
        }
    }
}

@Composable
private fun CognitiveMigrationPanel(
    migrations: List<MigrationRecordEntity>,
    onProposeMigration: () -> Unit,
    onApproveMigration: (String) -> Unit,
    onExecuteMigration: (String) -> Unit,
    onRollbackMigration: (String) -> Unit
) {
    val cognitiveMigrations = migrations.filter { migration -> migration.migrationType == COGNITIVE_MIGRATION_TYPE }.take(8)
    Text("Migraciones cognitivas", style = MaterialTheme.typography.titleMedium)
    Text("Propuestas auditables para refinar memoria sin reescribir eventos originales.")
    Button(onClick = onProposeMigration) { Text("Proponer migracion") }

    if (cognitiveMigrations.isEmpty()) {
        ProjectCard("Auditoria cognitiva", "Todavia no hay migraciones cognitivas propuestas.", "empty")
        return
    }

    cognitiveMigrations.forEach { migration ->
        ElevatedCard {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${migration.status} / risk=${migration.riskLevel}", style = MaterialTheme.typography.titleMedium)
                Text("approved=${migration.approvedByUser} chain=${migration.chainVerified} backup=${migration.backupRequired} rollback=${migration.rollbackAvailable}")
                Text("pre=${migration.preSnapshotId} post=${migration.postSnapshotId?.take(32) ?: "pending"}")
                Text("Diff logico", style = MaterialTheme.typography.titleMedium)
                Text(migrationPlanSection(migration.expectedEffect, "diff_logico").ifBlank { "Sin diff visible." }.take(900))
                Text("Capsulas propuestas", style = MaterialTheme.typography.titleMedium)
                Text(migrationPlanSection(migration.expectedEffect, "capsulas_propuestas").ifBlank { "Sin capsulas propuestas." }.take(900))
                Text("Backlinks propuestos", style = MaterialTheme.typography.titleMedium)
                Text(migrationPlanSection(migration.expectedEffect, "backlinks_propuestos").ifBlank { "Sin backlinks propuestos." }.take(900))
                Text("Auditoria y resultado", style = MaterialTheme.typography.titleMedium)
                Text("steps=${migration.stepsJson.take(360)}")
                Text("result=${migration.errorsJson.take(360)}")
                Text("strategy=${migration.rollbackStrategy.take(360)}")
                Text("affected=${migration.affectedArtifactsJson.take(360)}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (migration.status == "planned" && !migration.approvedByUser) {
                        Button(onClick = { onApproveMigration(migration.migrationId) }) { Text("Aprobar") }
                    }
                    if (migration.status == "approved") {
                        Button(onClick = { onExecuteMigration(migration.migrationId) }) { Text("Ejecutar") }
                    }
                }
                if (migration.rollbackAvailable && migration.status in setOf("approved", "completed", "failed")) {
                    Button(onClick = { onRollbackMigration(migration.migrationId) }) { Text("Rollback") }
                }
            }
        }
    }
}

private fun migrationPlanSection(plan: String, sectionName: String): String {
    val header = "$sectionName:"
    val lines = plan.lines()
    val startIndex = lines.indexOfFirst { line -> line.trim() == header }
    if (startIndex == -1) return ""

    return lines
        .drop(startIndex + 1)
        .takeWhile { line -> !line.trim().isMigrationPlanHeader() }
        .joinToString("\n")
        .trim()
}

private fun String.isMigrationPlanHeader(): Boolean {
    return this in setOf(
        "diff_logico:",
        "capsulas_propuestas:",
        "backlinks_propuestos:",
        "eventos_seleccionados:"
    ) || startsWith("policy=")
}

@Composable
private fun RestCycleHistoryPanel(
    migrations: List<MigrationRecordEntity>,
    onApproveRestCycle: (String) -> Unit,
    onRunRestCycleNow: () -> Unit
) {
    val restCycles = migrations.filter { migration -> migration.migrationType == REST_CYCLE_MIGRATION_TYPE }.take(8)
    Text("Rest cycle", style = MaterialTheme.typography.titleMedium)
    Text("Consolidaciones locales programadas, con aprobacion para cambios importantes.")
    Button(onClick = onRunRestCycleNow) { Text("Ejecutar descanso ahora") }

    if (restCycles.isEmpty()) {
        ProjectCard("Historial de descanso", "Todavia no hay consolidaciones registradas.", "empty")
        return
    }

    restCycles.forEach { migration ->
        val report = RestCycleReportUiStateBuilder.build(migration)
        ElevatedCard {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${migration.status} / risk=${migration.riskLevel}", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusChip("modo=${report.mode}")
                    StatusChip("riesgo=${report.risk}", attention = report.risk != "low")
                    StatusChip("cadena=${report.fullChainVerified}", attention = report.fullChainVerified != "true")
                }
                Text("organos_con_alerta=${report.organReconciliation} eventos=${report.sourceEvents} utiles=${report.meaningfulEvents}")
                Text("policy=${report.policyReason}")
                report.tasks.take(6).forEach { task ->
                    RestCycleTaskLine(task)
                }
                if (report.resultNotes.isNotEmpty()) {
                    Text("Resultado", style = MaterialTheme.typography.titleMedium)
                    report.resultNotes.take(6).forEach { note -> Text(note.take(180)) }
                }
                Text("approval_required=${report.approvalRequired} approved=${migration.approvedByUser} rollback=${migration.rollbackAvailable}")
                Text("strategy=${migration.rollbackStrategy.take(220)}")
                if (migration.status == "planned" && migration.approvalRequired && !migration.approvedByUser) {
                    Button(onClick = { onApproveRestCycle(migration.migrationId) }) { Text("Aprobar consolidacion") }
                }
            }
        }
    }
}

@Composable
private fun RestCycleTaskLine(task: RestCycleTaskUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        StatusChip(task.status, attention = task.risk != "low")
        Text("${task.name}: ${task.note}".take(160))
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
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Recuerdo seleccionado", style = MaterialTheme.typography.titleMedium)
            Text(selectedEvent?.body?.take(260) ?: selectedEventHash.take(32))
            Text("hash=${selectedEventHash.take(32)} links=${selectedLinks.size}")
            Button(onClick = onClearSelection) { Text("Cerrar") }
            MemoryBacklinkSection("Este recuerdo apunta a...", "Este recuerdo todavia no apunta a otros nodos.", graph.outgoing, eventsByHash, onSelectEventHash)
            MemoryBacklinkSection("Este recuerdo es mencionado por...", "Ningun nodo reciente apunta todavia a este recuerdo.", graph.incoming, eventsByHash, onSelectEventHash)
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
                AssistChip(onClick = {}, label = { Text(memoryNodeLabel(linkedEvent, backlink.linkedNodeId, backlink.linkedNodeType).take(90)) })
            }
        }
    }
}

private fun memoryNodeLabel(event: MemoryEventEntity?, nodeId: String, nodeType: String): String {
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
