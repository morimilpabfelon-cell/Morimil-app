package com.morimil.app.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanAgentPolicyTest {
    @Test
    fun localLanEndpointIsAccepted() {
        assertTrue(LanAgentPolicy.isLocalLanEndpoint("http://192.168.1.40:8787"))
    }

    @Test
    fun publicEndpointIsRejected() {
        assertFalse(LanAgentPolicy.isLocalLanEndpoint("https://8.8.8.8"))
    }

    @Test
    fun approvalIsRequired() {
        val decision = LanAgentPolicy().validateFileAuditDispatch(
            endpoint = LanAgentEndpoint("http://192.168.1.40:8787"),
            request = LanFileAuditRequest(
                requestId = "req-1",
                targetRootId = "morimil_pc_root",
                approved = false,
                nonce = "nonce-1",
                createdAtMillis = 1L
            ),
            pairingKey = "pair-123"
        )

        assertFalse(decision.allowed)
        assertTrue(decision.reasons.contains("human approval missing"))
    }
}
