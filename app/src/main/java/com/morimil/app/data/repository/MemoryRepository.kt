package com.morimil.app.data.repository

import androidx.room.withTransaction
import com.morimil.app.data.genesis.GenesisIdentity
import com.morimil.app.data.local.DecisionLogEntity
import com.morimil.app.data.local.GenesisCoreEntity
import com.morimil.app.data.local.LocalInstanceIdentityEntity
import com.morimil.app.data.local.MemoryEventEntity
import com.morimil.app.data.local.MemoryDao
import com.morimil.app.data.local.MemoryMessageEntity
import com.morimil.app.data.local.MemorySnapshotEntity
import com.morimil.app.data.local.MorimilDatabase
import com.morimil.app.data.local.ProjectStateEntity
import com.morimil.app.data.local.UserWorkspaceEntity
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

class MemoryRepository(private val database: MorimilDatabase) {
    private val memoryDao: MemoryDao = database.memoryDao()

    val messages: Flow<List<MemoryMessageEntity>> = memoryDao.observeMessages()
    val decisions: Flow<List<DecisionLogEntity>> = memoryDao.observeDecisions()
    val projects: Flow<List<ProjectStateEntity>> = memoryDao.observeProjects()
    val activeWorkspace: Flow<UserWorkspaceEntity?> = memoryDao.observeActiveWorkspace()
    val localIdentity: Flow<LocalInstanceIdentityEntity?> = memoryDao.observeLocalIdentity()
    val genesisCore: Flow<GenesisCoreEntity?> = memoryDao.observeGenesisCore()
    val recentMemoryEvents: Flow<List<MemoryEventEntity>> = memoryDao.observeRecentMemoryEvents()
    val livingMemorySnapshot: Flow<MemorySnapshotEntity?> = memoryDao.observeLivingMemorySnapshot()

    suspend fun addUserMessage(body: String) {
        memoryDao.insertMessage(
            MemoryMessageEntity(
                author = "user",
                body = body,
                createdAtMillis = System.currentTimeMillis()
            )
        )
        appendMemoryEvent(
            eventType = "conversation.user_message",
            actor = "user",
            body = body,
            importance = scoreImportance(body)
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
        appendMemoryEvent(
            eventType = "conversation.assistant_message",
            actor = "morimil",
            body = body,
            importance = scoreImportance(body)
        )
    }

    /**
     * Only touches the display name. The local birth identity is never
     * replaced after onboarding.
     */
    suspend fun renameWorkspace(displayName: String): List<String> {
        val clean = displayName.trim()
        if (clean.isEmpty()) {
            return listOf("Display name cannot be empty.")
        }
        val rows = memoryDao.renameWorkspace(clean, System.currentTimeMillis())
        return if (rows == 0) listOf("No workspace exists yet -- create your instance first.") else emptyList()
    }

    suspend fun hasExistingBirth(): Boolean {
        return memoryDao.countLocalIdentity() > 0 || memoryDao.countGenesisCore() > 0
    }

    /**
     * Names this device's instance exactly once. Genesis is bundled inside
     * the app and copied into local state; GitHub is not consulted at birth.
     */
    suspend fun birthLocalIdentity(
        alias: String,
        genesis: GenesisIdentity,
        sourceOrigin: String,
        genesisCoreHash: String,
        doctrineText: String?,
        policyText: String?
    ) {
        val cleanAlias = alias.trim().ifBlank { "Morimil" }
        val instanceId = "${genesis.agentId}.${cleanAlias.lowercase().replace(Regex("[^a-z0-9]+"), "-")}"
        val localMemoryRef = "local://morimil/$instanceId"

        database.withTransaction {
            require(memoryDao.countLocalIdentity() == 0) { "This Morimil instance has already been born." }
            require(memoryDao.countGenesisCore() == 0) { "Genesis Core already exists on this device." }

            memoryDao.insertLocalIdentity(
                LocalInstanceIdentityEntity(
                    instanceId = instanceId,
                    alias = cleanAlias,
                    bornAtMillis = System.currentTimeMillis(),
                    genesisAgentId = genesis.agentId,
                    genesisRole = genesis.role,
                    genesisRiskTier = genesis.riskTier,
                    genesisSchemaVersion = genesis.schemaVersion,
                    localMemoryOwner = "local_device",
                    localMemoryName = "morimil_local_memory",
                    localMemoryUri = localMemoryRef
                )
            )

            val core = GenesisCoreEntity(
                instanceId = instanceId,
                aliasAtBirth = cleanAlias,
                copiedAtMillis = System.currentTimeMillis(),
                sourceOrigin = sourceOrigin,
                schemaVersion = genesis.schemaVersion,
                agentId = genesis.agentId,
                role = genesis.role,
                owner = genesis.owner,
                riskTier = genesis.riskTier,
                doctrineRef = genesis.doctrineRef,
                policyRef = genesis.policyRef,
                allowedActionsJson = JSONArray(genesis.allowedActions).toString(),
                disallowedActionsJson = JSONArray(genesis.disallowedActions).toString(),
                doctrineText = doctrineText,
                policyText = policyText,
                contentSha256 = genesisCoreHash
            )
            memoryDao.insertGenesisCore(core)

            memoryDao.upsertWorkspace(
                UserWorkspaceEntity(
                    workspaceId = "local_primary",
                    displayName = cleanAlias,
                    genesisSource = genesis.agentId,
                    localPrimary = true,
                    optionalRepoOwner = null,
                    optionalRepoName = null,
                    optionalRepoPrivate = false,
                    repoProposalApproved = true,
                    updatedAtMillis = System.currentTimeMillis()
                )
            )

            insertMemoryEventAndRebuildSnapshot(
                eventType = "genesis.birth",
                actor = "system",
                body = "Instancia $cleanAlias nacida desde Genesis Core ${genesis.agentId}. Memoria local: $localMemoryRef.",
                importance = 100
            )

            require(memoryDao.countLocalIdentity() == 1) { "Birth invariant failed: local identity missing." }
            require(memoryDao.countGenesisCore() == 1) { "Birth invariant failed: Genesis Core missing." }
            require(memoryDao.countWorkspaces() >= 1) { "Birth invariant failed: workspace missing." }
            require(memoryDao.countMemoryEvents() >= 1) { "Birth invariant failed: birth event missing." }
            require(memoryDao.countLivingMemorySnapshot() == 1) { "Birth invariant failed: living memory snapshot missing." }
        }
    }

    suspend fun seedInitialStateIfNeeded() {
        if (memoryDao.countGenesisCore() == 0) return

        if (memoryDao.countMessages() == 0) {
            addAssistantMessage("Genesis movil v1 activo. Memoria local Room/SQLite conectada.")
            addAssistantMessage("Voz manual activa. Sin sincronizacion externa ni ejecucion de PC.")
        }

        memoryDao.upsertProject(
            ProjectStateEntity(
                projectId = "morimil_app",
                title = "Morimil_app",
                status = "phase_genesis_mobile_seed_v1",
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
            appendMemoryEvent(
                eventType = "decision.local_memory_enabled",
                actor = "system",
                body = "Room/SQLite local memory enabled as persistent phone memory.",
                importance = 90
            )
        }
    }

    suspend fun buildLivingMemoryContext(): String {
        val snapshot = memoryDao.getLivingMemorySnapshot()
        val events = memoryDao.loadMemoryContext()
            .sortedWith(compareBy<MemoryEventEntity> { it.createdAtMillis }.thenBy { it.id })

        val snapshotText = snapshot?.summary ?: "No living memory snapshot yet."
        val eventText = events.joinToString("\n") { event ->
            "- [${event.eventType}/${event.actor}/i${event.importance}] ${event.body.take(500)}"
        }

        return """
            LIVING MEMORY SNAPSHOT:
            $snapshotText

            RELEVANT LOCAL MEMORY EVENTS:
            ${eventText.ifBlank { "- No memory events yet." }}
        """.trimIndent()
    }

    private suspend fun appendMemoryEvent(
        eventType: String,
        actor: String,
        body: String,
        importance: Int
    ) {
        if (body.isBlank()) return
        if (memoryDao.countGenesisCore() == 0) return
        insertMemoryEventAndRebuildSnapshot(eventType, actor, body, importance)
    }

    private suspend fun insertMemoryEventAndRebuildSnapshot(
        eventType: String,
        actor: String,
        body: String,
        importance: Int
    ) {
        memoryDao.insertMemoryEvent(
            MemoryEventEntity(
                genesisCoreId = "primary_genesis",
                eventType = eventType,
                actor = actor,
                body = body.trim(),
                importance = importance.coerceIn(1, 100),
                createdAtMillis = System.currentTimeMillis()
            )
        )
        rebuildLivingMemorySnapshot()
    }

    private suspend fun rebuildLivingMemorySnapshot() {
        val events = memoryDao.loadMemoryContext(limit = 12)
        val eventCount = memoryDao.countMemoryEvents()
        val messageCount = memoryDao.countMessages()
        val important = events
            .sortedWith(compareByDescending<MemoryEventEntity> { it.importance }.thenByDescending { it.createdAtMillis })
            .take(6)
            .joinToString(" ") { it.body.take(180) }
            .ifBlank { "Genesis Core copied; living memory is waiting for lived events." }

        memoryDao.upsertMemorySnapshot(
            MemorySnapshotEntity(
                genesisCoreId = "primary_genesis",
                summary = important,
                eventCount = eventCount,
                messageCount = messageCount,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    private fun scoreImportance(body: String): Int {
        val lower = body.lowercase()
        return when {
            listOf("decid", "nombre", "genesis", "memoria", "api", "nunca", "siempre", "importante")
                .any { lower.contains(it) } -> 85
            body.length > 240 -> 65
            else -> 40
        }
    }

}
