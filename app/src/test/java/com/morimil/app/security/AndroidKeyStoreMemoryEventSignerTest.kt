package com.morimil.app.security

import com.morimil.app.core.memory.MemoryEventSigningException
import com.morimil.app.core.memory.MemoryIntegrityCore
import com.morimil.app.core.memory.MemorySignatureEpochRecorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

class AndroidKeyStoreMemoryEventSignerTest {
    @Test
    fun keystoreFailureReportsRuntimeIssueAndBlocksMemoryAppend() {
        val reporter = RecordingSigningIssueReporter()
        val recorder = RecordingSignatureEpochRecorder()
        val signer = AndroidKeyStoreMemoryEventSigner(
            keyAlias = "test_alias",
            signingIssueReporter = reporter,
            signatureEpochRecorder = recorder,
            privateKeyProvider = { error("keystore unavailable") }
        )

        val failure = runCatching { signer.signEventHash("sha256:test") }.exceptionOrNull()

        assertTrue(failure is MemoryEventSigningException)
        assertEquals("test_alias", reporter.reportedKeyAlias)
        assertTrue(reporter.reportedError is IllegalStateException)
        assertNull(recorder.recordedEventHash)
    }

    @Test
    fun successfulSignatureIsVerifiedBeforeRecordingSignedEpoch() {
        val recorder = RecordingSignatureEpochRecorder()
        val keyPair = generateKeyPair()
        val signer = AndroidKeyStoreMemoryEventSigner(
            keyAlias = "test_alias",
            signingIssueReporter = RecordingSigningIssueReporter(),
            signatureEpochRecorder = recorder,
            privateKeyProvider = { keyPair.private },
            publicKeyProvider = { keyPair.public }
        )

        val signedEvent = signer.signEventHash("sha256:test")

        assertEquals(
            MemoryIntegrityCore.MEMORY_EVENT_SIGNATURE_ALGORITHM_ANDROID_KEYSTORE_EC,
            signedEvent.signatureAlgorithm
        )
        assertFalse(signedEvent.eventSignature.isBlank())
        assertEquals("sha256:test", recorder.recordedEventHash)
        assertNull(
            signer.signatureIntegrityFailure(
                eventHash = "sha256:test",
                signatureAlgorithm = signedEvent.signatureAlgorithm,
                eventSignature = signedEvent.eventSignature
            )
        )
    }

    @Test
    fun signatureVerificationFailureBlocksEpochAndPersistence() {
        val signingKeyPair = generateKeyPair()
        val unrelatedKeyPair = generateKeyPair()
        val reporter = RecordingSigningIssueReporter()
        val recorder = RecordingSignatureEpochRecorder()
        val signer = AndroidKeyStoreMemoryEventSigner(
            keyAlias = "test_alias",
            signingIssueReporter = reporter,
            signatureEpochRecorder = recorder,
            privateKeyProvider = { signingKeyPair.private },
            publicKeyProvider = { unrelatedKeyPair.public }
        )

        val failure = runCatching { signer.signEventHash("sha256:test") }.exceptionOrNull()

        assertTrue(failure is MemoryEventSigningException)
        assertEquals("test_alias", reporter.reportedKeyAlias)
        assertNull(recorder.recordedEventHash)
    }

    private fun generateKeyPair() = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    private class RecordingSigningIssueReporter : MemorySigningIssueReporter {
        var reportedKeyAlias: String? = null
        var reportedError: Throwable? = null

        override fun reportKeystoreSigningFailure(keyAlias: String, error: Throwable) {
            reportedKeyAlias = keyAlias
            reportedError = error
        }

        override fun clearKeystoreSigningFailure(keyAlias: String) = Unit
    }

    private class RecordingSignatureEpochRecorder : MemorySignatureEpochRecorder {
        var recordedEventHash: String? = null

        override fun recordSignedEvent(eventHash: String) {
            recordedEventHash = eventHash
        }
    }
}
