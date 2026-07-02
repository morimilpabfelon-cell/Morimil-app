package com.morimil.app.core.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRuntimeGateTest {
    @Test
    fun birthOnlyAllowedOnce() {
        val gate = AppRuntimeGate()

        assertTrue(gate.canCreateBirth(existingBirth = false))
        assertFalse(gate.canCreateBirth(existingBirth = true))
    }

    @Test
    fun memoryAppendRequiresVerifiedState() {
        val gate = AppRuntimeGate()

        assertTrue(gate.canAppendMemory(chainOk = true, genesisOk = true))
        assertFalse(gate.canAppendMemory(chainOk = false, genesisOk = true))
        assertFalse(gate.canAppendMemory(chainOk = true, genesisOk = false))
    }

    @Test
    fun genesisEditIsDenied() {
        val gate = AppRuntimeGate()

        assertFalse(gate.canEditGenesis())
    }
}
