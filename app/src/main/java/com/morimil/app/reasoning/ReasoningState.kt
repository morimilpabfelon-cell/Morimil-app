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
        require(executionOrigin != ReasoningExecutionOrigin.TEMPORARY_EXTERNAL) {
            "auxiliary_advisory_cannot_be_final_reply"
        }
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

/**
 * Unverified output from replaceable compute outside Morimil's intrinsic motors.
 * It has no authority, is never a final reply, and cannot be spoken as Morimil.
 */
data class AuxiliaryAdvisory(
    val content: String,
    val providerLabel: String,
    val disclosurePolicyVersion: String
) {
    init {
        require(content.isNotBlank()) { "auxiliary_advisory_blank" }
        require(providerLabel.isNotBlank()) { "auxiliary_advisory_provider_blank" }
        require(disclosurePolicyVersion.isNotBlank()) {
            "auxiliary_advisory_disclosure_policy_blank"
        }
    }

    companion object {
        const val VERSION = "morimil.auxiliary-advisory.v1"
    }
}

data class ReasoningKernelResult(
    val state: ReasoningState,
    val morimilReply: String?,
    val auxiliaryAdvisory: AuxiliaryAdvisory?,
    val errorMessage: String?,
    val externalAuthorizationRequired: Boolean = false
) {
    init {
        require(morimilReply == null || auxiliaryAdvisory == null) {
            "reasoning_result_cannot_mix_morimil_reply_and_auxiliary_advisory"
        }
        require(auxiliaryAdvisory == null || state.finalReply == null) {
            "auxiliary_advisory_cannot_populate_final_reply"
        }
    }

    val succeeded: Boolean get() = morimilReply != null && errorMessage == null
    val advisoryAvailable: Boolean get() = auxiliaryAdvisory != null && errorMessage == null
}
