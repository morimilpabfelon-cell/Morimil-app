package com.morimil.app.core.memory

interface MemorySignatureEpochPolicy {
    fun signedEpochEventHash(): String?
}

interface MemorySignatureEpochRecorder {
    fun recordSignedEvent(eventHash: String)
}

object NoopMemorySignatureEpochPolicy : MemorySignatureEpochPolicy, MemorySignatureEpochRecorder {
    override fun signedEpochEventHash(): String? = null
    override fun recordSignedEvent(eventHash: String) = Unit
}
