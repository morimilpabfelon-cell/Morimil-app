package com.morimil.app.core.memory

import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryEventSignerTest {
    @Test
    fun unsignedAlgorithmCannotProducePersistableSignedEvent() {
        val failure = runCatching {
            SignedMemoryEvent(
                signatureAlgorithm = MemoryIntegrityCore.MEMORY_EVENT_SIGNATURE_ALGORITHM_UNSIGNED,
                eventSignature = "not-a-valid-production-signature"
            )
        }.exceptionOrNull()

        assertTrue(failure is MemoryEventSigningException)
    }

    @Test
    fun blankSignatureCannotProducePersistableSignedEvent() {
        val failure = runCatching {
            SignedMemoryEvent(
                signatureAlgorithm = MemoryIntegrityCore.MEMORY_EVENT_SIGNATURE_ALGORITHM_ANDROID_KEYSTORE_EC,
                eventSignature = "   "
            )
        }.exceptionOrNull()

        assertTrue(failure is MemoryEventSigningException)
    }
}
