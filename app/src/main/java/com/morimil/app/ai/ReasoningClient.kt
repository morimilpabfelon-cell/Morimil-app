package com.morimil.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.Locale

data class ChatTurn(val role: String, val content: String)

data class ReasoningParsedReply(
    val text: String,
    val truncated: Boolean = false,
    val finishReason: String? = null
)

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
        return parseReplyResult(config, responseBody).text
    }

    fun parseReplyResult(config: ReasoningProviderConfig, responseBody: String): ReasoningParsedReply {
        val parsed = when (config.wireFormat) {
            ReasoningWireFormat.MESSAGES -> parseMessagesReplyResult(responseBody)
            ReasoningWireFormat.CHAT -> parseChatReplyResult(responseBody)
            ReasoningWireFormat.RESPONSES -> parseResponsesReplyResult(responseBody)
        }
        if (parsed.text.isNotBlank()) return parsed

        val fallback = parseFallbackReplyResult(responseBody)
        return if (fallback.text.isBlank()) {
            parsed
        } else {
            fallback.copy(
                truncated = parsed.truncated || fallback.truncated,
                finishReason = parsed.finishReason ?: fallback.finishReason
            )
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

    private fun parseMessagesReplyResult(body: String): ReasoningParsedReply {
        val root = JSONObject(body)
        val content = root.optJSONArray("content") ?: return ReasoningParsedReply(
            text = "",
            truncated = isTruncatedReason(root.nullableString("stop_reason")),
            finishReason = root.nullableString("stop_reason")
        )
        val output = StringBuilder()
        for (i in 0 until content.length()) {
            val block = content.optJSONObject(i) ?: continue
            if (block.optString("type") == "text") {
                output.append(block.optString("text"))
            }
        }
        val finishReason = root.nullableString("stop_reason") ?: root.nullableString("finish_reason")
        return ReasoningParsedReply(
            text = output.toString(),
            truncated = isTruncatedReason(finishReason),
            finishReason = finishReason
        )
    }

    private fun parseChatReplyResult(body: String): ReasoningParsedReply {
        val root = JSONObject(body)
        val choices = root.optJSONArray("choices") ?: return ReasoningParsedReply("")
        val first = choices.optJSONObject(0) ?: return ReasoningParsedReply("")
        val message = first.optJSONObject("message") ?: return ReasoningParsedReply("")
        val content = firstContentValue(message, "content", "reasoning_content", "refusal") ?: return ReasoningParsedReply("")
        val finishReason = first.nullableString("finish_reason") ?: message.nullableString("finish_reason")
        return ReasoningParsedReply(
            text = parseContentValue(content),
            truncated = isTruncatedReason(finishReason),
            finishReason = finishReason
        )
    }

    private fun parseResponsesReplyResult(body: String): ReasoningParsedReply {
        val root = JSONObject(body)
        val direct = root.optString("output_text")
        val status = root.nullableString("status")
        val incompleteReason = root.optJSONObject("incomplete_details")?.nullableString("reason")
        val finishReason = incompleteReason ?: status
        val truncated = isTruncatedReason(incompleteReason) || status == "incomplete" && incompleteReason == null
        if (direct.isNotBlank()) {
            return ReasoningParsedReply(
                text = direct,
                truncated = truncated,
                finishReason = finishReason
            )
        }

        val output = root.optJSONArray("output") ?: return ReasoningParsedReply(
            text = "",
            truncated = truncated,
            finishReason = finishReason
        )
        val text = StringBuilder()
        for (i in 0 until output.length()) {
            val item = output.optJSONObject(i) ?: continue
            val content = item.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val part = content.optJSONObject(j) ?: continue
                when (part.optString("type")) {
                    "output_text", "text" -> text.append(part.optString("text"))
                    "refusal" -> text.append(part.optString("refusal"))
                }
            }
        }
        return ReasoningParsedReply(
            text = text.toString(),
            truncated = truncated,
            finishReason = finishReason
        )
    }

    private fun parseFallbackReplyResult(body: String): ReasoningParsedReply {
        return runCatching {
            val root = JSONObject(body)
            val text = root.optString("output_text").takeIf { it.isNotBlank() }
                ?: root.optJSONObject("message")?.let { parseContentValue(firstContentValue(it, "content", "refusal") ?: "") }
                ?: root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.let { message ->
                    parseContentValue(firstContentValue(message, "content", "refusal") ?: "")
                }
                ?: root.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                ?: ""
            val finishReason = root.nullableString("finish_reason")
                ?: root.nullableString("stop_reason")
                ?: root.optJSONObject("incomplete_details")?.nullableString("reason")
            ReasoningParsedReply(
                text = text,
                truncated = isTruncatedReason(finishReason),
                finishReason = finishReason
            )
        }.getOrDefault(ReasoningParsedReply(""))
    }

    private fun firstContentValue(root: JSONObject, vararg keys: String): Any? {
        keys.forEach { key ->
            val value = root.opt(key)
            if (value != null && value != JSONObject.NULL) return value
        }
        return null
    }

    private fun parseContentValue(content: Any?): String {
        return when (content) {
            is String -> content
            is JSONObject -> content.optString("text").ifBlank { content.optString("content") }.ifBlank { content.optString("refusal") }
            is JSONArray -> {
                val output = StringBuilder()
                for (i in 0 until content.length()) {
                    val part = content.opt(i)
                    when (part) {
                        is String -> output.append(part)
                        is JSONObject -> output.append(
                            part.optString("text")
                                .ifBlank { part.optString("content") }
                                .ifBlank { part.optString("refusal") }
                        )
                    }
                }
                output.toString()
            }
            else -> ""
        }
    }

    private fun JSONObject.nullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { value -> value.isNotBlank() }
    }

    private fun isTruncatedReason(reason: String?): Boolean {
        val clean = reason?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return clean == "length" ||
            clean == "max_tokens" ||
            clean == "max_output_tokens" ||
            clean == "token_limit" ||
            clean.contains("max_token") ||
            clean.contains("output_token")
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
            val requestedValid = config.validated()
            val latestUserMessage = history.lastOrNull { turn -> turn.role == "user" }?.content.orEmpty()
            val valid = ReasoningBudgetPolicy
                .effectiveConfigForMessage(requestedValid, latestUserMessage)
                .validated()
            if (valid.requiresRuntimeKey) {
                require(runtimeKey.isNotBlank()) { "Falta la llave de razonamiento." }
            }
            val compactHistory = ReasoningBudgetPolicy.compactHistory(history)
            require(compactHistory.isNotEmpty()) { "No hay mensaje para enviar." }

            val initialReply = sendSingleRequest(
                valid = valid,
                runtimeKey = runtimeKey,
                systemPrompt = systemPrompt,
                history = compactHistory
            )
            require(initialReply.text.isNotBlank()) { "El motor no devolvio texto." }
            recoverTruncatedReply(
                valid = valid,
                runtimeKey = runtimeKey,
                systemPrompt = systemPrompt,
                originalHistory = compactHistory,
                initialReply = initialReply
            ).text
        }.recoverCatching { error ->
            if (error is SocketTimeoutException) {
                throw IllegalStateException(ReasoningNetworkTimeoutPolicy.userMessage(), error)
            }
            throw error
        }
    }

    private fun sendSingleRequest(
        valid: ReasoningProviderConfig,
        runtimeKey: String,
        systemPrompt: String,
        history: List<ChatTurn>
    ): ReasoningParsedReply {
        val requestBody = ReasoningWire.buildBody(valid, systemPrompt, history)
        val connection = (URL(valid.baseUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = ReasoningNetworkTimeoutPolicy.CONNECT_TIMEOUT_MS
            readTimeout = ReasoningNetworkTimeoutPolicy.READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
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

        return try {
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
            ReasoningWire.parseReplyResult(valid, responseBody)
        } finally {
            connection.disconnect()
        }
    }

    private fun recoverTruncatedReply(
        valid: ReasoningProviderConfig,
        runtimeKey: String,
        systemPrompt: String,
        originalHistory: List<ChatTurn>,
        initialReply: ReasoningParsedReply
    ): ReasoningParsedReply {
        if (!initialReply.truncated) return initialReply

        var combinedText = initialReply.text.trimEnd()
        var continuationHistory = originalHistory +
            ChatTurn(role = "assistant", content = initialReply.text) +
            ChatTurn(role = "user", content = CONTINUATION_PROMPT)

        repeat(MAX_CONTINUATION_CALLS) {
            val continuation = sendSingleRequest(
                valid = valid,
                runtimeKey = runtimeKey,
                systemPrompt = systemPrompt,
                history = continuationHistory
            )
            if (continuation.text.isBlank()) {
                return initialReply.copy(text = appendTruncationWarning(combinedText), truncated = true)
            }
            combinedText = joinContinuation(combinedText, continuation.text)
            if (!continuation.truncated) {
                return initialReply.copy(text = combinedText, truncated = false, finishReason = continuation.finishReason)
            }
            continuationHistory = continuationHistory +
                ChatTurn(role = "assistant", content = continuation.text) +
                ChatTurn(role = "user", content = CONTINUATION_PROMPT)
        }

        return initialReply.copy(text = appendTruncationWarning(combinedText), truncated = true)
    }

    private fun joinContinuation(left: String, right: String): String {
        val cleanLeft = left.trimEnd()
        val cleanRight = right.trimStart()
        if (cleanLeft.isBlank()) return cleanRight
        if (cleanRight.isBlank()) return cleanLeft
        return cleanLeft + "\n" + cleanRight
    }

    private fun appendTruncationWarning(text: String): String {
        return text.trimEnd() + "\n\n[Morimil detecto que el motor corto la respuesta por limite de salida. Sube max_tokens/max_output_tokens o pide continuar.]"
    }

    companion object {
        const val MAX_HISTORY_MESSAGES = ReasoningBudgetPolicy.DEFAULT_HISTORY_MESSAGES
        private const val MAX_CONTINUATION_CALLS = 1
        private const val CONTINUATION_PROMPT = "Continua exactamente desde donde se corto la respuesta anterior. No reinicies, no repitas introducciones y no cambies de tema."
        private val MESSAGES_VERSION_HEADER = "anth" + "ropic-version"
        private const val MESSAGES_VERSION = "2023-06-01"
    }
}
