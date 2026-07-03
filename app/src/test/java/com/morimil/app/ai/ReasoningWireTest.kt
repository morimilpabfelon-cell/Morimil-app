package com.morimil.app.ai

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningWireTest {
    private val history = listOf(
        ChatTurn("user", "hola"),
        ChatTurn("assistant", "hola"),
        ChatTurn("user", "recuerda mi nombre")
    )

    @Test
    fun messagesBodyCarriesSystemSeparately() {
        val cfg = ReasoningProviderConfig.fromPreset(ReasoningPreset.MESSAGES_COMPATIBLE)
            .copy(model = "model-a")
        val body = JSONObject(ReasoningWire.buildBody(cfg, "SYS", history))

        assertEquals("SYS", body.getString("system"))
        assertEquals("model-a", body.getString("model"))
        assertEquals(3, body.getJSONArray("messages").length())
    }

    @Test
    fun messagesBodyIsInferredFromMessagesEndpoint() {
        val cfg = ReasoningProviderConfig.fromPreset(ReasoningPreset.CUSTOM)
            .copy(baseUrl = "https://example.com/v1/messages", model = "model-a")
        val body = JSONObject(ReasoningWire.buildBody(cfg, "SYS", history))

        assertEquals(ReasoningWireFormat.MESSAGES, cfg.wireFormat)
        assertEquals("SYS", body.getString("system"))
        assertEquals(3, body.getJSONArray("messages").length())
    }

    @Test
    fun chatBodyUsesSystemAsFirstMessage() {
        val cfg = ReasoningProviderConfig.fromPreset(ReasoningPreset.CHAT_COMPATIBLE)
            .copy(baseUrl = "https://example.com/chat", model = "model-b")
        val body = JSONObject(ReasoningWire.buildBody(cfg, "SYS", history))
        val messages = body.getJSONArray("messages")

        assertFalse(body.has("system"))
        assertEquals(4, messages.length())
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertEquals("SYS", messages.getJSONObject(0).getString("content"))
    }

    @Test
    fun chatBodyIsInferredFromChatCompletionsEndpoint() {
        val cfg = ReasoningProviderConfig.fromPreset(ReasoningPreset.MESSAGES_COMPATIBLE)
            .copy(baseUrl = "https://example.com/v1/chat/completions", model = "model-c")
        val body = JSONObject(ReasoningWire.buildBody(cfg, "SYS", history))
        val messages = body.getJSONArray("messages")

        assertEquals(ReasoningWireFormat.CHAT, cfg.wireFormat)
        assertFalse(body.has("system"))
        assertEquals("system", messages.getJSONObject(0).getString("role"))
    }

    @Test
    fun responsesBodyIsInferredFromResponsesEndpoint() {
        val cfg = ReasoningProviderConfig.fromPreset(ReasoningPreset.CUSTOM)
            .copy(baseUrl = "https://example.com/v1/responses", model = "model-r")
        val body = JSONObject(ReasoningWire.buildBody(cfg, "SYS", history))
        val input = body.getJSONArray("input")

        assertEquals(ReasoningWireFormat.RESPONSES, cfg.wireFormat)
        assertEquals("SYS", body.getString("instructions"))
        assertEquals("model-r", body.getString("model"))
        assertEquals(3, input.length())
        assertEquals(1024, body.getInt("max_output_tokens"))
    }

    @Test
    fun messagesReplyParsesTextBlocks() {
        val cfg = ReasoningProviderConfig.fromPreset(ReasoningPreset.MESSAGES_COMPATIBLE)
            .copy(model = "model-a")
        val response = """
            {"content":[{"type":"text","text":"hola "},{"type":"text","text":"Morimil"}]}
        """.trimIndent()

        assertEquals("hola Morimil", ReasoningWire.parseReply(cfg, response))
    }

    @Test
    fun chatReplyParsesFirstChoice() {
        val cfg = ReasoningProviderConfig.fromPreset(ReasoningPreset.CHAT_COMPATIBLE)
            .copy(baseUrl = "https://example.com/chat", model = "model-b")
        val response = """
            {"choices":[{"message":{"role":"assistant","content":"hola Morimil"}}]}
        """.trimIndent()

        assertEquals("hola Morimil", ReasoningWire.parseReply(cfg, response))
    }

    @Test
    fun responsesReplyParsesOutputText() {
        val cfg = ReasoningProviderConfig.fromPreset(ReasoningPreset.RESPONSES_COMPATIBLE)
            .copy(baseUrl = "https://example.com/v1/responses", model = "model-r")
        val response = """
            {"output_text":"hola Morimil"}
        """.trimIndent()

        assertEquals("hola Morimil", ReasoningWire.parseReply(cfg, response))
    }

    @Test
    fun localEndpointDoesNotRequireRuntimeKey() {
        val cfg = ReasoningProviderConfig.fromPreset(ReasoningPreset.LOCAL_COMPATIBLE)
            .copy(model = "local-model")

        assertFalse(cfg.requiresRuntimeKey)
    }

    @Test
    fun remoteEndpointRequiresRuntimeKey() {
        val cfg = ReasoningProviderConfig.fromPreset(ReasoningPreset.MESSAGES_COMPATIBLE)
            .copy(baseUrl = "https://example.com/v1/messages", model = "model-a")

        assertTrue(cfg.requiresRuntimeKey)
    }
}