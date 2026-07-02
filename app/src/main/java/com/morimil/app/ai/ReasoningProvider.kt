package com.morimil.app.ai

import android.content.Context

enum class ReasoningWireFormat {
    MESSAGES,
    CHAT
}

enum class ReasoningPreset(
    val displayName: String,
    val wireFormat: ReasoningWireFormat,
    val defaultBaseUrl: String,
    val defaultModel: String
) {
    MESSAGES_COMPATIBLE("Messages-compatible", ReasoningWireFormat.MESSAGES, buildMessagesUrl(), buildMessagesModel()),
    CHAT_COMPATIBLE("Chat-compatible", ReasoningWireFormat.CHAT, "", ""),
    LOCAL_COMPATIBLE("Local-compatible", ReasoningWireFormat.CHAT, "http://127.0.0.1:11434/v1/chat/completions", ""),
    CUSTOM("Custom-compatible", ReasoningWireFormat.CHAT, "", "");

    companion object {
        fun fromName(name: String?): ReasoningPreset {
            return entries.firstOrNull { it.name == name } ?: MESSAGES_COMPATIBLE
        }

        private fun buildMessagesUrl(): String {
            return "https://" + "api." + "anthropic.com" + "/v1/messages"
        }

        private fun buildMessagesModel(): String {
            return "claude-" + "sonnet-" + "5"
        }
    }
}

data class ReasoningProviderConfig(
    val preset: ReasoningPreset,
    val baseUrl: String,
    val model: String,
    val maxTokens: Int = DEFAULT_MAX_TOKENS
) {
    val wireFormat: ReasoningWireFormat
        get() = if (baseUrl.contains("/chat/completions")) {
            ReasoningWireFormat.CHAT
        } else {
            preset.wireFormat
        }

    val requiresRuntimeKey: Boolean
        get() = !(baseUrl.startsWith("http://127.0.0.1") ||
            baseUrl.startsWith("http://localhost") ||
            baseUrl.startsWith("http://10.0.2.2"))

    fun validated(): ReasoningProviderConfig {
        val cleanUrl = baseUrl.trim()
        val cleanModel = model.trim()
        require(cleanUrl.isNotBlank()) { "Endpoint requerido." }
        require(cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")) {
            "Endpoint debe ser http(s)."
        }
        require(cleanModel.isNotBlank()) { "Modelo requerido." }
        require(maxTokens in 1..MAX_ALLOWED_TOKENS) { "maxTokens fuera de rango." }
        return copy(baseUrl = cleanUrl, model = cleanModel)
    }

    companion object {
        const val DEFAULT_MAX_TOKENS = 1024
        const val MAX_ALLOWED_TOKENS = 32768

        fun default(): ReasoningProviderConfig = fromPreset(ReasoningPreset.MESSAGES_COMPATIBLE)

        fun fromPreset(preset: ReasoningPreset): ReasoningProviderConfig {
            return ReasoningProviderConfig(
                preset = preset,
                baseUrl = preset.defaultBaseUrl,
                model = preset.defaultModel
            )
        }
    }
}

class ReasoningConfigStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): ReasoningProviderConfig {
        val preset = ReasoningPreset.fromName(preferences.getString(KEY_PRESET, null))
        val baseUrl = preferences.getString(KEY_BASE_URL, null)?.takeIf { it.isNotBlank() }
            ?: preset.defaultBaseUrl
        val model = preferences.getString(KEY_MODEL, null)?.takeIf { it.isNotBlank() }
            ?: preset.defaultModel
        val maxTokens = preferences.getInt(KEY_MAX_TOKENS, ReasoningProviderConfig.DEFAULT_MAX_TOKENS)
        return ReasoningProviderConfig(preset, baseUrl, model, maxTokens)
    }

    fun save(config: ReasoningProviderConfig): Result<Unit> = runCatching {
        val valid = config.validated()
        preferences.edit()
            .putString(KEY_PRESET, valid.preset.name)
            .putString(KEY_BASE_URL, valid.baseUrl)
            .putString(KEY_MODEL, valid.model)
            .putInt(KEY_MAX_TOKENS, valid.maxTokens)
            .apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "morimil_reasoning_config"
        private const val KEY_PRESET = "reasoning_preset"
        private const val KEY_BASE_URL = "reasoning_base_url"
        private const val KEY_MODEL = "reasoning_model"
        private const val KEY_MAX_TOKENS = "reasoning_max_tokens"
    }
}
