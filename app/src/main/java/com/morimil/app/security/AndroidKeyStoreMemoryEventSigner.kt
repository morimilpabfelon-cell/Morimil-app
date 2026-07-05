package com.morimil.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.morimil.app.core.memory.MemoryEventSignatureVerifier
import com.morimil.app.core.memory.MemoryEventSigner
import com.morimil.app.core.memory.MemoryIntegrityCore
import com.morimil.app.core.memory.SignedMemoryEvent
import com.morimil.app.core.memory.UnsignedMemoryEventSigner
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

class AndroidKeyStoreMemoryEventSigner(
    private val keyAlias: String = MEMORY_EVENT_KEY_ALIAS,
    private val signingIssueReporter: MemorySigningIssueReporter = MemorySigningRuntimeIssues,
    private val privateKeyProvider: (() -> PrivateKey)? = null
) : MemoryEventSigner, MemoryEventSignatureVerifier {
    override fun signEventHash(eventHash: String): SignedMemoryEvent {
        return runCatching {
            val privateKey = privateKeyProvider?.invoke() ?: ensurePrivateKey()
            val signer = Signature.getInstance(SIGNATURE_ALGORITHM).apply {
                initSign(privateKey)
                update(signaturePayload(eventHash))
            }
            signingIssueReporter.clearKeystoreSigningFallback(keyAlias)
            SignedMemoryEvent(
                signatureAlgorithm = MemoryIntegrityCore.MEMORY_EVENT_SIGNATURE_ALGORITHM_ANDROID_KEYSTORE_EC,
                eventSignature = Base64.encodeToString(signer.sign(), Base64.NO_WRAP)
            )
        }.getOrElse { error ->
            signingIssueReporter.reportKeystoreSigningFallback(keyAlias, error)
            UnsignedMemoryEventSigner.signEventHash(eventHash)
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
            val publicKey = keyStore().getCertificate(keyAlias)?.publicKey
                ?: return@runCatching "signature_key_missing"
            val verifier = Signature.getInstance(SIGNATURE_ALGORITHM).apply {
                initVerify(publicKey)
                update(signaturePayload(eventHash))
            }
            val signatureBytes = Base64.decode(eventSignature, Base64.NO_WRAP)
            if (verifier.verify(signatureBytes)) null else "event_signature_mismatch"
        }.getOrElse { error ->
            "event_signature_error:${error::class.java.simpleName}"
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

    private fun keyStore(): KeyStore {
        return KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply { load(null) }
    }

    companion object {
        private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val MEMORY_EVENT_KEY_ALIAS = "morimil.memory_event_signing.v1"
        private const val EC_CURVE = "secp256r1"
        private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
        private const val SIGNATURE_PAYLOAD_PREFIX = "morimil.memory_event_signature.v1\n"

        fun signaturePayload(eventHash: String): ByteArray {
            return "$SIGNATURE_PAYLOAD_PREFIX$eventHash".toByteArray(StandardCharsets.UTF_8)
        }
    }
}
