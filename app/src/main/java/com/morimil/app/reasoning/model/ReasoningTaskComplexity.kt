package com.morimil.app.reasoning.model

import com.morimil.app.reasoning.ReasoningIntent
import java.text.Normalizer
import java.util.Locale

enum class ReasoningTaskComplexity {
    LIGHT_LOCAL,
    MEMORY_LOCAL,
    WEB_CONTEXT_LOCAL,
    DEEP_ANALYSIS,
    CODE_REVIEW,
    ARCHITECTURE_CRITICAL,
    TOOL_OR_AGENT,
    UNKNOWN
}

object ReasoningTaskComplexityClassifier {
    fun classify(input: String, intent: ReasoningIntent): ReasoningTaskComplexity {
        val text = normalize(input)
        if (text.isBlank()) return ReasoningTaskComplexity.UNKNOWN
        return when {
            intent == ReasoningIntent.TOOL_OR_AGENT_REQUEST -> ReasoningTaskComplexity.TOOL_OR_AGENT
            intent == ReasoningIntent.ARCHITECTURE_REVIEW -> ReasoningTaskComplexity.ARCHITECTURE_CRITICAL
            intent == ReasoningIntent.PROJECT_WORK -> ReasoningTaskComplexity.CODE_REVIEW
            intent == ReasoningIntent.SELF_IMPROVEMENT -> ReasoningTaskComplexity.DEEP_ANALYSIS
            hasAny(text, "busca", "buscar", "internet", "web", "google", "online") -> ReasoningTaskComplexity.WEB_CONTEXT_LOCAL
            intent == ReasoningIntent.MEMORY_QUERY -> ReasoningTaskComplexity.MEMORY_LOCAL
            hasAny(text, "codigo", "kotlin", "gradle", "build", "error", "repo") -> ReasoningTaskComplexity.CODE_REVIEW
            hasAny(text, "arquitectura", "kernel", "motor", "runtime", "seguridad") -> ReasoningTaskComplexity.ARCHITECTURE_CRITICAL
            hasAny(text, "revisa profundo", "auditoria", "analiza", "nivel asi", "no inventes") -> ReasoningTaskComplexity.DEEP_ANALYSIS
            text.length <= 220 -> ReasoningTaskComplexity.LIGHT_LOCAL
            else -> ReasoningTaskComplexity.DEEP_ANALYSIS
        }
    }

    fun routingHint(complexity: ReasoningTaskComplexity): String {
        return when (complexity) {
            ReasoningTaskComplexity.LIGHT_LOCAL -> "prefer_configured_backend"
            ReasoningTaskComplexity.MEMORY_LOCAL -> "prefer_configured_backend"
            ReasoningTaskComplexity.WEB_CONTEXT_LOCAL -> "prefer_configured_backend"
            ReasoningTaskComplexity.DEEP_ANALYSIS -> "prefer_configured_backend"
            ReasoningTaskComplexity.CODE_REVIEW -> "prefer_configured_backend"
            ReasoningTaskComplexity.ARCHITECTURE_CRITICAL -> "prefer_configured_backend"
            ReasoningTaskComplexity.TOOL_OR_AGENT -> "prefer_configured_backend"
            ReasoningTaskComplexity.UNKNOWN -> "prefer_configured_backend"
        }
    }

    private fun hasAny(text: String, vararg terms: String): Boolean {
        return terms.any { term -> text.contains(normalize(term)) }
    }

    private fun normalize(value: String): String {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)
            .trim()
    }
}
