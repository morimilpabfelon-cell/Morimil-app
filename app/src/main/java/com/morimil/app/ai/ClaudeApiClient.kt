package com.morimil.app.ai

@Deprecated("Use ReasoningClient with ReasoningProviderConfig instead.")
class ClaudeApiClient {
    fun sendMessage(
        apiKey: String,
        systemPrompt: String,
        history: List<ChatTurn>
    ): Result<String> {
        return ReasoningClient().sendMessage(
            config = ReasoningRuntimeState.get(),
            runtimeKey = apiKey,
            systemPrompt = systemPrompt,
            history = history
        )
    }

    companion object {
        const val MAX_HISTORY_MESSAGES = ReasoningClient.MAX_HISTORY_MESSAGES
    }
}
