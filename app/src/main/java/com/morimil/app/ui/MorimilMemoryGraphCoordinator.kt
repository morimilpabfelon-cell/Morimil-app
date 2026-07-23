package com.morimil.app.ui

import com.morimil.app.MorimilAppContainer
import com.morimil.app.data.local.MemoryEventEntity
import com.morimil.app.data.local.MemoryLinkEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class MorimilMemoryGraphCoordinator(
    private val container: MorimilAppContainer,
    private val scope: CoroutineScope,
    private val observeTask: suspend (String, suspend () -> Unit) -> Result<Unit>
) {
    private val _selectedMemoryEventHash = MutableStateFlow<String?>(null)
    val selectedMemoryEventHash: StateFlow<String?> = _selectedMemoryEventHash.asStateFlow()

    private val _selectedMemoryLinks = MutableStateFlow<List<MemoryLinkEntity>>(emptyList())
    val selectedMemoryLinks: StateFlow<List<MemoryLinkEntity>> = _selectedMemoryLinks.asStateFlow()

    private val _selectedGraphEvents = MutableStateFlow<List<MemoryEventEntity>>(emptyList())
    val selectedGraphEvents: StateFlow<List<MemoryEventEntity>> = _selectedGraphEvents.asStateFlow()

    private var selectedMemoryLinksJob: Job? = null

    fun approveMemoryEvent(event: MemoryEventEntity) {
        recordMemoryReview(
            event = event,
            action = "aprobado",
            note = "Recuerdo aprobado por el usuario como memoria util."
        )
    }

    fun degradeMemoryEvent(event: MemoryEventEntity) {
        recordMemoryReview(
            event = event,
            action = "ruido_degradado",
            note = "Recuerdo marcado por el usuario como ruido o baja prioridad."
        )
    }

    fun requestMemoryCorrection(event: MemoryEventEntity) {
        recordMemoryReview(
            event = event,
            action = "correccion_requerida",
            note = "El usuario marco este recuerdo para correccion futura."
        )
    }

    fun selectMemoryEvent(eventHash: String) {
        if (_selectedMemoryEventHash.value == eventHash) return
        _selectedMemoryEventHash.value = eventHash
        selectedMemoryLinksJob?.cancel()
        selectedMemoryLinksJob = scope.launch {
            container.memoryLinkRepository.observeMemoryLinksForEvent(eventHash).collect { links ->
                _selectedMemoryLinks.value = links
                _selectedGraphEvents.value = loadConnectedGraphEvents(
                    selectedEventHash = eventHash,
                    links = links
                )
            }
        }
    }

    fun clearSelectedMemoryEvent() {
        _selectedMemoryEventHash.value = null
        selectedMemoryLinksJob?.cancel()
        selectedMemoryLinksJob = null
        _selectedMemoryLinks.value = emptyList()
        _selectedGraphEvents.value = emptyList()
    }

    private suspend fun loadConnectedGraphEvents(
        selectedEventHash: String,
        links: List<MemoryLinkEntity>
    ): List<MemoryEventEntity> {
        val hashes = buildList {
            add(selectedEventHash)
            links.forEach { link ->
                if (link.sourceType == MEMORY_EVENT_NODE_TYPE) add(link.sourceId)
                if (link.targetType == MEMORY_EVENT_NODE_TYPE) add(link.targetId)
            }
        }
            .filter { hash -> hash.isNotBlank() }
            .distinct()
            .take(MAX_GRAPH_EVENT_LOOKUP)

        if (hashes.isEmpty()) return emptyList()
        return withContext(Dispatchers.IO) {
            container.memoryDatabase.memoryDao().loadMemoryEventsByHashes(hashes)
        }
    }

    private fun recordMemoryReview(
        event: MemoryEventEntity,
        action: String,
        note: String
    ) {
        scope.launch(Dispatchers.IO) {
            observeTask("memory_review.$action") {
                container.appendLivingMemoryUseCase.appendMemoryReview(
                    targetEvent = event,
                    action = action,
                    note = note
                )
            }
        }
    }

    private companion object {
        const val MEMORY_EVENT_NODE_TYPE = "memory_event"
        const val MAX_GRAPH_EVENT_LOOKUP = 60
    }
}
