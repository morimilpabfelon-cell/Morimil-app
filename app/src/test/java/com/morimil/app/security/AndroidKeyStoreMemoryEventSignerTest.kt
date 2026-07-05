package com.morimil.app.security

import com.morimil.app.core.memory.MemoryIntegrityCore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidKeyStoreMemoryEventSignerTest {
    @Test
    fun keystoreFailureReportsRuntimeIssueBeforeUnsignedFallback() {
        val reporter = RecordingSigningIssueReporter()
        val signer = AndroidKeyStoreMemoryEventSigner(
            keyAlias = "test_alias",
            signingIssueReporter = reporter,
            privateKeyProvider = { error("keystore unavailable") }
        )

        val signedEvent = signer.signEventHash("sha256:test")

        assertEquals(MemoryIntegrityCore.MEMORY_EVENT_SIGNATURE_ALGORITHM_UNSIGNED, signedEvent.signatureAlgorithm)
        assertNull(signedEvent.eventSignature)
        assertEquals("test_alias", reporter.reportedKeyAlias)
        assertTrue(reporter.reportedError is IllegalStateException)
    }

    private class RecordingSigningIssueReporter : MemorySigningIssueReporter {
        var reportedKeyAlias: String? = null
        var reportedError: Throwable? = null

        override fun reportKeystoreSigningFallback(keyAlias: String, error: Throwable) {
            reportedKeyAlias = keyAlias
            reportedError = error
        }

        override fun clearKeystoreSigningFallback(keyAlias: String) = Unit
    }
}
