package com.morimil.app.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class ReasoningProviderConfigTest {
    @Test
    fun reasoningRuntimeExposesOneConfiguredTemporaryHelper() {
        assertEquals(1, ReasoningHelperSlot.SINGLE_HELPER_ID)
        assertEquals(
            "Auxiliar temporal configurado",
            ReasoningHelperSlot.SINGLE_HELPER_LABEL
        )
    }
}
