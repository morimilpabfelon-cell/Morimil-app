package com.morimil.app.reasoning

import java.text.Normalizer
import java.util.Locale

enum class ReasoningIntent {
    GENERAL_CHAT,
    MEMORY_QUERY,
    PROJECT_WORK,
    ARCHITECTURE_REVIEW,
    TOOL_OR_AGENT_REQUEST,
    SELF_IMPROVEMENT,
    STATUS_CHECK,
    UNKNOWN
}

/**
 * Deterministic first-pass intent detector.
 *
 * This is intentionally simple and local. It gives the kernel a stable signal
 * before any model is consulted.
 */
object LocalIntentDetector {
    fun detect(input: String): ReasoningIntent {
        val text = normalize(input)
        if (text.isBlank()) return ReasoningIntent.UNKNOWN
        return when {
            hasAny(text, "memoria", "recuerdo", "recuerdas", "recordar", "living memory") -> ReasoningIntent.MEMORY_QUERY
            hasAny(text, "estado", "salud", "audit", "auditoria", "diagnostico", "diagnóstico") -> ReasoningIntent.STATUS_CHECK
            hasAny(text, "github", "repo", "repositorio", "gradle", "compilar", "build", "android studio", "powershell") -> ReasoningIntent.PROJECT_WORK
            hasAny(text, "arquitectura", "kernel", "motor", "runtime", "orquestador", "reasoning") -> ReasoningIntent.ARCHITECTURE_REVIEW
            hasAny(text, "herramienta", "agente", "ejecuta", "delegar", "pc executor", "comando") -> ReasoningIntent.TOOL_OR_AGENT_REQUEST
            hasAny(text, "mejorar", "auto mejora", "automejora", "migracion cognitiva", "migración cognitiva", "asi") -> ReasoningIntent.SELF_IMPROVEMENT
            else -> ReasoningIntent.GENERAL_CHAT
        }
    }

    private fun hasAny(text: String, vararg terms: String): Boolean {
        return terms.any { term -> text.contains(normalize(term)) }
    }

    private fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)
            .trim()
    }
}
