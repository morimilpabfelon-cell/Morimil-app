package com.morimil.app.data.repository

import com.morimil.app.data.genesis.GenesisIdentity
import com.morimil.app.data.local.DecisionLogEntity
import com.morimil.app.data.local.LocalInstanceIdentityEntity
import com.morimil.app.data.local.MemoryDao
import com.morimil.app.data.local.MemoryMessageEntity
import com.morimil.app.data.local.ProjectStateEntity
import com.morimil.app.data.local.UserWorkspaceEntity
import com.morimil.app.github.ForkResult
import kotlinx.coroutines.flow.Flow

class MemoryRepository(private val memoryDao: MemoryDao) {
    val messages: Flow<List<MemoryMessageEntity>> = memoryDao.observeMessages()
    val decisions: Flow<List<DecisionLogEntity>> = memoryDao.observeDecisions()
    val projects: Flow<List<ProjectStateEntity>> = memoryDao.observeProjects()
    val activeWorkspace: Flow<UserWorkspaceEntity?> = memoryDao.observeActiveWorkspace()
    val localIdentity: Flow<LocalInstanceIdentityEntity?> = memoryDao.observeLocalIdentity()

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

    /**
     * Only touches the display name -- the fork fields set at birth are
     * never modified here. There is exactly one repo for this instance,
     * and it does not change after onboarding.
     */
    suspend fun renameWorkspace(displayName: String): List<String> {
        val clean = displayName.trim()
        if (clean.isEmpty()) {
            return listOf("Display name cannot be empty.")
        }
        val rows = memoryDao.renameWorkspace(clean, System.currentTimeMillis())
        return if (rows == 0) listOf("No workspace exists yet -- create your instance first.") else emptyList()
    }

    /**
     * Names this device's instance exactly once, tied to the fork that was
     * just created for it. The Genesis fields (role, riskTier, agentId) are
     * copied verbatim from what GenesisReader fetched -- this device does
     * not invent its own role. Refuses (throws) if an identity already
     * exists; the caller is expected to check countLocalIdentity() first.
     *
     * Also syncs the workspace record's repo fields to point at this same
     * fork -- there is only ever one repo representing this instance's
     * chain, never a second, disconnected one a user could type in.
     */
    suspend fun birthLocalIdentity(alias: String, genesis: GenesisIdentity, fork: ForkResult) {
        val cleanAlias = alias.trim().ifBlank { "Morimil" }
        val instanceId = "${genesis.agentId}.${cleanAlias.lowercase().replace(Regex("[^a-z0-9]+"), "-")}"
        memoryDao.insertLocalIdentity(
            LocalInstanceIdentityEntity(
                instanceId = instanceId,
                alias = cleanAlias,
                bornAtMillis = System.currentTimeMillis(),
                genesisAgentId = genesis.agentId,
                genesisRole = genesis.role,
                genesisRiskTier = genesis.riskTier,
                genesisSchemaVersion = genesis.schemaVersion,
                forkOwner = fork.forkOwner,
                forkRepo = fork.forkRepo,
                forkHtmlUrl = fork.htmlUrl
            )
        )

        memoryDao.upsertWorkspace(
            UserWorkspaceEntity(
                workspaceId = "local_primary",
                displayName = cleanAlias,
                genesisSource = genesis.agentId,
                localPrimary = true,
                optionalRepoOwner = fork.forkOwner,
                optionalRepoName = fork.forkRepo,
                optionalRepoPrivate = false,
                repoProposalApproved = true,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun seedInitialStateIfNeeded() {
        if (memoryDao.countMessages() == 0) {
            addAssistantMessage("Fase 5D activa. Memoria local Room/SQLite conectada.")
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
