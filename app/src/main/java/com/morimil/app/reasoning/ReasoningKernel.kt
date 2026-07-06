package com.morimil.app.reasoning

import com.morimil.app.data.repository.MemoryOrganRepository
import com.morimil.app.data.repository.MemoryRepository
import com.morimil.app.data.repository.RecallScheduleRepository
import com.morimil.app.domain.usecase.AppendLivingMemoryUseCase
import com.morimil.app.domain.usecase.RunRestCycleUseCase

/**
 * First local reasoning kernel foundation.
 *
 * This version deliberately implements the safe degraded path first. It gives
 * Morimil a local reasoning state, local memory retrieval, conservative reply,
 * and post-turn maintenance without depending on a model backend.
 *
 * The model-backed path should be wired after local compilation confirms this
 * foundation is stable.
 */
class ReasoningKernel(
    private val memoryRepository: MemoryRepository,
    private val memoryOrganRepository: MemoryOrganRepository,
    private val appendLivingMemoryUseCase: AppendLivingMemoryUseCase,
    private val runRestCycleUseCase: RunRestCycleUseCase,
    private val recallScheduleRepository: RecallScheduleRepository
) {
    suspend fun reasonDegraded(request: ReasoningKernelRequest): ReasoningKernelResult {
        val cleanInput = request.input.trim()
        val intent = LocalIntentDetector.detect(cleanInput)
        var state = ReasoningState(
            input = cleanInput,
            mode = ReasoningMode.SAFE_DEGRADED,
            intent = intent,
            modelBackendLabel = "deterministic_local_fallback",
            memoryContextSummary = "pending",
            capsuleContextSummary = "pending",
            policyDecision = "safe_degraded_local_only",
            criticFindings = listOf("advanced_model_not_used"),
            trace = listOf(ReasoningTraceEvent("kernel_start", "mode=SAFE_DEGRADED intent=$intent"))
        )

        if (cleanInput.isBlank()) {
            val error = "Mensaje vacio."
            return ReasoningKernelResult(
                state = state.withError(error),
                reply = null,
                errorMessage = error
            )
        }

        return runCatching {
            val userMemoryEvent = appendLivingMemoryUseCase.appendUserMessage(cleanInput)
            state = state.withTrace("memory_append", "user_event=${userMemoryEvent?.eventHash ?: "none"}")

            val memoryContext = memoryRepository.buildLivingMemoryContext(cleanInput)
            val capsuleContext = memoryOrganRepository.buildKnowledgeCapsuleContext()
            state = state.copy(
                memoryContextSummary = summarizeContext(memoryContext),
                capsuleContextSummary = summarizeContext(capsuleContext)
            ).withTrace("context_built", "memory_chars=${memoryContext.length} capsule_chars=${capsuleContext.length}")

            val reply = DeterministicFallbackReasoner.reply(
                input = cleanInput,
                intent = intent,
                memoryContext = memoryContext,
                capsuleContext = capsuleContext,
                modelError = request.reason
            )
            appendLivingMemoryUseCase.appendAssistantMessage(reply)
            state = state.withFinalReply(reply)
            runPostTurnMaintenance()
            ReasoningKernelResult(state = state, reply = reply, errorMessage = null)
        }.getOrElse { error ->
            val message = error.message ?: error::class.java.simpleName
            ReasoningKernelResult(
                state = state.withError(message),
                reply = null,
                errorMessage = message
            )
        }
    }

    private suspend fun runPostTurnMaintenance() {
        runCatching { runRestCycleUseCase() }
        runCatching { recallScheduleRepository.seedFromRecentMemoryIfNeeded() }
    }

    private fun summarizeContext(value: String): String {
        return value
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(420)
            .ifBlank { "empty" }
    }
}

data class ReasoningKernelRequest(
    val input: String,
    val reason: String? = null
)
