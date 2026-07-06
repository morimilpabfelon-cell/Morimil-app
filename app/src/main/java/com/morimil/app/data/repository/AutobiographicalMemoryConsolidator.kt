package com.morimil.app.data.repository

import com.morimil.app.data.local.MemoryEventEntity
import org.json.JSONArray
import org.json.JSONObject

object AutobiographicalMemoryConsolidator {
    fun build(
        alias: String,
        sourceRestCycleEventHash: String,
        events: List<MemoryEventEntity>,
        generatedAtMillis: Long
    ): AutobiographicalMemoryDraft {
        val prioritized = events
            .filter { event -> event.memoryKind != "chat_noise" }
            .sortedWith(
                compareByDescending<MemoryEventEntity> { it.userConfirmed }
                    .thenByDescending { it.importance }
                    .thenByDescending { it.confidence }
                    .thenByDescending { it.createdAtMillis }
            )

        val identity = select(prioritized, limit = 4) { it.memoryKind == "identity" }
        val decisions = select(prioritized, limit = 6) { it.memoryKind == "decision" }
        val preferences = select(prioritized, limit = 5) { it.memoryKind == "preference" }
        val learning = select(prioritized, limit = 5) { it.memoryKind == "learning" }
        val corrections = select(prioritized, limit = 5) { it.memoryKind == "correction" }
        val errors = select(prioritized, limit = 4) { it.memoryKind == "error_detected" }
        val approvals = select(prioritized, limit = 4) { it.memoryKind == "approval" || it.memoryKind == "rejection" }
        val projects = select(prioritized, limit = 6) { event ->
            event.eventType.startsWith("project.") || event.tagsJson.contains("project")
        }
        val constraints = select(prioritized, limit = 6) { event ->
            event.memoryKind in setOf("correction", "rejection", "error_detected") ||
                event.tagsJson.contains("privacy") ||
                event.tagsJson.contains("security") ||
                event.body.contains("nunca", ignoreCase = true) ||
                event.body.contains("siempre", ignoreCase = true)
        }

        val selfSummary = buildString {
            appendLine("Morimil ($alias) es una instancia local con memoria viva firmada y append-only.")
            appendLine("Ultima consolidacion autobiografica: $generatedAtMillis.")
            appendLine("Fuente de consolidacion: ${sourceRestCycleEventHash.take(19)}.")
            appendSection("identidad", identity, 3)
            appendSection("decisiones_vigentes", decisions, 4)
        }.trim()

        val stableTraits = buildString {
            appendSection("identidad", identity, 4)
            appendSection("preferencias", preferences, 5)
            appendSection("aprendizajes", learning, 5)
        }.trim().ifBlank { "Sin rasgos estables consolidados todavia." }

        val activeGoals = buildString {
            appendSection("proyectos", projects, 6)
            appendSection("decisiones", decisions, 4)
            appendSection("aprobaciones_rechazos", approvals, 4)
        }.trim().ifBlank { "Sin metas activas consolidadas todavia." }

        val importantConstraints = buildString {
            appendSection("correcciones", corrections, 5)
            appendSection("errores", errors, 4)
            appendSection("restricciones", constraints, 6)
            appendLine("[dudas_abiertas]")
            appendLine("- none_detected_by_v1; Memory Doubt Layer debe refinar esta seccion.")
        }.trim()

        val sourceHashes = prioritized
            .take(16)
            .map { event -> event.eventHash }

        val evidenceJson = JSONObject()
            .put("schema", "morimil.autobiographical_consolidation.v1")
            .put("source_rest_cycle_event_hash", sourceRestCycleEventHash)
            .put("generated_at_millis", generatedAtMillis)
            .put("source_event_count", events.size)
            .put("identity_count", identity.size)
            .put("decision_count", decisions.size)
            .put("preference_count", preferences.size)
            .put("learning_count", learning.size)
            .put("correction_count", corrections.size)
            .put("project_signal_count", projects.size)
            .put("policy", "append_signed_event_before_snapshot_update")
            .put("open_doubt_policy", "future_memory_doubt_layer")
            .put("source_event_hashes", JSONArray(sourceHashes))
            .toString()

        return AutobiographicalMemoryDraft(
            alias = alias,
            selfSummary = selfSummary,
            stableTraits = stableTraits,
            activeGoals = activeGoals,
            importantConstraints = importantConstraints,
            evidenceJson = evidenceJson
        )
    }

    fun eventBody(draft: AutobiographicalMemoryDraft): String {
        return "Autobiografia local consolidada: alias=${draft.alias}; " +
            "self=${draft.selfSummary.take(220)}; " +
            "goals=${draft.activeGoals.take(220)}; " +
            "constraints=${draft.importantConstraints.take(220)}"
    }

    private fun select(
        events: List<MemoryEventEntity>,
        limit: Int,
        predicate: (MemoryEventEntity) -> Boolean
    ): List<MemoryEventEntity> {
        return events.filter(predicate).take(limit)
    }

    private fun StringBuilder.appendSection(
        title: String,
        events: List<MemoryEventEntity>,
        limit: Int
    ) {
        appendLine("[$title]")
        val selected = events.take(limit)
        if (selected.isEmpty()) {
            appendLine("- none")
        } else {
            selected.forEach { event ->
                appendLine(
                    "- ${event.memoryKind}/i${event.importance}/c${event.confidence}/${event.eventHash.take(19)}: " +
                        event.body.replace("\n", " ").take(220)
                )
            }
        }
    }
}

data class AutobiographicalMemoryDraft(
    val alias: String,
    val selfSummary: String,
    val stableTraits: String,
    val activeGoals: String,
    val importantConstraints: String,
    val evidenceJson: String
)
