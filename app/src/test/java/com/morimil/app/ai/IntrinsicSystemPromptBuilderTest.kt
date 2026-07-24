package com.morimil.app.ai

import com.morimil.app.data.genesis.GenesisIdentity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntrinsicSystemPromptBuilderTest {
    @Test
    fun intrinsicPromptReceivesPrivateContextWithoutAuxiliaryIdentityConfusion() {
        val prompt = IntrinsicSystemPromptBuilder.build(
            IntrinsicContextEnvelope(
                genesis = testGenesis(),
                instanceName = "Morimil",
                doctrineText = "private-doctrine",
                policyText = "private-policy",
                livingMemoryContext = "private-memory",
                knowledgeCapsuleContext = "private-capsule"
            )
        )

        assertTrue(prompt.contains("motor intrinseco"))
        assertTrue(prompt.contains("contexto=intrinseco_privado"))
        assertTrue(prompt.contains("divulgacion_externa=prohibida"))
        assertTrue(prompt.contains("private-doctrine"))
        assertTrue(prompt.contains("private-policy"))
        assertTrue(prompt.contains("private-memory"))
        assertTrue(prompt.contains("private-capsule"))
        assertFalse(prompt.contains("motor auxiliar temporal"))
        assertFalse(prompt.contains("No eres Morimil"))
        assertFalse(prompt.contains("temporary external computation provider"))
        assertFalse(prompt == ExternalReasoningDisclosurePolicy.AUXILIARY_BOUNDARY_PROMPT)
    }

    @Test
    fun blankInstanceNameIsRejectedBeforeBuildingPrivatePrompt() {
        val result = runCatching {
            IntrinsicContextEnvelope(
                genesis = testGenesis(),
                instanceName = "   ",
                doctrineText = null,
                policyText = null,
                livingMemoryContext = "",
                knowledgeCapsuleContext = ""
            )
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message == "intrinsic_instance_name_blank")
    }

    private fun testGenesis(): GenesisIdentity {
        return GenesisIdentity(
            schemaVersion = "1",
            agentId = "morimil",
            alias = "Morimil",
            role = "companion",
            owner = "guardian",
            riskTier = "local_only",
            allowedActions = listOf("reason"),
            disallowedActions = listOf("alter_identity"),
            doctrineRef = "doctrine.md",
            policyRef = "policy.md"
        )
    }
}
