package com.morimil.app.ai

import com.morimil.app.net.NetEvidenceProvider

class ContextReasoningClient(
    private val baseClient: ReasoningClient = ReasoningClient(),
    private val contextProvider: NetEvidenceProvider = NetEvidenceProvider()
) {
    suspend fun sendMessage(
        config: ReasoningProviderConfig,
        runtimeKey: String,
        systemPrompt: String,
        history: List<ChatTurn>
    ): Result<String> {
        val current = history.lastOrNull { turn -> turn.role == "user" }?.content.orEmpty()
        val addition = contextProvider.build(current).trim()
        val prompt = if (addition.isBlank()) systemPrompt else systemPrompt.trimEnd() + "\n\n" + addition
        return baseClient.sendMessage(config, runtimeKey, prompt, history)
    }
}
