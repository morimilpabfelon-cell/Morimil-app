package com.morimil.app.reasoning.model

import com.morimil.app.ai.ReasoningProviderConfig
import com.morimil.app.reasoning.ReasoningIntent
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
    val reason: String,
    val taskComplexity: ReasoningTaskComplexity = ReasoningTaskComplexity.UNKNOWN,
    val routingHint: String = "prefer_configured_backend"
)

object ModelBackendRouter {
    fun select(
        runtimeLabel: String,
        config: ReasoningProviderConfig,
        runtimeAccess: String,
        input: String = "",
        intent: ReasoningIntent = ReasoningIntent.UNKNOWN
    ): ModelBackendDecision {
        val endpoint = config.baseUrl.trim()
        val model = config.model.trim()
        val mode = ReasoningModeResolver.resolve(config, runtimeAccess)
        val complexity = ReasoningTaskComplexityClassifier.classify(input, intent)
        val hint = ReasoningTaskComplexityClassifier.routingHint(complexity)
        val routeSignal = "${complexity.name}:$hint"
        if (endpoint.isBlank()) {
            return fallback("missing_endpoint", runtimeLabel, endpoint, model, complexity, hint)
        }
        if (model.isBlank()) {
            return fallback("missing_model", runtimeLabel, endpoint, model, complexity, hint)
        }
        if (config.requiresRuntimeKey && runtimeAccess.isBlank()) {
            return fallback("missing_remote_runtime_access", runtimeLabel, endpoint, model, complexity, hint)
        }
        val kind = when (mode) {
            ReasoningMode.LOCAL_HELPER_MODEL -> ModelBackendKind.LOCAL_HTTP
            ReasoningMode.REMOTE_API_HELPER -> ModelBackendKind.REMOTE_API
            ReasoningMode.MORIMIL_CORE_FALLBACK -> ModelBackendKind.DETERMINISTIC_FALLBACK
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
                ModelBackendKind.LOCAL_HTTP -> "local_helper_model_selected:$routeSignal"
                ModelBackendKind.REMOTE_API -> "remote_api_helper_selected:$routeSignal"
                ModelBackendKind.DETERMINISTIC_FALLBACK -> "morimil_core_fallback_selected:$routeSignal"
            },
            taskComplexity = complexity,
            routingHint = hint
        )
    }

    private fun fallback(
        reason: String,
        runtimeLabel: String,
        endpoint: String,
        model: String,
        complexity: ReasoningTaskComplexity,
        routingHint: String
    ): ModelBackendDecision {
        return ModelBackendDecision(
            kind = ModelBackendKind.DETERMINISTIC_FALLBACK,
            mode = ReasoningMode.MORIMIL_CORE_FALLBACK,
            label = runtimeLabel.ifBlank { "morimil_core_fallback" },
            endpoint = endpoint,
            model = model,
            usable = false,
            reason = "$reason:${complexity.name}:$routingHint",
            taskComplexity = complexity,
            routingHint = routingHint
        )
    }
}
