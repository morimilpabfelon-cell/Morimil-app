package com.morimil.app.ai

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
            .filter { isRemoteReasoningCandidate(it.id) }
            .map { it.copy(rank = rankModel(it.id)) }
            .sortedWith(compareByDescending<DiscoveredReasoningModel> { it.rank }.thenBy { it.id })

        require(models.isNotEmpty()) { "La API no devolvio modelos de razonamiento compatibles." }

        return ReasoningDiscoveryResult(
            formatLabel = "Responses-compatible",
            endpoint = endpoint,
            preset = ReasoningPreset.RESPONSES_COMPATIBLE,
            models = models,
            note = "Modelos listados desde /v1/models. Se usara formato Responses."
        )
    }

    private fun discoverEndpointCompatible(runtimeKey: String, endpointHint: String): ReasoningDiscoveryResult {
        val endpoint = normalizeRuntimeEndpoint(endpointHint)
        val preset = presetForEndpoint(endpoint)
        val isLocal = isLocalEndpoint(endpoint)
        if (!isLocal) require(runtimeKey.isNotBlank()) { "Pega una llave de razonamiento primero." }

        val root = getJson(
            url = modelsEndpointFor(endpoint),
            headers = headersFor(preset.wireFormat, runtimeKey, isLocal)
        )
        val models = parseModelIds(root.optJSONArray("data"))
            .filter { model ->
                if (preset == ReasoningPreset.RESPONSES_COMPATIBLE) {
                    isRemoteReasoningCandidate(model.id)
                } else {
                    !isBlockedModel(model.id)
                }
            }
            .map { it.copy(rank = rankModel(it.id)) }
            .sortedWith(compareByDescending<DiscoveredReasoningModel> { it.rank }.thenBy { it.id })

        require(models.isNotEmpty()) { "La API no devolvio modelos compatibles." }

        return ReasoningDiscoveryResult(
            formatLabel = preset.displayName,
            endpoint = endpoint,
            preset = preset,
            models = models,
            note = "Modelos listados desde catalogo compatible. Se usara ${preset.displayName}."
        )
    }

    private fun getJson(url: String, headers: Map<String, String>): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }

        try {
            val statusCode = connection.responseCode
            val responseBody = if (statusCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }
            require(statusCode in 200..299) {
                "Listado de modelos rechazo la solicitud: HTTP $statusCode $responseBody"
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
        return models
    }

    private fun isRemoteReasoningCandidate(modelId: String): Boolean {
        val id = modelId.lowercase()
        if (isBlockedModel(id)) return false
        return id.startsWith("gpt-") || id.startsWith("o1") || id.startsWith("o3") || id.startsWith("o4")
    }

    private fun isBlockedModel(modelId: String): Boolean {
        val id = modelId.lowercase()
        return BLOCKED_MODEL_PARTS.any { id.contains(it) }
    }

    private fun rankModel(modelId: String): Int {
        val id = modelId.lowercase()
        return when {
            id.contains("gpt-5.5") -> 100
            id.contains("gpt-5") -> 95
            id.startsWith("o3") -> 90
            id.startsWith("o4") -> 88
            id.contains("gpt-4.1") -> 82
            id.contains("gpt-4o") -> 75
            id.contains("gpt-4") -> 65
            id.contains("qwen") -> 58
            id.contains("llama") -> 56
            id.contains("mistral") -> 54
            id.contains("gemma") -> 52
            id.contains("gpt-3.5") -> 20
            else -> 40
        }
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
            isLocalEndpoint(endpoint) -> ReasoningPreset.LOCAL_COMPATIBLE
            else -> ReasoningPreset.CHAT_COMPATIBLE
        }
    }

    private fun headersFor(
        wireFormat: ReasoningWireFormat,
        runtimeKey: String,
        isLocal: Boolean
    ): Map<String, String> {
        if (isLocal || runtimeKey.isBlank()) return emptyMap()
        return when (wireFormat) {
            ReasoningWireFormat.MESSAGES -> mapOf(
                "x-" + "api-key" to runtimeKey,
                MESSAGES_VERSION_HEADER to MESSAGES_VERSION
            )
            ReasoningWireFormat.CHAT,
            ReasoningWireFormat.RESPONSES -> bearerHeaders(runtimeKey)
        }
    }

    private fun bearerHeaders(runtimeKey: String): Map<String, String> {
        return mapOf("Authorization" to "Bearer $runtimeKey")
    }

    private fun isLocalEndpoint(endpoint: String): Boolean {
        val lower = endpoint.lowercase()
        return lower.startsWith("http://127.0.0.1") ||
            lower.startsWith("http://localhost") ||
            lower.startsWith("http://10.0.2.2")
    }

    companion object {
        private const val TIMEOUT_MS = 30000
        private const val DEFAULT_RESPONSES_ENDPOINT = "https://api.openai.com/v1/responses"
        private const val DEFAULT_MODELS_ENDPOINT = "https://api.openai.com/v1/models"
        private val MESSAGES_VERSION_HEADER = "anth" + "ropic-version"
        private const val MESSAGES_VERSION = "2023-06-01"
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
