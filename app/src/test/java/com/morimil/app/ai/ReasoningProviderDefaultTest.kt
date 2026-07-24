package com.morimil.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningProviderDefaultTest {
    @Test
    fun defaultConfigurationHasNoActiveAuxiliary() {
        val config = ReasoningProviderConfig.default()

        assertEquals(ReasoningPreset.DISABLED, config.preset)
        assertTrue(config.baseUrl.isBlank())
        assertTrue(config.model.isBlank())
        assertTrue(config.isDisabled)
        assertFalse(config.requiresRuntimeKey)
    }

    @Test
    fun missingOrUnknownPresetFailsClosed() {
        assertEquals(ReasoningPreset.DISABLED, ReasoningPreset.fromName(null))
        assertEquals(ReasoningPreset.DISABLED, ReasoningPreset.fromName("UNKNOWN_PROVIDER"))
    }

    @Test
    fun localUsbHelperRequiresExplicitPresetSelection() {
        val config = ReasoningProviderConfig.fromPreset(ReasoningPreset.LOCAL_USB_HELPER)

        assertEquals(ReasoningPreset.LOCAL_USB_HELPER, config.preset)
        assertFalse(config.isDisabled)
        assertTrue(config.baseUrl.startsWith("http://127.0.0.1:"))
        assertEquals("llama3.2", config.model)
    }

    @Test
    fun disabledConfigurationCannotBeValidatedAsAnActiveProvider() {
        val result = runCatching { ReasoningProviderConfig.default().validated() }

        assertTrue(result.isFailure)
        assertEquals(
            "Selecciona y configura un auxiliar antes de habilitarlo.",
            result.exceptionOrNull()?.message
        )
    }
}
