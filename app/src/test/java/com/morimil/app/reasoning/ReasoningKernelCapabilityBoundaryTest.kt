package com.morimil.app.reasoning

import com.morimil.app.ai.ReasoningProviderConfig
import com.morimil.app.data.genesis.GenesisIdentity
import com.morimil.app.reasoning.model.ReasoningEscalationDecision
import com.morimil.app.reasoning.model.ReasoningEscalationStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningKernelCapabilityBoundaryTest {
    @After
    fun clearAuthorizationStore() {
        ReasoningEscalationStore.clear()
    }

    @Test
    fun constructorExposesOnlyReadContextAndTemporaryComputationCapabilities() {
        val constructor = ReasoningKernel::class.java.declaredConstructors.single()

        assertEquals(
            listOf(
                ReasoningContextReader::class.java,
                IntrinsicTriMotorCoordinator::class.java,
                TemporaryExternalReasoningProvider::class.java
            ),
            constructor.parameterTypes.toList()
        )
        assertTrue(
            constructor.parameterTypes.none { type ->
                type.name.contains("Repository") ||
                    type.name.contains("Dao") ||
                    type.name.contains("MemoryUseCase")
            }
        )
    }

    @Test
    fun auxiliaryReplyRequiresOneShotApprovalAndRemainsTransient() = runBlocking {
        var livingMemoryReads = 0
        var capsuleReads = 0
        var motorCalls = 0
        val kernel = ReasoningKernel(
            contextReader = object : ReasoningContextReader {
                override suspend fun readLivingMemory(query: String): String {
                    livingMemoryReads += 1
                    return "signed local memory"
                }

                override suspend fun readKnowledgeCapsules(): String {
                    capsuleReads += 1
                    return "local knowledge"
                }
            },
            intrinsicCoordinator = IntrinsicTriMotorCoordinator(),
            temporaryExternalProvider = TemporaryExternalReasoningProvider {
                motorCalls += 1
                Result.success("temporary computed reply")
            }
        )
        val request = ReasoningKernelRequest(
            input = "Piensa sobre esto",
            alias = "Morimil",
            genesis = testGenesis(),
            doctrineText = null,
            policyText = null,
            priorHistory = emptyList(),
            runtimeConfig = ReasoningProviderConfig.default(),
            runtimeAccess = "",
            runtimeLabel = "motor auxiliar local"
        )

        val pendingResult = kernel.reason(request)

        assertTrue(pendingResult.externalAuthorizationRequired)
        assertNull(pendingResult.reply)
        assertEquals(0, motorCalls)
        assertEquals(
            ReasoningEscalationDecision.PENDING,
            ReasoningEscalationStore.pendingRequest.value?.decision
        )
        assertEquals("Piensa sobre esto", ReasoningEscalationStore.approveCurrent())

        val result = kernel.reason(request)

        assertEquals("temporary computed reply", result.reply)
        assertEquals(2, livingMemoryReads)
        assertEquals(2, capsuleReads)
        assertEquals(1, motorCalls)
        assertEquals(ReasoningExecutionOrigin.TEMPORARY_EXTERNAL, result.state.executionOrigin)
        assertTrue(
            result.state.trace.any { trace ->
                trace.stage == "external_authorization_gate" &&
                    trace.detail.contains("decision=approved_once")
            }
        )
        assertTrue(
            result.state.trace.any { trace ->
                trace.stage == "temporary_external_result" &&
                    trace.detail.contains("intrinsic_motor_state_unchanged")
            }
        )
        assertTrue(
            result.state.trace.any { trace ->
                trace.stage == "persistence_boundary" &&
                    trace.detail.contains("memory_write_capability=absent")
            }
        )
    }

    @Test
    fun intrinsicMotorRunsBeforeTemporaryExternalProvider() = runBlocking {
        var externalCalls = 0
        val intrinsicMotor = object : IntrinsicReasoningMotor {
            override val role = ReasoningMotorRole.INTUITIVE
            override val capabilityVersion = "intuitive-v1"

            override suspend fun compute(
                request: IntrinsicReasoningRequest
            ): Result<IntrinsicReasoningResponse> {
                return Result.success(IntrinsicReasoningResponse("intrinsic reply"))
            }
        }
        val kernel = ReasoningKernel(
            contextReader = object : ReasoningContextReader {
                override suspend fun readLivingMemory(query: String) = ""
                override suspend fun readKnowledgeCapsules() = ""
            },
            intrinsicCoordinator = IntrinsicTriMotorCoordinator(listOf(intrinsicMotor)),
            temporaryExternalProvider = TemporaryExternalReasoningProvider {
                externalCalls += 1
                Result.success("external reply")
            }
        )

        val result = kernel.reason(
            ReasoningKernelRequest(
                input = "Hola",
                alias = "Morimil",
                genesis = testGenesis(),
                doctrineText = null,
                policyText = null,
                priorHistory = emptyList(),
                runtimeConfig = ReasoningProviderConfig.default(),
                runtimeAccess = "",
                runtimeLabel = "temporary external provider"
            )
        )

        assertEquals("intrinsic reply", result.reply)
        assertEquals(0, externalCalls)
        assertEquals(ReasoningExecutionOrigin.MORIMIL_INTRINSIC, result.state.executionOrigin)
        assertTrue(
            result.state.trace.any { trace -> trace.stage == "intrinsic_motor_result" }
        )
        assertTrue(
            result.state.trace.none { trace -> trace.stage == "temporary_external_call" }
        )
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
