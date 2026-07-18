package com.morimil.app.reasoning

import com.morimil.app.ai.ChatTurn
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
 * Temporary auxiliary computation capability. It can return text to Morimil's
 * kernel but exposes no transcript, memory, identity or lifecycle writer.
 */
fun interface AuxiliaryReasoningMotor {
    suspend fun compute(request: AuxiliaryReasoningRequest): Result<String>
}

data class AuxiliaryReasoningRequest(
    val config: ReasoningProviderConfig,
    val runtimeAccess: String,
    val systemPrompt: String,
    val history: List<ChatTurn>
)

class ReasoningClientAuxiliaryMotor(
    private val client: ReasoningClient
) : AuxiliaryReasoningMotor {
    override suspend fun compute(request: AuxiliaryReasoningRequest): Result<String> {
        return client.sendMessage(
            config = request.config,
            runtimeAccess = request.runtimeAccess,
            systemPrompt = request.systemPrompt,
            history = request.history
        )
    }
}
