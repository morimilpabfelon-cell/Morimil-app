package com.morimil.app.ai

import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningNetworkTimeoutPolicyTest {
    @Test
    fun readTimeoutIsLongerThanLegacyFortyFiveSeconds() {
        assertTrue(ReasoningNetworkTimeoutPolicy.READ_TIMEOUT_MS > 45_000)
    }

    @Test
    fun connectTimeoutStaysShorterThanReadTimeout() {
        assertTrue(ReasoningNetworkTimeoutPolicy.CONNECT_TIMEOUT_MS < ReasoningNetworkTimeoutPolicy.READ_TIMEOUT_MS)
    }

    @Test
    fun timeoutMessageIsUserReadable() {
        val message = ReasoningNetworkTimeoutPolicy.userMessage()

        assertTrue(message.contains("Tiempo agotado"))
        assertTrue(message.contains("motor de razonamiento"))
    }
}
