package com.morimil.app.reasoning

import com.morimil.app.ai.ReasoningProviderConfig
import com.morimil.app.reasoning.model.ReasoningTaskComplexity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TriMotorReasoningCoordinatorTest {
    @Test
    fun emptyIntrinsicRegistryDoesNotInventAnApiBackedMotor() = runBlocking {
        val coordinator = IntrinsicTriMotorCoordinator()

        val result = coordinator.reason(request(ReasoningTaskComplexity.LIGHT_LOCAL))

        assertTrue(coordinator.availableRoles().isEmpty())
        assertTrue(result.isFailure)
    }

    @Test
    fun lightTaskActivatesOnlyIntrinsicIntuitiveMotor() = runBlocking {
        val calls = mutableListOf<ReasoningMotorRole>()
        val coordinator = IntrinsicTriMotorCoordinator(
            motors = allMotors(calls)
        )

        val result = coordinator.reason(
            request(ReasoningTaskComplexity.LIGHT_LOCAL)
        ).getOrThrow()

        assertEquals("intuitive reply", result.reply)
        assertEquals(listOf(ReasoningMotorRole.INTUITIVE), calls)
        assertEquals(listOf(ReasoningMotorRole.INTUITIVE), result.activatedRoles)
        assertEquals("intuitive-v1", result.activatedVersions[ReasoningMotorRole.INTUITIVE])
        assertFalse(result.requestedRoles.contains(ReasoningMotorRole.METACOGNITIVE))
    }

    @Test
    fun criticalTaskUsesDeliberativeThenMetacognitiveMotor() = runBlocking {
        val calls = mutableListOf<ReasoningMotorRole>()
        var candidateSeenByVerifier: String? = null
        val coordinator = IntrinsicTriMotorCoordinator(
            motors = listOf(
                fakeMotor(ReasoningMotorRole.INTUITIVE, calls, "intuitive reply"),
                fakeMotor(ReasoningMotorRole.DELIBERATIVE, calls, "deliberative draft"),
                object : IntrinsicReasoningMotor {
                    override val role = ReasoningMotorRole.METACOGNITIVE
                    override val capabilityVersion = "metacognitive-v1"

                    override suspend fun compute(
                        request: IntrinsicReasoningRequest
                    ): Result<IntrinsicReasoningResponse> {
                        calls += role
                        candidateSeenByVerifier = request.candidateReply
                        return Result.success(
                            IntrinsicReasoningResponse(
                                content = "verified reply",
                                findings = listOf("claim_checked")
                            )
                        )
                    }
                }
            )
        )

        val result = coordinator.reason(
            request(ReasoningTaskComplexity.ARCHITECTURE_CRITICAL)
        ).getOrThrow()

        assertEquals("deliberative draft", candidateSeenByVerifier)
        assertEquals("verified reply", result.reply)
        assertEquals(
            listOf(ReasoningMotorRole.DELIBERATIVE, ReasoningMotorRole.METACOGNITIVE),
            calls
        )
        assertEquals(listOf("claim_checked"), result.findings)
    }

    @Test
    fun missingDeepMotorsDegradesWithinIntrinsicRegistry() = runBlocking {
        val calls = mutableListOf<ReasoningMotorRole>()
        val coordinator = IntrinsicTriMotorCoordinator(
            motors = listOf(
                fakeMotor(ReasoningMotorRole.INTUITIVE, calls, "safe intrinsic fallback")
            )
        )

        val result = coordinator.reason(
            request(ReasoningTaskComplexity.DEEP_ANALYSIS)
        ).getOrThrow()

        assertEquals("safe intrinsic fallback", result.reply)
        assertEquals(listOf(ReasoningMotorRole.INTUITIVE), result.activatedRoles)
        assertTrue(result.unavailableRoles.contains(ReasoningMotorRole.DELIBERATIVE))
        assertTrue(result.unavailableRoles.contains(ReasoningMotorRole.METACOGNITIVE))
    }

    @Test
    fun failedPreferredMotorFallsBackWithoutLosingReply() = runBlocking {
        val calls = mutableListOf<ReasoningMotorRole>()
        val coordinator = IntrinsicTriMotorCoordinator(
            motors = listOf(
                fakeMotor(ReasoningMotorRole.DELIBERATIVE, calls, failure = true),
                fakeMotor(ReasoningMotorRole.INTUITIVE, calls, "fallback reply")
            )
        )

        val result = coordinator.reason(
            request(ReasoningTaskComplexity.CODE_REVIEW)
        ).getOrThrow()

        assertEquals("fallback reply", result.reply)
        assertTrue(result.failedRoles.contains(ReasoningMotorRole.DELIBERATIVE))
        assertEquals(
            listOf(ReasoningMotorRole.DELIBERATIVE, ReasoningMotorRole.INTUITIVE),
            calls
        )
    }

    @Test
    fun intrinsicContractContainsNoApiOrPersistenceCapability() {
        val forbidden = listOf(
            ReasoningProviderConfig::class.java.name,
            TemporaryExternalReasoningProvider::class.java.name,
            "Repository",
            "Dao",
            "MemoryUseCase",
            "RuntimeAccess",
            "Endpoint",
            "IdentityWriter",
            "Lifecycle"
        )
        val exposedTypeNames = IntrinsicReasoningMotor::class.java.methods
            .flatMap { method -> method.parameterTypes.toList() + method.returnType }
            .map { type -> type.name }

        assertTrue(exposedTypeNames.isNotEmpty())
        assertTrue(
            exposedTypeNames.none { name -> forbidden.any { token -> name.contains(token) } }
        )
        assertTrue(
            IntrinsicReasoningRequest::class.java.declaredFields.none { field ->
                forbidden.any { token -> field.type.name.contains(token) }
            }
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateMotorRolesAreRejected() {
        val calls = mutableListOf<ReasoningMotorRole>()
        IntrinsicTriMotorCoordinator(
            motors = listOf(
                fakeMotor(ReasoningMotorRole.INTUITIVE, calls, "first"),
                fakeMotor(ReasoningMotorRole.INTUITIVE, calls, "duplicate")
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun intrinsicMotorWithoutCapabilityVersionIsRejected() {
        IntrinsicTriMotorCoordinator(
            motors = listOf(
                object : IntrinsicReasoningMotor {
                    override val role = ReasoningMotorRole.INTUITIVE
                    override val capabilityVersion = ""

                    override suspend fun compute(
                        request: IntrinsicReasoningRequest
                    ): Result<IntrinsicReasoningResponse> {
                        return Result.success(IntrinsicReasoningResponse("reply"))
                    }
                }
            )
        )
    }

    private fun allMotors(calls: MutableList<ReasoningMotorRole>): List<IntrinsicReasoningMotor> {
        return listOf(
            fakeMotor(ReasoningMotorRole.INTUITIVE, calls, "intuitive reply"),
            fakeMotor(ReasoningMotorRole.DELIBERATIVE, calls, "deliberative reply"),
            fakeMotor(ReasoningMotorRole.METACOGNITIVE, calls, "verified reply")
        )
    }

    private fun fakeMotor(
        motorRole: ReasoningMotorRole,
        calls: MutableList<ReasoningMotorRole>,
        reply: String = "",
        failure: Boolean = false
    ): IntrinsicReasoningMotor {
        return object : IntrinsicReasoningMotor {
            override val role = motorRole
            override val capabilityVersion = "${motorRole.name.lowercase()}-v1"

            override suspend fun compute(
                request: IntrinsicReasoningRequest
            ): Result<IntrinsicReasoningResponse> {
                calls += role
                return if (failure) {
                    Result.failure(IllegalStateException("motor unavailable"))
                } else {
                    Result.success(IntrinsicReasoningResponse(content = reply))
                }
            }
        }
    }

    private fun request(complexity: ReasoningTaskComplexity): IntrinsicReasoningRequest {
        return IntrinsicReasoningRequest(
            systemPrompt = "system",
            history = emptyList(),
            taskComplexity = complexity
        )
    }
}
