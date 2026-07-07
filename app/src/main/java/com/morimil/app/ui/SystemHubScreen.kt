package com.morimil.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
fun SystemHubScreen(viewModel: MorimilViewModel) {
    var open by remember { mutableStateOf<SystemPane?>(null) }

    when (open) {
        SystemPane.Genesis -> BackablePane("Genesis", { open = null }) { GenesisScreen(viewModel) }
        SystemPane.Workspace -> BackablePane("Workspace", { open = null }) { UserWorkspaceScreen(viewModel) }
        SystemPane.Projects -> BackablePane("Proyectos", { open = null }) { ProjectsScreen(viewModel) }
        SystemPane.Pc -> BackablePane("PC", { open = null }) { PcHandoffScreen(viewModel.pcHandoffViewModel) }
        SystemPane.Improvements -> BackablePane("Mejoras", { open = null }) { ImprovementsScreen(viewModel) }
        null -> SystemHubIndex(onOpen = { open = it })
    }
}

private enum class SystemPane {
    Genesis,
    Workspace,
    Projects,
    Pc,
    Improvements
}

@Composable
private fun SystemHubIndex(onOpen: (SystemPane) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(morimilBackgroundBrush())
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = MorimilPillShape, color = MaterialTheme.colorScheme.surface) {
                Text(
                    text = "Sistema",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            SystemGlyph("*")
        }

        Text(
            text = "GENESIS: OK\nWORKSPACE: LOCAL\nMEJORAS: APROBACION",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(6.dp))
        SystemRow("○", "Genesis", "Identidad, semilla y constitucion") { onOpen(SystemPane.Genesis) }
        SystemRow("⌂", "Workspace", "Tu espacio de trabajo local") { onOpen(SystemPane.Workspace) }
        SystemRow("□", "Proyectos", "Ion Exchange y proyectos activos") { onOpen(SystemPane.Projects) }
        SystemRow("PC", "PC", "Puente local hacia tu computadora") { onOpen(SystemPane.Pc) }
        SystemRow("+", "Mejoras", "Propuestas, aprobaciones y roadmap") { onOpen(SystemPane.Improvements) }
    }
}

@Composable
private fun SystemRow(glyph: String, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SystemGlyph(glyph)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MorimilAccentWarm)
    }
}

@Composable
private fun SystemGlyph(glyph: String) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            glyph,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun BackablePane(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(morimilBackgroundBrush())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.clickable(onClick = onBack)
            ) {
                Text(
                    text = "‹",
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleLarge,
                    color = MorimilAccentWarm
                )
            }
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Box(Modifier.weight(1f)) { content() }
    }
}
