package com.morimil.app.reasoning.intrinsic

import com.morimil.app.ai.ChatTurn
import com.morimil.app.reasoning.IntrinsicReasoningRequest
import com.morimil.app.reasoning.IntrinsicTriMotorCoordinator
import com.morimil.app.reasoning.ReasoningMotorRole
import com.morimil.app.reasoning.model.ReasoningTaskComplexity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliberativeMotorV0Test {
    @Test
    fun recurrentDepthConvergesBeforeMaximumAndDecodesOnlyFinalState() = runBlocking {
        val core = RecordingCore(
            metrics = listOf(
                600 to 500,
                910 to 900
            ),
            reply = "respuesta deliberada"
        )
        val motor = DeliberativeMotorV0(core)

        val response = motor.compute(request(ReasoningTaskComplexity.CODE_REVIEW)).getOrThrow()

        assertEquals(ReasoningMotorRole.DELIBERATIVE, motor.role)
        assertEquals("morimil.deliberative.v0+weights-v0", motor.capabilityVersion)
        assertEquals("respuesta deliberada", response.content)
        assertEquals(listOf(1, 2), core.refinementPasses)
        assertEquals(listOf(2), core.decodedStateIds)
        assertTrue(response.findings.contains("deliberation_passes:2"))
        assertTrue(response.findings.contains("deliberation_stop:converged"))
    }

    @Test
    fun criticalTaskHonorsMinimumDepthEvenWhenFirstPassLooksCertain() = runBlocking {
        val core = RecordingCore(
            metrics = listOf(
                1_000 to 1_000,
                1_000 to 1_000,
                1_000 to 1_000
            )
        )

        val response = DeliberativeMotorV0(core)
            .compute(request(ReasoningTaskComplexity.ARCHITECTURE_CRITICAL))
            .getOrThrow()

        assertEquals(listOf(1, 2, 3), core.refinementPasses)
        assertTrue(response.findings.contains("deliberation_passes:3"))
        assertTrue(response.findings.contains("deliberation_stop:converged"))
    }

    @Test
    fun uncertainResultStopsAtBoundedBudgetAndStillUsesFinalHead() = runBlocking {
        val core = RecordingCore(
            metrics = List(5) { 400 to 400 },
            reply = "mejor respuesta disponible"
        )

        val response = DeliberativeMotorV0(core)
            .compute(request(ReasoningTaskComplexity.DEEP_ANALYSIS))
            .getOrThrow()

        assertEquals(listOf(1, 2, 3, 4, 5), core.refinementPasses)
        assertEquals(listOf(5), core.decodedStateIds)
        assertTrue(response.findings.contains("deliberation_stop:budget_exhausted"))
    }

    @Test
    fun effortPolicyAllocatesMoreDepthOnlyForHarderWork() {
        val light = DeliberativeEffortPolicyV0.budgetFor(ReasoningTaskComplexity.LIGHT_LOCAL)
        val deep = DeliberativeEffortPolicyV0.budgetFor(ReasoningTaskComplexity.DEEP_ANALYSIS)
        val critical = DeliberativeEffortPolicyV0.budgetFor(
            ReasoningTaskComplexity.ARCHITECTURE_CRITICAL
        )

        assertEquals(2, light.maximumPasses)
        assertEquals(5, deep.maximumPasses)
        assertEquals(8, critical.maximumPasses)
        assertTrue(light.maximumPasses < deep.maximumPasses)
        assertTrue(deep.maximumPasses < critical.maximumPasses)
    }

    @Test
    fun coordinatorCanActivateTheExplicitlyRegisteredMotor() = runBlocking {
        val motor = DeliberativeMotorV0(
            RecordingCore(metrics = listOf(950 to 950, 950 to 950))
        )
        val coordinator = IntrinsicTriMotorCoordinator(listOf(motor))

        val result = coordinator.reason(
            request(ReasoningTaskComplexity.CODE_REVIEW)
        ).getOrThrow()

        assertEquals(listOf(ReasoningMotorRole.DELIBERATIVE), result.activatedRoles)
        assertEquals("resultado", result.reply)
        assertTrue(result.unavailableRoles.contains(ReasoningMotorRole.METACOGNITIVE))
    }

    @Test
    fun candidateVerificationIsRejectedBeforeCoreExecution() = runBlocking {
        val core = RecordingCore(metrics = listOf(950 to 950))
        val result = DeliberativeMotorV0(core).compute(
            request(ReasoningTaskComplexity.CODE_REVIEW).copy(candidateReply = "candidate")
        )

        assertTrue(result.isFailure)
        assertFalse(core.initialized)
        assertTrue(core.refinementPasses.isEmpty())
    }

    @Test
    fun coreFailureDoesNotDecodeOrManufactureAReply() = runBlocking {
        val core = RecordingCore(
            metrics = listOf(500 to 500),
            failAtPass = 1
        )

        val result = DeliberativeMotorV0(core)
            .compute(request(ReasoningTaskComplexity.DEEP_ANALYSIS))

        assertTrue(result.isFailure)
        assertTrue(core.decodedStateIds.isEmpty())
        assertEquals(1, core.releaseCount)
    }

    @Test
    fun eachRequestStartsWithFreshEphemeralState() = runBlocking {
        val core = RecordingCore(metrics = listOf(950 to 950, 950 to 950))
        val motor = DeliberativeMotorV0(core)

        val first = motor.compute(request(ReasoningTaskComplexity.CODE_REVIEW)).getOrThrow()
        val second = motor.compute(request(ReasoningTaskComplexity.CODE_REVIEW)).getOrThrow()

        assertEquals(first.content, second.content)
        assertEquals(listOf(1, 2, 1, 2), core.refinementPasses)
        assertEquals(listOf(2, 2), core.decodedStateIds)
        assertEquals(2, core.initializationCount)
    }

    @Test
    fun emptyDecodedReplyIsRejected() = runBlocking {
        val core = RecordingCore(
            metrics = listOf(950 to 950, 950 to 950),
            reply = "   "
        )

        val result = DeliberativeMotorV0(core)
            .compute(request(ReasoningTaskComplexity.CODE_REVIEW))

        assertTrue(result.isFailure)
    }

    @Test
    fun publicRuntimeBoundaryContainsNoApiMemoryOrPersistenceWriter() {
        val forbidden = listOf(
            "Provider",
            "Endpoint",
            "Credential",
            "Http",
            "Socket",
            "Repository",
            "Dao",
            "MemoryUseCase",
            "IdentityWriter",
            "Lifecycle",
            "Installer",
            "Persistence"
        )
        val contractTypes = listOf(
            MorimilDeliberativeCore::class.java,
            DeliberativeCoreInput::class.java,
            DeliberativePassOutcome::class.java,
            DeliberativeMotorV0::class.java
        )
        val exposedTypeNames = contractTypes.flatMap { type ->
            type.methods.flatMap { method ->
                method.parameterTypes.toList() + method.returnType
            } + type.declaredFields.map { field -> field.type }
        }.map { type -> type.name }

        assertTrue(exposedTypeNames.isNotEmpty())
        assertTrue(
            exposedTypeNames.none { name -> forbidden.any { token -> name.contains(token) } }
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidCoreArtifactDigestIsRejected() {
        DeliberativeMotorV0(
            RecordingCore(
                metrics = listOf(950 to 950),
                artifactSha256 = "not-a-sha256"
            )
        )
    }

    private fun request(complexity: ReasoningTaskComplexity): IntrinsicReasoningRequest {
        return IntrinsicReasoningRequest(
            systemPrompt = "Responde con precisión.",
            history = listOf(ChatTurn(role = "user", content = "Analiza esta tarea.")),
            taskComplexity = complexity
        )
    }

    private data class FakeState(val id: Int) : DeliberativeLatentState

    private class RecordingCore(
        private val metrics: List<Pair<Int, Int>>,
        private val reply: String = "resultado",
        private val failAtPass: Int? = null,
        override val artifactSha256: String = "sha256:${"a".repeat(64)}"
    ) : MorimilDeliberativeCore {
        override val artifactVersion: String = "weights-v0"
        var initializationCount: Int = 0
        val initialized: Boolean get() = initializationCount > 0
        val refinementPasses = mutableListOf<Int>()
        val decodedStateIds = mutableListOf<Int>()
        var releaseCount: Int = 0

        override suspend fun initialize(
            input: DeliberativeCoreInput
        ): Result<DeliberativeLatentState> {
            initializationCount += 1
            return Result.success(FakeState(0))
        }

        override suspend fun refine(
            state: DeliberativeLatentState,
            pass: Int
        ): Result<DeliberativePassOutcome> {
            refinementPasses += pass
            if (pass == failAtPass) {
                return Result.failure(IllegalStateException("local core failed"))
            }
            val metric = metrics.getOrElse(pass - 1) { metrics.last() }
            return Result.success(
                DeliberativePassOutcome(
                    state = FakeState(pass),
                    certaintyPermille = metric.first,
                    stabilityPermille = metric.second
                )
            )
        }

        override suspend fun decode(state: DeliberativeLatentState): Result<String> {
            decodedStateIds += (state as FakeState).id
            return Result.success(reply)
        }

        override suspend fun release(state: DeliberativeLatentState) {
            releaseCount += 1
        }
    }
}
