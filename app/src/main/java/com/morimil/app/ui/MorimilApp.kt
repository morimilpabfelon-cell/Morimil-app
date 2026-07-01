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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.morimil.app.data.genesis.GenesisBlock
import com.morimil.app.data.genesis.GenesisReader
import java.util.Locale

private enum class MorimilTab(val label: String) {
    Chat("Chat"),
    Voice("Voice"),
    Genesis("Genesis"),
    Projects("Projects"),
    Memory("Memory"),
    Handoff("PC")
}

@Composable
fun MorimilApp(viewModel: MorimilViewModel = viewModel()) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            var selectedTab by remember { mutableStateOf(MorimilTab.Chat) }

            Scaffold(
                bottomBar = {
                    NavigationBar {
                        MorimilTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                label = { Text(tab.label) },
                                icon = { Text(tab.label.first().toString()) }
                            )
                        }
                    }
                }
            ) { paddingValues ->
                Column(modifier = Modifier.padding(paddingValues)) {
                    when (selectedTab) {
                        MorimilTab.Chat -> ChatScreen(viewModel)
                        MorimilTab.Voice -> VoiceScreen(viewModel)
                        MorimilTab.Genesis -> GenesisScreen()
                        MorimilTab.Projects -> ProjectsScreen(viewModel)
                        MorimilTab.Memory -> MemoryScreen(viewModel)
                        MorimilTab.Handoff -> PcHandoffScreen()
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatScreen(viewModel: MorimilViewModel) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Morimil", style = MaterialTheme.typography.headlineMedium)
        Text("Native companion shell with local memory, voice, and Genesis reader", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { message ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Text("${message.author}: ${message.body}", modifier = Modifier.padding(12.dp))
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Escribe a Morimil...") }
            )
            Button(
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

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Voice", style = MaterialTheme.typography.headlineMedium)
        Text("Fase 4 mantiene Fase 3: push-to-talk y TTS manual.")
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

        ProjectCard("Boundary", "No GitHub Sync, no PC execution, no background listener.", "protected")
    }
}

@Composable
private fun GenesisScreen() {
    val context = LocalContext.current
    val genesisResult = remember {
        GenesisReader(context).readGenesisBlock()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Genesis", style = MaterialTheme.typography.headlineMedium)
        Text("Fase 4: lector local del Bloque Genesis empaquetado. Sin red y sin token.")

        genesisResult.fold(
            onSuccess = { genesis ->
                GenesisContent(genesis)
            },
            onFailure = { error ->
                ProjectCard("Genesis Reader", error.message.orEmpty(), "error")
            }
        )
    }
}

@Composable
private fun ProjectsScreen(viewModel: MorimilViewModel) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Projects", style = MaterialTheme.typography.headlineMedium)
        if (projects.isEmpty()) {
            ProjectCard("Morimil_app", "Fase 4: esperando memoria local.", "loading")
        } else {
            projects.forEach { project ->
                ProjectCard(project.title, "Persisted project state in Room.", project.status)
            }
        }
        ProjectCard("Genesis Block", "Bundled local snapshot visible in Genesis tab.", "read-only")
    }
}

@Composable
private fun GenesisContent(genesis: GenesisBlock) {
    ProjectCard(genesis.alias, "${genesis.role} / ${genesis.riskTier}", "loaded")
    ProjectCard("Source", "${genesis.sourceRepo} / ${genesis.sourceMode}", genesis.currentAppPhase)
    ProjectCard("Allowed actions", genesis.allowedActions.joinToString(", "), "bounded")
    ProjectCard("Blocked actions", genesis.blockedActions.joinToString(", "), "protected")
    ProjectCard(
        "Mobile boundary",
        "memory=${genesis.localMemory}, voice=${genesis.voicePushToTalk}, github=${genesis.githubSync}, pc=${genesis.pcExecution}",
        "validated"
    )
}

@Composable
private fun MemoryScreen(viewModel: MorimilViewModel) {
    val decisions by viewModel.decisions.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Living Memory", style = MaterialTheme.typography.headlineMedium)
        Text("Room/SQLite local activo. Genesis Reader local activo.")
        ProjectCard("Conversation memory", "${messages.size} mensajes persistidos.", "connected")
        ProjectCard("Project state", "${projects.size} proyectos persistidos.", "connected")
        if (decisions.isEmpty()) {
            ProjectCard("Decision log", "Sin decisiones registradas todavia.", "empty")
        } else {
            decisions.take(3).forEach { decision ->
                ProjectCard(decision.title, "Decision persisted locally.", decision.status)
            }
        }
        ProjectCard("Scope guardian", "GitHub Sync y PC Handoff siguen bloqueados.", "protected")
    }
}

@Composable
private fun PcHandoffScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("PC Handoff", style = MaterialTheme.typography.headlineMedium)
        Text("Fase 4 placeholder. Aqui estaran los comandos aprobados para PC.")
        ProjectCard("Pending handoff", "No hay comandos reales en Fase 4.", "empty")
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
