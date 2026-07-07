package com.morimil.app.reasoning.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ReasoningEscalationRequest(
    val taskComplexity: ReasoningTaskComplexity,
    val routingHint: String,
    val reason: String,
    val inputPreview: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)

object ReasoningEscalationStore {
    private val _pendingRequest = MutableStateFlow<ReasoningEscalationRequest?>(null)
    val pendingRequest: StateFlow<ReasoningEscalationRequest?> = _pendingRequest.asStateFlow()

    fun publishIfNeeded(decision: ModelBackendDecision, input: String) {
        if (!requiresOwnerDecision(decision)) {
            clear()
            return
        }
        _pendingRequest.value = ReasoningEscalationRequest(
            taskComplexity = decision.taskComplexity,
            routingHint = decision.routingHint,
            reason = decision.reason,
            inputPreview = input.trim().replace(Regex("\\s+"), " ").take(MAX_INPUT_PREVIEW_CHARS)
        )
    }

    fun clear() {
        _pendingRequest.value = null
    }

    private fun requiresOwnerDecision(decision: ModelBackendDecision): Boolean {
        return decision.routingHint.contains("stronger") ||
            decision.routingHint.contains("approval") ||
            decision.taskComplexity == ReasoningTaskComplexity.CODE_REVIEW ||
            decision.taskComplexity == ReasoningTaskComplexity.ARCHITECTURE_CRITICAL ||
            decision.taskComplexity == ReasoningTaskComplexity.DEEP_ANALYSIS ||
            decision.taskComplexity == ReasoningTaskComplexity.TOOL_OR_AGENT
    }

    private const val MAX_INPUT_PREVIEW_CHARS = 240
}
