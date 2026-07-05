package com.morimil.app.core.memory

data class SignedMemoryEvent(
    val signatureAlgorithm: String,
    val eventSignature: String?
)

interface MemoryEventSigner {
    fun signEventHash(eventHash: String): SignedMemoryEvent
}

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
