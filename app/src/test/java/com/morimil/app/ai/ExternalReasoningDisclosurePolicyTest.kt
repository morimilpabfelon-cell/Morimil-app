package com.morimil.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalReasoningDisclosurePolicyTest {
    @Test
    fun disclosureContainsOnlyCurrentUserTask() {
        val disclosure = ExternalReasoningDisclosurePolicy.prepare(
            currentUserMessage = "  current request  "
        )

        assertEquals(
            ExternalReasoningDisclosureMode.AUXILIARY_USER_TASK_ONLY,
            disclosure.mode
        )
        assertEquals(listOf(ChatTurn("user", "current request")), disclosure.history)
        assertEquals(
            ExternalReasoningDisclosurePolicy.AUXILIARY_BOUNDARY_PROMPT,
            disclosure.systemPrompt
        )
    }

    @Test
    fun boundaryPromptContainsNoPrivateContext() {
        val prompt = ExternalReasoningDisclosurePolicy.AUXILIARY_BOUNDARY_PROMPT

        assertFalse(prompt.contains("private-doctrine"))
        assertFalse(prompt.contains("private-memory"))
        assertFalse(prompt.contains("private-capsule"))
        assertTrue(prompt.contains("temporary external computation provider"))
        assertTrue(prompt.contains("You are not Morimil"))
    }

    @Test
    fun disclosureContractRejectsAdditionalHistory() {
        val result = runCatching {
            ExternalReasoningDisclosure(
                mode = ExternalReasoningDisclosureMode.AUXILIARY_USER_TASK_ONLY,
                systemPrompt = ExternalReasoningDisclosurePolicy.AUXILIARY_BOUNDARY_PROMPT,
                history = listOf(
                    ChatTurn("user", "old turn"),
                    ChatTurn("assistant", "old answer"),
                    ChatTurn("user", "current request")
                )
            )
        }

        assertTrue(result.isFailure)
        assertEquals(
            "external_disclosure_must_contain_exactly_one_user_turn",
            result.exceptionOrNull()?.message
        )
    }

    @Test
    fun disclosureContractRejectsAnyOtherSystemPrompt() {
        val result = runCatching {
            ExternalReasoningDisclosure(
                mode = ExternalReasoningDisclosureMode.AUXILIARY_USER_TASK_ONLY,
                systemPrompt = "IDENTITY: Morimil; MEMORY: secret",
                history = listOf(ChatTurn("user", "current request"))
            )
        }

        assertTrue(result.isFailure)
        assertEquals(
            "external_disclosure_prompt_not_allowed",
            result.exceptionOrNull()?.message
        )
    }

    @Test
    fun blankTaskIsRejected() {
        val result = runCatching {
            ExternalReasoningDisclosurePolicy.prepare("   ")
        }

        assertTrue(result.isFailure)
        assertEquals("external_disclosure_user_task_blank", result.exceptionOrNull()?.message)
    }
}
