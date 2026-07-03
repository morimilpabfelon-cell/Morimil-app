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
    val providerLabel: String,
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
        require(cleanKey.isNotBlank()) { "Pega una llave de razonamiento primero." }

        val hint = endpointHint.trim().lowercase()
        val attempts = if (hint.contains("anthropic") || hint.contains("claude")) {
            listOf(::discoverAnthropic, ::discoverOpenAi)
        } else {
            listOf(::discoverOpenAi, ::discoverAnthropic)
        }

        val errors = mutableListOf<String>()
        for (attempt in attempts) {
            try {
                return@runCatching attempt(cleanKey)
            } catch (error: Throwable) {
                errors += error.message.orEmpty()
            }
        }

        ReasoningDiscoveryResult(
            providerLabel = "Custom",
            endpoint = endpointHint.trim(),
            preset = ReasoningPreset.CUSTOM,
            models = emptyList(),
            note = "No se pudo listar modelos conocidos. Usa endpoint/modelo manual si esta API no expone catalogo."
        )
    }

    private fun discoverOpenAi(runtimeKey: String): ReasoningDiscoveryResult {
        val root = getJson(
            url = "https://api.openai.com/v1/models",
            headers = mapOf("Authorization" to "Bearer $runtimeKey")
        )
        val models = parseModelIds(root.optJSONArray("data"))
            .filter { isOpenAiReasoningCandidate(it.id) }
            .map { it.copy(rank = rankOpenAiModel(it.id)) }
            .sortedWith(compareByDescending<DiscoveredReasoningModel> { it.rank }.thenBy { it.id })

        require(models.isNotEmpty()) { "OpenAI no devolvio modelos de razonamiento compatibles." }

        return ReasoningDiscoveryResult(
            providerLabel = "OpenAI",
            endpoint = "https://api.openai.com/v1/responses",
            preset = ReasoningPreset.RESPONSES_COMPATIBLE,
            models = models,
            note = "Modelos listados desde /v1/models. Se usara Responses API."
        )
    }

    private fun discoverAnthropic(runtimeKey: String): ReasoningDiscoveryResult {
        val root = getJson(
            url = "https://api.anthropic.com/v1/models",
            headers = mapOf(
                "x-api-key" to runtimeKey,
                "anthropic-version" to "2023-06-01"
            )
        )
        val models = parseAnthropicModels(root.optJSONArray("data"))
            .filter { it.id.contains("claude", ignoreCase = true) }
            .map { it.copy(rank = rankAnthropicModel(it.id)) }
            .sortedWith(compareByDescending<DiscoveredReasoningModel> { it.rank }.thenBy { it.id })

        require(models.isNotEmpty()) { "Anthropic no devolvio modelos Claude compatibles." }

        return ReasoningDiscoveryResult(
            providerLabel = "Anthropic",
            endpoint = "https://api.anthropic.com/v1/messages",
            preset = ReasoningPreset.MESSAGES_COMPATIBLE,
            models = models,
            note = "Modelos listados desde /v1/models. Se usara Messages API."
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

    private fun parseAnthropicModels(data: JSONArray?): List<DiscoveredReasoningModel> {
        if (data == null) return emptyList()
        val models = mutableListOf<DiscoveredReasoningModel>()
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            val id = item.optString("id").trim()
            if (id.isBlank()) continue
            val label = item.optString("display_name").takeIf { it.isNotBlank() } ?: id
            models += DiscoveredReasoningModel(id = id, label = label)
        }
        return models
    }

    private fun isOpenAiReasoningCandidate(modelId: String): Boolean {
        val id = modelId.lowercase()
        if (BLOCKED_MODEL_PARTS.any { id.contains(it) }) return false
        return id.startsWith("gpt-") || id.startsWith("o1") || id.startsWith("o3") || id.startsWith("o4")
    }

    private fun rankOpenAiModel(modelId: String): Int {
        val id = modelId.lowercase()
        return when {
            id.contains("gpt-5.5") -> 100
            id.contains("gpt-5") -> 95
            id.startsWith("o3") -> 90
            id.startsWith("o4") -> 88
            id.contains("gpt-4.1") -> 82
            id.contains("gpt-4o") -> 75
            id.contains("gpt-4") -> 65
            id.contains("gpt-3.5") -> 20
            else -> 40
        }
    }

    private fun rankAnthropicModel(modelId: String): Int {
        val id = modelId.lowercase()
        return when {
            id.contains("opus") -> 95
            id.contains("sonnet") -> 85
            id.contains("haiku") -> 65
            else -> 40
        }
    }

    companion object {
        private const val TIMEOUT_MS = 30000
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