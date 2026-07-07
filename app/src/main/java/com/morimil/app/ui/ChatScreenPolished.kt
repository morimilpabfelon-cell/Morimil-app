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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
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
fun ChatScreenPolished(viewModel: ChatViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val backendDecision by ReasoningBackendStatusStore.lastDecision.collectAsStateWithLifecycle()
    val pendingEscalation by ReasoningEscalationStore.pendingRequest.collectAsStateWithLifecycle()
    val pendingWebRequest by NativeWebRequestStore.pendingRequest.collectAsStateWithLifecycle()
    val messages = uiState.messages
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.refreshChatOrganismStatus()
        viewModel.refreshOrganismHealth()
    }

    LaunchedEffect(messages.size, uiState.isSending) {
        val target = when {
            uiState.isSending -> messages.size
            messages.isNotEmpty() -> messages.lastIndex
            else -> null
        }
        target?.let { listState.animateScrollToItem(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(morimilBackgroundBrush())
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp)
    ) {
        PolishedHeader(
            status = uiState.organismStatus,
            health = uiState.organismHealth,
            backendDecision = backendDecision,
            pendingEscalation = pendingEscalation,
            onRefreshHealth = viewModel::refreshOrganismHealth,
            onRunIntegrityAudit = viewModel::runMemoryIntegrityAudit
        )
        Spacer(Modifier.height(10.dp))
        PolishedVoiceControls(viewModel)
        if (pendingWebRequest != null) {
            Spacer(Modifier.height(8.dp))
            NativeWebBridgePanel(onPageReady = viewModel::sendMessage)
        }
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty() && !uiState.isSending) {
                item { PolishedEmptyState() }
            }
            items(messages, key = { message -> "${message.id}:${message.createdAtMillis}:${message.author}" }) { message ->
                PolishedMessageBubble(message)
            }
            if (uiState.isSending) {
                item { PolishedThinkingBubble() }
            }
        }

        uiState.error?.let { error ->
            Spacer(Modifier.height(8.dp))
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(8.dp))
        PolishedComposer(
            draft = draft,
            isSending = uiState.isSending,
            onDraftChange = { draft = it },
            onSend = {
                val text = draft.trim()
                if (text.isNotBlank()) {
                    sendPolishedMessage(text, viewModel)
                    draft = ""
                }
            }
        )
    }
}

@Composable
private fun PolishedHeader(
    status: ChatOrganismStatusUiState,
    health: OrganismHealthUiState,
    backendDecision: ModelBackendDecision?,
    pendingEscalation: ReasoningEscalationRequest?,
    onRefreshHealth: () -> Unit,
    onRunIntegrityAudit: () -> Unit
) {
    var showHealthCard by remember { mutableStateOf(false) }
    val attention = status.memoryNeedsAttention || health.level != HealthStatusLevel.Stable

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = MorimilPillShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.74f)) {
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
            PolishedHealthDot(health) { showHealthCard = true }
        }

        if (attention) {
            val trace = buildString {
                append("motor ")
                append(status.motorLabel.take(18).lowercase(Locale.ROOT))
                append(" · memoria ")
                append(status.memoryIntegrityLabel.take(22).lowercase(Locale.ROOT))
                backendDecision?.routingHint?.takeIf { it.isNotBlank() }?.let { route ->
                    append(" · ")
                    append(route.take(22).lowercase(Locale.ROOT))
                }
            }
            Surface(shape = MorimilPillShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f)) {
                Text(
                    trace,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MorimilAccentWarm,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (pendingEscalation?.decision == ReasoningEscalationDecision.PENDING) {
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Escalada pendiente: motor superior", style = MaterialTheme.typography.labelLarge)
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
            confirmButton = { TextButton(onClick = { showHealthCard = false }) { Text("Cerrar") } },
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
private fun PolishedHealthDot(health: OrganismHealthUiState, onClick: () -> Unit) {
    val color = if (health.level == HealthStatusLevel.Stable) Color(0xFF2E7D32) else Color(0xFFC62828)
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        modifier = Modifier
            .size(44.dp)
            .semantics { contentDescription = "Estado de Morimil: ${health.level.label}" }
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(color))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PolishedMessageBubble(message: MemoryMessageEntity) {
    val isUser = message.author == "user"
    var showTime by remember(message.id, message.createdAtMillis) { mutableStateOf(false) }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val maxWidth = maxWidth * 0.88f
        if (isUser) {
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .widthIn(max = maxWidth)
                    .combinedClickable(onClick = {}, onLongClick = { showTime = !showTime }),
                shape = MorimilBubbleUserShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                Column(Modifier.padding(horizontal = 15.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(message.body, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.bodyMedium)
                    AnimatedVisibility(showTime) { PolishedTime(message.createdAtMillis, MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)) }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .widthIn(max = maxWidth)
                    .combinedClickable(onClick = {}, onLongClick = { showTime = !showTime })
                    .padding(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(message.body, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodyMedium)
                AnimatedVisibility(showTime) { PolishedTime(message.createdAtMillis, MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun PolishedTime(createdAtMillis: Long, color: Color) {
    Text(
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(createdAtMillis)),
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
        color = color
    )
}

@Composable
private fun PolishedEmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("○", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Text("La semilla esta lista", style = MaterialTheme.typography.titleMedium)
        Text("Escribe una idea y Morimil la procesa desde su memoria local.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PolishedThinkingBubble() {
    Surface(shape = MorimilBubbleAiShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Morimil piensa", style = MaterialTheme.typography.labelMedium)
            val transition = rememberInfiniteTransition(label = "thinking")
            val phase by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
                label = "phase"
            )
            repeat(3) { index ->
                val alpha = 0.35f + (((phase + index * 0.24f) % 1f) * 0.65f)
                Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = alpha)))
            }
        }
    }
}

@Composable
private fun PolishedComposer(
    draft: String,
    isSending: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.66f), modifier = Modifier.weight(1f)) {
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
            color = if (!isSending && draft.isNotBlank()) MorimilAccentWarm else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
            modifier = Modifier.size(48.dp).clickable(enabled = !isSending && draft.isNotBlank()) { onSend() }
        ) {
            Box(contentAlignment = Alignment.Center) { Text("↑", style = MaterialTheme.typography.titleLarge, color = Color.White) }
        }
    }
}

@Composable
private fun PolishedVoiceControls(viewModel: ChatViewModel) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    var voiceStatus by remember { mutableStateOf("Voz lista.") }
    var ttsReady by remember { mutableStateOf(false) }

    val textToSpeech = remember { TextToSpeech(context) { status -> ttsReady = status == TextToSpeech.SUCCESS } }
    DisposableEffect(textToSpeech) {
        textToSpeech.language = Locale.getDefault()
        onDispose { textToSpeech.stop(); textToSpeech.shutdown() }
    }

    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    DisposableEffect(speechRecognizer) {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) { voiceStatus = "Escuchando..." }
            override fun onBeginningOfSpeech() { voiceStatus = "Voz detectada." }
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { voiceStatus = "Procesando voz..." }
            override fun onError(error: Int) { voiceStatus = "No se pudo reconocer voz. Codigo: $error" }
            override fun onPartialResults(partialResults: android.os.Bundle?) = Unit
            override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
            override fun onResults(results: android.os.Bundle?) {
                val best = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty().firstOrNull().orEmpty()
                if (best.isBlank()) voiceStatus = "No se recibio texto." else {
                    voiceStatus = "Texto reconocido y enviado."
                    sendPolishedMessage(best, viewModel)
                }
            }
        })
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

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = MorimilPillShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f),
            modifier = Modifier.weight(1f).clickable {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (granted) startListening() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("mic", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = MorimilAccentWarm)
                Text(voiceStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Surface(
            shape = MorimilPillShape,
            color = if (ttsReady && messages.isNotEmpty()) MaterialTheme.colorScheme.surface.copy(alpha = 0.58f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.44f),
            modifier = Modifier.clickable(enabled = ttsReady && messages.isNotEmpty()) {
                val last = messages.lastOrNull { it.author != "user" }?.body ?: messages.lastOrNull()?.body.orEmpty()
                textToSpeech.speak(last, TextToSpeech.QUEUE_FLUSH, null, "morimil-chat-last-message")
            }
        ) {
            Text("leer", modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp), style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun sendPolishedMessage(message: String, viewModel: ChatViewModel) {
    if (NativeWebNeedDetector.shouldOpen(message)) {
        NativeWebRequestStore.requestSearch(message)
    } else {
        viewModel.sendMessage(message)
    }
}
