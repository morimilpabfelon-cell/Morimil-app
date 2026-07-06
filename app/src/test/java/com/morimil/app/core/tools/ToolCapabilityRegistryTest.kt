package com.morimil.app.core.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCapabilityRegistryTest {
    @Test
    fun readOnlyRepositoryActionIsAllowedWithoutExtraApproval() {
        val result = ToolCapabilityRegistry.evaluateAction(ToolCapabilityRegistry.ACTION_READ_REPOSITORY)

        assertEquals(ToolCapabilityDecision.ALLOW, result.decision)
        assertEquals("low", result.riskLevel)
        assertTrue(result.requiredControls.isEmpty())
    }

    @Test
    fun localWriteActionRequiresHumanApprovalAndAuthorizedDevice() {
        val result = ToolCapabilityRegistry.evaluateAction(ToolCapabilityRegistry.ACTION_RUN_GRADLE_TESTS)

        assertEquals(ToolCapabilityDecision.APPROVAL_REQUIRED, result.decision)
        assertTrue("human_approval" in result.requiredControls)
        assertTrue("authorized_device" in result.requiredControls)
    }

    @Test
    fun localWriteActionIsAllowedAfterRequiredControls() {
        val result = ToolCapabilityRegistry.evaluateAction(
            ToolCapabilityRegistry.ACTION_RUN_GRADLE_TESTS,
            humanApproved = true,
            authorizedDevice = true
        )

        assertEquals(ToolCapabilityDecision.ALLOW, result.decision)
        assertTrue(result.requiredControls.isEmpty())
    }

    @Test
    fun secretActionRequiresHumanAndCredentialApproval() {
        val result = ToolCapabilityRegistry.evaluateAction(ToolCapabilityRegistry.ACTION_SECRET_REASONING_RUNTIME_KEY)

        assertEquals(ToolCapabilityDecision.APPROVAL_REQUIRED, result.decision)
        assertTrue("human_approval" in result.requiredControls)
        assertTrue("credential_approval" in result.requiredControls)
    }

    @Test
    fun unknownActionIsDeniedUntilRegistered() {
        val result = ToolCapabilityRegistry.evaluateAction("github_push")

        assertEquals(ToolCapabilityDecision.DENY, result.decision)
        assertEquals("critical", result.riskLevel)
        assertTrue("unknown_tool_action" in result.reasons)
        assertTrue("register_tool_capability" in result.requiredControls)
    }

    @Test
    fun agentToolsetsValidateAsRegisteredCapabilities() {
        val result = ToolCapabilityRegistry.validateActionIds(ToolCapabilityRegistry.actionsForAgent("github_agent"))

        assertEquals(ToolCapabilityDecision.ALLOW, result.decision)
        assertEquals("medium", result.riskLevel)
        assertTrue("registered_tool_actions" in result.reasons)
    }
}
