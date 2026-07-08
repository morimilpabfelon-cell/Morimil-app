package com.morimil.app.ai

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ReasoningProfileRuntimeStore {
    private val emptyRemoteApiConfig = ReasoningProviderConfig(
        preset = ReasoningPreset.CUSTOM,
        baseUrl = "",
        model = ""
    )

    private val _remoteApiConfig = MutableStateFlow(emptyRemoteApiConfig)

    val superiorConfig: StateFlow<ReasoningProviderConfig> = _remoteApiConfig.asStateFlow()

    fun loadSuperior(context: Context? = null): ReasoningProviderConfig = loadRemoteApi(context)

    fun saveSuperior(config: ReasoningProviderConfig): Result<Unit> = saveRemoteApi(config)

    fun saveSuperior(context: Context, config: ReasoningProviderConfig): Result<Unit> = saveRemoteApi(context, config)

    fun loadRemoteApi(context: Context? = null): ReasoningProviderConfig {
        context?.let { hydrateRemoteApi(it) }
        return _remoteApiConfig.value
    }

    fun saveRemoteApi(config: ReasoningProviderConfig): Result<Unit> = runCatching {
        _remoteApiConfig.value = config.validated()
    }

    fun saveRemoteApi(context: Context, config: ReasoningProviderConfig): Result<Unit> = runCatching {
        val valid = config.validated()
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(SETTING_BASE_URL, valid.baseUrl)
            .putString(SETTING_MODEL, valid.model)
            .putInt(SETTING_MAX_TOKENS, valid.maxTokens)
            .apply()
        _remoteApiConfig.value = valid
    }

    private fun hydrateRemoteApi(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val baseUrl = prefs.getString(SETTING_BASE_URL, null).orEmpty()
        val model = prefs.getString(SETTING_MODEL, null).orEmpty()
        if (baseUrl.isBlank() || model.isBlank()) return
        val maxTokens = prefs.getInt(SETTING_MAX_TOKENS, ReasoningProviderConfig.DEFAULT_MAX_TOKENS)
        _remoteApiConfig.value = ReasoningProviderConfig(
            preset = ReasoningPreset.CUSTOM,
            baseUrl = baseUrl,
            model = model,
            maxTokens = maxTokens
        )
    }

    private const val PREFERENCES_NAME = "morimil_remote_api_reasoning_profile"
    private const val SETTING_BASE_URL = "base_url"
    private const val SETTING_MODEL = "model"
    private const val SETTING_MAX_TOKENS = "max_tokens"
}
