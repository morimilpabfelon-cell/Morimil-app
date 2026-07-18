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
    fun lightTaskActivatesOnlyIntuitiveMotor() = runBlocking {
        val calls = mutableListOf<ReasoningMotorRole>()
        val coordinator = TriMotorReasoningCoordinator(
            motors = allMotors(calls)
        )

        val result = coordinator.reason(
            complexity = ReasoningTaskComplexity.LIGHT_LOCAL,
            request = request()
        ).getOrThrow()

        assertEquals("intuitive reply", result.reply)
        assertEquals(listOf(ReasoningMotorRole.INTUITIVE), calls)
        assertEquals(listOf(ReasoningMotorRole.INTUITIVE), result.activatedRoles)
        assertEquals(
            ReasoningMotorBinding.MORIMIL_INTRINSIC,
            result.activatedBindings.single().binding
        )
        assertFalse(result.requestedRoles.contains(ReasoningMotorRole.METACOGNITIVE))
    }

    @Test
    fun criticalTaskUsesDeliberativeThenMetacognitiveMotor() = runBlocking {
        val calls = mutableListOf<ReasoningMotorRole>()
        var candidateSeenByVerifier: String? = null
        val coordinator = TriMotorReasoningCoordinator(
            motors = listOf(
                fakeMotor(ReasoningMotorRole.INTUITIVE, calls, "intuitive reply"),
                fakeMotor(ReasoningMotorRole.DELIBERATIVE, calls, "deliberative draft"),
                object : SpecializedReasoningMotor {
                    override val role = ReasoningMotorRole.METACOGNITIVE
                    override val binding = ReasoningMotorBinding.MORIMIL_INTRINSIC

                    override suspend fun compute(
                        request: SpecializedReasoningRequest
                    ): Result<SpecializedReasoningResponse> {
                        calls += role
                        candidateSeenByVerifier = request.candidateReply
                        return Result.success(
                            SpecializedReasoningResponse(
                                content = "verified reply",
                                findings = listOf("claim_checked")
                            )
                        )
                    }
                }
            )
        )

        val result = coordinator.reason(
            complexity = ReasoningTaskComplexity.ARCHITECTURE_CRITICAL,
            request = request()
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
    fun missingDeepMotorsDegradesToAvailableIntuitiveMotor() = runBlocking {
        val calls = mutableListOf<ReasoningMotorRole>()
        val coordinator = TriMotorReasoningCoordinator(
            motors = listOf(
                fakeMotor(ReasoningMotorRole.INTUITIVE, calls, "safe local fallback")
            )
        )

        val result = coordinator.reason(
            complexity = ReasoningTaskComplexity.DEEP_ANALYSIS,
            request = request()
        ).getOrThrow()

        assertEquals("safe local fallback", result.reply)
        assertEquals(listOf(ReasoningMotorRole.INTUITIVE), result.activatedRoles)
        assertTrue(result.unavailableRoles.contains(ReasoningMotorRole.DELIBERATIVE))
        assertTrue(result.unavailableRoles.contains(ReasoningMotorRole.METACOGNITIVE))
    }

    @Test
    fun failedPreferredMotorFallsBackWithoutLosingReply() = runBlocking {
        val calls = mutableListOf<ReasoningMotorRole>()
        val coordinator = TriMotorReasoningCoordinator(
            motors = listOf(
                fakeMotor(ReasoningMotorRole.DELIBERATIVE, calls, failure = true),
                fakeMotor(ReasoningMotorRole.INTUITIVE, calls, "fallback reply")
            )
        )

        val result = coordinator.reason(
            complexity = ReasoningTaskComplexity.CODE_REVIEW,
            request = request()
        ).getOrThrow()

        assertEquals("fallback reply", result.reply)
        assertTrue(result.failedRoles.contains(ReasoningMotorRole.DELIBERATIVE))
        assertEquals(
            listOf(ReasoningMotorRole.DELIBERATIVE, ReasoningMotorRole.INTUITIVE),
            calls
        )
    }

    @Test
    fun motorContractsExposeNoPersistenceCapability() {
        val forbidden = listOf("Repository", "Dao", "MemoryUseCase", "IdentityWriter", "Lifecycle")
        val parameterNames = SpecializedReasoningMotor::class.java.methods
            .flatMap { method -> method.parameterTypes.toList() }
            .map { type -> type.name }

        assertTrue(parameterNames.isNotEmpty())
        assertTrue(
            parameterNames.none { name -> forbidden.any { token -> name.contains(token) } }
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateMotorRolesAreRejected() {
        val calls = mutableListOf<ReasoningMotorRole>()
        TriMotorReasoningCoordinator(
            motors = listOf(
                fakeMotor(ReasoningMotorRole.INTUITIVE, calls, "first"),
                fakeMotor(ReasoningMotorRole.INTUITIVE, calls, "duplicate")
            )
        )
    }

    @Test
    fun auxiliaryAdapterIsNeverReportedAsIntrinsic() = runBlocking {
        val coordinator = TriMotorReasoningCoordinator(
            motors = listOf(
                AuxiliaryReasoningMotorAdapter(
                    delegate = AuxiliaryReasoningMotor {
                        Result.success("temporary reply")
                    }
                )
            )
        )

        val result = coordinator.reason(
            complexity = ReasoningTaskComplexity.LIGHT_LOCAL,
            request = request()
        ).getOrThrow()

        assertEquals(
            ReasoningMotorBinding.TEMPORARY_AUXILIARY,
            result.activatedBindings.single().binding
        )
    }

    private fun allMotors(calls: MutableList<ReasoningMotorRole>): List<SpecializedReasoningMotor> {
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
    ): SpecializedReasoningMotor {
        return object : SpecializedReasoningMotor {
            override val role = motorRole
            override val binding = ReasoningMotorBinding.MORIMIL_INTRINSIC

            override suspend fun compute(
                request: SpecializedReasoningRequest
            ): Result<SpecializedReasoningResponse> {
                calls += role
                return if (failure) {
                    Result.failure(IllegalStateException("motor unavailable"))
                } else {
                    Result.success(SpecializedReasoningResponse(content = reply))
                }
            }
        }
    }

    private fun request(): AuxiliaryReasoningRequest {
        return AuxiliaryReasoningRequest(
            config = ReasoningProviderConfig.default(),
            runtimeAccess = "",
            systemPrompt = "system",
            history = emptyList()
        )
    }
}
