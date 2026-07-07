package com.morimil.app.reasoning

import com.morimil.app.ai.ReasoningEndpointPolicy
import com.morimil.app.ai.ReasoningProviderConfig

enum class ReasoningMode {
    ONLINE_SUPERIOR,
    LOCAL_OPERATIVE,
    SAFE_DEGRADED
}

object ReasoningModeResolver {
    fun resolve(config: ReasoningProviderConfig, runtimeKey: String): ReasoningMode {
        val endpoint = config.baseUrl.trim()
        if (endpoint.isBlank()) return ReasoningMode.SAFE_DEGRADED
        if (config.requiresRuntimeKey && runtimeKey.isBlank()) return ReasoningMode.SAFE_DEGRADED
        return if (ReasoningEndpointPolicy.isLocalTrustedEndpoint(endpoint)) {
            ReasoningMode.LOCAL_OPERATIVE
        } else {
            ReasoningMode.ONLINE_SUPERIOR
        }
    }
}
