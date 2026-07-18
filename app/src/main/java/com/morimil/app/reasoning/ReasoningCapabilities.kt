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
    val history: List<ChatTurn>
)

class ReasoningClientTemporaryExternalProvider(
    private val client: ReasoningClient
) : TemporaryExternalReasoningProvider {
    override suspend fun compute(request: TemporaryExternalReasoningRequest): Result<String> {
        return client.sendMessage(
            config = request.config,
            runtimeKey = request.runtimeAccess,
            systemPrompt = request.systemPrompt,
            history = request.history
        )
    }
}
