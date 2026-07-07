package com.morimil.app.reasoning.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ReasoningEscalationDecision {
    PENDING,
    APPROVED,
    LOCAL_ONLY
}

data class ReasoningEscalationRequest(
    val taskComplexity: ReasoningTaskComplexity,
    val routingHint: String,
    val reason: String,
    val inputPreview: String,
    val decision: ReasoningEscalationDecision = ReasoningEscalationDecision.PENDING,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val decidedAtMillis: Long? = null
)

object ReasoningEscalationStore {
    private val _pendingRequest = MutableStateFlow<ReasoningEscalationRequest?>(null)
    val pendingRequest: StateFlow<ReasoningEscalationRequest?> = _pendingRequest.asStateFlow()

    fun publishIfNeeded(decision: ModelBackendDecision, input: String): ReasoningEscalationRequest? {
        if (!requiresOwnerDecision(decision)) {
            clear()
            return null
        }
        val preview = input.trim().replace(Regex("\\s+"), " ").take(MAX_INPUT_PREVIEW_CHARS)
        val existing = _pendingRequest.value
        val next = if (existing != null && existing.inputPreview == preview && existing.taskComplexity == decision.taskComplexity) {
            existing.copy(
                routingHint = decision.routingHint,
                reason = decision.reason
            )
        } else {
            ReasoningEscalationRequest(
                taskComplexity = decision.taskComplexity,
                routingHint = decision.routingHint,
                reason = decision.reason,
                inputPreview = preview
            )
        }
        _pendingRequest.value = next
        return next
    }

    fun approveCurrent() {
        _pendingRequest.value = _pendingRequest.value?.copy(
            decision = ReasoningEscalationDecision.APPROVED,
            decidedAtMillis = System.currentTimeMillis()
        )
    }

    fun keepLocal() {
        _pendingRequest.value = _pendingRequest.value?.copy(
            decision = ReasoningEscalationDecision.LOCAL_ONLY,
            decidedAtMillis = System.currentTimeMillis()
        )
    }

    fun clear() {
        _pendingRequest.value = null
    }

    fun requiresOwnerDecision(decision: ModelBackendDecision): Boolean {
        return decision.routingHint.contains("stronger") ||
            decision.routingHint.contains("approval") ||
            decision.taskComplexity == ReasoningTaskComplexity.CODE_REVIEW ||
            decision.taskComplexity == ReasoningTaskComplexity.ARCHITECTURE_CRITICAL ||
            decision.taskComplexity == ReasoningTaskComplexity.DEEP_ANALYSIS ||
            decision.taskComplexity == ReasoningTaskComplexity.TOOL_OR_AGENT
    }

    private const val MAX_INPUT_PREVIEW_CHARS = 240
}
