package com.morimil.app.domain.usecase

import com.morimil.app.data.local.MemoryEventEntity
import com.morimil.app.data.repository.AppendedMemoryEventReference
import com.morimil.app.data.repository.MemoryRepository

class AppendLivingMemoryUseCase(
    private val memoryRepository: MemoryRepository
) {
    suspend fun appendUserMessage(body: String): AppendedMemoryEventReference? {
        return memoryRepository.addUserMessage(body)
    }

    suspend fun appendAssistantMessage(body: String) {
        memoryRepository.addAssistantMessage(body)
    }

    suspend fun appendSystemEvent(
        eventType: String,
        body: String,
        importance: Int
    ): String? {
        return memoryRepository.recordSystemMemoryEvent(
            eventType = eventType,
            body = body,
            importance = importance
        )
    }

    suspend fun appendMemoryReview(
        targetEvent: MemoryEventEntity,
        action: String,
        note: String
    ) {
        memoryRepository.recordMemoryReview(
            targetEvent = targetEvent,
            action = action,
            note = note
        )
    }
}
