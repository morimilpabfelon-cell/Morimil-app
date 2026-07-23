package com.morimil.app.core.memory

class MemoryEventSigningException(
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)

data class SignedMemoryEvent(
    val signatureAlgorithm: String,
    val eventSignature: String
) {
    init {
        if (signatureAlgorithm != MemoryIntegrityCore.MEMORY_EVENT_SIGNATURE_ALGORITHM_ANDROID_KEYSTORE_EC) {
            throw MemoryEventSigningException(
                "Memory append blocked: unsupported signature algorithm $signatureAlgorithm."
            )
        }
        if (eventSignature.isBlank()) {
            throw MemoryEventSigningException(
                "Memory append blocked: cryptographic signature is missing."
            )
        }
    }
}

interface MemoryEventSigner {
    fun signEventHash(eventHash: String): SignedMemoryEvent
}

interface MemoryEventSignatureVerifier {
    fun signatureIntegrityFailure(
        eventHash: String,
        signatureAlgorithm: String?,
        eventSignature: String?
    ): String?
}

/**
 * Read-side compatibility for pre-signing memory epochs. It does not create
 * SignedMemoryEvent values and therefore cannot authorize new unsigned writes.
 */
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
