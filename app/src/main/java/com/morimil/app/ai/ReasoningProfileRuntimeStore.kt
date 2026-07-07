package com.morimil.app.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ReasoningProfileRuntimeStore {
    private val emptySuperior = ReasoningProviderConfig(
        preset = ReasoningPreset.CUSTOM,
        baseUrl = "",
        model = ""
    )

    private val _superiorConfig = MutableStateFlow(emptySuperior)
    val superiorConfig: StateFlow<ReasoningProviderConfig> = _superiorConfig.asStateFlow()

    fun saveSuperior(config: ReasoningProviderConfig): Result<Unit> = runCatching {
        _superiorConfig.value = config.validated()
    }

    fun loadSuperior(): ReasoningProviderConfig = _superiorConfig.value
}
