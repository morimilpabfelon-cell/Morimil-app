package com.morimil.app.reasoning

import com.morimil.app.ai.ChatTurn
import com.morimil.app.ai.ExternalReasoningDisclosureMode
import com.morimil.app.ai.ExternalReasoningDisclosurePolicy
import com.morimil.app.ai.ReasoningPreset
import com.morimil.app.ai.ReasoningProviderConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemporaryHelperBoundaryTest {
    private val config = ReasoningProviderConfig(
        preset = ReasoningPreset.LOCAL_USB_HELPER,
        baseUrl = "http://127.0.0.1:11434/v1/chat/completions",
        model = "helper-model"
    )

    @Test
    fun validRequestContainsOnlyOneUserTask() {
        val request = TemporaryExternalReasoningRequest(
            config = config,
            runtimeAccess = "",
            systemPrompt = ExternalReasoningDisclosurePolicy.AUXILIARY_BOUNDARY_PROMPT,
            history = listOf(ChatTurn("user", "current task")),
            disclosureMode = ExternalReasoningDisclosureMode.AUXILIARY_USER_TASK_ONLY
        )

        assertEquals(1, request.history.size)
        assertEquals("current task", request.history.single().content)
    }

    @Test
    fun privateSystemPromptIsRejected() {
        val result = runCatching {
            TemporaryExternalReasoningRequest(
                config = config,
                runtimeAccess = "",
                systemPrompt = "IDENTITY: Morimil\nMEMORY: private-memory",
                history = listOf(ChatTurn("user", "current task")),
                disclosureMode = ExternalReasoningDisclosureMode.AUXILIARY_USER_TASK_ONLY
            )
        }

        assertTrue(result.isFailure)
        assertEquals(
            "temporary_helper_system_prompt_not_allowed",
            result.exceptionOrNull()?.message
        )
    }

    @Test
    fun privateHistoryIsRejected() {
        val result = runCatching {
            TemporaryExternalReasoningRequest(
                config = config,
                runtimeAccess = "",
                systemPrompt = ExternalReasoningDisclosurePolicy.AUXILIARY_BOUNDARY_PROMPT,
                history = listOf(
                    ChatTurn("user", "old private turn"),
                    ChatTurn("assistant", "old private answer"),
                    ChatTurn("user", "current task")
                ),
                disclosureMode = ExternalReasoningDisclosureMode.AUXILIARY_USER_TASK_ONLY
            )
        }

        assertTrue(result.isFailure)
        assertEquals(
            "temporary_helper_must_receive_exactly_one_user_turn",
            result.exceptionOrNull()?.message
        )
    }
}
