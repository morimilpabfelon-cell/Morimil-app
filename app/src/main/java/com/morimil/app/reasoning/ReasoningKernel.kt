package com.morimil.app.reasoning

import com.morimil.app.ai.ChatTurn
import com.morimil.app.ai.ReasoningClient
import com.morimil.app.ai.ReasoningProviderConfig
import com.morimil.app.ai.ReasoningRuntimeState
import com.morimil.app.ai.SystemPromptBuilder
import com.morimil.app.data.genesis.GenesisIdentity
import com.morimil.app.reasoning.model.ModelBackendRouter
import com.morimil.app.reasoning.model.ReasoningBackendStatusStore
import com.morimil.app.web.NativeWebContextStore

class ReasoningKernel(
    private val contextReader: ReasoningContextReader,
    private val motorCoordinator: TriMotorReasoningCoordinator
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
        ReasoningRuntimeState.set(request.runtimeConfig)

        var state = ReasoningState(
            input = cleanInput,
            mode = backend.mode,
            intent = intent,
            modelBackendLabel = "${backend.label}:${backend.kind}",
            memoryContextSummary = "pending",
            capsuleContextSummary = "pending",
            policyDecision = if (backend.usable) {
                "configured_motor_allowed"
            } else {
                "model_unavailable_degraded"
            },
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
            return ReasoningKernelResult(
                state = errorState,
                reply = null,
                errorMessage = error
            )
        }

        return runCatching {
            val localMemoryContext = contextReader.readLivingMemory(cleanInput)
            val webBudget = webPromptBudgetFor(localMemoryContext)
            val nativeWebContext = NativeWebContextStore.consumePromptContext(
                query = cleanInput,
                maxChars = webBudget
            )
            val memoryContext = buildString {
                appendLine(localMemoryContext)
                if (nativeWebContext.isNotBlank()) {
                    appendLine()
                    appendLine("EXTERNAL TEMPORARY CONTEXT:")
                    appendLine(nativeWebContext)
                }
            }.take(MAX_COMBINED_MEMORY_CONTEXT_CHARS)
            val capsuleContext = contextReader.readKnowledgeCapsules()
            state = state.copy(
                memoryContextSummary = summarizeContext(memoryContext),
                capsuleContextSummary = summarizeContext(capsuleContext)
            ).withTrace(
                "context_built",
                "memory_chars=${localMemoryContext.length} combined_chars=${memoryContext.length} capsule_chars=${capsuleContext.length} web_chars=${nativeWebContext.length}"
            )

            val reply = when {
                !backend.usable -> degradedReply(cleanInput, intent, memoryContext, capsuleContext, backend.reason)
                else -> {
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

                    state = state.withTrace(
                        "motor_plan",
                        "complexity=${backend.taskComplexity} backend=${backend.kind} endpoint=${backend.endpoint.take(80)} model=${backend.model}"
                    )
                    motorCoordinator.reason(
                        complexity = backend.taskComplexity,
                        request = AuxiliaryReasoningRequest(
                            config = request.runtimeConfig,
                            runtimeAccess = request.runtimeAccess,
                            systemPrompt = systemPrompt,
                            history = recentHistory
                        )
                    ).map { motorResult ->
                        state = state.copy(
                            criticFindings = motorResult.findings
                        ).withTrace(
                            "motor_result",
                            "requested=${motorResult.requestedRoles} activated=${motorResult.activatedBindings} unavailable=${motorResult.unavailableRoles} failed=${motorResult.failedRoles}"
                        )
                        motorResult.reply
                    }.getOrElse { error ->
                        state = state.withTrace("motor_failed", error.message ?: error::class.java.simpleName)
                        degradedReply(cleanInput, intent, memoryContext, capsuleContext, error.message ?: error::class.java.simpleName)
                    }
                }
            }

            state = state.withFinalReply(reply)
            state = state.withTrace(
                "persistence_boundary",
                "reply_is_transient; memory_write_capability=absent"
            )

            ReasoningKernelResult(
                state = state,
                reply = reply,
                errorMessage = null
            )
        }.getOrElse { error ->
            val message = error.message ?: error::class.java.simpleName
            val errorState = state.withError(message)
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

    private fun webPromptBudgetFor(localMemoryContext: String): Int {
        return minOf(MAX_WEB_CONTEXT_CHARS, localMemoryContext.length.coerceAtLeast(0))
    }

    private companion object {
        const val MAX_COMBINED_MEMORY_CONTEXT_CHARS = 24_000
        const val MAX_WEB_CONTEXT_CHARS = 2_500
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
    val runtimeLabel: String
)
