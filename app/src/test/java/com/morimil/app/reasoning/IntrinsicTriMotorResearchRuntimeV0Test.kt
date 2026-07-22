package com.morimil.app.reasoning

import com.morimil.app.ai.ChatTurn
import com.morimil.app.reasoning.authority.HybridAuthorityRoute
import com.morimil.app.reasoning.authority.HybridAuthorityStatus
import com.morimil.app.reasoning.intrinsic.DeliberativeCoreInput
import com.morimil.app.reasoning.intrinsic.DeliberativeLatentState
import com.morimil.app.reasoning.intrinsic.DeliberativeMotorV0
import com.morimil.app.reasoning.intrinsic.DeliberativePassOutcome
import com.morimil.app.reasoning.intrinsic.IntuitiveCoreInputV0
import com.morimil.app.reasoning.intrinsic.IntuitiveMotorV0
import com.morimil.app.reasoning.intrinsic.MetacognitiveCoreInputV0
import com.morimil.app.reasoning.intrinsic.MetacognitiveMotorV0
import com.morimil.app.reasoning.intrinsic.MorimilDeliberativeCore
import com.morimil.app.reasoning.intrinsic.MorimilIntuitiveCoreV0
import com.morimil.app.reasoning.intrinsic.MorimilMetacognitiveCoreV0
import com.morimil.app.reasoning.intrinsic.RequestScopedIntrinsicCoreOutputV0
import com.morimil.app.reasoning.model.ReasoningTaskComplexity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntrinsicTriMotorResearchRuntimeV0Test {
    @Test
    fun researchRuntimeRegistersExactlyThreeIntrinsicRoles() {
        val fixture = fixture()

        assertEquals(
            ReasoningMotorRole.entries.toSet(),
            fixture.runtime.availableRoles()
        )
        assertEquals(
            "morimil.intrinsic-trimotor.research-runtime.v0",
            fixture.runtime.runtimeVersion
        )
        assertFalse(HybridAuthorityRuntimePolicy().hybridAuthorityRuntimeEnabled)
    }

    @Test
    fun lightArithmeticUsesIntuitiveMotorButFinalAuthorityIsDeterministic() = runBlocking {
        val fixture = fixture(
            intuitiveReply = "FINAL:13",
            deliberativeReply = "FINAL:13",
            metacognitiveReply = "FINAL:13"
        )

        val result = fixture.runtime.reason(
            request(
                prompt = "Calcula 15 menos 2 por 6 respetando prioridad.",
                taskKind = ReasoningTaskKind.ARITHMETIC,
                complexity = ReasoningTaskComplexity.LIGHT_LOCAL
            )
        ).getOrThrow()

        assertEquals("FINAL:3", result.reply)
        assertEquals(listOf(ReasoningMotorRole.INTUITIVE), result.activatedRoles)
        assertEquals("FINAL:13", result.primaryCandidate)
        assertNull(result.verifierCandidate)
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
    fun deepLogicUsesDeliberativeAndBlindMetacognitiveConsensus() = runBlocking {
        val fixture = fixture(
            deliberativeReply = "FINAL:SI",
            metacognitiveReply = "FINAL:SI"
        )

        val result = fixture.runtime.reason(
            request(
                prompt = "Todos los A son B. Todos los B son C. Devuelve FINAL:SI si se sigue.",
                taskKind = ReasoningTaskKind.LOGIC,
                complexity = ReasoningTaskComplexity.DEEP_ANALYSIS
            )
        ).getOrThrow()

        assertEquals("FINAL:SI", result.reply)
        assertEquals(
            listOf(
                ReasoningMotorRole.DELIBERATIVE,
                ReasoningMotorRole.METACOGNITIVE
            ),
            result.activatedRoles
        )
        assertEquals("FINAL:SI", result.primaryCandidate)
        assertEquals("FINAL:SI", result.verifierCandidate)
        assertEquals(
            HybridAuthorityRoute.STRICT_GENERATIVE_CONSENSUS,
            result.authorityDecision?.route
        )
        assertEquals(
            HybridAuthorityStatus.ACCEPTED_STRICT_CONSENSUS,
            result.authorityDecision?.status
        )
        assertEquals(
            "Todos los A son B. Todos los B son C. Devuelve FINAL:SI si se sigue.",
            fixture.metacognitiveCore.lastInput?.authorityPrompt
        )
        assertTrue(result.findings.contains("verification_mode:blind"))
        assertTrue(fixture.deliberativeCore.released)
    }

    @Test
    fun deepGenerativeDisagreementAbstainsWithoutExposingCandidate() = runBlocking {
        val fixture = fixture(
            deliberativeReply = "FINAL:SI",
            metacognitiveReply = "FINAL:NO"
        )

        val result = fixture.runtime.reason(
            request(
                prompt = "Todos los A son B. Todos los B son C.",
                taskKind = ReasoningTaskKind.LOGIC,
                complexity = ReasoningTaskComplexity.ARCHITECTURE_CRITICAL
            )
        ).getOrThrow()

        assertEquals("", result.reply)
        assertFalse(requireNotNull(result.authorityDecision).accepted)
        assertEquals(
            TriMotorFinalizationStatus.ABSTAINED_BY_AUTHORITY,
            result.finalizationStatus
        )
    }

    @Test
    fun metacognitiveMotorRejectsCandidateAwareVerification() = runBlocking {
        val motor = MetacognitiveMotorV0(FakeMetacognitiveCore("FINAL:SI"))

        val result = motor.compute(
            request(
                prompt = "Todos los A son B.",
                taskKind = ReasoningTaskKind.LOGIC,
                complexity = ReasoningTaskComplexity.DEEP_ANALYSIS
            ).copy(candidateReply = "FINAL:SI")
        )

        assertTrue(result.isFailure)
        assertEquals(
            "metacognitive_motor_requires_blind_request",
            result.exceptionOrNull()?.message
        )
    }

    @Test
    fun intuitiveMotorRejectsCandidateVerificationRole() = runBlocking {
        val motor = IntuitiveMotorV0(FakeIntuitiveCore("FINAL:1"))

        val result = motor.compute(
            request(
                prompt = "Calcula 1 por 1.",
                taskKind = ReasoningTaskKind.ARITHMETIC,
                complexity = ReasoningTaskComplexity.LIGHT_LOCAL
            ).copy(candidateReply = "FINAL:1")
        )

        assertTrue(result.isFailure)
        assertEquals(
            "intuitive_motor_cannot_verify_candidate",
            result.exceptionOrNull()?.message
        )
    }

    @Test
    fun researchRuntimeSurfaceExposesNoProviderMemoryIdentityOrLifecycleCapability() {
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
        val exposed = listOf(
            IntrinsicTriMotorResearchRuntimeV0::class.java,
            IntuitiveCoreInputV0::class.java,
            MetacognitiveCoreInputV0::class.java,
            MorimilIntuitiveCoreV0::class.java,
            MorimilMetacognitiveCoreV0::class.java
        ).flatMap { type ->
            type.declaredMethods.flatMap { method ->
                method.parameterTypes.toList() + method.returnType
            } + type.declaredFields.map { field -> field.type }
        }.map { type -> type.name }

        assertTrue(exposed.isNotEmpty())
        assertTrue(
            exposed.none { name -> forbidden.any { token -> name.contains(token) } }
        )
    }

    private fun fixture(
        intuitiveReply: String = "FINAL:SI",
        deliberativeReply: String = "FINAL:SI",
        metacognitiveReply: String = "FINAL:SI"
    ): Fixture {
        val intuitiveCore = FakeIntuitiveCore(intuitiveReply)
        val deliberativeCore = FakeDeliberativeCore(deliberativeReply)
        val metacognitiveCore = FakeMetacognitiveCore(metacognitiveReply)

        return Fixture(
            runtime = IntrinsicTriMotorResearchRuntimeV0.create(
                intuitiveMotor = IntuitiveMotorV0(intuitiveCore),
                deliberativeMotor = DeliberativeMotorV0(deliberativeCore),
                metacognitiveMotor = MetacognitiveMotorV0(metacognitiveCore)
            ),
            deliberativeCore = deliberativeCore,
            metacognitiveCore = metacognitiveCore
        )
    }

    private fun request(
        prompt: String,
        taskKind: ReasoningTaskKind,
        complexity: ReasoningTaskComplexity
    ): IntrinsicReasoningRequest {
        return IntrinsicReasoningRequest(
            systemPrompt = "system",
            history = listOf(ChatTurn(role = "user", content = prompt)),
            taskComplexity = complexity,
            taskKind = taskKind,
            authorityPrompt = prompt
        )
    }

    private data class Fixture(
        val runtime: IntrinsicTriMotorResearchRuntimeV0,
        val deliberativeCore: FakeDeliberativeCore,
        val metacognitiveCore: FakeMetacognitiveCore
    )

    private class FakeIntuitiveCore(
        private val reply: String
    ) : MorimilIntuitiveCoreV0 {
        override val coreVersion: String = "intuitive-core-test-v0"

        override suspend fun compute(
            input: IntuitiveCoreInputV0
        ): Result<RequestScopedIntrinsicCoreOutputV0> {
            return Result.success(
                RequestScopedIntrinsicCoreOutputV0(
                    content = reply,
                    findings = listOf("intuitive_core:test")
                )
            )
        }
    }

    private class FakeMetacognitiveCore(
        private val reply: String
    ) : MorimilMetacognitiveCoreV0 {
        override val coreVersion: String = "metacognitive-core-test-v0"
        var lastInput: MetacognitiveCoreInputV0? = null

        override suspend fun compute(
            input: MetacognitiveCoreInputV0
        ): Result<RequestScopedIntrinsicCoreOutputV0> {
            lastInput = input
            return Result.success(
                RequestScopedIntrinsicCoreOutputV0(
                    content = reply,
                    findings = listOf("metacognitive_core:test")
                )
            )
        }
    }

    private class FakeDeliberativeCore(
        private val reply: String
    ) : MorimilDeliberativeCore {
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
        ): Result<String> = Result.success(reply)

        override suspend fun release(state: DeliberativeLatentState) {
            released = true
        }

        private object State : DeliberativeLatentState
    }
}
