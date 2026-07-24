package com.morimil.app.ai

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ReasoningProfileRuntimeStore {
    private val emptyRemoteHelperConfig = ReasoningProviderConfig(
        preset = ReasoningPreset.CUSTOM,
        baseUrl = "",
        model = ""
    )

    private val _remoteHelperConfig = MutableStateFlow(emptyRemoteHelperConfig)

    val remoteHelperConfig: StateFlow<ReasoningProviderConfig> =
        _remoteHelperConfig.asStateFlow()

    fun loadRemoteHelper(context: Context? = null): ReasoningProviderConfig {
        context?.let { hydrateRemoteHelper(it) }
        return _remoteHelperConfig.value
    }

    fun saveRemoteHelper(config: ReasoningProviderConfig): Result<Unit> = runCatching {
        _remoteHelperConfig.value = config.validated()
    }

    fun saveRemoteHelper(
        context: Context,
        config: ReasoningProviderConfig
    ): Result<Unit> = runCatching {
        val valid = config.validated()
        val committed = context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(SETTING_BASE_URL, valid.baseUrl)
            .putString(SETTING_MODEL, valid.model)
            .putInt(SETTING_MAX_TOKENS, valid.maxTokens)
            .remove(LEGACY_SETTING_ALLOW_PRIVATE_CONTEXT_TO_REMOTE)
            .commit()
        check(committed) { "No se pudo guardar el auxiliar remoto temporal." }
        _remoteHelperConfig.value = valid
    }

    private fun hydrateRemoteHelper(context: Context) {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        if (prefs.contains(LEGACY_SETTING_ALLOW_PRIVATE_CONTEXT_TO_REMOTE)) {
            check(
                prefs.edit()
                    .remove(LEGACY_SETTING_ALLOW_PRIVATE_CONTEXT_TO_REMOTE)
                    .commit()
            ) { "No se pudo eliminar la autorizacion heredada de contexto privado." }
        }
        val baseUrl = prefs.getString(SETTING_BASE_URL, null).orEmpty()
        val model = prefs.getString(SETTING_MODEL, null).orEmpty()
        if (baseUrl.isBlank() || model.isBlank()) {
            _remoteHelperConfig.value = emptyRemoteHelperConfig
            return
        }
        val maxTokens = prefs.getInt(
            SETTING_MAX_TOKENS,
            ReasoningProviderConfig.DEFAULT_MAX_TOKENS
        )
        _remoteHelperConfig.value = ReasoningProviderConfig(
            preset = ReasoningPreset.CUSTOM,
            baseUrl = baseUrl,
            model = model,
            maxTokens = maxTokens
        ).validated()
    }

    private const val PREFERENCES_NAME = "morimil_remote_api_reasoning_profile"
    private const val SETTING_BASE_URL = "base_url"
    private const val SETTING_MODEL = "model"
    private const val SETTING_MAX_TOKENS = "max_tokens"
    private const val LEGACY_SETTING_ALLOW_PRIVATE_CONTEXT_TO_REMOTE =
        "allow_private_context_to_remote"
}
