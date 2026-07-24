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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.morimil.app.data.local.ReasoningTurnAuthor
import com.morimil.app.data.local.ReasoningTurnEntity
import com.morimil.app.reasoning.model.ReasoningEscalationDecision
import com.morimil.app.reasoning.model.ReasoningEscalationRequest
import com.morimil.app.reasoning.model.ReasoningEscalationStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreenPolished(viewModel: ChatViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingEscalation by ReasoningEscalationStore.pendingRequest.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var draft by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.refreshChatOrganismStatus()
        viewModel.refreshOrganismHealth()
    }
    LaunchedEffect(uiState.messages.size, uiState.isSending) {
        val target = when {
            uiState.isSending -> uiState.messages.size
            uiState.messages.isNotEmpty() -> uiState.messages.lastIndex
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
        IntrinsicChatHeader(
            status = uiState.organismStatus,
            health = uiState.organismHealth,
            pendingEscalation = pendingEscalation,
            onRefreshHealth = viewModel::refreshOrganismHealth,
            onRunIntegrityAudit = viewModel::runMemoryIntegrityAudit
        )
        Spacer(Modifier.height(10.dp))
        IntrinsicVoiceControls(viewModel)
        Spacer(Modifier.height(8.dp))
        NativeWebBridgePanel(onPageReady = viewModel::sendMessage)
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (uiState.messages.isEmpty() && !uiState.isSending) {
                item { IntrinsicEmptyState() }
            }
            items(
                items = uiState.messages,
                key = { message -> "${message.id}:${message.createdAtMillis}:${message.author}" }
            ) { message ->
                IntrinsicMessageBubble(message)
            }
            if (uiState.isSending) {
                item { IntrinsicProcessingBubble() }
            }
        }

        uiState.error?.let { error ->
            Spacer(Modifier.height(8.dp))
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(8.dp))
        IntrinsicComposer(
            draft = draft,
            isSending = uiState.isSending,
            onDraftChange = { draft = it },
            onSend = {
                val text = draft.trim()
                if (text.isNotBlank()) {
                    viewModel.sendMessage(text)
                    draft = ""
                }
            }
        )
    }
}

@Composable
private fun IntrinsicChatHeader(
    status: ChatOrganismStatusUiState,
    health: OrganismHealthUiState,
    pendingEscalation: ReasoningEscalationRequest?,
    onRefreshHealth: () -> Unit,
    onRunIntegrityAudit: () -> Unit
) {
    var showHealthCard by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = MorimilPillShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.74f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "○",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text("Morimil", style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.weight(1f))
            val dotColor = if (health.level == HealthStatusLevel.Stable) {
                Color(0xFF2E7D32)
            } else {
                Color(0xFFC62828)
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                modifier = Modifier
                    .size(44.dp)
                    .semantics {
                        contentDescription = "Estado de Morimil: ${health.level.label}"
                    }
                    .clickable { showHealthCard = true }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(dotColor))
                }
            }
        }

        Text(
            buildString {
                append("INTRINSECO: ")
                append(status.intrinsicLabel.uppercase(Locale.ROOT))
                append("\nAUXILIAR: ")
                append(status.helperLabel.uppercase(Locale.ROOT))
                append("\nMODELO AUX: ")
                append(status.helperModelLabel.take(32).uppercase(Locale.ROOT))
                append("\nMEMORIA: ")
                append(status.memoryIntegrityLabel.uppercase(Locale.ROOT))
            },
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = if (status.memoryNeedsAttention) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
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
                        "Consulta externa pendiente: auxiliar remoto temporal",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        "La consulta enviara solamente la tarea actual. No enviara identidad, memoria, doctrina, Genesis ni historial privado.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = ReasoningEscalationStore::approveCurrent) {
                            Text("Autorizar una consulta")
                        }
                        Button(onClick = ReasoningEscalationStore::keepLocal) {
                            Text("No usar auxiliar")
                        }
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
private fun IntrinsicMessageBubble(message: ReasoningTurnEntity) {
    val isUser = message.author == ReasoningTurnAuthor.USER
    val isAdvisory = message.author == ReasoningTurnAuthor.AUXILIARY_ADVISORY
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val maxBubbleWidth = maxWidth * 0.88f
        if (isUser) {
            Surface(
                modifier = Modifier.align(Alignment.CenterEnd).widthIn(max = maxBubbleWidth),
                shape = MorimilBubbleUserShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                Text(
                    message.body,
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            Surface(
                modifier = Modifier.align(Alignment.CenterStart).widthIn(max = maxBubbleWidth),
                shape = MorimilBubbleAiShape,
                color = if (isAdvisory) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                }
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        if (isAdvisory) "Auxiliar temporal — salida no verificada" else "Morimil",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isAdvisory) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                    Text(
                        message.body,
                        color = if (isAdvisory) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onBackground
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        SimpleDateFormat("HH:mm", Locale.getDefault())
                            .format(Date(message.createdAtMillis)),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun IntrinsicProcessingBubble() {
    Surface(
        shape = MorimilBubbleAiShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    ) {
        Text(
            "Morimil procesa la solicitud",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun IntrinsicEmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("○", style = MaterialTheme.typography.headlineMedium)
        Text("La semilla esta lista", style = MaterialTheme.typography.titleMedium)
        Text(
            "Los motores intrinsecos conservan identidad y continuidad. Los auxiliares externos solo aportan calculo temporal.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun IntrinsicComposer(
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
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.66f),
            modifier = Modifier.weight(1f)
        ) {
            TextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Escribe a Morimil") },
                enabled = !isSending
            )
        }
        Surface(
            shape = CircleShape,
            color = if (!isSending && draft.isNotBlank()) {
                MorimilAccentWarm
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
            },
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
private fun IntrinsicVoiceControls(viewModel: ChatViewModel) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    var voiceStatus by remember { mutableStateOf("Voz lista.") }
    var ttsReady by remember { mutableStateOf(false) }

    val textToSpeech = remember {
        TextToSpeech(context) { status -> ttsReady = status == TextToSpeech.SUCCESS }
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
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
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

            override fun onPartialResults(partialResults: android.os.Bundle?) = Unit
            override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit

            override fun onResults(results: android.os.Bundle?) {
                val best = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    .orEmpty()
                    .firstOrNull()
                    .orEmpty()
                if (best.isBlank()) {
                    voiceStatus = "No se recibio texto."
                } else {
                    voiceStatus = "Texto reconocido y enviado."
                    viewModel.sendMessage(best)
                }
            }
        })
        onDispose { speechRecognizer.destroy() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        voiceStatus = if (granted) "Microfono autorizado." else "Microfono no autorizado."
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = {
            if (
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                }
                speechRecognizer.startListening(intent)
            }
        }) {
            Text("Hablar")
        }
        Button(
            enabled = ttsReady && messages.isNotEmpty(),
            onClick = {
                val lastIntrinsic = messages.lastOrNull { turn ->
                    turn.author == ReasoningTurnAuthor.MORIMIL
                }
                if (lastIntrinsic == null) {
                    voiceStatus = "No hay respuesta intrinseca de Morimil para leer."
                } else {
                    textToSpeech.speak(
                        lastIntrinsic.body,
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        "morimil_intrinsic_reply"
                    )
                    voiceStatus = "Leyendo la ultima respuesta intrinseca."
                }
            }
        ) {
            Text("Leer Morimil")
        }
    }
    AnimatedVisibility(visible = voiceStatus.isNotBlank()) {
        Text(voiceStatus, style = MaterialTheme.typography.bodySmall)
    }
}
