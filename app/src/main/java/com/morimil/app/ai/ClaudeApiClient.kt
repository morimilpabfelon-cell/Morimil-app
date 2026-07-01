package com.morimil.app.ai

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ChatTurn(val role: String, val content: String)

/**
 * Calls the Claude Messages API. The model itself is stateless between
 * calls -- every request must include the relevant memory as `messages`.
 * That memory always comes from the phone's own local storage; this client
 * never stores anything itself, it only relays.
 */
class ClaudeApiClient {

    fun sendMessage(
        apiKey: String,
        systemPrompt: String,
        history: List<ChatTurn>
    ): Result<String> = runCatching {
        require(apiKey.isNotBlank()) { "Missing Anthropic API key." }
        require(history.isNotEmpty()) { "No message to send." }

        val messagesArray = JSONArray()
        for (turn in history) {
            messagesArray.put(
                JSONObject()
                    .put("role", turn.role)
                    .put("content", turn.content)
            )
        }

        val requestBody = JSONObject()
            .put("model", MODEL)
            .put("max_tokens", MAX_TOKENS)
            .put("system", systemPrompt)
            .put("messages", messagesArray)
            .toString()

        val connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("anthropic-version", API_VERSION)
        }

        try {
            connection.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }

            val statusCode = connection.responseCode
            val body = if (statusCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }
            require(statusCode in 200..299) { "Claude API error: HTTP $statusCode $body" }

            val root = JSONObject(body)
            val contentArray = root.getJSONArray("content")
            val textParts = StringBuilder()
            for (i in 0 until contentArray.length()) {
                val block = contentArray.getJSONObject(i)
                if (block.optString("type") == "text") {
                    textParts.append(block.optString("text"))
                }
            }
            require(textParts.isNotEmpty()) { "Claude API returned no text content." }
            textParts.toString()
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val API_URL = "https://api.anthropic.com/v1/messages"
        private const val API_VERSION = "2023-06-01"
        private const val MODEL = "claude-sonnet-5"
        private const val MAX_TOKENS = 1024
        private const val TIMEOUT_MS = 30_000

        // Sensible internal default: bounds cost/latency per request while
        // keeping enough continuity for a real conversation. Not exposed as
        // a user-facing setting.
        const val MAX_HISTORY_MESSAGES = 50
    }
}
