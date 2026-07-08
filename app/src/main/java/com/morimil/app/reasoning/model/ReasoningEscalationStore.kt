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

    /**
     * Morimil's configured reasoning motor is part of Morimil, not an external
     * action that needs a conversation-level approval gate. This store is kept as
     * a no-op compatibility surface so old UI/state wiring cannot recreate the
     * "Autorizar / Seguir local" prompt.
     */
    fun publishIfNeeded(decision: ModelBackendDecision, input: String): ReasoningEscalationRequest? {
        clear()
        return null
    }

    fun approveCurrent() {
        clear()
    }

    fun keepLocal() {
        clear()
    }

    fun clear() {
        _pendingRequest.value = null
    }

    fun requiresOwnerDecision(decision: ModelBackendDecision): Boolean = false
}
