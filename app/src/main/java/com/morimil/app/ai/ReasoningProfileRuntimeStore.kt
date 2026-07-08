package com.morimil.app.ai

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ReasoningProfileRuntimeStore {
    private val emptyThirdMotor = ReasoningProviderConfig(
        preset = ReasoningPreset.CUSTOM,
        baseUrl = "",
        model = ""
    )

    private val _superiorConfig = MutableStateFlow(emptyThirdMotor)
    val superiorConfig: StateFlow<ReasoningProviderConfig> = _superiorConfig.asStateFlow()

    fun loadSuperior(context: Context? = null): ReasoningProviderConfig {
        context?.let { hydrateSuperior(it) }
        return _superiorConfig.value
    }

    fun saveSuperior(config: ReasoningProviderConfig): Result<Unit> = runCatching {
        val valid = config.validatedAsThirdMotor()
        _superiorConfig.value = valid
    }

    fun saveSuperior(context: Context, config: ReasoningProviderConfig): Result<Unit> = runCatching {
        val valid = config.validatedAsThirdMotor()
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(SETTING_BASE_URL, valid.baseUrl)
            .putString(SETTING_MODEL, valid.model)
            .putInt(SETTING_MAX_TOKENS, valid.maxTokens)
            .apply()
        _superiorConfig.value = valid
    }

    private fun hydrateSuperior(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val baseUrl = prefs.getString(SETTING_BASE_URL, null).orEmpty()
        val model = prefs.getString(SETTING_MODEL, null).orEmpty()
        if (baseUrl.isBlank() || model.isBlank()) {
            _superiorConfig.value = emptyThirdMotor
            return
        }
        if (ReasoningEndpointPolicy.isLocalTrustedEndpoint(baseUrl)) {
            prefs.edit().clear().apply()
            _superiorConfig.value = emptyThirdMotor
            return
        }
        val maxTokens = prefs.getInt(SETTING_MAX_TOKENS, ReasoningProviderConfig.DEFAULT_MAX_TOKENS)
        _superiorConfig.value = ReasoningProviderConfig(
            preset = ReasoningPreset.CUSTOM,
            baseUrl = baseUrl,
            model = model,
            maxTokens = maxTokens
        )
    }

    private fun ReasoningProviderConfig.validatedAsThirdMotor(): ReasoningProviderConfig {
        val valid = validated()
        require(!ReasoningEndpointPolicy.isLocalTrustedEndpoint(valid.baseUrl)) {
            "El tercer motor no acepta endpoints locales. Usa el motor auxiliar local para USB o LAN."
        }
        return valid
    }

    private const val PREFERENCES_NAME = "morimil_third_reasoning_profile"
    private const val SETTING_BASE_URL = "base_url"
    private const val SETTING_MODEL = "model"
    private const val SETTING_MAX_TOKENS = "max_tokens"
}
