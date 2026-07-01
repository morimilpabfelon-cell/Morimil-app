package com.morimil.app.data.repository

import com.morimil.app.data.local.DecisionLogEntity
import com.morimil.app.data.local.MemoryDao
import com.morimil.app.data.local.MemoryMessageEntity
import com.morimil.app.data.local.ProjectStateEntity
import kotlinx.coroutines.flow.Flow

class MemoryRepository(private val memoryDao: MemoryDao) {
    val messages: Flow<List<MemoryMessageEntity>> = memoryDao.observeMessages()
    val decisions: Flow<List<DecisionLogEntity>> = memoryDao.observeDecisions()
    val projects: Flow<List<ProjectStateEntity>> = memoryDao.observeProjects()

    suspend fun addUserMessage(body: String) {
        memoryDao.insertMessage(
            MemoryMessageEntity(
                author = "user",
                body = body,
                createdAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun addAssistantMessage(body: String) {
        memoryDao.insertMessage(
            MemoryMessageEntity(
                author = "morimil",
                body = body,
                createdAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun seedInitialStateIfNeeded() {
        if (memoryDao.countMessages() == 0) {
            addAssistantMessage("Fase 2 activa. Memoria local Room/SQLite conectada.")
            addAssistantMessage("Voz, GitHub Sync y PC Handoff siguen bloqueados.")
        }

        memoryDao.upsertProject(
            ProjectStateEntity(
                projectId = "morimil_app",
                title = "Morimil_app",
                status = "phase_2_local_memory",
                updatedAtMillis = System.currentTimeMillis()
            )
        )

        if (memoryDao.countDecisions() == 0) {
            memoryDao.insertDecision(
                DecisionLogEntity(
                    title = "Phase 2 local Room memory enabled",
                    status = "accepted_for_local_persistence",
                    createdAtMillis = System.currentTimeMillis()
                )
            )
        }
    }
}
