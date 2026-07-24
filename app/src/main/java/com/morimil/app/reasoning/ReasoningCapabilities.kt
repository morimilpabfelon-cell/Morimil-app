package com.morimil.app.reasoning

import com.morimil.app.ai.ChatTurn
import com.morimil.app.ai.ExternalReasoningDisclosureMode
import com.morimil.app.ai.ReasoningClient
import com.morimil.app.ai.ReasoningEndpointPolicy
import com.morimil.app.ai.ReasoningProviderConfig
import com.morimil.app.data.repository.MemoryOrganRepository
import com.morimil.app.data.repository.MemoryRepository

/** Read-only senses through which Morimil's own kernel can consult continuity. */
interface ReasoningContextReader {
    suspend fun readLivingMemory(query: String): String
    suspend fun readKnowledgeCapsules(): String
}

class RepositoryReasoningContextReader(
    private val memoryRepository: MemoryRepository,
    private val memoryOrganRepository: MemoryOrganRepository
) : ReasoningContextReader {
    override suspend fun readLivingMemory(query: String): String {
        return memoryRepository.buildLivingMemoryContext(query)
    }

    override suspend fun readKnowledgeCapsules(): String {
        return memoryOrganRepository.buildKnowledgeCapsuleContext()
    }
}

/**
 * Temporary external reasoning capability. It can return text to Morimil's
 * kernel but is not one of Morimil's intrinsic motors and exposes no transcript,
 * memory, identity or lifecycle writer.
 */
fun interface TemporaryExternalReasoningProvider {
    suspend fun compute(request: TemporaryExternalReasoningRequest): Result<String>
}

data class TemporaryExternalReasoningRequest(
    val config: ReasoningProviderConfig,
    val runtimeAccess: String,
    val systemPrompt: String,
    val history: List<ChatTurn>,
    val disclosureMode: ExternalReasoningDisclosureMode,
    val privateContextIncluded: Boolean
) {
    init {
        require(systemPrompt.isNotBlank()) { "temporary_external_system_prompt_blank" }
        require(history.isNotEmpty()) { "temporary_external_history_empty" }
        when (disclosureMode) {
            ExternalReasoningDisclosureMode.LOCAL_FULL_CONTEXT,
            ExternalReasoningDisclosureMode.REMOTE_FULL_CONTEXT_EXPLICIT -> {
                require(privateContextIncluded) {
                    "full_context_disclosure_requires_private_context_marker"
                }
            }
            ExternalReasoningDisclosureMode.REMOTE_USER_MESSAGE_ONLY -> {
                require(!privateContextIncluded) {
                    "remote_user_only_disclosure_cannot_include_private_context"
                }
                require(history.size == 1 && history.single().role == "user") {
                    "remote_user_only_disclosure_must_contain_one_user_turn"
                }
            }
        }
    }
}

class ReasoningClientTemporaryExternalProvider(
    private val client: ReasoningClient
) : TemporaryExternalReasoningProvider {
    override suspend fun compute(request: TemporaryExternalReasoningRequest): Result<String> {
        val valid = request.config.validated()
        val isLocal = ReasoningEndpointPolicy.isLocalTrustedEndpoint(valid.baseUrl)
        if (isLocal) {
            require(request.disclosureMode == ExternalReasoningDisclosureMode.LOCAL_FULL_CONTEXT) {
                "local_helper_requires_local_full_context_contract"
            }
        } else if (valid.allowPrivateContextToRemote) {
            require(
                request.disclosureMode ==
                    ExternalReasoningDisclosureMode.REMOTE_FULL_CONTEXT_EXPLICIT
            ) { "remote_private_context_requires_explicit_disclosure_contract" }
        } else {
            require(
                request.disclosureMode ==
                    ExternalReasoningDisclosureMode.REMOTE_USER_MESSAGE_ONLY
            ) { "remote_default_requires_user_message_only_contract" }
            require(!request.privateContextIncluded) {
                "remote_default_cannot_include_private_context"
            }
        }
        return client.sendMessage(
            config = valid,
            runtimeKey = request.runtimeAccess,
            systemPrompt = request.systemPrompt,
            history = request.history
        )
    }
}
