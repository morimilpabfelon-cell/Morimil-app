package com.morimil.app.ai

enum class ExternalReasoningDisclosureMode {
    LOCAL_FULL_CONTEXT,
    REMOTE_USER_MESSAGE_ONLY,
    REMOTE_FULL_CONTEXT_EXPLICIT
}

data class ExternalReasoningDisclosure(
    val mode: ExternalReasoningDisclosureMode,
    val systemPrompt: String,
    val history: List<ChatTurn>,
    val privateContextIncluded: Boolean
) {
    init {
        require(systemPrompt.isNotBlank()) { "external_disclosure_system_prompt_blank" }
        require(history.isNotEmpty()) { "external_disclosure_history_empty" }
        if (mode == ExternalReasoningDisclosureMode.REMOTE_USER_MESSAGE_ONLY) {
            require(!privateContextIncluded) { "remote_user_only_disclosure_cannot_include_private_context" }
            require(history.size == 1 && history.single().role == "user") {
                "remote_user_only_disclosure_must_contain_exactly_one_user_turn"
            }
        }
    }
}

/**
 * Keeps local helper behavior intact while making remote disclosure fail closed.
 * A remote provider receives only the current user message unless the owner has
 * explicitly enabled private-context disclosure for that exact profile.
 */
object ExternalReasoningDisclosurePolicy {
    const val VERSION = "morimil.external-disclosure.v0"

    fun prepare(
        config: ReasoningProviderConfig,
        fullSystemPrompt: String,
        fullHistory: List<ChatTurn>
    ): ExternalReasoningDisclosure {
        val valid = config.validated()
        val currentUser = fullHistory.lastOrNull { turn -> turn.role == "user" }
            ?: error("external_disclosure_current_user_turn_missing")

        if (ReasoningEndpointPolicy.isLocalTrustedEndpoint(valid.baseUrl)) {
            return ExternalReasoningDisclosure(
                mode = ExternalReasoningDisclosureMode.LOCAL_FULL_CONTEXT,
                systemPrompt = fullSystemPrompt,
                history = fullHistory,
                privateContextIncluded = true
            )
        }

        if (valid.allowPrivateContextToRemote) {
            return ExternalReasoningDisclosure(
                mode = ExternalReasoningDisclosureMode.REMOTE_FULL_CONTEXT_EXPLICIT,
                systemPrompt = fullSystemPrompt,
                history = fullHistory,
                privateContextIncluded = true
            )
        }

        return ExternalReasoningDisclosure(
            mode = ExternalReasoningDisclosureMode.REMOTE_USER_MESSAGE_ONLY,
            systemPrompt = REMOTE_MINIMAL_SYSTEM_PROMPT,
            history = listOf(ChatTurn(role = "user", content = currentUser.content)),
            privateContextIncluded = false
        )
    }

    internal const val REMOTE_MINIMAL_SYSTEM_PROMPT =
        "You are a temporary external computation helper. Answer only the current user request. " +
            "You do not own Morimil's identity, memory, doctrine, continuity, tools, lifecycle or final authority. " +
            "Do not claim to be Morimil. Return advisory text only."
}
