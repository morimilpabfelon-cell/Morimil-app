package com.morimil.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.morimil.app.core.memory.MemoryEventSignatureVerifier
import com.morimil.app.core.memory.MemoryEventSigner
import com.morimil.app.core.memory.MemoryEventSigningException
import com.morimil.app.core.memory.MemoryIntegrityCore
import com.morimil.app.core.memory.MemorySignatureEpochRecorder
import com.morimil.app.core.memory.NoopMemorySignatureEpochPolicy
import com.morimil.app.core.memory.SignedMemoryEvent
import com.morimil.app.core.memory.requirePersistableSignature
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class AndroidKeyStoreMemoryEventSigner(
    private val keyAlias: String = MEMORY_EVENT_KEY_ALIAS,
    private val signingIssueReporter: MemorySigningIssueReporter = MemorySigningRuntimeIssues,
    private val signatureEpochRecorder: MemorySignatureEpochRecorder = NoopMemorySignatureEpochPolicy,
    private val privateKeyProvider: (() -> PrivateKey)? = null,
    private val publicKeyProvider: (() -> PublicKey)? = null
) : MemoryEventSigner, MemoryEventSignatureVerifier {
    override fun signEventHash(eventHash: String): SignedMemoryEvent {
        return try {
            require(eventHash.isNotBlank()) { "Memory event hash must not be blank." }
            val privateKey = privateKeyProvider?.invoke() ?: ensurePrivateKey()
            val signatureBytes = Signature.getInstance(SIGNATURE_ALGORITHM).run {
                initSign(privateKey)
                update(signaturePayload(eventHash))
                sign()
            }
            verifyGeneratedSignature(eventHash, signatureBytes)

            val signedEvent = SignedMemoryEvent(
                signatureAlgorithm = MemoryIntegrityCore.MEMORY_EVENT_SIGNATURE_ALGORITHM_ANDROID_KEYSTORE_EC,
                eventSignature = Base64.getEncoder().encodeToString(signatureBytes)
            ).requirePersistableSignature()

            signatureEpochRecorder.recordSignedEvent(eventHash)
            signingIssueReporter.clearKeystoreSigningFailure(keyAlias)
            signedEvent
        } catch (error: Throwable) {
            runCatching { signingIssueReporter.reportKeystoreSigningFailure(keyAlias, error) }
            if (error is MemoryEventSigningException) throw error
            throw MemoryEventSigningException(
                "Memory append blocked because Keystore signing failed for $keyAlias.",
                error
            )
        }
    }

    override fun signatureIntegrityFailure(
        eventHash: String,
        signatureAlgorithm: String?,
        eventSignature: String?
    ): String? {
        if (signatureAlgorithm.isNullOrBlank() ||
            signatureAlgorithm == MemoryIntegrityCore.MEMORY_EVENT_SIGNATURE_ALGORITHM_UNSIGNED
        ) {
            return null
        }
        if (signatureAlgorithm != MemoryIntegrityCore.MEMORY_EVENT_SIGNATURE_ALGORITHM_ANDROID_KEYSTORE_EC) {
            return "unsupported_signature_algorithm:$signatureAlgorithm"
        }
        if (eventSignature.isNullOrBlank()) return "missing_event_signature"

        return runCatching<String?> {
            val signatureBytes = Base64.getDecoder().decode(eventSignature)
            if (verifySignature(eventHash, signatureBytes)) null else "event_signature_mismatch"
        }.getOrElse { error ->
            "event_signature_error:${error::class.java.simpleName}"
        }
    }

    private fun verifyGeneratedSignature(eventHash: String, signatureBytes: ByteArray) {
        check(verifySignature(eventHash, signatureBytes)) {
            "Generated memory signature could not be verified with its public key."
        }
    }

    private fun verifySignature(eventHash: String, signatureBytes: ByteArray): Boolean {
        val publicKey = publicKeyProvider?.invoke() ?: ensurePublicKey()
        return Signature.getInstance(SIGNATURE_ALGORITHM).run {
            initVerify(publicKey)
            update(signaturePayload(eventHash))
            verify(signatureBytes)
        }
    }

    private fun ensurePrivateKey(): PrivateKey {
        val store = keyStore()
        val existingKey = store.getKey(keyAlias, null) as? PrivateKey
        if (existingKey != null) return existingKey

        val generator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE_PROVIDER
        )
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec(EC_CURVE))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(false)
            .build()
        generator.initialize(spec)
        generator.generateKeyPair()
        return store.getKey(keyAlias, null) as PrivateKey
    }

    private fun ensurePublicKey(): PublicKey {
        return keyStore().getCertificate(keyAlias)?.publicKey
            ?: throw MemoryEventSigningException(
                "Memory append blocked because the signing public key is missing for $keyAlias."
            )
    }

    private fun keyStore(): KeyStore {
        return KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply { load(null) }
    }

    companion object {
        private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val MEMORY_EVENT_KEY_ALIAS = "morimil.memory_event_signing.v1"
        private const val EC_CURVE = "secp256r1"
        private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
        private const val SIGNATURE_PAYLOAD_PREFIX = "morimil.memory_event_signature.v1\n"

        fun signingKeyExists(keyAlias: String = MEMORY_EVENT_KEY_ALIAS): Boolean {
            return runCatching {
                KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply { load(null) }
                    .containsAlias(keyAlias)
            }.getOrDefault(false)
        }

        fun signaturePayload(eventHash: String): ByteArray {
            return "$SIGNATURE_PAYLOAD_PREFIX$eventHash".toByteArray(StandardCharsets.UTF_8)
        }
    }
}
