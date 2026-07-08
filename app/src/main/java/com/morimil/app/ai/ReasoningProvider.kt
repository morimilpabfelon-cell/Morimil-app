package com.morimil.app.ai

import android.content.Context

private const val DEFAULT_LOCAL_MODEL = "llama3.2"

private fun localUsbChatUrl(): String {
    return "http://" + "127.0.0.1" + ":11434" + "/v1/" + "chat/" + "completions"
}

private fun localEmulatorChatUrl(): String {
    return "http://" + "10.0.2.2" + ":11434" + "/v1/" + "chat/" + "completions"
}

enum class ReasoningWireFormat {
    MESSAGES,
    CHAT,
    RESPONSES
}

enum class ReasoningPreset(
    val displayName: String,
    val wireFormat: ReasoningWireFormat,
    val defaultBaseUrl: String,
    val defaultModel: String
) {
    LOCAL_USB_HELPER("Ollama USB helper", ReasoningWireFormat.CHAT, localUsbChatUrl(), DEFAULT_LOCAL_MODEL),
    LOCAL_EMULATOR_HELPER("Ollama emulator helper", ReasoningWireFormat.CHAT, localEmulatorChatUrl(), DEFAULT_LOCAL_MODEL),
    LOCAL_COMPATIBLE("Local helper legacy", ReasoningWireFormat.CHAT, localEmulatorChatUrl(), DEFAULT_LOCAL_MODEL),
    MESSAGES_COMPATIBLE("Remote Messages helper", ReasoningWireFormat.MESSAGES, "", ""),
    CHAT_COMPATIBLE("Remote Chat helper", ReasoningWireFormat.CHAT, "", ""),
    RESPONSES_COMPATIBLE("Remote Responses helper", ReasoningWireFormat.RESPONSES, "", ""),
    CUSTOM("Custom helper", ReasoningWireFormat.CHAT, "", "");

    companion object {
        fun fromName(name: String?): ReasoningPreset {
            return entries.firstOrNull { it.name == name } ?: LOCAL_USB_HELPER
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
        get() {
            val cleanUrl = baseUrl.trim().lowercase()
            return when {
                cleanUrl.endsWith("/responses") || cleanUrl.contains("/responses?") -> ReasoningWireFormat.RESPONSES
                cleanUrl.contains("/chat/completions") -> ReasoningWireFormat.CHAT
                cleanUrl.endsWith("/messages") || cleanUrl.contains("/messages?") -> ReasoningWireFormat.MESSAGES
                else -> preset.wireFormat
            }
        }

    val requiresRuntimeKey: Boolean
        get() = baseUrl.isNotBlank() && !ReasoningEndpointPolicy.isLocalTrustedEndpoint(baseUrl)

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
        const val DEFAULT_MAX_TOKENS = 2048
        const val MAX_ALLOWED_TOKENS = 32768

        fun default(): ReasoningProviderConfig = fromPreset(ReasoningPreset.LOCAL_USB_HELPER)

        fun fromPreset(preset: ReasoningPreset): ReasoningProviderConfig {
            return ReasoningProviderConfig(
                preset = preset,
                baseUrl = preset.defaultBaseUrl,
                model = preset.defaultModel
            )
        }
    }
}

data class ReasoningMotorSlot(val config: ReasoningProviderConfig) {
    val id: Int = SINGLE_HELPER_ID
    val displayName: String = SINGLE_HELPER_LABEL

    companion object {
        const val SINGLE_HELPER_ID = 1
        const val SINGLE_HELPER_LABEL = "Motor auxiliar configurado"
    }
}

class ReasoningConfigStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun loadActiveSlot(): ReasoningMotorSlot = ReasoningMotorSlot(load())

    fun load(): ReasoningProviderConfig {
        val preset = ReasoningPreset.fromName(preferences.getString(SETTING_PRESET, null))
        val baseUrl = preferences.getString(SETTING_BASE_URL, null)?.takeIf { it.isNotBlank() }
            ?: preset.defaultBaseUrl
        val model = preferences.getString(SETTING_MODEL, null)?.takeIf { it.isNotBlank() }
            ?: preset.defaultModel
        val maxTokens = preferences.getInt(SETTING_MAX_TOKENS, ReasoningProviderConfig.DEFAULT_MAX_TOKENS)
        return ReasoningProviderConfig(preset, baseUrl, model, maxTokens)
    }

    fun save(config: ReasoningProviderConfig): Result<Unit> = runCatching {
        val valid = config.validated()
        preferences.edit()
            .putString(SETTING_PRESET, valid.preset.name)
            .putString(SETTING_BASE_URL, valid.baseUrl)
            .putString(SETTING_MODEL, valid.model)
            .putInt(SETTING_MAX_TOKENS, valid.maxTokens)
            .apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "morimil_reasoning_config"
        private const val SETTING_PRESET = "reasoning_preset"
        private const val SETTING_BASE_URL = "reasoning_base_url"
        private const val SETTING_MODEL = "reasoning_model"
        private const val SETTING_MAX_TOKENS = "reasoning_max_tokens"
    }
}
