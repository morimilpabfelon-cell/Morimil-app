package com.morimil.app.ui

import com.morimil.app.MorimilAppContainer
import com.morimil.app.ai.ChatTurn
import com.morimil.app.ai.ReasoningClient
import com.morimil.app.data.genesis.GenesisIdentitySource
import com.morimil.app.data.genesis.GenesisUltraIntegrationGate
import com.morimil.app.data.local.LocalInstanceIdentityEntity
import com.morimil.app.data.local.ReasoningTurnAuthor
import com.morimil.app.data.local.ReasoningTurnEntity
import com.morimil.app.reasoning.ReasoningExecutionOrigin
import com.morimil.app.reasoning.ReasoningKernelRequest
import com.morimil.app.reasoning.model.ReasoningEscalationStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class MorimilChatCoordinator(
    private val container: MorimilAppContainer,
    private val scope: CoroutineScope,
    private val localIdentity: StateFlow<LocalInstanceIdentityEntity?>,
    private val messages: StateFlow<List<ReasoningTurnEntity>>,
    private val observeTask: suspend (String, suspend () -> Unit) -> Result<Unit>
) {
    private val _genesisResult = MutableStateFlow<Result<GenesisIdentitySource>?>(null)
    val genesisResult: StateFlow<Result<GenesisIdentitySource>?> = _genesisResult.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError.asStateFlow()

    private var cachedDoctrineText: String? = null
    private var cachedPolicyText: String? = null

    fun refreshGenesis() {
        scope.launch {
            val result = container.genesisReader.readGenesisIdentity()
            _genesisResult.value = result
            result.getOrNull()?.identity?.doctrineRef?.let { ref ->
                cachedDoctrineText = container.genesisReader.readDoctrineText(ref).getOrNull()
            }
            result.getOrNull()?.identity?.policyRef?.let { ref ->
                cachedPolicyText = container.genesisReader.readPolicyText(ref).getOrNull()
            }
        }
    }

    suspend fun bornInstance(alias: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            GenesisUltraIntegrationGate.requireBirthReady()
            val genesisSource = _genesisResult.value?.getOrNull()
                ?: error("Genesis identity not loaded yet.")
            val genesis = genesisSource.identity
            require(!container.memoryRepository.hasExistingBirth()) {
                "This Morimil instance has already been born."
            }
            val installedBundle = container.genesisReader.installGenesisBundle().getOrThrow()
            val sourceOrigin = "${genesisSource.origin.label}:${installedBundle.installPath}"
            val genesisCoreHash = installedBundle.verification.genesisCoreHash
            require(genesisCoreHash == genesisSource.manifest.genesisCoreHash) {
                "Installed Genesis bundle does not match loaded Genesis manifest."
            }

            try {
                container.memoryRepository.birthLocalIdentity(
                    alias,
                    genesis,
                    sourceOrigin,
                    genesisCoreHash,
                    cachedDoctrineText,
                    cachedPolicyText
                )
                container.memoryRepository.seedInitialStateIfNeeded()
                container.reasoningTranscriptRepository.seedIntroTurnsIfNeeded()
                container.agentOrchestrationRepository.seedDefaultOrchestrationIfNeeded()
                observeTask("rest_cycle.birth") {
                    container.runRestCycleUseCase()
                }
                observeTask("recall.birth") {
                    container.recallScheduleRepository.seedFromRecentMemoryIfNeeded()
                }
                Unit
            } catch (error: Exception) {
                container.genesisReader.clearInstalledGenesisBundle()
                throw error
            }
        }
    }

    fun sendMessage(body: String) {
        sendMessageInternal(body = body, appendUserTurn = true)
    }

    fun approveExternalReasoning() {
        val task = ReasoningEscalationStore.approveCurrent() ?: return
        sendMessageInternal(body = task, appendUserTurn = false)
    }

    fun keepReasoningLocal() {
        val task = ReasoningEscalationStore.keepLocal() ?: return
        sendMessageInternal(body = task, appendUserTurn = false)
    }

    private fun sendMessageInternal(body: String, appendUserTurn: Boolean) {
        val cleanBody = body.trim()
        if (cleanBody.isEmpty() || _isSending.value) return
        ReasoningEscalationStore.discardIfTaskChanged(cleanBody)

        scope.launch {
            _chatError.value = null

            val genesis = _genesisResult.value?.getOrNull()?.identity
            if (genesis == null) {
                _chatError.value = "Genesis no esta cargado todavia. Intenta de nuevo en un momento."
                return@launch
            }

            val configuredHelper = container.reasoningConfigStore.loadActiveHelper()
            val runtimeConfig = configuredHelper.config
            val runtimeAccess = if (runtimeConfig.requiresRuntimeKey) {
                container.secretVault.readReasoningKey(
                    slotId = configuredHelper.id,
                    endpoint = runtimeConfig.baseUrl
                ).getOrNull().orEmpty()
            } else {
                ""
            }
            val alias = localIdentity.value?.alias ?: genesis.alias

            _isSending.value = true
            try {
                val trustedTurns = messages.value
                    .filter { turn -> ReasoningTurnAuthor.isTrustedConversationAuthor(turn.author) }
                val historyTurns = if (!appendUserTurn &&
                    trustedTurns.lastOrNull()?.author == ReasoningTurnAuthor.USER &&
                    trustedTurns.lastOrNull()?.body?.trim() == cleanBody
                ) {
                    trustedTurns.dropLast(1)
                } else {
                    trustedTurns
                }
                val priorHistory = historyTurns
                    .takeLast(ReasoningClient.MAX_HISTORY_MESSAGES - 1)
                    .map { turn ->
                        ChatTurn(
                            role = if (turn.author == ReasoningTurnAuthor.USER) "user" else "assistant",
                            content = turn.body
                        )
                    }

                val result = withContext(Dispatchers.IO) {
                    if (appendUserTurn) {
                        container.reasoningTranscriptRepository.appendUserTurn(cleanBody)
                    }
                    container.reasoningKernel.reason(
                        ReasoningKernelRequest(
                            input = cleanBody,
                            genesis = genesis,
                            alias = alias,
                            doctrineText = cachedDoctrineText,
                            policyText = cachedPolicyText,
                            priorHistory = priorHistory,
                            runtimeLabel = configuredHelper.displayName,
                            runtimeConfig = runtimeConfig,
                            runtimeAccess = runtimeAccess
                        )
                    )
                }

                result.reply?.let { reply ->
                    withContext(Dispatchers.IO) {
                        if (result.state.executionOrigin == ReasoningExecutionOrigin.TEMPORARY_EXTERNAL) {
                            container.reasoningTranscriptRepository.appendAuxiliaryAdvisoryTurn(reply)
                        } else {
                            container.reasoningTranscriptRepository.appendMorimilTurn(reply)
                        }
                    }
                }

                if (result.errorMessage != null) {
                    _chatError.value = result.errorMessage ?: "Error con el razonamiento."
                }
            } finally {
                ReasoningEscalationStore.clearResolvedFor(cleanBody)
                _isSending.value = false
            }
        }
    }
}
