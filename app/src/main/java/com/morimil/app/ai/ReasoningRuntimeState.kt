package com.morimil.app.ai

object ReasoningRuntimeState {
    @Volatile
    private var activeConfig: ReasoningProviderConfig = ReasoningProviderConfig.default()

    fun get(): ReasoningProviderConfig = activeConfig

    fun set(config: ReasoningProviderConfig) {
        activeConfig = config
    }
}
