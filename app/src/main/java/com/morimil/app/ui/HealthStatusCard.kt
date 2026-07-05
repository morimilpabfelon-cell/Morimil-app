package com.morimil.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun HealthStatusCard(
    health: OrganismHealthUiState,
    onRefresh: () -> Unit,
    onRunIntegrityAudit: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Salud del organismo", style = MaterialTheme.typography.titleMedium)
            Text(health.healthSentence)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                HealthStatusChip(health.level.label, level = health.level)
                HealthStatusChip(
                    health.overallLabel,
                    attention = health.memoryNeedsAttention ||
                        health.auditNeedsAttention ||
                        health.recallNeedsAttention ||
                        health.restCycleNeedsAttention ||
                        health.internalIssueNeedsAttention
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                HealthStatusChip(health.eventCountLabel)
                HealthStatusChip(health.recallLabel, attention = health.recallNeedsAttention)
                HealthStatusChip(
                    health.recommendedActionLabel,
                    attention = health.recommendedActionLabel != "accion: continuar"
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                HealthStatusChip(health.memoryLabel, attention = health.memoryNeedsAttention)
                HealthStatusChip(health.auditAgeLabel, attention = health.memoryNeedsAttention || health.auditNeedsAttention)
                HealthStatusChip(health.auditSourceLabel)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                HealthStatusChip(health.motorLabel, attention = health.motorNeedsAttention)
                HealthStatusChip(health.restCycleLabel, attention = health.restCycleNeedsAttention)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                HealthStatusChip(health.internalIssueLabel, attention = health.internalIssueNeedsAttention)
            }
            if (health.internalIssueNeedsAttention && health.internalIssueDetailLabel.isNotBlank()) {
                Text(health.internalIssueDetailLabel, style = MaterialTheme.typography.bodySmall)
            }
            val checkedLabel = health.checkedAtMillis?.toString() ?: "pending"
            Text("modelo=${health.modelLabel.take(80)} checked=$checkedLabel")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRefresh) { Text("Actualizar salud") }
                Button(onClick = onRunIntegrityAudit) { Text("Auditar memoria") }
            }
        }
    }
}

@Composable
private fun HealthStatusChip(
    label: String,
    attention: Boolean = false,
    level: HealthStatusLevel? = null
) {
    val containerColor = when {
        level == HealthStatusLevel.Stable -> Color(0xFF166534)
        level == HealthStatusLevel.Watch -> MaterialTheme.colorScheme.secondaryContainer
        level == HealthStatusLevel.Attention -> MaterialTheme.colorScheme.tertiaryContainer
        level == HealthStatusLevel.Critical -> MaterialTheme.colorScheme.errorContainer
        attention -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val labelColor = when {
        level == HealthStatusLevel.Stable -> Color.White
        level == HealthStatusLevel.Critical -> MaterialTheme.colorScheme.onErrorContainer
        attention || level == HealthStatusLevel.Attention -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    AssistChip(
        onClick = {},
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = containerColor,
            labelColor = labelColor
        )
    )
}
