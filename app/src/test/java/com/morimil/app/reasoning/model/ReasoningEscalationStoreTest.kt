package com.morimil.app.reasoning.model

import com.morimil.app.reasoning.ReasoningMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningEscalationStoreTest {
    @After
    fun clearStore() {
        ReasoningEscalationStore.clear()
    }

    @Test
    fun exactApprovalIsConsumedOnce() {
        val backend = backend()
        val task = "Analiza esta arquitectura"

        assertEquals(
            ReasoningEscalationGateResult.PENDING,
            ReasoningEscalationStore.consumeOrRequest(backend, task)
        )
        val pending = ReasoningEscalationStore.pendingRequest.value
        assertNotNull(pending)
        assertEquals(ReasoningEscalationDecision.PENDING, pending?.decision)
        assertEquals(task, ReasoningEscalationStore.approveCurrent())
        assertEquals(ReasoningEscalationDecision.APPROVED, ReasoningEscalationStore.pendingRequest.value?.decision)

        assertEquals(
            ReasoningEscalationGateResult.APPROVED_ONCE,
            ReasoningEscalationStore.consumeOrRequest(backend, task)
        )
        assertNull(ReasoningEscalationStore.pendingRequest.value)

        assertEquals(
            ReasoningEscalationGateResult.PENDING,
            ReasoningEscalationStore.consumeOrRequest(backend, task)
        )
    }

    @Test
    fun approvalCannotMoveToAnotherBackendScope() {
        val first = backend(endpoint = "http://127.0.0.1:11434/v1/chat/completions", model = "model-a")
        val second = backend(endpoint = "https://example.test/v1/chat/completions", model = "model-b")
        val task = "Misma tarea"

        ReasoningEscalationStore.consumeOrRequest(first, task)
        val firstRequest = requireNotNull(ReasoningEscalationStore.pendingRequest.value)
        ReasoningEscalationStore.approveCurrent()

        assertEquals(
            ReasoningEscalationGateResult.PENDING,
            ReasoningEscalationStore.consumeOrRequest(second, task)
        )
        val secondRequest = requireNotNull(ReasoningEscalationStore.pendingRequest.value)
        assertNotEquals(firstRequest.requestId, secondRequest.requestId)
        assertNotEquals(firstRequest.backendScopeDigest, secondRequest.backendScopeDigest)
        assertEquals(ReasoningEscalationDecision.PENDING, secondRequest.decision)
    }

    @Test
    fun localOnlyDecisionProducesDeterministicOneShotOutcome() {
        val backend = backend()
        val task = "No enviar afuera"

        ReasoningEscalationStore.consumeOrRequest(backend, task)
        assertEquals(task, ReasoningEscalationStore.keepLocal())
        assertEquals(
            ReasoningEscalationGateResult.LOCAL_ONLY_ONCE,
            ReasoningEscalationStore.consumeOrRequest(backend, task)
        )
        assertNull(ReasoningEscalationStore.pendingRequest.value)
    }

    @Test
    fun everyUsableNonIntrinsicBackendRequiresDecision() {
        assertTrue(ReasoningEscalationStore.requiresOwnerDecision(backend(kind = ModelBackendKind.LOCAL_HTTP)))
        assertTrue(ReasoningEscalationStore.requiresOwnerDecision(backend(kind = ModelBackendKind.REMOTE_API)))
    }

    private fun backend(
        kind: ModelBackendKind = ModelBackendKind.LOCAL_HTTP,
        endpoint: String = "http://127.0.0.1:11434/v1/chat/completions",
        model: String = "llama3.2"
    ): ModelBackendDecision {
        return ModelBackendDecision(
            kind = kind,
            mode = if (kind == ModelBackendKind.REMOTE_API) {
                ReasoningMode.REMOTE_API_HELPER
            } else {
                ReasoningMode.LOCAL_HELPER_MODEL
            },
            label = "temporary auxiliary",
            endpoint = endpoint,
            model = model,
            usable = true,
            reason = "test_backend",
            taskComplexity = ReasoningTaskComplexity.UNKNOWN,
            routingHint = "test"
        )
    }
}
