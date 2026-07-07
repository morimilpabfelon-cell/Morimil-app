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
import com.morimil.app.reasoning.model.ModelBackendRouter
import com.morimil.app.reasoning.model.ReasoningBackendStatusStore
import com.morimil.app.reasoning.model.ReasoningEscalationStore
import com.morimil.app.reasoning.trace.KernelTraceRepository
import com.morimil.app.web.NativeWebContextStore

class ReasoningKernel(
    private val memoryRepository: MemoryRepository,
    private val memoryOrganRepository: MemoryOrganRepository,
    private val memoryLinkRepository: MemoryLinkRepository,
    private val appendLivingMemoryUseCase: AppendLivingMemoryUseCase,
    private val runRestCycleUseCase: RunRestCycleUseCase,
    private val recallScheduleRepository: RecallScheduleRepository,
    private val reasoningClient: ReasoningClient,
    private val kernelTraceRepository: KernelTraceRepository
) {
    suspend fun reason(request: ReasoningKernelRequest): ReasoningKernelResult {
        val cleanInput = request.input.trim()
        val intent = LocalIntentDetector.detect(cleanInput)
        val backend = ModelBackendRouter.select(
            runtimeLabel = request.runtimeLabel,
            config = request.runtimeConfig,
            runtimeAccess = request.runtimeAccess,
            input = cleanInput,
            intent = intent
        )
        ReasoningBackendStatusStore.update(backend)
        ReasoningEscalationStore.publishIfNeeded(backend, cleanInput)
        ReasoningRuntimeState.set(request.runtimeConfig)

        var state = ReasoningState(
            input = cleanInput,
            mode = backend.mode,
            intent = intent,
            modelBackendLabel = "${backend.label}:${backend.kind}",
            memoryContextSummary = "pending",
            capsuleContextSummary = "pending",
            policyDecision = if (backend.usable) "model_request_allowed" else "model_unavailable_degraded",
            criticFindings = emptyList(),
            trace = listOf(
                ReasoningTraceEvent(
                    "kernel_start",
                    "mode=${backend.mode} intent=$intent backend=${backend.kind} complexity=${backend.taskComplexity} hint=${backend.routingHint} reason=${backend.reason}"
                )
            )
        )

        if (cleanInput.isBlank()) {
            val error = "Mensaje vacio."
            val errorState = state.withError(error)
            runCatching {
                kernelTraceRepository.recordTurnTrace(
                    state = errorState,
                    backend = backend,
                    reply = null,
                    errorMessage = error,
                    fallbackUsed = !backend.usable
                )
            }
            return ReasoningKernelResult(
                state = errorState,
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

            val localMemoryContext = memoryRepository.buildLivingMemoryContext(cleanInput)
            val nativeWebContext = NativeWebContextStore.promptContext()
            val memoryContext = buildString {
                appendLine(localMemoryContext)
                appendLine()
                appendLine("EXTERNAL TEMPORARY CONTEXT:")
                appendLine(nativeWebContext)
            }.take(MAX_COMBINED_MEMORY_CONTEXT_CHARS)
            val capsuleContext = memoryOrganRepository.buildKnowledgeCapsuleContext()
            state = state.copy(
                memoryContextSummary = summarizeContext(memoryContext),
                capsuleContextSummary = summarizeContext(capsuleContext)
            ).withTrace(
                "context_built",
                "memory_chars=${memoryContext.length} capsule_chars=${capsuleContext.length} web_chars=${nativeWebContext.length}"
            )

            var fallbackUsed = !backend.usable
            val reply = if (!backend.usable) {
                degradedReply(cleanInput, intent, memoryContext, capsuleContext, backend.reason)
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

                state = state.withTrace("model_call", "backend=${backend.kind} endpoint=${backend.endpoint.take(80)} model=${backend.model} complexity=${backend.taskComplexity}")
                reasoningClient.sendMessage(
                    request.runtimeConfig,
                    request.runtimeAccess,
                    systemPrompt,
                    recentHistory
                ).getOrElse { error ->
                    fallbackUsed = true
                    state = state.withTrace("model_failed", error.message ?: error::class.java.simpleName)
                    degradedReply(cleanInput, intent, memoryContext, capsuleContext, error.message ?: error::class.java.simpleName)
                }
            }

            appendLivingMemoryUseCase.appendAssistantMessage(reply)
            state = state.withFinalReply(reply)
            runCatching {
                kernelTraceRepository.recordTurnTrace(
                    state = state,
                    backend = backend,
                    reply = reply,
                    errorMessage = null,
                    fallbackUsed = fallbackUsed
                )
            }

            runCatching { runRestCycleUseCase() }
                .onSuccess { ran -> state = state.withTrace("rest_cycle", "ran=$ran") }
                .onFailure { error -> state = state.withTrace("rest_cycle_failed", error.message ?: error::class.java.simpleName) }

            runCatching { recallScheduleRepository.seedFromRecentMemoryIfNeeded() }
                .onSuccess { created -> state = state.withTrace("recall_schedule", "created=$created") }
                .onFailure { error -> state = state.withTrace("recall_schedule_failed", error.message ?: error::class.java.simpleName) }

            ReasoningKernelResult(
                state = state,
                reply = reply,
                errorMessage = null
            )
        }.getOrElse { error ->
            val message = error.message ?: error::class.java.simpleName
            val errorState = state.withError(message)
            runCatching {
                kernelTraceRepository.recordTurnTrace(
                    state = errorState,
                    backend = backend,
                    reply = null,
                    errorMessage = message,
                    fallbackUsed = !backend.usable
                )
            }
            ReasoningKernelResult(
                state = errorState,
                reply = null,
                errorMessage = message
            )
        }
    }

    private fun degradedReply(
        input: String,
        intent: ReasoningIntent,
        memoryContext: String,
        capsuleContext: String,
        modelError: String? = null
    ): String {
        return DeterministicFallbackReasoner.reply(
            input = input,
            intent = intent,
            memoryContext = memoryContext,
            capsuleContext = capsuleContext,
            modelError = modelError
        )
    }

    private fun summarizeContext(value: String): String {
        val clean = value.trim()
        if (clean.isBlank()) return "empty"
        return clean
            .lines()
            .filter { it.isNotBlank() }
            .take(3)
            .joinToString(" | ") { it.take(120) }
            .take(420)
    }

    private companion object {
        const val MAX_COMBINED_MEMORY_CONTEXT_CHARS = 24_000
    }
}

data class ReasoningKernelRequest(
    val input: String,
    val alias: String,
    val genesis: GenesisIdentity,
    val doctrineText: String?,
    val policyText: String?,
    val priorHistory: List<ChatTurn>,
    val runtimeConfig: ReasoningProviderConfig,
    val runtimeAccess: String,
    val runtimeLabel: String,
    val fallbackGenesisCoreId: String? = null
)
