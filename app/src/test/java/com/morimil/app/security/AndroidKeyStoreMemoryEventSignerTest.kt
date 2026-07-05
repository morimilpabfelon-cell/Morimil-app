package com.morimil.app.security

import com.morimil.app.core.memory.MemoryIntegrityCore
import com.morimil.app.core.memory.MemorySignatureEpochRecorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

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

    @Test
    fun successfulSignatureRecordsSignedEpoch() {
        val recorder = RecordingSignatureEpochRecorder()
        val keyPair = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        val signer = AndroidKeyStoreMemoryEventSigner(
            keyAlias = "test_alias",
            signingIssueReporter = RecordingSigningIssueReporter(),
            signatureEpochRecorder = recorder,
            privateKeyProvider = { keyPair.private }
        )

        val signedEvent = signer.signEventHash("sha256:test")

        assertEquals(
            MemoryIntegrityCore.MEMORY_EVENT_SIGNATURE_ALGORITHM_ANDROID_KEYSTORE_EC,
            signedEvent.signatureAlgorithm
        )
        assertEquals("sha256:test", recorder.recordedEventHash)
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

    private class RecordingSignatureEpochRecorder : MemorySignatureEpochRecorder {
        var recordedEventHash: String? = null

        override fun recordSignedEvent(eventHash: String) {
            recordedEventHash = eventHash
        }
    }
}
