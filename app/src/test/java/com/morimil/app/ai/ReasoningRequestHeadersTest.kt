package com.morimil.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningRequestHeadersTest {
    @Test
    fun localUsbRequestNeverReceivesStoredApiCredential() {
        val config = ReasoningProviderConfig.fromPreset(ReasoningPreset.LOCAL_USB_HELPER)
            .copy(model = "llama3.2")
            .validated()

        val headers = ReasoningRequestHeaders.build(config, "must-not-leave-secret-vault")

        assertTrue(headers.isEmpty())
    }

    @Test
    fun remoteChatRequestUsesBearerAuthentication() {
        val config = ReasoningProviderConfig(
            preset = ReasoningPreset.CHAT_COMPATIBLE,
            baseUrl = "https://provider.example/v1/chat/completions",
            model = "reasoning-model"
        ).validated()

        val headers = ReasoningRequestHeaders.build(config, "remote-key")

        assertEquals("Bearer remote-key", headers["Authorization"])
        assertEquals(1, headers.size)
    }

    @Test
    fun remoteMessagesRequestUsesProviderCompatibleHeaders() {
        val config = ReasoningProviderConfig(
            preset = ReasoningPreset.MESSAGES_COMPATIBLE,
            baseUrl = "https://provider.example/v1/messages",
            model = "reasoning-model"
        ).validated()

        val headers = ReasoningRequestHeaders.build(config, "remote-key")

        assertEquals("remote-key", headers["x-api-key"])
        assertEquals("2023-06-01", headers["anthropic-version"])
    }

    @Test
    fun remoteRequestWithoutCredentialFailsClosed() {
        val config = ReasoningProviderConfig(
            preset = ReasoningPreset.RESPONSES_COMPATIBLE,
            baseUrl = "https://provider.example/v1/responses",
            model = "reasoning-model"
        ).validated()

        assertTrue(runCatching { ReasoningRequestHeaders.build(config, "") }.isFailure)
    }
}
