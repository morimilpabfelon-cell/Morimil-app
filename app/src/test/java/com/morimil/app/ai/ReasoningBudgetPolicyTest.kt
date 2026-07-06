package com.morimil.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningBudgetPolicyTest {
    @Test
    fun normalMessagesAreCappedToControlledOutputBudget() {
        val config = ReasoningProviderConfig.fromPreset(ReasoningPreset.CHAT_COMPATIBLE)
            .copy(baseUrl = "https://example.com/v1/chat/completions", model = "model", maxTokens = 8192)

        val effective = ReasoningBudgetPolicy.effectiveConfigForMessage(config, "hola revisa esto")

        assertEquals(1536, effective.maxTokens)
    }

    @Test
    fun explicitDeepReviewCanUseExtendedBudget() {
        val config = ReasoningProviderConfig.fromPreset(ReasoningPreset.CHAT_COMPATIBLE)
            .copy(baseUrl = "https://example.com/v1/chat/completions", model = "model", maxTokens = 8192)

        val effective = ReasoningBudgetPolicy.effectiveConfigForMessage(config, "haz una revision profunda del error")

        assertEquals(4096, effective.maxTokens)
    }

    @Test
    fun lowerUserConfiguredBudgetIsPreserved() {
        val config = ReasoningProviderConfig.fromPreset(ReasoningPreset.CHAT_COMPATIBLE)
            .copy(baseUrl = "https://example.com/v1/chat/completions", model = "model", maxTokens = 700)

        val effective = ReasoningBudgetPolicy.effectiveConfigForMessage(config, "respuesta normal")

        assertEquals(700, effective.maxTokens)
    }

    @Test
    fun historyIsLimitedAndOlderTurnsAreCompacted() {
        val longText = "x".repeat(2_000)
        val history = (1..20).map { index ->
            ChatTurn(role = if (index % 2 == 0) "assistant" else "user", content = "turn=$index $longText")
        } + ChatTurn(role = "user", content = longText)

        val compacted = ReasoningBudgetPolicy.compactHistory(history)

        assertEquals(ReasoningBudgetPolicy.DEFAULT_HISTORY_MESSAGES, compacted.size)
        assertEquals(longText, compacted.last().content)
        assertTrue(compacted.first().content.length < 1_100)
        assertTrue(compacted.first().content.contains("contexto_compactado_por_presupuesto"))
    }

    @Test
    fun promptSectionsAreCompactedWithMarker() {
        val text = "abc ".repeat(2_000)

        val compacted = ReasoningBudgetPolicy.compactMemoryContext(text)

        assertTrue(compacted.length < text.length)
        assertTrue(compacted.contains("contexto_compactado_por_presupuesto"))
    }
}
