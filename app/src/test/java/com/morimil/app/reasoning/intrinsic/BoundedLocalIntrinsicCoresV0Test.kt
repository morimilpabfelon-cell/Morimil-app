package com.morimil.app.reasoning.intrinsic

import com.morimil.app.ai.ChatTurn
import com.morimil.app.reasoning.IntrinsicReasoningRequest
import com.morimil.app.reasoning.ReasoningMotorRole
import com.morimil.app.reasoning.ReasoningTaskKind
import com.morimil.app.reasoning.TriMotorFinalizationStatus
import com.morimil.app.reasoning.authority.HybridAuthorityRoute
import com.morimil.app.reasoning.authority.HybridAuthorityStatus
import com.morimil.app.reasoning.model.ReasoningTaskComplexity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedLocalIntrinsicCoresV0Test {
    @Test
    fun intuitiveCoreComputesExactArithmeticLocally() = runBlocking {
        val output = BoundedLocalIntuitiveCoreV0().compute(
            intuitiveInput(
                taskKind = ReasoningTaskKind.ARITHMETIC,
                prompt = "Calcula 15 menos 2 por 6 respetando prioridad."
            )
        ).getOrThrow()

        assertEquals("FINAL:3", output.content)
        assertTrue(output.findings.contains("deterministic_arithmetic_subtract_multiply"))
        assertTrue(output.findings.contains("intrinsic_core:intuitive_bounded_local"))
        assertTrue(output.findings.contains("request_state:stateless"))
    }

    @Test
    fun intuitiveCoreComputesRestrictedCodeWithoutExecutingCode() = runBlocking {
        val output = BoundedLocalIntuitiveCoreV0().compute(
            intuitiveInput(
                taskKind = ReasoningTaskKind.RESTRICTED_CODE,
                prompt = "Que imprime Python con print(sum([1, 2, 3]))?"
            )
        ).getOrThrow()

        assertEquals("FINAL:6", output.content)
        assertTrue(output.findings.contains("restricted_code_sum"))
    }

    @Test
    fun metacognitiveCoreRecomputesCheckableClaimFromOriginalPrompt() = runBlocking {
        val output = BoundedLocalMetacognitiveCoreV0().compute(
            MetacognitiveCoreInputV0(
                systemPrompt = "Verifica localmente.",
                history = emptyList(),
                taskComplexity = ReasoningTaskComplexity.DEEP_ANALYSIS,
                taskKind = ReasoningTaskKind.CLAIM_VERIFICATION,
                authorityPrompt = "Una respuesta afirma que 9 por 6 es 42."
            )
        ).getOrThrow()

        assertEquals("FINAL:54", output.content)
        assertTrue(output.findings.contains("deterministic_claim_multiplication"))
        assertTrue(output.findings.contains("intrinsic_core:metacognitive_bounded_local"))
        assertTrue(
            output.findings.contains("verification_mode:blind_deterministic_recomputation")
        )
    }

    @Test
    fun generativeTaskFailsClosedInsteadOfInventingConsensus() = runBlocking {
        val result = BoundedLocalMetacognitiveCoreV0().compute(
            MetacognitiveCoreInputV0(
                systemPrompt = "Verifica localmente.",
                history = emptyList(),
                taskComplexity = ReasoningTaskComplexity.DEEP_ANALYSIS,
                taskKind = ReasoningTaskKind.LOGIC,
                authorityPrompt = "Todos los A son B. Todos los B son C."
            )
        )

        assertTrue(result.isFailure)
        assertEquals(
            "bounded_local_task_kind_unsupported:logic",
            result.exceptionOrNull()?.message
        )
    }

    @Test
    fun malformedBoundedPromptFailsClosed() = runBlocking {
        val result = BoundedLocalIntuitiveCoreV0().compute(
            intuitiveInput(
                taskKind = ReasoningTaskKind.ARITHMETIC,
                prompt = "Calcula algo complicado sin numeros."
            )
        )

        assertTrue(result.isFailure)
        assertEquals(
            "bounded_local_authority_abstained:deterministic_arithmetic_prompt_unsupported",
            result.exceptionOrNull()?.message
        )
    }

    @Test
    fun factoryRunsRealBoundedVerifierAboveWrongDeliberativeCandidate() = runBlocking {
        val deliberativeCore = WrongDeliberativeCore()
        val runtime = BoundedLocalTriMotorResearchRuntimeFactoryV0.create(
            DeliberativeMotorV0(deliberativeCore)
        )

        val result = runtime.reason(
            IntrinsicReasoningRequest(
                systemPrompt = "Razona localmente.",
                history = listOf(
                    ChatTurn(
                        role = "user",
                        content = "Calcula 15 menos 2 por 6 respetando prioridad."
                    )
                ),
                taskComplexity = ReasoningTaskComplexity.DEEP_ANALYSIS,
                taskKind = ReasoningTaskKind.ARITHMETIC,
                authorityPrompt = "Calcula 15 menos 2 por 6 respetando prioridad."
            )
        ).getOrThrow()

        assertEquals("FINAL:3", result.reply)
        assertEquals("FINAL:13", result.primaryCandidate)
        assertEquals("FINAL:3", result.verifierCandidate)
        assertEquals(
            listOf(ReasoningMotorRole.DELIBERATIVE, ReasoningMotorRole.METACOGNITIVE),
            result.activatedRoles
        )
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
        assertTrue(deliberativeCore.released)
    }

    @Test
    fun realBoundedCoreSurfaceExposesNoExternalOrPersistenceCapability() {
        val forbidden = listOf(
            "Provider",
            "Endpoint",
            "Credential",
            "Http",
            "Socket",
            "URL",
            "Repository",
            "Dao",
            "MemoryUseCase",
            "IdentityWriter",
            "Lifecycle",
            "Installer",
            "Downloader"
        )
        val exposedNames = listOf(
            BoundedLocalIntuitiveCoreV0::class.java,
            BoundedLocalMetacognitiveCoreV0::class.java,
            BoundedLocalTriMotorResearchRuntimeFactoryV0::class.java
        ).flatMap { type ->
            type.methods.flatMap { method ->
                method.parameterTypes.toList() + method.returnType
            } + type.declaredFields.map { field -> field.type }
        }.map { type -> type.name }

        assertTrue(exposedNames.isNotEmpty())
        assertTrue(
            exposedNames.none { name -> forbidden.any { token -> name.contains(token) } }
        )
        assertFalse(
            MetacognitiveCoreInputV0::class.java.declaredFields.any { field ->
                field.name.contains("candidate", ignoreCase = true)
            }
        )
    }

    private fun intuitiveInput(
        taskKind: ReasoningTaskKind,
        prompt: String
    ): IntuitiveCoreInputV0 {
        return IntuitiveCoreInputV0(
            systemPrompt = "Resuelve localmente.",
            history = listOf(ChatTurn(role = "user", content = prompt)),
            taskComplexity = ReasoningTaskComplexity.LIGHT_LOCAL,
            taskKind = taskKind,
            authorityPrompt = prompt
        )
    }

    private class WrongDeliberativeCore : MorimilDeliberativeCore {
        override val artifactVersion: String = "morimil-deliberative-v0.2"
        override val artifactSha256: String = "sha256:${"a".repeat(64)}"
        var released: Boolean = false

        override suspend fun initialize(
            input: DeliberativeCoreInput
        ): Result<DeliberativeLatentState> {
            released = false
            return Result.success(State)
        }

        override suspend fun refine(
            state: DeliberativeLatentState,
            pass: Int
        ): Result<DeliberativePassOutcome> {
            return Result.success(
                DeliberativePassOutcome(
                    state = state,
                    certaintyPermille = 1_000,
                    stabilityPermille = 1_000
                )
            )
        }

        override suspend fun decode(
            state: DeliberativeLatentState
        ): Result<String> = Result.success("FINAL:13")

        override suspend fun release(state: DeliberativeLatentState) {
            released = true
        }

        private object State : DeliberativeLatentState
    }
}
