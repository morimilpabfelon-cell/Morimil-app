package com.morimil.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.morimil.app.data.local.AutobiographicalSnapshotEntity
import com.morimil.app.data.local.KnowledgeCapsuleEntity
import com.morimil.app.data.local.MemoryLinkEntity
import com.morimil.app.data.local.MigrationRecordEntity

@Composable
fun MemoryOrgansPanel(
    selfSnapshot: AutobiographicalSnapshotEntity?,
    knowledgeCapsules: List<KnowledgeCapsuleEntity>,
    links: List<MemoryLinkEntity>,
    migrations: List<MigrationRecordEntity>,
    audit: MemoryIntegrityAuditUiState,
    onRunIntegrityAudit: () -> Unit,
    onOpenMemory: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Organos de memoria", style = MaterialTheme.typography.titleLarge)
        Text("Autobiografia, capsulas, grafo, migraciones y auditoria local en una vista operable.")

        MemoryOrganMetricsRow(
            selfSnapshot = selfSnapshot,
            knowledgeCapsules = knowledgeCapsules,
            links = links,
            migrations = migrations,
            audit = audit
        )
        MemoryIntegrityAuditPanel(
            audit = audit,
            onRunIntegrityAudit = onRunIntegrityAudit
        )
        AutobiographyOrganPanel(selfSnapshot)
        KnowledgeCapsulesOrganPanel(
            capsules = knowledgeCapsules,
            onOpenMemory = onOpenMemory
        )
        ConsolidatedKnowledgePanel(knowledgeCapsules)
        LinksOrganPanel(links)
        MigrationOrganPanel(migrations)
    }
}

@Composable
private fun MemoryOrganMetricsRow(
    selfSnapshot: AutobiographicalSnapshotEntity?,
    knowledgeCapsules: List<KnowledgeCapsuleEntity>,
    links: List<MemoryLinkEntity>,
    migrations: List<MigrationRecordEntity>,
    audit: MemoryIntegrityAuditUiState
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MemoryOrganMetric("Autobiografia", if (selfSnapshot == null) "0" else "1", if (selfSnapshot == null) "pending" else "active")
            MemoryOrganMetric("Capsulas", knowledgeCapsules.size.toString(), if (knowledgeCapsules.isEmpty()) "empty" else "active")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MemoryOrganMetric("Links", links.size.toString(), if (links.isEmpty()) "empty" else "graph")
            MemoryOrganMetric("Migraciones", migrations.size.toString(), audit.statusLabel)
        }
    }
}

@Composable
private fun MemoryOrganMetric(title: String, value: String, status: String) {
    ElevatedCard(modifier = Modifier.weight(1f)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(value, style = MaterialTheme.typography.headlineSmall)
            AssistChip(onClick = {}, label = { Text(status) })
        }
    }
}

@Composable
private fun MemoryIntegrityAuditPanel(
    audit: MemoryIntegrityAuditUiState,
    onRunIntegrityAudit: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Auditoria de integridad", style = MaterialTheme.typography.titleMedium)
            Text("Verifica la cadena de eventos vivos y la cadena de capsulas desde el nucleo unico de integridad.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text("eventos=${audit.memoryChainVerified.auditLabel()}") })
                AssistChip(onClick = {}, label = { Text("capsulas=${audit.capsuleChainVerified.auditLabel()}") })
                AssistChip(onClick = {}, label = { Text(audit.statusLabel) })
            }
            audit.checkedAtMillis?.let { checkedAt ->
                Text("checked_at=$checkedAt")
            }
            audit.errorMessage?.let { error ->
                Text("error=${error.take(220)}")
            }
            Button(onClick = onRunIntegrityAudit) {
                Text("Verificar integridad")
            }
        }
    }
}

@Composable
private fun AutobiographyOrganPanel(selfSnapshot: AutobiographicalSnapshotEntity?) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Autobiografia", style = MaterialTheme.typography.titleMedium)
            if (selfSnapshot == null) {
                Text("Todavia no hay snapshot autobiografico activo.")
                AssistChip(onClick = {}, label = { Text("pending") })
                return@Column
            }
            Text("alias=${selfSnapshot.alias} updated=${selfSnapshot.updatedAtMillis}")
            Text("resumen=${selfSnapshot.selfSummary.take(360)}")
            Text("rasgos=${selfSnapshot.stableTraits.take(240)}")
            Text("metas=${selfSnapshot.activeGoals.take(240)}")
            Text("limites=${selfSnapshot.importantConstraints.take(240)}")
            selfSnapshot.sourceEventHash?.let { hash ->
                Text("source=${hash.take(32)}")
            }
        }
    }
}

@Composable
private fun KnowledgeCapsulesOrganPanel(
    capsules: List<KnowledgeCapsuleEntity>,
    onOpenMemory: (String) -> Unit
) {
    Text("Capsulas de conocimiento", style = MaterialTheme.typography.titleMedium)
    if (capsules.isEmpty()) {
        EmptyOrganCard("Capsulas", "No hay capsulas activas todavia. Se crean cuando el texto tiene intencion explicita de conocimiento estable.")
        return
    }

    capsules.take(8).forEach { capsule ->
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${capsule.title} / v${capsule.capsuleVersion}", style = MaterialTheme.typography.titleMedium)
                Text(capsule.summary.take(420))
                Text("category=${capsule.capsuleCategory} type=${capsule.capsuleType} confidence=${capsule.confidence}")
                Text("claims=${capsule.claimsJson.take(260)}")
                Text("tags=${capsule.tags.take(180)}")
                Text("hash=${capsule.capsuleHash.take(32)} previous=${capsule.previousCapsuleHash?.take(24) ?: "none"}")
                capsule.sourceEventHash?.let { sourceHash ->
                    Button(onClick = { onOpenMemory(sourceHash) }) {
                        Text("Abrir recuerdo fuente")
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsolidatedKnowledgePanel(capsules: List<KnowledgeCapsuleEntity>) {
    val grouped = capsules.groupBy { capsule -> capsule.capsuleCategory }
    Text("Conocimiento consolidado", style = MaterialTheme.typography.titleMedium)
    if (grouped.isEmpty()) {
        EmptyOrganCard("Conocimiento", "Sin categorias consolidadas todavia.")
        return
    }

    grouped.entries.sortedByDescending { entry -> entry.value.size }.take(6).forEach { (category, categoryCapsules) ->
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(category, style = MaterialTheme.typography.titleMedium)
                Text("${categoryCapsules.size} capsulas / confianza promedio=${categoryCapsules.averageConfidence()}")
                Text(categoryCapsules.joinToString(" | ") { capsule -> capsule.title }.take(260))
            }
        }
    }
}

@Composable
private fun LinksOrganPanel(links: List<MemoryLinkEntity>) {
    Text("Links de memoria", style = MaterialTheme.typography.titleMedium)
    if (links.isEmpty()) {
        EmptyOrganCard("Links", "No hay enlaces recientes entre nodos de memoria.")
        return
    }

    links.take(10).forEach { link ->
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${link.relation} / strength=${link.strength}", style = MaterialTheme.typography.titleMedium)
                Text("${link.sourceType}:${link.sourceId.take(24)} -> ${link.targetType}:${link.targetId.take(24)}")
                Text("reason=${link.reason.take(220)}")
                Text("verification=${link.verificationState} privacy=${link.privacyVisibility}")
            }
        }
    }
}

@Composable
private fun MigrationOrganPanel(migrations: List<MigrationRecordEntity>) {
    Text("Migraciones", style = MaterialTheme.typography.titleMedium)
    if (migrations.isEmpty()) {
        EmptyOrganCard("Migraciones", "Todavia no hay historial de migraciones o descansos.")
        return
    }

    migrations.take(8).forEach { migration ->
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${migration.migrationType} / ${migration.status}", style = MaterialTheme.typography.titleMedium)
                Text("risk=${migration.riskLevel} approved=${migration.approvedByUser} rollback=${migration.rollbackAvailable}")
                Text("from=${migration.fromVersion} -> ${migration.toVersion}")
                Text("effect=${migration.expectedEffect.take(280)}")
                Text("errors=${migration.errorsJson.take(180)}")
            }
        }
    }
}

@Composable
private fun EmptyOrganCard(title: String, body: String) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body)
            AssistChip(onClick = {}, label = { Text("empty") })
        }
    }
}

private fun Boolean?.auditLabel(): String {
    return when (this) {
        true -> "verified"
        false -> "attention"
        null -> "not_checked"
    }
}

private fun List<KnowledgeCapsuleEntity>.averageConfidence(): Int {
    if (isEmpty()) return 0
    return sumOf { capsule -> capsule.confidence } / size
}
