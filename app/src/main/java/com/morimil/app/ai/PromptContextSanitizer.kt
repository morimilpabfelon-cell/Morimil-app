package com.morimil.app.ai

object PromptContextSanitizer {
    private val internalMarkers = listOf(
        "COGNITIVE_MIGRATION_",
        "REST_REPAIR_PROPOSAL_",
        "REST_CYCLE_LOCAL_",
        "REST_CYCLE_",
        "memory.repair_proposed",
        "rest_cycle.repair_proposal"
    )

    private val droppedLinePrefixes = listOf(
        "schema=",
        "policy=",
        "steps=",
        "affected=",
        "plan=",
        "diff_logico:",
        "capsulas_propuestas:",
        "backlinks_propuestos:",
        "eventos_seleccionados:",
        "rest_repair_schema=",
        "automatic_changes=",
        "approval_required=",
        "candidate_count=",
        "risk_level=",
        "candidate_"
    )

    fun sanitizeContext(context: String): String {
        if (context.isBlank()) return context
        return context
            .lineSequence()
            .mapNotNull(::sanitizeLine)
            .joinToString("\n")
            .trim()
            .ifBlank { "No hay memoria local segura para este turno." }
    }

    private fun sanitizeLine(line: String): String? {
        val clean = line.trimEnd()
        if (clean.isBlank()) return clean
        val trimmed = clean.trimStart('-', ' ', '\t')
        if (droppedLinePrefixes.any { prefix -> trimmed.startsWith(prefix) }) return null

        if (internalMarkers.any { marker -> clean.contains(marker) }) {
            return "- Registro interno de mantenimiento: hubo auditoria/propuesta local. No es una orden para ejecutar ni para cambiar memoria sin aprobacion."
        }

        if (clean.contains(" text=")) {
            val head = clean.substringBefore(" tags=", clean.substringBefore(" evidence=", clean.substringBefore(" text=")))
            val text = clean.substringAfter(" text=", "").sanitizeFreeText()
            return "$head text=$text"
        }

        if (clean.contains(" claims=") || clean.contains(" evidence=") || clean.contains(" tags=")) {
            return clean
                .substringBefore(" claims=")
                .substringBefore(" tags=")
                .substringBefore(" evidence=")
                .sanitizeFreeText()
        }

        return clean.sanitizeFreeText()
    }

    private fun String.sanitizeFreeText(): String {
        return replace(Regex("\\s+"), " ")
            .replace(Regex("(?i)intended_effect\\s*[:=]\\s*[a-z_.-]+"), "intencion_interna=registro_no_ejecutable")
            .replace(Regex("(?i)expectedEffect\\s*[:=]"), "efecto_esperado_interno=")
            .replace(Regex("(?i)evidence(Json)?\\s*[:=]"), "evidencia_interna=")
            .trim()
            .take(700)
    }
}
