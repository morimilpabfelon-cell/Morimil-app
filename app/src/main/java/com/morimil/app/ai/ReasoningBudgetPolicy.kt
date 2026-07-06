package com.morimil.app.ai

import kotlin.math.ceil

object ReasoningBudgetPolicy {
    const val DEFAULT_HISTORY_MESSAGES = 12

    private const val NORMAL_OUTPUT_TOKEN_CAP = 1536
    private const val EXTENDED_OUTPUT_TOKEN_CAP = 4096
    private const val MAX_HISTORY_TURN_CHARS = 900
    private const val MAX_DOCTRINE_CHARS = 2400
    private const val MAX_POLICY_CHARS = 1800
    private const val MAX_MEMORY_CONTEXT_CHARS = 3600
    private const val MAX_CAPSULE_CONTEXT_CHARS = 1600

    fun effectiveConfigForMessage(
        config: ReasoningProviderConfig,
        userMessage: String
    ): ReasoningProviderConfig {
        val requested = config.maxTokens.coerceIn(1, ReasoningProviderConfig.MAX_ALLOWED_TOKENS)
        val cap = if (requiresExtendedAnswerBudget(userMessage)) {
            EXTENDED_OUTPUT_TOKEN_CAP
        } else {
            NORMAL_OUTPUT_TOKEN_CAP
        }
        return config.copy(maxTokens = minOf(requested, cap))
    }

    fun compactHistory(history: List<ChatTurn>): List<ChatTurn> {
        val selected = history.takeLast(DEFAULT_HISTORY_MESSAGES)
        return selected.mapIndexed { index, turn ->
            val isCurrentUserMessage = index == selected.lastIndex && turn.role == "user"
            if (isCurrentUserMessage) {
                turn
            } else {
                turn.copy(content = compactTurn(turn.content))
            }
        }
    }

    fun compactDoctrine(text: String?): String {
        return compactSection(
            text = text,
            fallback = "Doctrina completa no cargada esta sesion. Usa las acciones permitidas/prohibidas como limite operativo.",
            maxChars = MAX_DOCTRINE_CHARS
        )
    }

    fun compactPolicy(text: String?): String {
        return compactSection(
            text = text,
            fallback = "Politica completa no cargada esta sesion. Mantente dentro de las acciones permitidas/prohibidas.",
            maxChars = MAX_POLICY_CHARS
        )
    }

    fun compactMemoryContext(text: String): String {
        return compactSection(
            text = text,
            fallback = "No hay memoria local segura para este turno.",
            maxChars = MAX_MEMORY_CONTEXT_CHARS
        )
    }

    fun compactCapsuleContext(text: String): String {
        return compactSection(
            text = text,
            fallback = "No knowledge capsules yet.",
            maxChars = MAX_CAPSULE_CONTEXT_CHARS
        )
    }

    fun estimateTokens(text: String): Int {
        if (text.isBlank()) return 0
        return ceil(text.length / 4.0).toInt()
    }

    private fun compactTurn(text: String): String {
        return compactSection(
            text = text,
            fallback = "",
            maxChars = MAX_HISTORY_TURN_CHARS
        )
    }

    private fun compactSection(text: String?, fallback: String, maxChars: Int): String {
        val clean = text?.trim().orEmpty()
        if (clean.isBlank()) return fallback
        if (clean.length <= maxChars) return clean

        val headBudget = (maxChars * 0.72).toInt()
        val tailBudget = maxChars - headBudget - TRUNCATION_MARKER.length
        val head = clean.take(headBudget).trimEnd()
        val tail = clean.takeLast(tailBudget.coerceAtLeast(0)).trimStart()
        return "$head\n$TRUNCATION_MARKER\n$tail"
    }

    private fun requiresExtendedAnswerBudget(userMessage: String): Boolean {
        val lower = userMessage.lowercase()
        return listOf(
            "codigo completo",
            "código completo",
            "respuesta larga",
            "explicacion completa",
            "explicación completa",
            "revisa profundo",
            "revision profunda",
            "revisión profunda",
            "analisis profundo",
            "análisis profundo",
            "documento completo",
            "sin resumir"
        ).any { marker -> marker in lower }
    }

    private const val TRUNCATION_MARKER = "[contexto_compactado_por_presupuesto: se mantiene inicio y cierre; detalles completos siguen en memoria local]"
}
