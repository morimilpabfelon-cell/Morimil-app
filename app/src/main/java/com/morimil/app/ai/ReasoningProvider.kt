package com.morimil.app.ai

import android.content.Context

private fun localChatUrl(): String {
    return "http://" + "127.0.0.1" + ":11434" + "/v1/" + "chat/" + "completions"
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
    MESSAGES_COMPATIBLE("Messages-compatible", ReasoningWireFormat.MESSAGES, "", ""),
    CHAT_COMPATIBLE("Chat-compatible", ReasoningWireFormat.CHAT, "", ""),
    RESPONSES_COMPATIBLE("Responses-compatible", ReasoningWireFormat.RESPONSES, "", ""),
    LOCAL_COMPATIBLE("Local-compatible", ReasoningWireFormat.CHAT, localChatUrl(), ""),
    CUSTOM("Custom-compatible", ReasoningWireFormat.CHAT, "", "");

    companion object {
        fun fromName(name: String?): ReasoningPreset {
            return entries.firstOrNull { it.name == name } ?: CUSTOM
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
        get() = baseUrl.isNotBlank() && !(baseUrl.startsWith("http://127.0.0.1") ||
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
        const val DEFAULT_MAX_TOKENS = 2048
        const val MAX_ALLOWED_TOKENS = 32768

        fun default(): ReasoningProviderConfig = fromPreset(ReasoningPreset.CUSTOM)

        fun fromPreset(preset: ReasoningPreset): ReasoningProviderConfig {
            return ReasoningProviderConfig(
                preset = preset,
                baseUrl = preset.defaultBaseUrl,
                model = preset.defaultModel
            )
        }
    }
}

/**
 * Compatibility wrapper kept so existing UI code can still refer to a slot.
 * The runtime now has exactly one reasoning API: slot 1.
 */
data class ReasoningMotorSlot(
    val id: Int,
    val label: String,
    val config: ReasoningProviderConfig,
    val enabled: Boolean = true
) {
    val displayName: String
        get() = label.ifBlank { SINGLE_API_LABEL }

    companion object {
        const val SINGLE_API_ID = 1
        const val SINGLE_API_LABEL = "API principal"
    }
}

class ReasoningConfigStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun loadActiveSlotId(): Int = ReasoningMotorSlot.SINGLE_API_ID

    fun setActiveSlot(slotId: Int) {
        preferences.edit()
            .putInt(KEY_ACTIVE_SLOT, ReasoningMotorSlot.SINGLE_API_ID)
            .removeRetiredSlotKeys()
            .apply()
    }

    fun loadSlots(): List<ReasoningMotorSlot> {
        return listOf(loadActiveSlot())
    }

    fun loadActiveSlot(): ReasoningMotorSlot {
        return loadSingleSlot()
    }

    fun loadSlot(slotId: Int): ReasoningMotorSlot {
        return loadSingleSlot()
    }

    fun saveSlot(slot: ReasoningMotorSlot): Result<Unit> = save(slot.config, slot.displayName)

    fun load(): ReasoningProviderConfig {
        return loadActiveSlot().config
    }

    fun save(config: ReasoningProviderConfig): Result<Unit> = save(config, ReasoningMotorSlot.SINGLE_API_LABEL)

    private fun loadSingleSlot(): ReasoningMotorSlot {
        val prefix = slotPrefix(ReasoningMotorSlot.SINGLE_API_ID)
        val preset = ReasoningPreset.fromName(
            preferences.getString(prefix + KEY_PRESET, null)
                ?: preferences.getString(KEY_PRESET, null)
        )
        val baseUrl = preferences.getString(prefix + KEY_BASE_URL, null)?.takeIf { it.isNotBlank() }
            ?: preferences.getString(KEY_BASE_URL, null)?.takeIf { it.isNotBlank() }
            ?: preset.defaultBaseUrl
        val model = preferences.getString(prefix + KEY_MODEL, null)?.takeIf { it.isNotBlank() }
            ?: preferences.getString(KEY_MODEL, null)?.takeIf { it.isNotBlank() }
            ?: preset.defaultModel
        val maxTokens = if (preferences.contains(prefix + KEY_MAX_TOKENS)) {
            preferences.getInt(prefix + KEY_MAX_TOKENS, ReasoningProviderConfig.DEFAULT_MAX_TOKENS)
        } else {
            preferences.getInt(KEY_MAX_TOKENS, ReasoningProviderConfig.DEFAULT_MAX_TOKENS)
        }
        val label = preferences.getString(prefix + KEY_LABEL, null)?.takeIf { it.isNotBlank() }
            ?: ReasoningMotorSlot.SINGLE_API_LABEL

        return ReasoningMotorSlot(
            id = ReasoningMotorSlot.SINGLE_API_ID,
            label = label,
            config = ReasoningProviderConfig(preset, baseUrl, model, maxTokens),
            enabled = true
        )
    }

    private fun save(config: ReasoningProviderConfig, label: String): Result<Unit> = runCatching {
        val valid = config.validated()
        val prefix = slotPrefix(ReasoningMotorSlot.SINGLE_API_ID)
        preferences.edit()
            .putInt(KEY_ACTIVE_SLOT, ReasoningMotorSlot.SINGLE_API_ID)
            .putString(prefix + KEY_LABEL, label.ifBlank { ReasoningMotorSlot.SINGLE_API_LABEL })
            .putString(prefix + KEY_PRESET, valid.preset.name)
            .putString(prefix + KEY_BASE_URL, valid.baseUrl)
            .putString(prefix + KEY_MODEL, valid.model)
            .putInt(prefix + KEY_MAX_TOKENS, valid.maxTokens)
            .putBoolean(prefix + KEY_ENABLED, true)
            .putString(KEY_PRESET, valid.preset.name)
            .putString(KEY_BASE_URL, valid.baseUrl)
            .putString(KEY_MODEL, valid.model)
            .putInt(KEY_MAX_TOKENS, valid.maxTokens)
            .removeRetiredSlotKeys()
            .apply()
    }

    private fun android.content.SharedPreferences.Editor.removeRetiredSlotKeys(): android.content.SharedPreferences.Editor {
        for (slotId in 2..RETIRED_MAX_PROVIDER_SLOTS) {
            val prefix = slotPrefix(slotId)
            remove(prefix + KEY_LABEL)
            remove(prefix + KEY_PRESET)
            remove(prefix + KEY_BASE_URL)
            remove(prefix + KEY_MODEL)
            remove(prefix + KEY_MAX_TOKENS)
            remove(prefix + KEY_ENABLED)
        }
        return this
    }

    companion object {
        const val MAX_PROVIDER_SLOTS = 1
        private const val RETIRED_MAX_PROVIDER_SLOTS = 10

        private const val PREFERENCES_NAME = "morimil_reasoning_config"
        private const val KEY_ACTIVE_SLOT = "reasoning_active_slot"
        private const val KEY_LABEL = "reasoning_label"
        private const val KEY_ENABLED = "reasoning_enabled"
        private const val KEY_PRESET = "reasoning_preset"
        private const val KEY_BASE_URL = "reasoning_base_url"
        private const val KEY_MODEL = "reasoning_model"
        private const val KEY_MAX_TOKENS = "reasoning_max_tokens"

        private fun slotPrefix(slotId: Int): String = "reasoning_slot_${slotId.coerceAtLeast(1)}_"
    }
}
