package com.morimil.app.reasoning.trace

import com.morimil.app.domain.usecase.AppendLivingMemoryUseCase
import com.morimil.app.reasoning.ReasoningState
import com.morimil.app.reasoning.model.ModelBackendDecision
import java.security.MessageDigest

/**
 * First persistent trace store for Morimil's reasoning kernel.
 *
 * This intentionally records traces as signed living-memory system events rather
 * than adding a new Room table in this phase. That keeps the build stable and
 * preserves the existing memory hash/signature chain.
 */
class KernelTraceRepository(
    private val appendLivingMemoryUseCase: AppendLivingMemoryUseCase
) {
    suspend fun recordTurnTrace(
        state: ReasoningState,
        backend: ModelBackendDecision,
        reply: String?,
        errorMessage: String?,
        fallbackUsed: Boolean
    ): String? {
        val body = buildString {
            appendLine("kernel_trace_version=1")
            appendLine("input_sha256=${sha256(state.input)}")
            appendLine("input_chars=${state.input.length}")
            appendLine("intent=${state.intent.name}")
            appendLine("mode=${state.mode.name}")
            appendLine("backend_kind=${backend.kind.name}")
            appendLine("backend_label=${sanitize(backend.label, 120)}")
            appendLine("backend_usable=${backend.usable}")
            appendLine("model=${sanitize(backend.model, 160)}")
            appendLine("endpoint=${sanitize(backend.endpoint, 180)}")
            appendLine("router_reason=${sanitize(backend.reason, 180)}")
            appendLine("policy_decision=${sanitize(state.policyDecision, 160)}")
            appendLine("memory_context_summary=${sanitize(state.memoryContextSummary, 360)}")
            appendLine("capsule_context_summary=${sanitize(state.capsuleContextSummary, 360)}")
            appendLine("fallback_used=$fallbackUsed")
            appendLine("reply_sha256=${reply?.let(::sha256).orEmpty()}")
            appendLine("reply_chars=${reply?.length ?: 0}")
            appendLine("error=${sanitize(errorMessage.orEmpty(), 260)}")
            appendLine("trace_events=${serializeTrace(state)}")
        }.take(MAX_TRACE_BODY_CHARS)

        return appendLivingMemoryUseCase.appendSystemEvent(
            eventType = EVENT_TYPE,
            body = body,
            importance = IMPORTANCE
        )
    }

    private fun serializeTrace(state: ReasoningState): String {
        return state.trace
            .takeLast(MAX_TRACE_EVENTS)
            .joinToString(separator = " | ") { event ->
                "${sanitize(event.stage, 80)}:${sanitize(event.detail, 180)}"
            }
            .ifBlank { "none" }
    }

    private fun sanitize(value: String, maxLength: Int): String {
        return value
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(maxLength)
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    companion object {
        const val EVENT_TYPE = "kernel_trace.turn"
        private const val IMPORTANCE = 4
        private const val MAX_TRACE_BODY_CHARS = 4_000
        private const val MAX_TRACE_EVENTS = 24
    }
}
