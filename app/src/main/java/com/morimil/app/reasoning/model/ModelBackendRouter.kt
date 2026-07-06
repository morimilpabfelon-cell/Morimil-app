package com.morimil.app.reasoning.model

import com.morimil.app.ai.ReasoningProviderConfig
import com.morimil.app.reasoning.ReasoningMode
import com.morimil.app.reasoning.ReasoningModeResolver

enum class ModelBackendKind {
    REMOTE_API,
    LOCAL_HTTP,
    DETERMINISTIC_FALLBACK
}

data class ModelBackendDecision(
    val kind: ModelBackendKind,
    val mode: ReasoningMode,
    val label: String,
    val endpoint: String,
    val model: String,
    val usable: Boolean,
    val reason: String
)

/**
 * Real backend routing boundary for Morimil.
 *
 * Ollama-style local servers are treated as LOCAL_HTTP model backends. Remote
 * providers are REMOTE_API backends. Neither one is the reasoning kernel.
 */
object ModelBackendRouter {
    fun select(
        runtimeLabel: String,
        config: ReasoningProviderConfig,
        runtimeAccess: String
    ): ModelBackendDecision {
        val endpoint = config.baseUrl.trim()
        val model = config.model.trim()
        val mode = ReasoningModeResolver.resolve(config, runtimeAccess)
        if (endpoint.isBlank()) {
            return fallback("missing_endpoint", runtimeLabel, endpoint, model)
        }
        if (model.isBlank()) {
            return fallback("missing_model", runtimeLabel, endpoint, model)
        }
        if (config.requiresRuntimeKey && runtimeAccess.isBlank()) {
            return fallback("missing_remote_runtime_access", runtimeLabel, endpoint, model)
        }
        val kind = when (mode) {
            ReasoningMode.LOCAL_OPERATIVE -> ModelBackendKind.LOCAL_HTTP
            ReasoningMode.ONLINE_SUPERIOR -> ModelBackendKind.REMOTE_API
            ReasoningMode.SAFE_DEGRADED -> ModelBackendKind.DETERMINISTIC_FALLBACK
        }
        val usable = kind != ModelBackendKind.DETERMINISTIC_FALLBACK
        return ModelBackendDecision(
            kind = kind,
            mode = mode,
            label = runtimeLabel.ifBlank { kind.name.lowercase() },
            endpoint = endpoint,
            model = model,
            usable = usable,
            reason = when (kind) {
                ModelBackendKind.LOCAL_HTTP -> "local_http_backend_selected"
                ModelBackendKind.REMOTE_API -> "remote_api_backend_selected"
                ModelBackendKind.DETERMINISTIC_FALLBACK -> "safe_degraded_fallback_selected"
            }
        )
    }

    private fun fallback(
        reason: String,
        runtimeLabel: String,
        endpoint: String,
        model: String
    ): ModelBackendDecision {
        return ModelBackendDecision(
            kind = ModelBackendKind.DETERMINISTIC_FALLBACK,
            mode = ReasoningMode.SAFE_DEGRADED,
            label = runtimeLabel.ifBlank { "deterministic_local_fallback" },
            endpoint = endpoint,
            model = model,
            usable = false,
            reason = reason
        )
    }
}
