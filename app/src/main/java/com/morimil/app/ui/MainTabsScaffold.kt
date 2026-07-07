package com.morimil.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private enum class MorimilTab(val label: String, val glyph: String) {
    Chat("Chat", "Aa"),
    Memory("Memoria", "&"),
    Motor("Motor", "⌘"),
    System("Sistema", "*")
}

@Composable
fun MainTabsScaffold(viewModel: MorimilViewModel) {
    var selectedTab by remember { mutableStateOf(MorimilTab.Chat) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(morimilBackgroundBrush())
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    MorimilTab.Chat -> ChatScreenPolished(viewModel.chatViewModel)
                    MorimilTab.Memory -> MemoryScreen(viewModel.memoryViewModel)
                    MorimilTab.Motor -> MotorScreen(viewModel.motorViewModel)
                    MorimilTab.System -> SystemHubScreen(viewModel)
                }
            }
            MorimilFloatingNavBar(
                selected = selectedTab,
                onSelect = { selectedTab = it }
            )
        }
    }
}

@Composable
private fun MorimilFloatingNavBar(selected: MorimilTab, onSelect: (MorimilTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MorimilTab.entries.forEach { tab ->
                    val active = tab == selected
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            )
                            .clickable { onSelect(tab) }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = tab.glyph,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (active) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}
