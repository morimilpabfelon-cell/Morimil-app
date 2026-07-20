package com.morimil.app.reasoning

import com.morimil.app.ai.ChatTurn
import com.morimil.app.reasoning.authority.HybridAuthorityRoute
import com.morimil.app.reasoning.authority.HybridAuthorityStatus
import com.morimil.app.reasoning.model.ReasoningTaskComplexity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridAuthorityRuntimeIntegrationV0Test {
    @Test
    fun classifierRoutesOnlyBoundedKnownPatterns() {
        assertEquals(
            ReasoningTaskKind.ARITHMETIC,
            ReasoningTaskKindClassifierV0.classify(
                "Calcula 15 menos 2 por 6 respetando prioridad."
            )
        )
        assertEquals(
            ReasoningTaskKind.RESTRICTED_CODE,
            ReasoningTaskKindClassifierV0.classify(
                "Que imprime Python con print(len('morimil'))?"
            )
        )
        assertEquals(
            ReasoningTaskKind.CLAIM_VERIFICATION,
            ReasoningTaskKindClassifierV0.classify(
                "Una respuesta afirma que 9 por 6 es 42."
            )
        )
        assertEquals(
            ReasoningTaskKind.LOGIC,
            ReasoningTaskKindClassifierV0.classify(
                "Todos los A son B. Todos los B son C."
            )
        )
        assertEquals(
            ReasoningTaskKind.SPANISH,
            ReasoningTaskKindClassifierV0.classify(
                "Ana llego antes que Luis y Luis antes que Marta."
            )
        )
        assertEquals(
            ReasoningTaskKind.INSTRUCTION,
            ReasoningTaskKindClassifierV0.classify(
                "Devuelve exactamente FINAL:AZUL y nada mas."
            )
        )
        assertEquals(
            ReasoningTaskKind.UNKNOWN,
            ReasoningTaskKindClassifierV0.classify("Explica este tema abierto.")
        )
    }

    @Test
    fun runtimeFlagIsDisabledByDefault() {
        assertFalse(HybridAuthorityRuntimePolicy().hybridAuthorityRuntimeEnabled)
    }

    @Test
    fun disabledFlagPreservesLegacyVerifierReplacement() = runBlocking {
        var candidateSeenByVerifier: String? = null
        val coordinator = IntrinsicTriMotorCoordinator(
            motors = listOf(
                fakeMotor(ReasoningMotorRole.DELIBERATIVE, "primary draft"),
                capturingVerifier("verified reply") { candidate ->
                    candidateSeenByVerifier = candidate
                }
            )
        )

        val result = coordinator.reason(
            request(
                prompt = "architecture prompt",
                taskKind = ReasoningTaskKind.UNKNOWN
            )
        ).getOrThrow()

        assertEquals("verified reply", result.reply)
        assertEquals("primary draft", result.primaryCandidate)
        assertEquals("verified reply", result.verifierCandidate)
        assertEquals("primary draft", candidateSeenByVerifier)
        assertNull(result.authorityDecision)
        assertEquals(
            TriMotorFinalizationStatus.LEGACY_UNROUTED,
            result.finalizationStatus
        )
    }

    @Test
    fun enabledFlagOverridesObservedFalseArithmeticConsensus() = runBlocking {
        var candidateSeenByVerifier: String? = "not-called"
        val coordinator = IntrinsicTriMotorCoordinator(
            motors = listOf(
                fakeMotor(ReasoningMotorRole.DELIBERATIVE, "FINAL:13"),
                capturingVerifier("FINAL:13") { candidate ->
                    candidateSeenByVerifier = candidate
                }
            ),
            runtimePolicy = HybridAuthorityRuntimePolicy(
                hybridAuthorityRuntimeEnabled = true
            )
        )

        val result = coordinator.reason(
            request(
                prompt = "Calcula 15 menos 2 por 6 respetando prioridad.",
                taskKind = ReasoningTaskKind.ARITHMETIC
            )
        ).getOrThrow()

        assertEquals("FINAL:3", result.reply)
        assertEquals("FINAL:13", result.primaryCandidate)
        assertEquals("FINAL:13", result.verifierCandidate)
        assertNull(candidateSeenByVerifier)
        assertEquals(
            HybridAuthorityRoute.DETERMINISTIC_ARITHMETIC,
            result.authorityDecision?.route
        )
        assertEquals(
            HybridAuthorityStatus.ACCEPTED_DETERMINISTIC,
            result.authorityDecision?.status
        )
        assertEquals(
            TriMotorFinalizationStatus.ACCEPTED_BY_AUTHORITY,
            result.finalizationStatus
        )
    }

    @Test
    fun enabledFlagAbstainsWhenSpanishVerifierBreaksStrictFormat() = runBlocking {
        val coordinator = IntrinsicTriMotorCoordinator(
            motors = listOf(
                fakeMotor(ReasoningMotorRole.DELIBERATIVE, "FINAL:C"),
                capturingVerifier(
                    "Ana llego primero, Luis segundo y Marta tercero. FINAL:A"
                ) { }
            ),
            runtimePolicy = HybridAuthorityRuntimePolicy(
                hybridAuthorityRuntimeEnabled = true
            )
        )

        val result = coordinator.reason(
            request(
                prompt = "Ana llego antes que Luis y Luis antes que Marta.",
                taskKind = ReasoningTaskKind.SPANISH
            )
        ).getOrThrow()

        assertEquals("", result.reply)
        assertEquals("FINAL:C", result.primaryCandidate)
        assertEquals(
            "Ana llego primero, Luis segundo y Marta tercero. FINAL:A",
            result.verifierCandidate
        )
        assertFalse(requireNotNull(result.authorityDecision).accepted)
        assertEquals(
            HybridAuthorityRoute.STRICT_GENERATIVE_CONSENSUS,
            result.authorityDecision?.route
        )
        assertEquals(
            TriMotorFinalizationStatus.ABSTAINED_BY_AUTHORITY,
            result.finalizationStatus
        )
    }

    @Test
    fun enabledFlagAbstainsWhenPromptIsMissing() = runBlocking {
        val coordinator = IntrinsicTriMotorCoordinator(
            motors = listOf(
                fakeMotor(ReasoningMotorRole.DELIBERATIVE, "FINAL:SI"),
                capturingVerifier("FINAL:SI") { }
            ),
            runtimePolicy = HybridAuthorityRuntimePolicy(
                hybridAuthorityRuntimeEnabled = true
            )
        )

        val result = coordinator.reason(
            IntrinsicReasoningRequest(
                systemPrompt = "system",
                history = emptyList(),
                taskComplexity = ReasoningTaskComplexity.DEEP_ANALYSIS,
                taskKind = ReasoningTaskKind.LOGIC
            )
        ).getOrThrow()

        assertEquals("", result.reply)
        assertEquals(
            HybridAuthorityRoute.UNSUPPORTED,
            result.authorityDecision?.route
        )
        assertTrue(result.findings.contains("hybrid_authority_prompt_missing"))
    }

    @Test
    fun runtimeIntegrationExposesNoApiMemoryIdentityOrPersistenceCapability() {
        val forbidden = listOf(
            "ReasoningProviderConfig",
            "TemporaryExternalReasoningProvider",
            "Repository",
            "Dao",
            "MemoryUseCase",
            "RuntimeAccess",
            "Endpoint",
            "IdentityWriter",
            "Lifecycle",
            "Installer",
            "Downloader"
        )
        val exposedTypeNames = listOf(
            IntrinsicTriMotorCoordinator::class.java,
            IntrinsicReasoningRequest::class.java,
            TriMotorReasoningResult::class.java,
            HybridAuthorityRuntimePolicy::class.java
        ).flatMap { type ->
            type.declaredMethods.flatMap { method ->
                method.parameterTypes.toList() + method.returnType
            } + type.declaredFields.map { field -> field.type }
        }.map { type -> type.name }

        assertTrue(exposedTypeNames.isNotEmpty())
        assertTrue(
            exposedTypeNames.none { name ->
                forbidden.any { token -> name.contains(token) }
            }
        )
    }

    private fun request(
        prompt: String,
        taskKind: ReasoningTaskKind
    ): IntrinsicReasoningRequest {
        return IntrinsicReasoningRequest(
            systemPrompt = "system",
            history = listOf(ChatTurn(role = "user", content = prompt)),
            taskComplexity = ReasoningTaskComplexity.ARCHITECTURE_CRITICAL,
            taskKind = taskKind,
            authorityPrompt = prompt
        )
    }

    private fun fakeMotor(
        role: ReasoningMotorRole,
        reply: String
    ): IntrinsicReasoningMotor {
        return object : IntrinsicReasoningMotor {
            override val role: ReasoningMotorRole = role
            override val capabilityVersion: String =
                "${role.name.lowercase()}-test-v1"

            override suspend fun compute(
                request: IntrinsicReasoningRequest
            ): Result<IntrinsicReasoningResponse> {
                return Result.success(IntrinsicReasoningResponse(reply))
            }
        }
    }

    private fun capturingVerifier(
        reply: String,
        onCandidate: (String?) -> Unit
    ): IntrinsicReasoningMotor {
        return object : IntrinsicReasoningMotor {
            override val role = ReasoningMotorRole.METACOGNITIVE
            override val capabilityVersion = "metacognitive-test-v1"

            override suspend fun compute(
                request: IntrinsicReasoningRequest
            ): Result<IntrinsicReasoningResponse> {
                onCandidate(request.candidateReply)
                return Result.success(IntrinsicReasoningResponse(reply))
            }
        }
    }
}
