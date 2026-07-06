package com.morimil.app.reasoning

import com.morimil.app.ai.ReasoningProviderConfig

/**
 * Local operating mode for one reasoning turn.
 *
 * The mode is owned by Morimil, not by the model provider. A remote model can
 * improve the answer, but it must never be required for basic continuity.
 */
enum class ReasoningMode {
    ONLINE_SUPERIOR,
    LOCAL_OPERATIVE,
    SAFE_DEGRADED
}

object ReasoningModeResolver {
    fun resolve(config: ReasoningProviderConfig, runtimeKey: String): ReasoningMode {
        val endpoint = config.baseUrl.trim().lowercase()
        if (endpoint.isBlank()) return ReasoningMode.SAFE_DEGRADED
        if (config.requiresRuntimeKey && runtimeKey.isBlank()) return ReasoningMode.SAFE_DEGRADED
        return if (isLocalEndpoint(endpoint)) {
            ReasoningMode.LOCAL_OPERATIVE
        } else {
            ReasoningMode.ONLINE_SUPERIOR
        }
    }

    private fun isLocalEndpoint(endpoint: String): Boolean {
        return endpoint.startsWith("http://127.0.0.1") ||
            endpoint.startsWith("http://localhost") ||
            endpoint.startsWith("http://10.0.2.2")
    }
}
