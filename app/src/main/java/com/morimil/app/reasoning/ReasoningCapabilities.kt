package com.morimil.app.reasoning

import com.morimil.app.ai.ChatTurn
import com.morimil.app.ai.ExternalReasoningDisclosureMode
import com.morimil.app.ai.ExternalReasoningDisclosurePolicy
import com.morimil.app.ai.ReasoningClient
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
 * Temporary external compute capability. It can return advisory text to
 * Morimil's kernel but is not one of Morimil's intrinsic motors and exposes no
 * memory, identity, Genesis, tool or lifecycle capability.
 */
fun interface TemporaryExternalReasoningProvider {
    suspend fun compute(request: TemporaryExternalReasoningRequest): Result<String>
}

data class TemporaryExternalReasoningRequest(
    val config: ReasoningProviderConfig,
    val runtimeAccess: String,
    val systemPrompt: String,
    val history: List<ChatTurn>,
    val disclosureMode: ExternalReasoningDisclosureMode
) {
    init {
        require(
            disclosureMode == ExternalReasoningDisclosureMode.AUXILIARY_USER_TASK_ONLY
        ) { "temporary_helper_disclosure_mode_not_allowed" }
        require(systemPrompt == ExternalReasoningDisclosurePolicy.AUXILIARY_BOUNDARY_PROMPT) {
            "temporary_helper_system_prompt_not_allowed"
        }
        require(history.size == 1 && history.single().role == "user") {
            "temporary_helper_must_receive_exactly_one_user_turn"
        }
        require(history.single().content.isNotBlank()) {
            "temporary_helper_user_task_blank"
        }
    }
}

class ReasoningClientTemporaryExternalProvider(
    private val client: ReasoningClient
) : TemporaryExternalReasoningProvider {
    override suspend fun compute(request: TemporaryExternalReasoningRequest): Result<String> {
        val valid = request.config.validated()
        require(
            request.disclosureMode == ExternalReasoningDisclosureMode.AUXILIARY_USER_TASK_ONLY
        ) { "temporary_helper_requires_user_task_only_contract" }
        require(request.systemPrompt == ExternalReasoningDisclosurePolicy.AUXILIARY_BOUNDARY_PROMPT) {
            "temporary_helper_prompt_boundary_bypassed"
        }
        require(request.history.size == 1 && request.history.single().role == "user") {
            "temporary_helper_history_boundary_bypassed"
        }
        return client.sendMessage(
            config = valid,
            runtimeKey = request.runtimeAccess,
            systemPrompt = request.systemPrompt,
            history = request.history
        )
    }
}
