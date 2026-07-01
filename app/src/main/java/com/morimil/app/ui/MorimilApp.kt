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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private enum class MorimilTab(val label: String) {
    Chat("Chat"),
    Projects("Projects"),
    Memory("Memory"),
    Handoff("PC")
}

@Composable
fun MorimilApp() {
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
                        MorimilTab.Chat -> ChatScreen()
                        MorimilTab.Projects -> ProjectsScreen()
                        MorimilTab.Memory -> MemoryScreen()
                        MorimilTab.Handoff -> PcHandoffScreen()
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatScreen() {
    val messages = remember {
        mutableStateListOf(
            "Morimil: Fase 1 activa. App nativa primero.",
            "Morimil: Memoria real, voz y GitHub Sync vendran despues."
        )
    }
    var draft by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Morimil", style = MaterialTheme.typography.headlineMedium)
        Text("Native mobile companion shell", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { message ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Text(message, modifier = Modifier.padding(12.dp))
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
                    if (draft.isNotBlank()) {
                        messages.add("Tu: $draft")
                        messages.add("Morimil: Recibido. En Fase 1 esto es UI mock.")
                        draft = ""
                    }
                }
            ) {
                Text("Enviar")
            }
        }
    }
}

@Composable
private fun ProjectsScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Projects", style = MaterialTheme.typography.headlineMedium)
        ProjectCard("Morimil_app", "Fase 1: app nativa Android skeleton.", "active")
        ProjectCard("Genesis Block", "Repo Morimil existente. No se modifica en Fase 1.", "locked")
        ProjectCard("Future Project", "Espacio futuro para proyectos creados desde la app.", "placeholder")
    }
}

@Composable
private fun MemoryScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Living Memory", style = MaterialTheme.typography.headlineMedium)
        Text("Fase 1 placeholder. La memoria real se implementara despues.")
        ProjectCard("Conversation memory", "Placeholder para conversaciones persistentes.", "not connected")
        ProjectCard("Decision log", "Placeholder para decisiones persistentes.", "not connected")
        ProjectCard("Scope guardian", "Placeholder para detectar desviaciones.", "not connected")
    }
}

@Composable
private fun PcHandoffScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("PC Handoff", style = MaterialTheme.typography.headlineMedium)
        Text("Fase 1 placeholder. Aqui estaran los comandos aprobados para PC.")
        ProjectCard("Pending handoff", "No hay comandos reales en Fase 1.", "empty")
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
