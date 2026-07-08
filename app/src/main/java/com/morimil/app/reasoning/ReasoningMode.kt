package com.morimil.app.reasoning

import com.morimil.app.ai.ReasoningEndpointPolicy
import com.morimil.app.ai.ReasoningProviderConfig

enum class ReasoningMode {
    REMOTE_API_HELPER,
    LOCAL_HELPER_MODEL,
    MORIMIL_CORE_FALLBACK
}

object ReasoningModeResolver {
    fun resolve(config: ReasoningProviderConfig, runtimeKey: String): ReasoningMode {
        val endpoint = config.baseUrl.trim()
        if (endpoint.isBlank()) return ReasoningMode.MORIMIL_CORE_FALLBACK
        if (config.requiresRuntimeKey && runtimeKey.isBlank()) return ReasoningMode.MORIMIL_CORE_FALLBACK
        return if (ReasoningEndpointPolicy.isLocalTrustedEndpoint(endpoint)) {
            ReasoningMode.LOCAL_HELPER_MODEL
        } else {
            ReasoningMode.REMOTE_API_HELPER
        }
    }
}
