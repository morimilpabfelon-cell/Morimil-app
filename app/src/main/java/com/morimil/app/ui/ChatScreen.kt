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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morimil.app.data.local.MemoryMessageEntity
import com.morimil.app.reasoning.model.ModelBackendDecision
import com.morimil.app.reasoning.model.ReasoningBackendStatusStore
import com.morimil.app.web.NativeWebNeedDetector
import com.morimil.app.web.NativeWebRequestStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val backendDecision by ReasoningBackendStatusStore.lastDecision.collectAsStateWithLifecycle()
    val messages = uiState.messages
    val isSending = uiState.isSending
    val chatError = uiState.error
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.refreshChatOrganismStatus()
        viewModel.refreshOrganismHealth()
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
        ChatOrganismHeader(
            status = uiState.organismStatus,
            health = uiState.organismHealth,
            backendDecision = backendDecision,
            onRefreshHealth = viewModel::refreshOrganismHealth,
            onRunIntegrityAudit = viewModel::runMemoryIntegrityAudit
        )
        Spacer(Modifier.height(16.dp))
        ChatVoiceControls(viewModel)
        Spacer(Modifier.height(12.dp))
        NativeWebBridgePanel(onPageReady = viewModel::sendMessage)
        Spacer(Modifier.height(16.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty() && !isSending) {
                item { ChatEmptyState() }
            }
            itemsIndexed(
                items = messages,
                key = { _, message -> "${message.id}:${message.createdAtMillis}:${message.author}" }
            ) { index, message ->
                if (shouldShowDaySeparator(messages, index)) {
                    DaySeparator(dayLabel(message.createdAtMillis))
                }
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut()
                ) {
                    ChatMessageBubble(message = message)
                }
            }
            if (isSending) {
                item {
                    AnimatedVisibility(visible = true, enter = fadeIn() + expandVertically()) {
                        ThinkingBubble()
                    }
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
                    val message = draft
                    sendOrQueueNativeWeb(message, viewModel)
                    draft = ""
                }
            ) {
                Text("Enviar")
            }
        }
    }
}

@Composable
private fun ChatOrganismHeader(
    status: ChatOrganismStatusUiState,
    health: OrganismHealthUiState,
    backendDecision: ModelBackendDecision?,
    onRefreshHealth: () -> Unit,
    onRunIntegrityAudit: () -> Unit
) {
    var showHealthCard by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Morimil", style = MaterialTheme.typography.headlineMedium)
                Text("Conversacion real, con memoria local como contexto", style = MaterialTheme.typography.bodyMedium)
            }
            HealthStatusDot(health = health, onClick = { showHealthCard = true })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip("motor: ${status.motorLabel}")
            StatusChip(status.modelLabel.take(36))
            StatusChip(status.memoryIntegrityLabel, attention = status.memoryNeedsAttention)
        }
        backendDecision?.let { decision ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip("modo: ${decision.mode.name}", attention = !decision.usable)
                StatusChip("backend: ${decision.kind.name}", attention = !decision.usable)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip("tarea: ${decision.taskComplexity.name.take(36)}")
                StatusChip(
                    "ruta: ${decision.routingHint.take(36)}",
                    attention = decision.routingHint.contains("stronger") || decision.routingHint.contains("approval")
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip("razon: ${decision.reason.take(36)}", attention = !decision.usable)
                if (decision.model.isNotBlank()) {
                    StatusChip("modelo: ${decision.model.take(36)}")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(health.level.label, attention = health.level != HealthStatusLevel.Stable)
            StatusChip(health.overallLabel, attention = health.level != HealthStatusLevel.Stable)
            StatusChip(health.eventCountLabel)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(health.auditAgeLabel, attention = health.memoryNeedsAttention || health.auditNeedsAttention)
            StatusChip(health.recallLabel, attention = health.recallNeedsAttention)
        }
        Text(health.healthSentence, style = MaterialTheme.typography.bodySmall)
    }

    if (showHealthCard) {
        AlertDialog(
            onDismissRequest = { showHealthCard = false },
            confirmButton = {
                TextButton(onClick = { showHealthCard = false }) { Text("Cerrar") }
            },
            title = { Text("Estado del organismo") },
            text = {
                HealthStatusCard(
                    health = health,
                    onRefresh = onRefreshHealth,
                    onRunIntegrityAudit = onRunIntegrityAudit
                )
            }
        )
    }
}

@Composable
private fun HealthStatusDot(health: OrganismHealthUiState, onClick: () -> Unit) {
    val dotColor = healthStatusColor(health.level)
    val description = "Estado de Morimil: ${health.level.label}. Tocar para abrir salud del organismo."
    Box(
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = description }
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatMessageBubble(message: MemoryMessageEntity) {
    val isUser = message.author == "user"
    var showTime by remember(message.id, message.createdAtMillis) { mutableStateOf(false) }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val maxBubbleWidth = maxWidth * 0.85f
        Surface(
            modifier = Modifier
                .align(if (isUser) Alignment.CenterEnd else Alignment.CenterStart)
                .widthIn(max = maxBubbleWidth)
                .combinedClickable(
                    onClick = {},
                    onLongClick = { showTime = !showTime }
                ),
            shape = if (isUser) {
                RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 4.dp)
            } else {
                RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 20.dp)
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
                    message.body,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
                AnimatedVisibility(visible = showTime) {
                    Text(
                        timeLabel(message.createdAtMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DaySeparator(label: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Text(
                label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun ChatEmptyState() {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("La semilla aun no tiene recuerdos aqui", style = MaterialTheme.typography.titleMedium)
            Text("Cuando converses con Morimil, los recuerdos locales empezaran a aparecer en este cuerpo.")
        }
    }
}

@Composable
private fun ThinkingBubble() {
    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.align(Alignment.CenterStart),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 20.dp),
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
private fun ChatVoiceControls(viewModel: ChatViewModel) {
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
                        sendOrQueueNativeWeb(bestMatch, viewModel)
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

private fun sendOrQueueNativeWeb(message: String, viewModel: ChatViewModel) {
    if (NativeWebNeedDetector.shouldOpen(message)) {
        NativeWebRequestStore.requestSearch(message)
    } else {
        viewModel.sendMessage(message)
    }
}
private fun shouldShowDaySeparator(messages: List<MemoryMessageEntity>, index: Int): Boolean {
    if (index == 0) return true
    return dayLabel(messages[index].createdAtMillis) != dayLabel(messages[index - 1].createdAtMillis)
}

private fun dayLabel(createdAtMillis: Long): String {
    return SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(createdAtMillis))
}

private fun timeLabel(createdAtMillis: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(createdAtMillis))
}

private fun healthStatusColor(level: HealthStatusLevel): Color {
    return if (level == HealthStatusLevel.Stable) {
        Color(0xFF2E7D32)
    } else {
        Color(0xFFC62828)
    }
}
