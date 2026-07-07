package com.morimil.app.reasoning.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide observable status for the last backend selected by the kernel.
 *
 * This is intentionally separate from UI so Motor/Chat/Health screens can
 * observe the same truth without duplicating routing logic.
 */
object ReasoningBackendStatusStore {
    private val _lastDecision = MutableStateFlow<ModelBackendDecision?>(null)
    val lastDecision: StateFlow<ModelBackendDecision?> = _lastDecision.asStateFlow()

    fun update(decision: ModelBackendDecision) {
        _lastDecision.value = decision
    }

    fun clear() {
        _lastDecision.value = null
    }
}
