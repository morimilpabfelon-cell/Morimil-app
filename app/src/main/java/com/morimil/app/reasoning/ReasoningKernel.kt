package com.morimil.app.reasoning

import com.morimil.app.ai.ChatTurn
import com.morimil.app.ai.ReasoningClient
import com.morimil.app.ai.ReasoningProviderConfig
import com.morimil.app.ai.ReasoningRuntimeState
import com.morimil.app.ai.SystemPromptBuilder
import com.morimil.app.data.genesis.GenesisIdentity
import com.morimil.app.data.repository.MemoryLinkRepository
import com.morimil.app.data.repository.MemoryOrganRepository
import com.morimil.app.data.repository.MemoryRepository
import com.morimil.app.data.repository.RecallScheduleRepository
import com.morimil.app.domain.usecase.AppendLivingMemoryUseCase
import com.morimil.app.domain.usecase.RunRestCycleUseCase

/**
 * First local reasoning kernel foundation.
 *
 * The kernel owns turn state, local memory retrieval, fallback behavior and
 * post-turn maintenance. Model calls are treated as replaceable backends, not
 * as Morimil's identity or final authority.
 */
class ReasoningKernel(
    private val memoryRepository: MemoryRepository,
    private val memoryOrganRepository: MemoryOrganRepository,
    private val memoryLinkRepository: MemoryLinkRepository,
    private val appendLivingMemoryUseCase: AppendLivingMemoryUseCase,
    private val runRestCycleUseCase: RunRestCycleUseCase,
    private val recallScheduleRepository: RecallScheduleRepository,
    private val reasoningClient: ReasoningClient
) {
    suspend fun reason(request: ReasoningKernelRequest): ReasoningKernelResult {
        val cleanInput = request.input.trim()
        val intent = LocalIntentDetector.detect(cleanInput)
        val mode = ReasoningModeResolver.resolve(request.runtimeConfig, request.runtimeAccess)
        ReasoningRuntimeState.set(request.runtimeConfig)

        var state = ReasoningState(
            input = cleanInput,
            mode = mode,
            intent = intent,
            modelBackendLabel = request.runtimeLabel,
            memoryContextSummary = "pending",
            capsuleContextSummary = "pending",
            policyDecision = if (mode == ReasoningMode.SAFE_DEGRADED) {
                "model_unavailable_degraded"
            } else {
                "model_request_allowed"
            },
            criticFindings = emptyList(),
            trace = listOf(ReasoningTraceEvent("kernel_start", "mode=$mode intent=$intent backend=${request.runtimeLabel}"))
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

            val activeGenesisCoreId = userMemoryEvent?.genesisCoreId
                ?: request.fallbackGenesisCoreId
                ?: "primary_genesis"

            val capturedCapsule = memoryOrganRepository.captureKnowledgeCapsuleFromText(
                genesisCoreId = activeGenesisCoreId,
                text = cleanInput,
                sourceEventHash = userMemoryEvent?.eventHash
            )
            if (capturedCapsule != null && userMemoryEvent != null) {
                memoryLinkRepository.createMemoryLink(
                    instanceId = userMemoryEvent.instanceId,
                    genesisCoreHash = userMemoryEvent.genesisCoreHash,
                    sourceId = capturedCapsule.capsuleId,
                    sourceType = MemoryLinkRepository.KNOWLEDGE_CAPSULE_NODE_TYPE,
                    targetId = userMemoryEvent.eventHash,
                    targetType = MemoryLinkRepository.MEMORY_EVENT_NODE_TYPE,
                    relation = MemoryLinkRepository.RELATION_DERIVED_FROM,
                    strength = 0.96,
                    reason = "kernel_capsule_source_event:${capturedCapsule.capsuleHash.take(19)}"
                )
                state = state.withTrace("capsule_capture", "capsule=${capturedCapsule.capsuleId}")
            }

            val memoryContext = memoryRepository.buildLivingMemoryContext(cleanInput)
            val capsuleContext = memoryOrganRepository.buildKnowledgeCapsuleContext()
            state = state.copy(
                memoryContextSummary = summarizeContext(memoryContext),
                capsuleContextSummary = summarizeContext(capsuleContext)
            ).withTrace("context_built", "memory_chars=${memoryContext.length} capsule_chars=${capsuleContext.length}")

            val reply = if (mode == ReasoningMode.SAFE_DEGRADED) {
                degradedReply(cleanInput, intent, memoryContext, capsuleContext, "No usable model backend or runtime access available.")
            } else {
                val systemPrompt = SystemPromptBuilder.build(
                    genesis = request.genesis,
                    alias = request.alias,
                    doctrineText = request.doctrineText,
                    policyText = request.policyText,
                    livingMemoryContext = memoryContext,
                    knowledgeCapsuleContext = capsuleContext
                )
                val recentHistory = request.priorHistory
                    .takeLast(ReasoningClient.MAX_HISTORY_MESSAGES - 1) +
                    ChatTurn(role = "user", content = cleanInput)

                reasoningClient.sendMessage(
                    request.runtimeConfig,
                    request.runtimeAccess,
                    systemPrompt,
                    recentHistory
                ).getOrElse { error ->
                    state = state.withTrace("model_failed", error.message ?: error::class.java.simpleName)
                    degradedReply(cleanInput, intent, memoryContext, capsuleContext, error.message ?: error::class.java.simpleName)
                }
            }

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

    suspend fun reasonDegraded(request: ReasoningKernelRequest): ReasoningKernelResult {
        return reason(
            request.copy(
                runtimeLabel = "deterministic_local_fallback",
                runtimeConfig = ReasoningProviderConfig.default(),
                runtimeAccess = ""
            )
        )
    }

    private fun degradedReply(
        input: String,
        intent: ReasoningIntent,
        memoryContext: String,
        capsuleContext: String,
        modelError: String?
    ): String {
        return DeterministicFallbackReasoner.reply(
            input = input,
            intent = intent,
            memoryContext = memoryContext,
            capsuleContext = capsuleContext,
            modelError = modelError
        )
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
    val genesis: GenesisIdentity,
    val alias: String,
    val doctrineText: String?,
    val policyText: String?,
    val priorHistory: List<ChatTurn>,
    val fallbackGenesisCoreId: String?,
    val runtimeLabel: String,
    val runtimeConfig: ReasoningProviderConfig,
    val runtimeAccess: String
)
