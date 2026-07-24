package com.morimil.app.reasoning

data class ReasoningTraceEvent(
    val stage: String,
    val detail: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)

enum class ReasoningExecutionOrigin {
    PENDING,
    MORIMIL_INTRINSIC,
    TEMPORARY_EXTERNAL,
    DETERMINISTIC_FALLBACK
}

data class ReasoningState(
    val input: String,
    val mode: ReasoningMode,
    val intent: ReasoningIntent,
    val modelBackendLabel: String,
    val executionOrigin: ReasoningExecutionOrigin,
    val memoryContextSummary: String,
    val capsuleContextSummary: String,
    val policyDecision: String,
    val criticFindings: List<String>,
    val trace: List<ReasoningTraceEvent>,
    val finalReply: String? = null,
    val errorMessage: String? = null
) {
    fun withTrace(stage: String, detail: String): ReasoningState {
        return copy(trace = trace + ReasoningTraceEvent(stage = stage, detail = detail.take(500)))
    }

    fun withFinalReply(reply: String): ReasoningState {
        return copy(
            finalReply = reply,
            trace = trace + ReasoningTraceEvent(stage = "final_reply", detail = reply.take(500))
        )
    }

    fun withError(error: String): ReasoningState {
        return copy(
            errorMessage = error.take(500),
            trace = trace + ReasoningTraceEvent(stage = "error", detail = error.take(500))
        )
    }
}

data class ReasoningKernelResult(
    val state: ReasoningState,
    val reply: String?,
    val errorMessage: String?,
    val externalAuthorizationRequired: Boolean = false
) {
    val succeeded: Boolean get() = reply != null && errorMessage == null
}
