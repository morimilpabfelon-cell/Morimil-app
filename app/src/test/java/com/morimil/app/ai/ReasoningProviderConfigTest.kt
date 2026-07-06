package com.morimil.app.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class ReasoningProviderConfigTest {
    @Test
    fun reasoningRuntimeExposesExactlyOneApiSlot() {
        assertEquals(1, ReasoningConfigStore.MAX_PROVIDER_SLOTS)
        assertEquals(1, ReasoningMotorSlot.SINGLE_API_ID)
        assertEquals("API principal", ReasoningMotorSlot.SINGLE_API_LABEL)
    }
}
