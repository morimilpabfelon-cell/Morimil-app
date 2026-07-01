package com.morimil.app.data.repository

import com.morimil.app.data.local.DecisionLogEntity
import com.morimil.app.data.local.MemoryDao
import com.morimil.app.data.local.MemoryMessageEntity
import com.morimil.app.data.local.ProjectStateEntity
import com.morimil.app.data.local.UserWorkspaceEntity
import kotlinx.coroutines.flow.Flow

class MemoryRepository(private val memoryDao: MemoryDao) {
    val messages: Flow<List<MemoryMessageEntity>> = memoryDao.observeMessages()
    val decisions: Flow<List<DecisionLogEntity>> = memoryDao.observeDecisions()
    val projects: Flow<List<ProjectStateEntity>> = memoryDao.observeProjects()
    val activeWorkspace: Flow<UserWorkspaceEntity?> = memoryDao.observeActiveWorkspace()

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

    suspend fun saveWorkspaceProposal(
        displayName: String,
        repoOwner: String?,
        repoName: String?,
        repoPrivate: Boolean,
        approved: Boolean
    ) {
        memoryDao.upsertWorkspace(
            UserWorkspaceEntity(
                workspaceId = "local_primary",
                displayName = displayName,
                genesisSource = "Morimil Genesis Block",
                localPrimary = true,
                optionalRepoOwner = repoOwner,
                optionalRepoName = repoName,
                optionalRepoPrivate = repoPrivate,
                repoProposalApproved = approved,
                updatedAtMillis = System.currentTimeMillis()
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
                status = "phase_5d_user_workspace_bootstrap",
                updatedAtMillis = System.currentTimeMillis()
            )
        )

        if (memoryDao.countWorkspaces() == 0) {
            saveWorkspaceProposal(
                displayName = "Local Morimil Workspace",
                repoOwner = null,
                repoName = null,
                repoPrivate = true,
                approved = false
            )
        }

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
