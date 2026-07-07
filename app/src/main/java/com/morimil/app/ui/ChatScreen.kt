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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morimil.app.data.local.MemoryMessageEntity
import com.morimil.app.reasoning.model.ModelBackendDecision
import com.morimil.app.reasoning.model.ReasoningBackendStatusStore
import com.morimil.app.reasoning.model.ReasoningEscalationDecision
import com.morimil.app.reasoning.model.ReasoningEscalationRequest
import com.morimil.app.reasoning.model.ReasoningEscalationStore
import com.morimil.app.web.NativeWebNeedDetector
import com.morimil.app.web.NativeWebRequestStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val backendDecision by ReasoningBackendStatusStore.lastDecision.collectAsStateWithLifecycle()
    val pendingEscalation by ReasoningEscalationStore.pendingRequest.collectAsStateWithLifecycle()
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(morimilBackgroundBrush())
            .padding(16.dp)
    ) {
        ChatOrganismHeader(
            status = uiState.organismStatus,
            health = uiState.organismHealth,
            backendDecision = backendDecision,
            pendingEscalation = pendingEscalation,
            onRefreshHealth = viewModel::refreshOrganismHealth,
            onRunIntegrityAudit = viewModel::runMemoryIntegrityAudit
        )
        Spacer(Modifier.height(12.dp))
        ChatVoiceControls(viewModel)
        Spacer(Modifier.height(10.dp))
        NativeWebBridgePanel(onPageReady = viewModel::sendMessage)
        Spacer(Modifier.height(14.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(10.dp))
        ChatComposer(
            draft = draft,
            isSending = isSending,
            onDraftChange = { draft = it },
            onSend = {
                val message = draft
                sendOrQueueNativeWeb(message, viewModel)
                draft = ""
            }
        )
    }
}

@Composable
private fun ChatOrganismHeader(
    status: ChatOrganismStatusUiState,
    health: OrganismHealthUiState,
    backendDecision: ModelBackendDecision?,
    pendingEscalation: ReasoningEscalationRequest?,
    onRefreshHealth: () -> Unit,
    onRunIntegrityAudit: () -> Unit
) {
    var showHealthCard by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = MorimilPillShape, color = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("○", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text("Morimil", style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.weight(1f))
            HealthStatusDot(health = health, onClick = { showHealthCard = true })
        }

        val attention = status.memoryNeedsAttention || health.level != HealthStatusLevel.Stable
        Text(
            buildString {
                append("MOTOR: "); append(status.motorLabel.uppercase(Locale.ROOT)); append("\n")
                append("MODELO: "); append(status.modelLabel.take(28).uppercase(Locale.ROOT)); append("\n")
                append("MEMORIA: "); append(status.memoryIntegrityLabel.uppercase(Locale.ROOT)); append("\n")
                append("RUTA: "); append((backendDecision?.routingHint ?: "local").take(28).uppercase(Locale.ROOT))
            },
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = if (attention) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (pendingEscalation?.decision == ReasoningEscalationDecision.PENDING) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Escalada pendiente: motor superior",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = ReasoningEscalationStore::approveCurrent) { Text("Autorizar") }
                        Button(onClick = ReasoningEscalationStore::keepLocal) { Text("Seguir local") }
                    }
                }
            }
        }
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
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier
            .size(44.dp)
            .semantics { contentDescription = description }
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatMessageBubble(message: MemoryMessageEntity) {
    val isUser = message.author == "user"
    var showTime by remember(message.id, message.createdAtMillis) { mutableStateOf(false) }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val maxBubbleWidth = maxWidth * 0.85f
        if (isUser) {
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .widthIn(max = maxBubbleWidth)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { showTime = !showTime }
                    ),
                shape = MorimilBubbleUserShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        message.body,
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    AnimatedVisibility(visible = showTime) {
                        Text(
                            timeLabel(message.createdAtMillis),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .widthIn(max = maxBubbleWidth)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { showTime = !showTime }
                    )
                    .padding(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    message.body,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyMedium
                )
                AnimatedVisibility(visible = showTime) {
                    Text(
                        timeLabel(message.createdAtMillis),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
            color = MaterialTheme.colorScheme.surface
        ) {
            Text(
                label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChatEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("○", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Text("La semilla aun no tiene recuerdos aqui", style = MaterialTheme.typography.titleMedium)
        Text(
            "Cuando converses con Morimil, los recuerdos locales apareceran en este cuerpo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ThinkingBubble() {
    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.align(Alignment.CenterStart),
            shape = MorimilBubbleAiShape,
            color = MaterialTheme.colorScheme.surface
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
private fun ChatComposer(
    draft: String,
    isSending: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.weight(1f)
        ) {
            TextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Escribe a Morimil") },
                enabled = !isSending,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                )
            )
        }
        Surface(
            shape = CircleShape,
            color = if (!isSending && draft.isNotBlank()) MorimilAccentWarm else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .size(48.dp)
                .clickable(enabled = !isSending && draft.isNotBlank()) { onSend() }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("↑", style = MaterialTheme.typography.titleLarge, color = Color.White)
            }
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

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = MorimilPillShape,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .weight(1f)
                .clickable {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) startListening() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("mic", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = MorimilAccentWarm)
                Text(voiceStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Surface(
            shape = MorimilPillShape,
            color = if (ttsReady && messages.isNotEmpty()) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.clickable(enabled = ttsReady && messages.isNotEmpty()) {
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
            Text(
                "leer",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
