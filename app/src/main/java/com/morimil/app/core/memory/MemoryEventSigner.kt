package com.morimil.app.core.memory

data class SignedMemoryEvent(
    val signatureAlgorithm: String,
    val eventSignature: String?
)

class MemoryEventSigningException(
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)

fun SignedMemoryEvent.requirePersistableSignature(): SignedMemoryEvent {
    if (signatureAlgorithm != MemoryIntegrityCore.MEMORY_EVENT_SIGNATURE_ALGORITHM_ANDROID_KEYSTORE_EC) {
        throw MemoryEventSigningException(
            "Memory append blocked: unsupported signature algorithm $signatureAlgorithm."
        )
    }
    if (eventSignature.isNullOrBlank()) {
        throw MemoryEventSigningException(
            "Memory append blocked: cryptographic signature is missing."
        )
    }
    return this
}

interface MemoryEventSigner {
    fun signEventHash(eventHash: String): SignedMemoryEvent
}

/**
 * Legacy/test-only signer. Production persistence calls requirePersistableSignature(),
 * so this result can never be appended to living memory.
 */
object UnsignedMemoryEventSigner : MemoryEventSigner {
    override fun signEventHash(eventHash: String): SignedMemoryEvent {
        return SignedMemoryEvent(
            signatureAlgorithm = MemoryIntegrityCore.MEMORY_EVENT_SIGNATURE_ALGORITHM_UNSIGNED,
            eventSignature = null
        )
    }
}

interface MemoryEventSignatureVerifier {
    fun signatureIntegrityFailure(
        eventHash: String,
        signatureAlgorithm: String?,
        eventSignature: String?
    ): String?
}

object UnsignedOnlyMemoryEventSignatureVerifier : MemoryEventSignatureVerifier {
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
        return "signature_verifier_unavailable:$signatureAlgorithm"
    }
}
