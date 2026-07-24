package com.morimil.app.reasoning.model

import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ReasoningEscalationDecision {
    PENDING,
    APPROVED,
    LOCAL_ONLY
}

enum class ReasoningEscalationGateResult {
    NOT_REQUIRED,
    PENDING,
    APPROVED_ONCE,
    LOCAL_ONLY_ONCE
}

data class ReasoningEscalationRequest(
    val requestId: String,
    val backendKind: ModelBackendKind,
    val backendLabel: String,
    val endpoint: String,
    val model: String,
    val taskComplexity: ReasoningTaskComplexity,
    val routingHint: String,
    val reason: String,
    val inputPreview: String,
    val taskDigest: String,
    val backendScopeDigest: String,
    val decision: ReasoningEscalationDecision = ReasoningEscalationDecision.PENDING,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val decidedAtMillis: Long? = null
)

/**
 * In-memory, one-shot authorization boundary for every non-intrinsic provider.
 *
 * Local HTTP helpers and remote APIs are both auxiliary compute. An approval is
 * bound to the exact task, backend kind, endpoint and model, and is consumed
 * immediately before the external call. Nothing is persisted across process
 * death and no standing grant exists in this version.
 */
object ReasoningEscalationStore {
    const val VERSION = "morimil.external-authorization.v1"

    private val lock = Any()
    private val _pendingRequest = MutableStateFlow<ReasoningEscalationRequest?>(null)
    val pendingRequest: StateFlow<ReasoningEscalationRequest?> = _pendingRequest.asStateFlow()

    private var currentTask: String? = null

    fun consumeOrRequest(
        decision: ModelBackendDecision,
        input: String
    ): ReasoningEscalationGateResult = synchronized(lock) {
        if (!requiresOwnerDecision(decision)) {
            return@synchronized ReasoningEscalationGateResult.NOT_REQUIRED
        }

        val cleanInput = input.trim()
        require(cleanInput.isNotBlank()) { "external_authorization_task_blank" }
        val taskDigest = sha256(cleanInput)
        val scopeDigest = backendScopeDigest(decision)
        val current = _pendingRequest.value

        if (current != null &&
            current.taskDigest == taskDigest &&
            current.backendScopeDigest == scopeDigest
        ) {
            return@synchronized when (current.decision) {
                ReasoningEscalationDecision.PENDING -> ReasoningEscalationGateResult.PENDING
                ReasoningEscalationDecision.APPROVED -> {
                    clearLocked()
                    ReasoningEscalationGateResult.APPROVED_ONCE
                }
                ReasoningEscalationDecision.LOCAL_ONLY -> {
                    clearLocked()
                    ReasoningEscalationGateResult.LOCAL_ONLY_ONCE
                }
            }
        }

        val createdAt = System.currentTimeMillis()
        currentTask = cleanInput
        _pendingRequest.value = ReasoningEscalationRequest(
            requestId = sha256("$scopeDigest\n$taskDigest\n$createdAt").take(24),
            backendKind = decision.kind,
            backendLabel = decision.label,
            endpoint = decision.endpoint,
            model = decision.model,
            taskComplexity = decision.taskComplexity,
            routingHint = decision.routingHint,
            reason = decision.reason,
            inputPreview = cleanInput.replace(Regex("\\s+"), " ").take(240),
            taskDigest = taskDigest,
            backendScopeDigest = scopeDigest,
            createdAtMillis = createdAt
        )
        ReasoningEscalationGateResult.PENDING
    }

    fun approveCurrent() = synchronized(lock) {
        val current = _pendingRequest.value ?: return@synchronized
        if (current.decision == ReasoningEscalationDecision.PENDING) {
            _pendingRequest.value = current.copy(
                decision = ReasoningEscalationDecision.APPROVED,
                decidedAtMillis = System.currentTimeMillis()
            )
        }
    }

    fun keepLocal() = synchronized(lock) {
        val current = _pendingRequest.value ?: return@synchronized
        if (current.decision == ReasoningEscalationDecision.PENDING) {
            _pendingRequest.value = current.copy(
                decision = ReasoningEscalationDecision.LOCAL_ONLY,
                decidedAtMillis = System.currentTimeMillis()
            )
        }
    }

    fun taskForRequest(requestId: String): String? = synchronized(lock) {
        if (_pendingRequest.value?.requestId == requestId) currentTask else null
    }

    fun discardIfTaskChanged(input: String) = synchronized(lock) {
        val current = _pendingRequest.value ?: return@synchronized
        if (current.taskDigest != sha256(input.trim())) {
            clearLocked()
        }
    }

    fun clearResolvedFor(input: String) = synchronized(lock) {
        val current = _pendingRequest.value ?: return@synchronized
        if (current.decision != ReasoningEscalationDecision.PENDING &&
            current.taskDigest == sha256(input.trim())
        ) {
            clearLocked()
        }
    }

    fun clear() = synchronized(lock) {
        clearLocked()
    }

    fun requiresOwnerDecision(decision: ModelBackendDecision): Boolean {
        return decision.usable && decision.kind != ModelBackendKind.DETERMINISTIC_FALLBACK
    }

    private fun backendScopeDigest(decision: ModelBackendDecision): String {
        return sha256(
            listOf(
                decision.kind.name,
                decision.endpoint.trim(),
                decision.model.trim()
            ).joinToString("\n")
        )
    }

    private fun clearLocked() {
        currentTask = null
        _pendingRequest.value = null
    }

    private fun sha256(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}
