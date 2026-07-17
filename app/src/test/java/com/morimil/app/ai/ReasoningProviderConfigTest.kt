package com.morimil.app.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class ReasoningProviderConfigTest {
    @Test
    fun reasoningRuntimeExposesOneConfiguredHelperMotor() {
        assertEquals(1, ReasoningMotorSlot.SINGLE_HELPER_ID)
        assertEquals("Motor auxiliar configurado", ReasoningMotorSlot.SINGLE_HELPER_LABEL)
    }
}
