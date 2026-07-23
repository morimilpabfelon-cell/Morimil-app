package com.morimil.app.reasoning

import com.morimil.app.ai.ChatTurn
import com.morimil.app.reasoning.authority.HybridAuthorityRoute
import com.morimil.app.reasoning.authority.HybridAuthorityStatus
import com.morimil.app.reasoning.authority.HybridAuthorityTaskKind
import com.morimil.app.reasoning.model.ReasoningTaskComplexity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridAuthorityGenerativeFailClosedV0Test {
    @Test
    fun generativeTaskKindsCannotReachConsensusAcceptanceRoute() {
        val generativeKinds = listOf(
            ReasoningTaskKind.LOGIC,
            ReasoningTaskKind.SPANISH,
            ReasoningTaskKind.INSTRUCTION
        )

        generativeKinds.forEach { kind ->
            assertEquals(HybridAuthorityTaskKind.UNKNOWN, kind.toHybridAuthorityTaskKind())
        }
    }

    @Test
    fun matchingGeneratedRepliesAbstainForEveryGenerativeKind() = runBlocking {
        val cases = listOf(
            ReasoningTaskKind.LOGIC to "Todos los A son B. Todos los B son C.",
            ReasoningTaskKind.SPANISH to "Ana llego antes que Luis.",
            ReasoningTaskKind.INSTRUCTION to "Devuelve exactamente FINAL:AZUL."
        )

        cases.forEach { (kind, prompt) ->
            val coordinator = coordinator(
                primaryReply = "FINAL:SI",
                verifierReply = "FINAL:SI"
            )
            val result = coordinator.reason(request(kind, prompt)).getOrThrow()

            assertEquals("", result.reply)
            assertEquals(HybridAuthorityRoute.UNSUPPORTED, result.authorityDecision?.route)
            assertEquals(HybridAuthorityStatus.ABSTAINED, result.authorityDecision?.status)
            assertFalse(requireNotNull(result.authorityDecision).accepted)
            assertEquals(
                TriMotorFinalizationStatus.ABSTAINED_BY_AUTHORITY,
                result.finalizationStatus
            )
            assertTrue(result.findings.contains("hybrid_authority_task_unknown"))
        }
    }

    @Test
    fun deterministicArithmeticStillOverridesMatchingWrongReplies() = runBlocking {
        val coordinator = coordinator(
            primaryReply = "FINAL:13",
            verifierReply = "FINAL:13"
        )
        val result = coordinator.reason(
            request(
                ReasoningTaskKind.ARITHMETIC,
                "Calcula 15 menos 2 por 6 respetando prioridad."
            )
        ).getOrThrow()

        assertEquals("FINAL:3", result.reply)
        assertEquals(
            HybridAuthorityStatus.ACCEPTED_DETERMINISTIC,
            result.authorityDecision?.status
        )
        assertEquals(
            TriMotorFinalizationStatus.ACCEPTED_BY_AUTHORITY,
            result.finalizationStatus
        )
    }

    private fun coordinator(
        primaryReply: String,
        verifierReply: String
    ): IntrinsicTriMotorCoordinator {
        return IntrinsicTriMotorCoordinator(
            motors = listOf(
                fixedMotor(ReasoningMotorRole.DELIBERATIVE, primaryReply),
                fixedMotor(ReasoningMotorRole.METACOGNITIVE, verifierReply)
            ),
            runtimePolicy = HybridAuthorityRuntimePolicy(
                hybridAuthorityRuntimeEnabled = true
            )
        )
    }

    private fun request(
        kind: ReasoningTaskKind,
        prompt: String
    ): IntrinsicReasoningRequest {
        return IntrinsicReasoningRequest(
            systemPrompt = "system",
            history = listOf(ChatTurn(role = "user", content = prompt)),
            taskComplexity = ReasoningTaskComplexity.DEEP_ANALYSIS,
            taskKind = kind,
            authorityPrompt = prompt
        )
    }

    private fun fixedMotor(
        role: ReasoningMotorRole,
        reply: String
    ): IntrinsicReasoningMotor {
        return object : IntrinsicReasoningMotor {
            override val role: ReasoningMotorRole = role
            override val capabilityVersion: String = "${role.name.lowercase()}-fail-closed-test-v0"

            override suspend fun compute(
                request: IntrinsicReasoningRequest
            ): Result<IntrinsicReasoningResponse> {
                return Result.success(IntrinsicReasoningResponse(reply))
            }
        }
    }
}
