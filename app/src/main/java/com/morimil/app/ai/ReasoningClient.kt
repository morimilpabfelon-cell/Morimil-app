package com.morimil.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ChatTurn(val role: String, val content: String)

object ReasoningWire {
    fun buildBody(
        config: ReasoningProviderConfig,
        systemPrompt: String,
        history: List<ChatTurn>
    ): String {
        return when (config.wireFormat) {
            ReasoningWireFormat.MESSAGES -> messagesBody(config, systemPrompt, history)
            ReasoningWireFormat.CHAT -> chatBody(config, systemPrompt, history)
            ReasoningWireFormat.RESPONSES -> responsesBody(config, systemPrompt, history)
        }
    }

    fun parseReply(config: ReasoningProviderConfig, responseBody: String): String {
        return when (config.wireFormat) {
            ReasoningWireFormat.MESSAGES -> parseMessagesReply(responseBody)
            ReasoningWireFormat.CHAT -> parseChatReply(responseBody)
            ReasoningWireFormat.RESPONSES -> parseResponsesReply(responseBody)
        }
    }

    private fun messagesBody(
        config: ReasoningProviderConfig,
        systemPrompt: String,
        history: List<ChatTurn>
    ): String {
        val messages = JSONArray()
        history.forEach { turn ->
            messages.put(JSONObject().put("role", turn.role).put("content", turn.content))
        }
        return JSONObject()
            .put("model", config.model)
            .put("max_tokens", config.maxTokens)
            .put("system", systemPrompt)
            .put("messages", messages)
            .toString()
    }

    private fun chatBody(
        config: ReasoningProviderConfig,
        systemPrompt: String,
        history: List<ChatTurn>
    ): String {
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
        history.forEach { turn ->
            messages.put(JSONObject().put("role", turn.role).put("content", turn.content))
        }
        return JSONObject()
            .put("model", config.model)
            .put("max_tokens", config.maxTokens)
            .put("messages", messages)
            .toString()
    }

    private fun responsesBody(
        config: ReasoningProviderConfig,
        systemPrompt: String,
        history: List<ChatTurn>
    ): String {
        val input = JSONArray()
        history.forEach { turn ->
            input.put(
                JSONObject()
                    .put("role", turn.role)
                    .put("content", turn.content)
            )
        }
        return JSONObject()
            .put("model", config.model)
            .put("instructions", systemPrompt)
            .put("input", input)
            .put("max_output_tokens", config.maxTokens)
            .toString()
    }

    private fun parseMessagesReply(body: String): String {
        val root = JSONObject(body)
        val content = root.optJSONArray("content") ?: return ""
        val output = StringBuilder()
        for (i in 0 until content.length()) {
            val block = content.optJSONObject(i) ?: continue
            if (block.optString("type") == "text") {
                output.append(block.optString("text"))
            }
        }
        return output.toString()
    }

    private fun parseChatReply(body: String): String {
        val root = JSONObject(body)
        val choices = root.optJSONArray("choices") ?: return ""
        val first = choices.optJSONObject(0) ?: return ""
        val message = first.optJSONObject("message") ?: return ""
        val content = message.opt("content") ?: return ""
        return when (content) {
            is String -> content
            is JSONArray -> {
                val output = StringBuilder()
                for (i in 0 until content.length()) {
                    val part = content.optJSONObject(i) ?: continue
                    output.append(part.optString("text"))
                }
                output.toString()
            }
            else -> ""
        }
    }

    private fun parseResponsesReply(body: String): String {
        val root = JSONObject(body)
        val direct = root.optString("output_text")
        if (direct.isNotBlank()) return direct

        val output = root.optJSONArray("output") ?: return ""
        val text = StringBuilder()
        for (i in 0 until output.length()) {
            val item = output.optJSONObject(i) ?: continue
            val content = item.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val part = content.optJSONObject(j) ?: continue
                when (part.optString("type")) {
                    "output_text", "text" -> text.append(part.optString("text"))
                }
            }
        }
        return text.toString()
    }
}

class ReasoningClient {
    suspend fun sendMessage(
        config: ReasoningProviderConfig,
        runtimeKey: String,
        systemPrompt: String,
        history: List<ChatTurn>
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val valid = config.validated()
        if (valid.requiresRuntimeKey) {
            require(runtimeKey.isNotBlank()) { "Falta la llave de razonamiento." }
        }
        require(history.isNotEmpty()) { "No hay mensaje para enviar." }

        val requestBody = ReasoningWire.buildBody(valid, systemPrompt, history)
        val connection = (URL(valid.baseUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            when (valid.wireFormat) {
                ReasoningWireFormat.MESSAGES -> {
                    if (runtimeKey.isNotBlank()) setRequestProperty("x-" + "api-key", runtimeKey)
                    setRequestProperty(MESSAGES_VERSION_HEADER, MESSAGES_VERSION)
                }
                ReasoningWireFormat.CHAT,
                ReasoningWireFormat.RESPONSES -> {
                    if (runtimeKey.isNotBlank()) setRequestProperty("Authorization", "Bearer " + runtimeKey)
                }
            }
        }

        try {
            connection.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }
            val statusCode = connection.responseCode
            val responseBody = if (statusCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }
            require(statusCode in 200..299) {
                "Motor de razonamiento rechazo la solicitud: HTTP $statusCode $responseBody"
            }
            val reply = ReasoningWire.parseReply(valid, responseBody)
            require(reply.isNotBlank()) { "El motor no devolvio texto." }
            reply
        } finally {
            connection.disconnect()
        }
        }
    }

    companion object {
        const val MAX_HISTORY_MESSAGES = 50
        private const val TIMEOUT_MS = 45000
        private val MESSAGES_VERSION_HEADER = "anth" + "ropic-version"
        private const val MESSAGES_VERSION = "2023-06-01"
    }
}

