package com.morimil.app.ai

enum class ExternalReasoningDisclosureMode {
    AUXILIARY_USER_TASK_ONLY
}

data class ExternalReasoningDisclosure(
    val mode: ExternalReasoningDisclosureMode,
    val systemPrompt: String,
    val history: List<ChatTurn>
) {
    init {
        require(mode == ExternalReasoningDisclosureMode.AUXILIARY_USER_TASK_ONLY) {
            "external_disclosure_mode_not_allowed"
        }
        require(systemPrompt == ExternalReasoningDisclosurePolicy.AUXILIARY_BOUNDARY_PROMPT) {
            "external_disclosure_prompt_not_allowed"
        }
        require(history.size == 1 && history.single().role == "user") {
            "external_disclosure_must_contain_exactly_one_user_turn"
        }
        require(history.single().content.isNotBlank()) {
            "external_disclosure_user_task_blank"
        }
    }
}

/**
 * Immutable confidentiality boundary for every non-intrinsic compute provider.
 *
 * Ollama, remote APIs and future compatible providers receive only the current
 * user task. They never receive Morimil's identity, doctrine, living memory,
 * knowledge capsules, private history, Genesis state, tools or lifecycle data.
 */
object ExternalReasoningDisclosurePolicy {
    const val VERSION = "morimil.external-disclosure.v1"

    fun prepare(currentUserMessage: String): ExternalReasoningDisclosure {
        val cleanTask = currentUserMessage.trim()
        require(cleanTask.isNotBlank()) { "external_disclosure_user_task_blank" }
        return ExternalReasoningDisclosure(
            mode = ExternalReasoningDisclosureMode.AUXILIARY_USER_TASK_ONLY,
            systemPrompt = AUXILIARY_BOUNDARY_PROMPT,
            history = listOf(ChatTurn(role = "user", content = cleanTask))
        )
    }

    internal const val AUXILIARY_BOUNDARY_PROMPT =
        "You are a temporary external computation provider. Answer only the current user task. " +
            "You are not Morimil and have no access to Morimil's identity, memory, doctrine, " +
            "Genesis, continuity, tools, lifecycle or final authority. Return advisory text only."
}
