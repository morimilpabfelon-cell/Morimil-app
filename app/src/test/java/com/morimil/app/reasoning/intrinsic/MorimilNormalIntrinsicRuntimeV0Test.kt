package com.morimil.app.reasoning.intrinsic

import com.morimil.app.ai.ChatTurn
import com.morimil.app.ai.ReasoningProviderConfig
import com.morimil.app.data.genesis.GenesisIdentity
import com.morimil.app.reasoning.IntrinsicReasoningRequest
import com.morimil.app.reasoning.ReasoningContextReader
import com.morimil.app.reasoning.ReasoningExecutionOrigin
import com.morimil.app.reasoning.ReasoningKernel
import com.morimil.app.reasoning.ReasoningKernelRequest
import com.morimil.app.reasoning.ReasoningMotorRole
import com.morimil.app.reasoning.ReasoningTaskKind
import com.morimil.app.reasoning.TemporaryExternalReasoningProvider
import com.morimil.app.reasoning.model.ReasoningEscalationDecision
import com.morimil.app.reasoning.model.ReasoningEscalationStore
import com.morimil.app.reasoning.model.ReasoningTaskComplexity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MorimilNormalIntrinsicRuntimeV0Test {
    @After
    fun clearAuthorizationStore() {
        ReasoningEscalationStore.clear()
    }

    @Test
    fun normalRuntimeRegistersOnlyBoundedIntuitiveRole() {
        val coordinator = MorimilNormalIntrinsicRuntimeV0.createCoordinator()

        assertEquals(setOf(ReasoningMotorRole.INTUITIVE), coordinator.availableRoles())
        assertEquals(
            setOf(ReasoningMotorRole.INTUITIVE),
            MorimilNormalIntrinsicRuntimeV0.registeredRoles
        )
    }

    @Test
    fun exactArithmeticRunsLocallyWithoutTemporaryProvider() = runBlocking {
        var externalCalls = 0
        val kernel = kernel(
            externalProvider = TemporaryExternalReasoningProvider {
                externalCalls += 1
                Result.success("temporary external reply")
            }
        )

        val result = kernel.reason(
            kernelRequest("Calcula 15 menos 2 por 6 respetando prioridad.")
        )

        assertEquals("FINAL:3", result.reply)
        assertEquals(0, externalCalls)
        assertEquals(ReasoningExecutionOrigin.MORIMIL_INTRINSIC, result.state.executionOrigin)
        assertTrue(
            result.state.criticFindings.contains("intrinsic_core:intuitive_bounded_local")
        )
        assertTrue(
            result.state.modelBackendLabel.contains(BoundedLocalIntuitiveCoreV0.VERSION)
        )
    }

    @Test
    fun exactClosedOrderLogicRunsLocallyWithoutTemporaryProvider() = runBlocking {
        var externalCalls = 0
        val kernel = kernel(
            externalProvider = TemporaryExternalReasoningProvider {
                externalCalls += 1
                Result.success("temporary external reply")
            }
        )

        val result = kernel.reason(
            kernelRequest(
                "Ana llegó antes que Bruno y Bruno antes que Carla. ¿Quién llegó primero?"
            )
        )

        assertEquals("FINAL:ANA", result.reply)
        assertEquals(0, externalCalls)
        assertEquals(ReasoningExecutionOrigin.MORIMIL_INTRINSIC, result.state.executionOrigin)
        assertTrue(
            result.state.criticFindings.contains("deterministic_closed_order_unique_topology")
        )
        assertTrue(
            result.state.modelBackendLabel.contains(BoundedLocalIntuitiveCoreV0.VERSION)
        )
    }

    @Test
    fun exactInstructionRunsLocallyWithoutTemporaryProvider() = runBlocking {
        var externalCalls = 0
        val kernel = kernel(
            externalProvider = TemporaryExternalReasoningProvider {
                externalCalls += 1
                Result.success("temporary external reply")
            }
        )

        val result = kernel.reason(
            kernelRequest("Calcula 12 - 5 y devuelve exactamente FINAL:<resultado>.")
        )

        assertEquals("FINAL:7", result.reply)
        assertEquals(0, externalCalls)
        assertEquals(ReasoningExecutionOrigin.MORIMIL_INTRINSIC, result.state.executionOrigin)
        assertTrue(
            result.state.criticFindings.contains("deterministic_exact_instruction_subtraction")
        )
        assertTrue(
            result.state.modelBackendLabel.contains(BoundedLocalIntuitiveCoreV0.VERSION)
        )
    }

    @Test
    fun malformedInstructionRequiresApprovalBeforeTemporaryFallback() = runBlocking {
        var externalCalls = 0
        val kernel = kernel(
            externalProvider = TemporaryExternalReasoningProvider {
                externalCalls += 1
                Result.success("temporary external reply")
            }
        )
        val request = kernelRequest("Devuelve exactamente FINAL:azul y nada más.")

        val pending = kernel.reason(request)

        assertTrue(pending.externalAuthorizationRequired)
        assertNull(pending.reply)
        assertEquals(0, externalCalls)
        assertEquals(
            ReasoningEscalationDecision.PENDING,
            ReasoningEscalationStore.pendingRequest.value?.decision
        )
        ReasoningEscalationStore.approveCurrent()

        val result = kernel.reason(request)

        assertEquals("temporary external reply", result.reply)
        assertEquals(1, externalCalls)
        assertEquals(ReasoningExecutionOrigin.TEMPORARY_EXTERNAL, result.state.executionOrigin)
        assertTrue(
            result.state.trace.any { event -> event.stage == "intrinsic_motor_unavailable" }
        )
        assertTrue(
            result.state.trace.any { event ->
                event.stage == "external_authorization_gate" &&
                    event.detail.contains("decision=approved_once")
            }
        )
    }

    @Test
    fun unsupportedGenerativeTaskRequiresApprovalBeforeTemporaryFallback() = runBlocking {
        var externalCalls = 0
        val kernel = kernel(
            externalProvider = TemporaryExternalReasoningProvider {
                externalCalls += 1
                Result.success("temporary external reply")
            }
        )
        val request = kernelRequest("Escribe una historia breve sobre el mar.")

        val pending = kernel.reason(request)

        assertTrue(pending.externalAuthorizationRequired)
        assertNull(pending.reply)
        assertEquals(0, externalCalls)
        assertEquals(
            ReasoningEscalationDecision.PENDING,
            ReasoningEscalationStore.pendingRequest.value?.decision
        )
        ReasoningEscalationStore.approveCurrent()

        val result = kernel.reason(request)

        assertEquals("temporary external reply", result.reply)
        assertEquals(1, externalCalls)
        assertEquals(ReasoningExecutionOrigin.TEMPORARY_EXTERNAL, result.state.executionOrigin)
        assertTrue(
            result.state.trace.any { event -> event.stage == "intrinsic_motor_unavailable" }
        )
        assertTrue(
            result.state.trace.any { event ->
                event.stage == "external_authorization_gate" &&
                    event.detail.contains("decision=approved_once")
            }
        )
    }

    @Test
    fun unknownTaskCannotProduceAnIntrinsicReply() = runBlocking {
        val result = MorimilNormalIntrinsicRuntimeV0.createCoordinator().reason(
            IntrinsicReasoningRequest(
                systemPrompt = "Morimil conserva autoridad final.",
                history = listOf(
                    ChatTurn(role = "user", content = "Escribe una historia breve.")
                ),
                taskComplexity = ReasoningTaskComplexity.LIGHT_LOCAL,
                taskKind = ReasoningTaskKind.UNKNOWN,
                authorityPrompt = "Escribe una historia breve."
            )
        )

        assertTrue(result.isFailure)
        assertEquals(
            "No primary reasoning motor produced a reply.",
            result.exceptionOrNull()?.message
        )
    }

    private fun kernel(
        externalProvider: TemporaryExternalReasoningProvider
    ): ReasoningKernel {
        return ReasoningKernel(
            contextReader = object : ReasoningContextReader {
                override suspend fun readLivingMemory(query: String): String = ""
                override suspend fun readKnowledgeCapsules(): String = ""
            },
            intrinsicCoordinator = MorimilNormalIntrinsicRuntimeV0.createCoordinator(),
            temporaryExternalProvider = externalProvider
        )
    }

    private fun kernelRequest(input: String): ReasoningKernelRequest {
        return ReasoningKernelRequest(
            input = input,
            alias = "Morimil",
            genesis = GenesisIdentity(
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
            ),
            doctrineText = null,
            policyText = null,
            priorHistory = emptyList(),
            runtimeConfig = ReasoningProviderConfig.default(),
            runtimeAccess = "",
            runtimeLabel = "motor auxiliar local"
        )
    }
}
