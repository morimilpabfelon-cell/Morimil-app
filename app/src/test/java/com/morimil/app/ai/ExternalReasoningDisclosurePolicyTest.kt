package com.morimil.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalReasoningDisclosurePolicyTest {
    private val privatePrompt = """
        IDENTITY: Morimil
        DOCTRINE: private-doctrine
        LIVING MEMORY: private-memory
        KNOWLEDGE CAPSULES: private-capsule
    """.trimIndent()

    private val history = listOf(
        ChatTurn("user", "old private user turn"),
        ChatTurn("assistant", "old private assistant turn"),
        ChatTurn("user", "current request")
    )

    @Test
    fun localUsbHelperKeepsFullLocalContext() {
        val disclosure = ExternalReasoningDisclosurePolicy.prepare(
            config = ReasoningProviderConfig.default(),
            fullSystemPrompt = privatePrompt,
            fullHistory = history
        )

        assertEquals(ExternalReasoningDisclosureMode.LOCAL_FULL_CONTEXT, disclosure.mode)
        assertTrue(disclosure.privateContextIncluded)
        assertEquals(privatePrompt, disclosure.systemPrompt)
        assertEquals(history, disclosure.history)
    }

    @Test
    fun remoteHelperReceivesOnlyCurrentUserMessageByDefault() {
        val disclosure = ExternalReasoningDisclosurePolicy.prepare(
            config = remoteConfig(allowPrivateContext = false),
            fullSystemPrompt = privatePrompt,
            fullHistory = history
        )

        assertEquals(
            ExternalReasoningDisclosureMode.REMOTE_USER_MESSAGE_ONLY,
            disclosure.mode
        )
        assertFalse(disclosure.privateContextIncluded)
        assertEquals(listOf(ChatTurn("user", "current request")), disclosure.history)
        assertFalse(disclosure.systemPrompt.contains("private-doctrine"))
        assertFalse(disclosure.systemPrompt.contains("private-memory"))
        assertFalse(disclosure.systemPrompt.contains("private-capsule"))
        assertTrue(disclosure.systemPrompt.contains("temporary external computation helper"))
    }

    @Test
    fun explicitRemoteConsentIsRequiredForFullContext() {
        val disclosure = ExternalReasoningDisclosurePolicy.prepare(
            config = remoteConfig(allowPrivateContext = true),
            fullSystemPrompt = privatePrompt,
            fullHistory = history
        )

        assertEquals(
            ExternalReasoningDisclosureMode.REMOTE_FULL_CONTEXT_EXPLICIT,
            disclosure.mode
        )
        assertTrue(disclosure.privateContextIncluded)
        assertEquals(privatePrompt, disclosure.systemPrompt)
        assertEquals(history, disclosure.history)
    }

    @Test
    fun remoteUserOnlyContractRejectsHiddenAdditionalHistory() {
        val result = runCatching {
            ExternalReasoningDisclosure(
                mode = ExternalReasoningDisclosureMode.REMOTE_USER_MESSAGE_ONLY,
                systemPrompt = ExternalReasoningDisclosurePolicy.REMOTE_MINIMAL_SYSTEM_PROMPT,
                history = history,
                privateContextIncluded = false
            )
        }

        assertTrue(result.isFailure)
        assertEquals(
            "remote_user_only_disclosure_must_contain_exactly_one_user_turn",
            result.exceptionOrNull()?.message
        )
    }

    private fun remoteConfig(allowPrivateContext: Boolean): ReasoningProviderConfig {
        return ReasoningProviderConfig(
            preset = ReasoningPreset.RESPONSES_COMPATIBLE,
            baseUrl = "https://api.example.com/v1/responses",
            model = "example-reasoner",
            allowPrivateContextToRemote = allowPrivateContext
        )
    }
}
