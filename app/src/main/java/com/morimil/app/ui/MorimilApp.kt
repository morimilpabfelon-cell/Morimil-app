package com.morimil.app.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private enum class MorimilTab(val label: String) {
    Chat("Chat"),
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
        Text("Native mobile companion shell with local Room memory", style = MaterialTheme.typography.bodyMedium)
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
private fun ProjectsScreen(viewModel: MorimilViewModel) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Projects", style = MaterialTheme.typography.headlineMedium)
        if (projects.isEmpty()) {
            ProjectCard("Morimil_app", "Fase 2: esperando memoria local.", "loading")
        } else {
            projects.forEach { project ->
                ProjectCard(project.title, "Persisted project state in Room.", project.status)
            }
        }
        ProjectCard("Genesis Block", "Repo Morimil existente. No se modifica desde la app.", "locked")
    }
}

@Composable
private fun MemoryScreen(viewModel: MorimilViewModel) {
    val decisions by viewModel.decisions.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Living Memory", style = MaterialTheme.typography.headlineMedium)
        Text("Fase 2: Room/SQLite local activo. Datos guardados solo en el dispositivo.")
        ProjectCard("Conversation memory", "${messages.size} mensajes persistidos.", "connected")
        if (decisions.isEmpty()) {
            ProjectCard("Decision log", "Sin decisiones registradas todavia.", "empty")
        } else {
            decisions.take(3).forEach { decision ->
                ProjectCard(decision.title, "Decision persisted locally.", decision.status)
            }
        }
        ProjectCard("Scope guardian", "Voz, GitHub Sync y PC Handoff siguen bloqueados.", "protected")
    }
}

@Composable
private fun PcHandoffScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("PC Handoff", style = MaterialTheme.typography.headlineMedium)
        Text("Fase 2 placeholder. Aqui estaran los comandos aprobados para PC.")
        ProjectCard("Pending handoff", "No hay comandos reales en Fase 2.", "empty")
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
