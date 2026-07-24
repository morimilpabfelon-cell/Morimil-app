package com.morimil.app.reasoning

import com.morimil.app.ai.ChatTurn
import com.morimil.app.ai.ExternalReasoningDisclosurePolicy
import com.morimil.app.ai.IntrinsicContextEnvelope
import com.morimil.app.ai.IntrinsicSystemPromptBuilder
import com.morimil.app.ai.ReasoningClient
import com.morimil.app.ai.ReasoningProviderConfig
import com.morimil.app.ai.ReasoningRuntimeState
import com.morimil.app.data.genesis.GenesisIdentity
import com.morimil.app.reasoning.model.ModelBackendRouter
import com.morimil.app.reasoning.model.ReasoningBackendStatusStore
import com.morimil.app.reasoning.model.ReasoningEscalationGateResult
import com.morimil.app.reasoning.model.ReasoningEscalationStore
import com.morimil.app.web.NativeWebContextStore

class ReasoningKernel(
    private val contextReader: ReasoningContextReader,
    private val intrinsicCoordinator: IntrinsicTriMotorCoordinator,
    private val temporaryExternalProvider: TemporaryExternalReasoningProvider
) {
    suspend fun reason(request: ReasoningKernelRequest): ReasoningKernelResult {
        val cleanInput = request.input.trim()
        HybridAuthorityPresentationStore.resetDisabled()
        val authorityTaskKind = ReasoningTaskKindClassifierV0.classify(cleanInput)
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
        val intrinsicRoles = intrinsicCoordinator.availableRoles()

        var state = ReasoningState(
            input = cleanInput,
            mode = backend.mode,
            intent = intent,
            modelBackendLabel = "${backend.label}:${backend.kind}",
            executionOrigin = ReasoningExecutionOrigin.PENDING,
            memoryContextSummary = "pending",
            capsuleContextSummary = "pending",
            policyDecision = when {
                intrinsicRoles.isNotEmpty() -> "intrinsic_reasoning_first"
                backend.usable -> "temporary_helper_available"
                else -> "intrinsic_unavailable_deterministic_fallback"
            },
            criticFindings = emptyList(),
            trace = listOf(
                ReasoningTraceEvent(
                    "kernel_start",
                    "mode=${backend.mode} intent=$intent backend=${backend.kind} " +
                        "intrinsic_roles=$intrinsicRoles complexity=${backend.taskComplexity} " +
                        "authority_task_kind=$authorityTaskKind hint=${backend.routingHint} " +
                        "reason=${backend.reason}"
                )
            )
        )

        if (cleanInput.isBlank()) {
            val error = "Mensaje vacio."
            val errorState = state.withError(error)
            return ReasoningKernelResult(
                state = errorState,
                morimilReply = null,
                auxiliaryAdvisory = null,
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
                "memory_chars=${localMemoryContext.length} " +
                    "combined_chars=${memoryContext.length} " +
                    "capsule_chars=${capsuleContext.length} " +
                    "web_chars=${nativeWebContext.length}"
            )

            val systemPrompt = IntrinsicSystemPromptBuilder.build(
                IntrinsicContextEnvelope(
                    genesis = request.genesis,
                    instanceName = request.alias,
                    doctrineText = request.doctrineText,
                    policyText = request.policyText,
                    livingMemoryContext = memoryContext,
                    knowledgeCapsuleContext = capsuleContext
                )
            )
            val recentHistory = request.priorHistory
                .takeLast(ReasoningClient.MAX_HISTORY_MESSAGES - 1) +
                ChatTurn(role = "user", content = cleanInput)

            state = state.withTrace(
                "intrinsic_context_boundary",
                "version=${IntrinsicSystemPromptBuilder.VERSION} private_context=true " +
                    "external_disclosure=false"
            ).withTrace(
                "intrinsic_motor_plan",
                "complexity=${backend.taskComplexity} " +
                    "authority_task_kind=$authorityTaskKind available=$intrinsicRoles"
            )

            var authorizationRequired = false
            val reply: String? = intrinsicCoordinator.reason(
                IntrinsicReasoningRequest(
                    systemPrompt = systemPrompt,
                    history = recentHistory,
                    taskComplexity = backend.taskComplexity,
                    taskKind = authorityTaskKind,
                    authorityPrompt = cleanInput
                )
            ).fold(
                onSuccess = { motorResult ->
                    HybridAuthorityPresentationStore.publish(
                        finalizationStatus = motorResult.finalizationStatus,
                        authorityDecision = motorResult.authorityDecision
                    )
                    state = state.copy(
                        criticFindings = motorResult.findings,
                        executionOrigin = ReasoningExecutionOrigin.MORIMIL_INTRINSIC,
                        modelBackendLabel = "morimil_intrinsic:${motorResult.activatedVersions}"
                    ).withTrace(
                        "intrinsic_motor_result",
                        "requested=${motorResult.requestedRoles} " +
                            "activated=${motorResult.activatedVersions} " +
                            "unavailable=${motorResult.unavailableRoles} " +
                            "failed=${motorResult.failedRoles} " +
                            "finalization=${motorResult.finalizationStatus} " +
                            "authority_route=${motorResult.authorityDecision?.route}"
                    )
                    motorResult.reply
                },
                onFailure = { intrinsicError ->
                    state = state.withTrace(
                        "intrinsic_motor_unavailable",
                        intrinsicError.message ?: intrinsicError::class.java.simpleName
                    )
                    if (!backend.usable) {
                        state = state.copy(
                            executionOrigin = ReasoningExecutionOrigin.DETERMINISTIC_FALLBACK
                        )
                        degradedReply(cleanInput, intent, memoryContext, capsuleContext, backend.reason)
                    } else {
                        when (ReasoningEscalationStore.consumeOrRequest(backend, cleanInput)) {
                            ReasoningEscalationGateResult.PENDING -> {
                                authorizationRequired = true
                                state = state.copy(
                                    policyDecision = "external_authorization_pending",
                                    executionOrigin = ReasoningExecutionOrigin.PENDING
                                ).withTrace(
                                    "external_authorization_gate",
                                    "version=${ReasoningEscalationStore.VERSION} decision=pending " +
                                        "backend=${backend.kind} model=${backend.model.take(80)}"
                                )
                                null
                            }

                            ReasoningEscalationGateResult.LOCAL_ONLY_ONCE -> {
                                state = state.copy(
                                    policyDecision = "external_authorization_declined",
                                    executionOrigin = ReasoningExecutionOrigin.DETERMINISTIC_FALLBACK
                                ).withTrace(
                                    "external_authorization_gate",
                                    "version=${ReasoningEscalationStore.VERSION} decision=local_only"
                                )
                                degradedReply(
                                    cleanInput,
                                    intent,
                                    memoryContext,
                                    capsuleContext,
                                    "external_helper_declined_by_guardian"
                                )
                            }

                            ReasoningEscalationGateResult.APPROVED_ONCE -> {
                                state = state.copy(
                                    policyDecision = "external_authorization_approved_once"
                                ).withTrace(
                                    "external_authorization_gate",
                                    "version=${ReasoningEscalationStore.VERSION} decision=approved_once " +
                                        "backend=${backend.kind} model=${backend.model.take(80)}"
                                )
                                val disclosure = ExternalReasoningDisclosurePolicy.prepare(
                                    currentUserMessage = cleanInput
                                )
                                state = state.withTrace(
                                    "external_disclosure",
                                    "policy=${ExternalReasoningDisclosurePolicy.VERSION} " +
                                        "mode=${disclosure.mode} private_context=false " +
                                        "history_turns=${disclosure.history.size} " +
                                        "system_chars=${disclosure.systemPrompt.length}"
                                ).withTrace(
                                    "temporary_external_call",
                                    "backend=${backend.kind} endpoint=${backend.endpoint.take(80)} " +
                                        "model=${backend.model} complexity=${backend.taskComplexity}"
                                )
                                temporaryExternalProvider.compute(
                                    TemporaryExternalReasoningRequest(
                                        config = request.runtimeConfig,
                                        runtimeAccess = request.runtimeAccess,
                                        systemPrompt = disclosure.systemPrompt,
                                        history = disclosure.history,
                                        disclosureMode = disclosure.mode
                                    )
                                ).map { externalReply ->
                                    state = state.copy(
                                        executionOrigin = ReasoningExecutionOrigin.TEMPORARY_EXTERNAL
                                    ).withTrace(
                                        "temporary_external_result",
                                        "reply_is_unverified_advisory; intrinsic_motor_state_unchanged"
                                    )
                                    externalReply
                                }.getOrElse { externalError ->
                                    state = state.copy(
                                        executionOrigin = ReasoningExecutionOrigin.DETERMINISTIC_FALLBACK
                                    ).withTrace(
                                        "temporary_external_failed",
                                        externalError.message ?: externalError::class.java.simpleName
                                    )
                                    degradedReply(
                                        cleanInput,
                                        intent,
                                        memoryContext,
                                        capsuleContext,
                                        externalError.message ?: externalError::class.java.simpleName
                                    )
                                }
                            }

                            ReasoningEscalationGateResult.NOT_REQUIRED -> {
                                state = state.copy(
                                    policyDecision = "external_authorization_not_applicable",
                                    executionOrigin = ReasoningExecutionOrigin.DETERMINISTIC_FALLBACK
                                ).withTrace(
                                    "external_authorization_gate",
                                    "version=${ReasoningEscalationStore.VERSION} decision=not_required"
                                )
                                degradedReply(
                                    cleanInput,
                                    intent,
                                    memoryContext,
                                    capsuleContext,
                                    "external_authorization_not_applicable"
                                )
                            }
                        }
                    }
                }
            )

            if (authorizationRequired) {
                return@runCatching ReasoningKernelResult(
                    state = state,
                    morimilReply = null,
                    auxiliaryAdvisory = null,
                    errorMessage = null,
                    externalAuthorizationRequired = true
                )
            }

            val completedOutput = requireNotNull(reply) { "reasoning_reply_missing" }
            if (state.executionOrigin == ReasoningExecutionOrigin.TEMPORARY_EXTERNAL) {
                val advisory = AuxiliaryAdvisory(
                    content = completedOutput,
                    providerLabel = "${backend.label}:${backend.kind}",
                    disclosurePolicyVersion = ExternalReasoningDisclosurePolicy.VERSION
                )
                state = state.withTrace(
                    "auxiliary_advisory_boundary",
                    "version=${AuxiliaryAdvisory.VERSION} final_reply=false voice_authority=false " +
                        "trusted_history=false"
                ).withTrace(
                    "persistence_boundary",
                    "auxiliary_advisory_transcript_only; memory_write_capability=absent"
                )
                return@runCatching ReasoningKernelResult(
                    state = state,
                    morimilReply = null,
                    auxiliaryAdvisory = advisory,
                    errorMessage = null
                )
            }

            state = state.withFinalReply(completedOutput)
            state = state.withTrace(
                "persistence_boundary",
                "reply_is_transient; memory_write_capability=absent"
            )

            ReasoningKernelResult(
                state = state,
                morimilReply = completedOutput,
                auxiliaryAdvisory = null,
                errorMessage = null
            )
        }.getOrElse { error ->
            val message = error.message ?: error::class.java.simpleName
            val errorState = state.withError(message)
            ReasoningKernelResult(
                state = errorState,
                morimilReply = null,
                auxiliaryAdvisory = null,
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
