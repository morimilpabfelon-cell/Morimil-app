package com.morimil.app.core.memory

import com.morimil.app.data.local.MemoryEventEntity

class MemoryIntegrityVerifier(
    private val eventIntegrity: MemoryEventIntegrity = MemoryEventIntegrity()
) {
    fun verifyMemoryEventChain(
        events: List<MemoryEventEntity>,
        requireGenesisStart: Boolean = true
    ): Boolean {
        return eventIntegrity.verifyMemoryEventChain(
            events = events,
            requireGenesisStart = requireGenesisStart
        )
    }

    fun inspectMemoryEventTail(
        events: List<MemoryEventEntity>,
        fallbackPreviousHash: String?
    ): MemoryTailIntegrity {
        var expectedPreviousHash = fallbackPreviousHash ?: events.firstOrNull()?.previousEventHash
        var lastTrustedEventHash = fallbackPreviousHash
        events.forEach { event ->
            if (event.eventHash == MemoryEventIntegrity.LEGACY_EVENT_HASH) {
                if (fallbackPreviousHash == null) {
                    expectedPreviousHash = event.eventHash
                    lastTrustedEventHash = event.eventHash
                }
                return@forEach
            }
            val failure = eventIntegrity.memoryEventIntegrityFailure(event, expectedPreviousHash)
            if (failure != null) {
                return MemoryTailIntegrity(
                    trusted = false,
                    appendPreviousEventHash = lastTrustedEventHash,
                    lastTrustedEventHash = lastTrustedEventHash,
                    firstUntrustedHash = event.eventHash,
                    reason = failure
                )
            }
            expectedPreviousHash = event.eventHash
            lastTrustedEventHash = event.eventHash
        }
        return MemoryTailIntegrity(
            trusted = true,
            appendPreviousEventHash = lastTrustedEventHash,
            lastTrustedEventHash = lastTrustedEventHash,
            firstUntrustedHash = null,
            reason = null
        )
    }
}

data class MemoryTailIntegrity(
    val trusted: Boolean,
    val appendPreviousEventHash: String?,
    val lastTrustedEventHash: String?,
    val firstUntrustedHash: String?,
    val reason: String?
)
