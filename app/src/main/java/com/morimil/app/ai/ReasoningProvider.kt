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
        const val DEFAULT_MAX_TOKENS = 4096
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

data class ReasoningMotorSlot(
    val id: Int,
    val label: String,
    val config: ReasoningProviderConfig,
    val enabled: Boolean = false
) {
    val displayName: String
        get() = label.ifBlank { "Motor $id" }
}

class ReasoningConfigStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun loadActiveSlotId(): Int {
        return preferences.getInt(KEY_ACTIVE_SLOT, 1).coerceIn(1, MAX_PROVIDER_SLOTS)
    }

    fun setActiveSlot(slotId: Int) {
        preferences.edit()
            .putInt(KEY_ACTIVE_SLOT, slotId.coerceIn(1, MAX_PROVIDER_SLOTS))
            .apply()
    }

    fun loadSlots(): List<ReasoningMotorSlot> {
        return (1..MAX_PROVIDER_SLOTS).map(::loadSlot)
    }

    fun loadActiveSlot(): ReasoningMotorSlot {
        return loadSlot(loadActiveSlotId())
    }

    fun loadSlot(slotId: Int): ReasoningMotorSlot {
        val id = slotId.coerceIn(1, MAX_PROVIDER_SLOTS)
        val prefix = slotPrefix(id)
        val hasSlotConfig = preferences.contains(prefix + KEY_BASE_URL) ||
            preferences.contains(prefix + KEY_MODEL) ||
            preferences.contains(prefix + KEY_PRESET)

        val preset = ReasoningPreset.fromName(
            preferences.getString(prefix + KEY_PRESET, null)
                ?: if (id == 1) preferences.getString(KEY_PRESET, null) else null
        )
        val legacyBaseUrl = if (id == 1) {
            preferences.getString(KEY_BASE_URL, null)?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        val baseUrl = preferences.getString(prefix + KEY_BASE_URL, null)?.takeIf { it.isNotBlank() }
            ?: legacyBaseUrl
            ?: preset.defaultBaseUrl
        val legacyModel = if (id == 1) {
            preferences.getString(KEY_MODEL, null)?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        val model = preferences.getString(prefix + KEY_MODEL, null)?.takeIf { it.isNotBlank() }
            ?: legacyModel
            ?: preset.defaultModel
        val maxTokens = if (preferences.contains(prefix + KEY_MAX_TOKENS)) {
            preferences.getInt(prefix + KEY_MAX_TOKENS, ReasoningProviderConfig.DEFAULT_MAX_TOKENS)
        } else if (id == 1) {
            preferences.getInt(KEY_MAX_TOKENS, ReasoningProviderConfig.DEFAULT_MAX_TOKENS)
        } else {
            ReasoningProviderConfig.DEFAULT_MAX_TOKENS
        }
        val label = preferences.getString(prefix + KEY_LABEL, null)?.takeIf { it.isNotBlank() }
            ?: "Motor $id"
        val enabled = preferences.getBoolean(prefix + KEY_ENABLED, hasSlotConfig || id == 1)

        return ReasoningMotorSlot(
            id = id,
            label = label,
            config = ReasoningProviderConfig(preset, baseUrl, model, maxTokens),
            enabled = enabled
        )
    }

    fun saveSlot(slot: ReasoningMotorSlot): Result<Unit> = runCatching {
        val id = slot.id.coerceIn(1, MAX_PROVIDER_SLOTS)
        val valid = slot.config.validated()
        val prefix = slotPrefix(id)
        preferences.edit()
            .putString(prefix + KEY_LABEL, slot.displayName)
            .putString(prefix + KEY_PRESET, valid.preset.name)
            .putString(prefix + KEY_BASE_URL, valid.baseUrl)
            .putString(prefix + KEY_MODEL, valid.model)
            .putInt(prefix + KEY_MAX_TOKENS, valid.maxTokens)
            .putBoolean(prefix + KEY_ENABLED, true)
            .apply()
    }

    fun load(): ReasoningProviderConfig {
        return loadActiveSlot().config
    }

    fun save(config: ReasoningProviderConfig): Result<Unit> = runCatching {
        val valid = config.validated()
        val activeSlotId = loadActiveSlotId()
        val activeSlot = loadSlot(activeSlotId).copy(config = valid, enabled = true)
        saveSlot(activeSlot).getOrThrow()

        // Keep the legacy single-config keys populated so older installs can
        // read the same active motor during gradual migration.
        preferences.edit()
            .putString(KEY_PRESET, valid.preset.name)
            .putString(KEY_BASE_URL, valid.baseUrl)
            .putString(KEY_MODEL, valid.model)
            .putInt(KEY_MAX_TOKENS, valid.maxTokens)
            .apply()
    }

    companion object {
        const val MAX_PROVIDER_SLOTS = 10

        private const val PREFERENCES_NAME = "morimil_reasoning_config"
        private const val KEY_ACTIVE_SLOT = "reasoning_active_slot"
        private const val KEY_LABEL = "reasoning_label"
        private const val KEY_ENABLED = "reasoning_enabled"
        private const val KEY_PRESET = "reasoning_preset"
        private const val KEY_BASE_URL = "reasoning_base_url"
        private const val KEY_MODEL = "reasoning_model"
        private const val KEY_MAX_TOKENS = "reasoning_max_tokens"

        private fun slotPrefix(slotId: Int): String = "reasoning_slot_${slotId.coerceIn(1, MAX_PROVIDER_SLOTS)}_"
    }
}
