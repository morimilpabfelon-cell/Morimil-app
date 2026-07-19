package com.morimil.app.ai

import com.morimil.app.net.BoundedHttpBodyReader
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class DiscoveredReasoningModel(
    val id: String,
    val label: String = id,
    val rank: Int = 0
)

data class ReasoningDiscoveryResult(
    val formatLabel: String,
    val endpoint: String,
    val preset: ReasoningPreset,
    val models: List<DiscoveredReasoningModel>,
    val note: String
) {
    val bestModel: DiscoveredReasoningModel?
        get() = models.maxWithOrNull(compareBy<DiscoveredReasoningModel> { it.rank }.thenBy { it.id })
}

class ReasoningModelDiscoveryClient {
    fun discover(runtimeKey: String, endpointHint: String): Result<ReasoningDiscoveryResult> = runCatching {
        val cleanKey = runtimeKey.trim()
        val cleanEndpoint = endpointHint.trim()
        if (cleanEndpoint.isBlank()) {
            require(cleanKey.isNotBlank()) { "Pega una llave de razonamiento primero." }
            return@runCatching discoverDefaultResponsesCompatible(cleanKey)
        }

        discoverEndpointCompatible(cleanKey, cleanEndpoint)
    }

    private fun discoverDefaultResponsesCompatible(runtimeKey: String): ReasoningDiscoveryResult {
        val endpoint = DEFAULT_RESPONSES_ENDPOINT
        val root = getJson(url = DEFAULT_MODELS_ENDPOINT, headers = bearerHeaders(runtimeKey))
        val models = parseModelIds(root.optJSONArray("data"))
            .filter { !isBlockedModel(it.id) }
            .map { it.copy(rank = rankModel(it.id)) }
            .sortedWith(compareByDescending<DiscoveredReasoningModel> { it.rank }.thenBy { it.id })

        require(models.isNotEmpty()) { "La API no devolvio modelos de texto compatibles." }

        return ReasoningDiscoveryResult(
            formatLabel = "Responses-compatible",
            endpoint = endpoint,
            preset = ReasoningPreset.RESPONSES_COMPATIBLE,
            models = models,
            note = "Modelos de texto listados desde /v1/models y ordenados por señales de razonamiento."
        )
    }

    private fun discoverEndpointCompatible(runtimeKey: String, endpointHint: String): ReasoningDiscoveryResult {
        val endpoint = normalizeRuntimeEndpoint(endpointHint)
        require(ReasoningEndpointPolicy.isAllowedTemporaryReasoningEndpoint(endpoint)) {
            "El auxiliar local usa USB/ADB por loopback; las APIs remotas requieren HTTPS."
        }
        val preset = presetForEndpoint(endpoint)
        val isLocal = isLocalEndpoint(endpoint)
        if (!isLocal) require(runtimeKey.isNotBlank()) { "Pega una llave de razonamiento primero." }

        val root = getJson(
            url = modelsEndpointFor(endpoint),
            headers = headersFor(preset.wireFormat, runtimeKey, isLocal)
        )
        val models = parseModelIds(root.optJSONArray("data"))
            .filter { !isBlockedModel(it.id) }
            .map { it.copy(rank = rankModel(it.id)) }
            .sortedWith(compareByDescending<DiscoveredReasoningModel> { it.rank }.thenBy { it.id })

        require(models.isNotEmpty()) { "La API no devolvio modelos de texto compatibles." }

        return ReasoningDiscoveryResult(
            formatLabel = preset.displayName,
            endpoint = endpoint,
            preset = preset,
            models = models,
            note = "Modelos listados desde un catalogo compatible y ordenados por señales de razonamiento; el usuario conserva la seleccion final."
        )
    }

    private fun getJson(url: String, headers: Map<String, String>): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }

        try {
            val statusCode = connection.responseCode
            val responseBody = if (statusCode in 200..299) {
                BoundedHttpBodyReader.read(
                    stream = connection.inputStream,
                    declaredLength = connection.contentLengthLong,
                    maxBytes = MAX_CATALOG_BYTES
                )
            } else {
                BoundedHttpBodyReader.read(
                    stream = connection.errorStream,
                    declaredLength = connection.contentLengthLong,
                    maxBytes = MAX_ERROR_BYTES
                )
            }
            require(statusCode in 200..299) {
                "Listado de modelos rechazo la solicitud: HTTP $statusCode ${responseBody.take(MAX_ERROR_MESSAGE_CHARS)}"
            }
            return JSONObject(responseBody)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseModelIds(data: JSONArray?): List<DiscoveredReasoningModel> {
        if (data == null) return emptyList()
        val models = mutableListOf<DiscoveredReasoningModel>()
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            val id = item.optString("id").trim()
            if (id.isNotBlank()) models += DiscoveredReasoningModel(id = id)
        }
        return models.distinctBy { it.id }
    }

    private fun isBlockedModel(modelId: String): Boolean {
        val id = modelId.lowercase()
        return BLOCKED_MODEL_PARTS.any { id.contains(it) }
    }

    private fun rankModel(modelId: String): Int {
        val id = modelId.lowercase()
        var score = 40
        if (REASONING_SIGNALS.any { id.contains(it) }) score += 50
        if (id.startsWith("o1") || id.startsWith("o3") || id.startsWith("o4")) score += 45
        if (GENERAL_MODEL_FAMILIES.any { id.contains(it) }) score += 10
        if (id.contains("mini") || id.contains("small") || id.contains("lite")) score -= 5
        return score
    }

    private fun normalizeRuntimeEndpoint(endpointHint: String): String {
        val clean = endpointHint.trim().trimEnd('/')
        val lower = clean.lowercase()
        return when {
            lower.endsWith("/models") -> clean.removeSuffix("/models") + "/chat/completions"
            lower.endsWith("/v1") && isLocalEndpoint(clean) -> "$clean/chat/completions"
            lower.endsWith("/v1") -> "$clean/responses"
            lower.contains("/responses") || lower.contains("/chat/completions") || lower.contains("/messages") -> clean
            isLocalEndpoint(clean) -> "$clean/v1/chat/completions"
            else -> "$clean/v1/chat/completions"
        }
    }

    private fun modelsEndpointFor(runtimeEndpoint: String): String {
        val clean = runtimeEndpoint.trim().trimEnd('/')
        return when {
            clean.endsWith("/responses") -> clean.removeSuffix("/responses") + "/models"
            clean.endsWith("/chat/completions") -> clean.removeSuffix("/chat/completions") + "/models"
            clean.endsWith("/messages") -> clean.removeSuffix("/messages") + "/models"
            clean.endsWith("/models") -> clean
            clean.endsWith("/v1") -> "$clean/models"
            else -> "$clean/models"
        }
    }

    private fun presetForEndpoint(endpoint: String): ReasoningPreset {
        val lower = endpoint.lowercase()
        return when {
            lower.contains("/responses") -> ReasoningPreset.RESPONSES_COMPATIBLE
            lower.contains("/messages") -> ReasoningPreset.MESSAGES_COMPATIBLE
            isLocalEndpoint(endpoint) -> ReasoningPreset.LOCAL_USB_HELPER
            else -> ReasoningPreset.CHAT_COMPATIBLE
        }
    }

    private fun headersFor(
        wireFormat: ReasoningWireFormat,
        runtimeKey: String,
        isLocal: Boolean
    ): Map<String, String> {
        if (isLocal) return emptyMap()
        val cleanKey = runtimeKey.trim()
        require(cleanKey.isNotBlank()) { "Pega una llave de razonamiento primero." }
        return when (wireFormat) {
            ReasoningWireFormat.MESSAGES -> mapOf(
                API_KEY_HEADER to cleanKey,
                MESSAGES_VERSION_HEADER to MESSAGES_VERSION
            )
            ReasoningWireFormat.CHAT,
            ReasoningWireFormat.RESPONSES -> bearerHeaders(cleanKey)
        }
    }

    private fun bearerHeaders(runtimeKey: String): Map<String, String> {
        return mapOf("Authorization" to "Bearer $runtimeKey")
    }

    private fun isLocalEndpoint(endpoint: String): Boolean {
        return ReasoningEndpointPolicy.isLocalTrustedEndpoint(endpoint)
    }

    companion object {
        private const val TIMEOUT_MS = 30000
        private const val MAX_CATALOG_BYTES = 2 * 1024 * 1024
        private const val MAX_ERROR_BYTES = 64 * 1024
        private const val MAX_ERROR_MESSAGE_CHARS = 2_000
        private const val DEFAULT_RESPONSES_ENDPOINT = "https://api.openai.com/v1/responses"
        private const val DEFAULT_MODELS_ENDPOINT = "https://api.openai.com/v1/models"
        private const val API_KEY_HEADER = "x-api-key"
        private const val MESSAGES_VERSION_HEADER = "anthropic-version"
        private const val MESSAGES_VERSION = "2023-06-01"
        private val REASONING_SIGNALS = listOf(
            "reasoning",
            "reasoner",
            "thinking",
            "deep-think",
            "deepthink",
            "r1",
            "qwq"
        )
        private val GENERAL_MODEL_FAMILIES = listOf(
            "gpt",
            "claude",
            "gemini",
            "qwen",
            "llama",
            "mistral",
            "deepseek",
            "grok",
            "command"
        )
        private val BLOCKED_MODEL_PARTS = listOf(
            "embedding",
            "audio",
            "tts",
            "whisper",
            "image",
            "vision",
            "moderation",
            "transcribe",
            "realtime"
        )
    }
}
