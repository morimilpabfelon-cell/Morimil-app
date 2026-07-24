package com.morimil.app.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecretVaultReasoningScopeInstrumentedTest {
    @Test
    fun reasoningKeysAreReadableOnlyForTheExactBoundOrigin() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val vault = SecretVault(context)
        val slot = 10
        val firstEndpoint = "https://api-a.example.com/v1/responses"
        val secondEndpoint = "https://api-b.example.com/v1/responses"

        vault.clearAllReasoningKeys(slot)
        try {
            vault.saveReasoningKey(slot, firstEndpoint, "first-secret").getOrThrow()
            assertTrue(vault.hasReasoningKey(slot, firstEndpoint))
            assertFalse(vault.hasReasoningKey(slot, secondEndpoint))
            assertEquals(
                "first-secret",
                vault.readReasoningKey(slot, firstEndpoint).getOrThrow()
            )
            assertNull(vault.readReasoningKey(slot, secondEndpoint).getOrThrow())

            vault.saveReasoningKey(slot, secondEndpoint, "second-secret").getOrThrow()
            assertFalse(vault.hasReasoningKey(slot, firstEndpoint))
            assertTrue(vault.hasReasoningKey(slot, secondEndpoint))
            assertNull(vault.readReasoningKey(slot, firstEndpoint).getOrThrow())
            assertEquals(
                "second-secret",
                vault.readReasoningKey(slot, secondEndpoint).getOrThrow()
            )

            vault.clearAllReasoningKeys(slot)
            vault.saveSecret("reasoning_runtime_key_slot_$slot", "legacy-unbound")
                .getOrThrow()
            assertTrue(vault.hasLegacyUnboundReasoningKey(slot))
            assertNull(vault.readReasoningKey(slot, firstEndpoint).getOrThrow())
            assertNull(vault.readReasoningKey(slot, secondEndpoint).getOrThrow())
        } finally {
            vault.clearAllReasoningKeys(slot)
        }
    }
}
