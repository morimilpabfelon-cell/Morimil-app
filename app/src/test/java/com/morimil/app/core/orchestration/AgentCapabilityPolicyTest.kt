package com.morimil.app.core.orchestration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentCapabilityPolicyTest {
    @Test
    fun androidBuildGoalsRouteToBuildAgentWithApproval() {
        val plan = AgentCapabilityPolicy.planDelegation("Corre gradle testDebugUnitTest y assembleDebug")

        assertEquals(AgentCapabilityPolicy.AGENT_ANDROID_BUILD, plan.assignedAgentId)
        assertTrue(plan.approvalRequired)
        assertEquals("medium", plan.riskLevel)
        assertTrue("run_gradle_tests" in plan.allowedActions)
    }

    @Test
    fun pcExecutorIsHighRiskAndTransportFlexible() {
        val plan = AgentCapabilityPolicy.planDelegation("Ejecutar una tarea aprobada en mi PC", AgentCapabilityPolicy.AGENT_PC_EXECUTOR, "personal_pc")

        assertEquals("personal_pc", plan.targetDeviceId)
        assertEquals("high", plan.riskLevel)
        assertTrue(AgentCapabilityPolicy.TRANSPORT_WIFI in plan.allowedTransports)
        assertTrue(AgentCapabilityPolicy.TRANSPORT_USB in plan.allowedTransports)
        assertTrue(AgentCapabilityPolicy.TRANSPORT_INTERNET in plan.allowedTransports)
    }

    @Test
    fun jsonEncodingIsStable() {
        val json = AgentCapabilityPolicy.encodeJson(listOf("wifi_lan", "usb_local"))

        assertEquals("[\"wifi_lan\",\"usb_local\"]", json)
    }
}