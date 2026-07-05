package com.morimil.app.data.repository

import androidx.room.withTransaction
import com.morimil.app.core.memory.MemoryEventSigner
import com.morimil.app.core.memory.MemoryIntegrityCore
import com.morimil.app.core.memory.UnsignedMemoryEventSigner
import com.morimil.app.data.genesis.GenesisIdentity
import com.morimil.app.data.local.DecisionLogEntity
import com.morimil.app.data.local.GenesisCoreEntity
import com.morimil.app.data.local.LocalInstanceIdentityEntity
import com.morimil.app.data.local.MemoryDao
import com.morimil.app.data.local.MemoryEventEntity
import com.morimil.app.data.local.MemoryMessageEntity
import com.morimil.app.data.local.MemorySnapshotEntity
import com.morimil.app.data.local.MorimilDatabase
import com.morimil.app.data.local.ProjectStateEntity
import com.morimil.app.data.local.UserWorkspaceEntity
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

class MemoryRepository(
    private val database: MorimilDatabase,
    private val memoryIntegrityCore: MemoryIntegrityCore = MemoryIntegrityCore(),
    private val memoryEventSigner: MemoryEventSigner = UnsignedMemoryEventSigner
) {
    private val memoryDao: MemoryDao = database.memoryDao()

    val messages: Flow<List<MemoryMessageEntity>> = memoryDao.observeMessages()
    val decisions: Flow<List<DecisionLogEntity>> = memoryDao.observeDecisions()
    val projects: Flow<List<ProjectStateEntity>> = memoryDao.observeProjects()
    val activeWorkspace: Flow<UserWorkspaceEntity?> = memoryDao.observeActiveWorkspace()
    val localIdentity: Flow<LocalInstanceIdentityEntity?> = memoryDao.observeLocalIdentity()
    val genesisCore: Flow<GenesisCoreEntity?> = memoryDao.observeGenesisCore()
    val recentMemoryEvents: Flow<List<MemoryEventEntity>> = memoryDao.observeRecentMemoryEvents()
    val livingMemorySnapshot: Flow<MemorySnapshotEntity?> = memoryDao.observeLivingMemorySnapshot()

    suspend fun addUserMessage(body: String): AppendedMemoryEventReference? {
        return MemoryAppendGate.withAppendLock {
            database.withTransaction {
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
        }
    }

    suspend fun addAssistantMessage(body: String) {
        MemoryAppendGate.withAppendLock {
            database.withTransaction {
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
        }
    }

    suspend fun renameWorkspace(displayName: String): List<String> {
        return listOf("El nombre solo se define una vez.")
    }

    suspend fun hasExistingBirth(): Boolean {
        return memoryDao.countLocalIdentity() > 0 || memoryDao.countGenesisCore() > 0
    }

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

        MemoryAppendGate.withAppendLock {
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
            MemoryAppendGate.withAppendLock {
                database.withTransaction {
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
            }
        }
    }

    suspend fun buildLivingMemoryContext(): String {
        val snapshot = memoryDao.getLivingMemorySnapshot()
        val events = memoryDao.loadMemoryContext(30)
            .sortedWith(compareBy<MemoryEventEntity> { it.createdAtMillis }.thenBy { it.id })

        val snapshotText = snapshot?.summary ?: "No living memory snapshot yet."
        val eventText = events.joinToString("\n") { event ->
            "- [${event.memoryKind}/${event.eventType}/${event.actor}/${event.source}/${event.privacyVisibility}/i${event.importance}/c${event.confidence}/${event.eventHash.take(19)}] " +
                "tags=${event.tagsJson} evidence=${event.evidenceJson.take(180)} text=${event.body.take(500)}"
        }

        return """
            LIVING MEMORY SNAPSHOT:
            $snapshotText

            RELEVANT LOCAL MEMORY EVENTS:
            ${eventText.ifBlank { "- No memory events yet." }}

            MEMORY RULE:
            Treat these local memory events as the valid phone memory. Prefer user-confirmed decisions, corrections and preferences over generic conversation text. Ignore chat_noise unless the user explicitly confirms it.
        """.trimIndent()
    }

    suspend fun auditLivingMemoryChain(): Boolean {
        return memoryIntegrityCore.verifyMemoryEventChain(memoryDao.loadMemoryEventAuditChain())
    }

    suspend fun recordMemoryReview(
        targetEvent: MemoryEventEntity,
        action: String,
        note: String
    ) {
        val cleanAction = action.trim()
            .ifBlank { "reviewed" }
            .replace(Regex("[^a-zA-Z0-9_.-]+"), "_")
        val cleanNote = note.trim().ifBlank { "Revision local de memoria." }
        val reviewImportance = when (cleanAction) {
            "aprobado" -> 80
            "correccion_requerida" -> 90
            "ruido_degradado" -> 30
            else -> 60
        }

        MemoryAppendGate.withAppendLock {
            database.withTransaction {
                insertMemoryEventAndRebuildSnapshot(
                    eventType = "memory_review.$cleanAction",
                    actor = "user",
                    body = "Revision local de memoria: action=$cleanAction; " +
                        "target_event_hash=${targetEvent.eventHash}; " +
                        "target_kind=${targetEvent.memoryKind}; " +
                        "note=$cleanNote; excerpt=${targetEvent.body.take(220)}",
                    importance = reviewImportance
                )
            }
        }
    }

    suspend fun recordSystemMemoryEvent(
        eventType: String,
        body: String,
        importance: Int
    ): String? {
        if (body.isBlank()) return null
        return MemoryAppendGate.withAppendLock {
            database.withTransaction {
                require(memoryDao.countGenesisCore() > 0) {
                    "Cannot append living memory without a local Genesis Core."
                }
                insertMemoryEventAndRebuildSnapshot(
                    eventType = eventType,
                    actor = "system",
                    body = body,
                    importance = importance
                )
            }
        }
    }

    private suspend fun appendMemoryEvent(
        eventType: String,
        actor: String,
        body: String,
        importance: Int
    ): AppendedMemoryEventReference? {
        if (body.isBlank()) return null
        val genesisCore = requireNotNull(memoryDao.loadGenesisCore()) {
            "Cannot append living memory without a local Genesis Core."
        }
        val eventHash = insertMemoryEventAndRebuildSnapshot(eventType, actor, body, importance)
        return AppendedMemoryEventReference(
            eventHash = eventHash,
            genesisCoreId = genesisCore.coreId,
            genesisCoreHash = genesisCore.contentSha256,
            instanceId = genesisCore.instanceId
        )
    }

    private suspend fun insertMemoryEventAndRebuildSnapshot(
        eventType: String,
        actor: String,
        body: String,
        importance: Int
    ): String {
        val cleanBody = body.trim()
        val createdAtMillis = System.currentTimeMillis()
        val genesisCore = requireNotNull(memoryDao.loadGenesisCore()) {
            "Cannot append living memory without a local Genesis Core."
        }
        val previousEventHash = resolveMemoryAppendPreviousHash(genesisCore, createdAtMillis)
        val source = if (actor == "user" || actor == "morimil") "chat" else "system"
        val contextTag = "local_runtime"
        val privacyVisibility = PRIVATE_LOCAL
        val classification = MemoryEventClassifier.classify(eventType, actor, cleanBody)
        val cleanImportance = if (classification.memoryKind == "chat_noise") {
            classification.importance.coerceIn(1, 20)
        } else {
            maxOf(importance, classification.importance).coerceIn(1, 100)
        }
        val tagsJson = JSONArray(classification.tags).toString()
        val evidenceJson = buildEvidenceJson(
            eventType = eventType,
            actor = actor,
            source = source,
            classification = classification,
            body = cleanBody
        )
        val eventHash = memoryIntegrityCore.hashMemoryEventV3(
            genesisCoreId = genesisCore.coreId,
            genesisCoreHash = genesisCore.contentSha256,
            previousEventHash = previousEventHash,
            eventType = eventType,
            actor = actor,
            source = source,
            contextTag = contextTag,
            privacyVisibility = privacyVisibility,
            memoryKind = classification.memoryKind,
            tagsJson = tagsJson,
            evidenceJson = evidenceJson,
            confidence = classification.confidence,
            userConfirmed = classification.userConfirmed,
            body = cleanBody,
            importance = cleanImportance,
            createdAtMillis = createdAtMillis
        )
        val signature = memoryEventSigner.signEventHash(eventHash)

        memoryDao.insertMemoryEvent(
            MemoryEventEntity(
                genesisCoreId = genesisCore.coreId,
                genesisCoreHash = genesisCore.contentSha256,
                previousEventHash = previousEventHash,
                eventHash = eventHash,
                hashAlgorithm = MemoryIntegrityCore.HASH_ALGORITHM_SHA256,
                canonicalization = MemoryIntegrityCore.MEMORY_EVENT_CANONICALIZATION_V3,
                signatureAlgorithm = signature.signatureAlgorithm,
                eventSignature = signature.eventSignature,
                eventType = eventType,
                actor = actor,
                source = source,
                contextTag = contextTag,
                privacyVisibility = privacyVisibility,
                memoryKind = classification.memoryKind,
                tagsJson = tagsJson,
                evidenceJson = evidenceJson,
                confidence = classification.confidence,
                userConfirmed = classification.userConfirmed,
                body = cleanBody,
                importance = cleanImportance,
                createdAtMillis = createdAtMillis
            )
        )
        rebuildLivingMemorySnapshot()
        return eventHash
    }

    private suspend fun rebuildLivingMemorySnapshot() {
        val events = memoryDao.loadMemoryContext(limit = 50)
            .filter { event -> event.memoryKind != "chat_noise" || event.userConfirmed || event.importance >= 40 }
        val eventCount = memoryDao.countMemoryEvents()
        val messageCount = memoryDao.countMessages()
        val prioritized = events
            .sortedWith(
                compareByDescending<MemoryEventEntity> { it.userConfirmed }
                    .thenByDescending { it.importance }
                    .thenByDescending { it.confidence }
                    .thenByDescending { it.createdAtMillis }
            )
            .take(8)
            .joinToString("\n") { event ->
                "- ${event.memoryKind}: ${event.body.take(180)} (${event.eventHash.take(19)})"
            }
            .ifBlank { "Genesis Core copied; living memory is waiting for lived events." }

        memoryDao.upsertMemorySnapshot(
            MemorySnapshotEntity(
                genesisCoreId = "primary_genesis",
                summary = prioritized,
                eventCount = eventCount,
                messageCount = messageCount,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    private fun scoreImportance(body: String): Int {
        return MemoryEventClassifier.scoreImportance(body)
    }

    private fun buildEvidenceJson(
        eventType: String,
        actor: String,
        source: String,
        classification: MemoryClassification,
        body: String
    ): String {
        return JSONObject()
            .put("schema", "morimil.memory_evidence.v1")
            .put("classifier", "local_keyword_v1")
            .put("event_type", eventType)
            .put("actor", actor)
            .put("source", source)
            .put("memory_kind", classification.memoryKind)
            .put("user_confirmed", classification.userConfirmed)
            .put("confidence", classification.confidence)
            .put("excerpt", body.take(240))
            .toString()
    }

    private suspend fun resolveMemoryAppendPreviousHash(
        genesisCore: GenesisCoreEntity,
        createdAtMillis: Long
    ): String? {
        val recoveryBoundary = memoryDao.loadLatestMemoryEventByType(MEMORY_INTEGRITY_QUARANTINE_EVENT_TYPE)
        val eventTail = if (recoveryBoundary == null) {
            memoryDao.loadMemoryEventTail(MEMORY_EVENT_TAIL_VERIFICATION_LIMIT)
        } else {
            memoryDao.loadMemoryEventTailAfterLatestEventType(
                eventType = MEMORY_INTEGRITY_QUARANTINE_EVENT_TYPE,
                limit = MEMORY_EVENT_TAIL_VERIFICATION_LIMIT
            )
        }.asReversed()
        val tailIntegrity = memoryIntegrityCore.inspectMemoryEventTail(
            events = eventTail,
            fallbackPreviousHash = recoveryBoundary?.eventHash
        )
        if (tailIntegrity.trusted) return tailIntegrity.appendPreviousEventHash

        return insertMemoryIntegrityQuarantineEvent(
            genesisCore = genesisCore,
            previousEventHash = tailIntegrity.lastTrustedEventHash ?: recoveryBoundary?.eventHash,
            firstUntrustedHash = tailIntegrity.firstUntrustedHash,
            reason = tailIntegrity.reason ?: "unknown_tail_integrity_break",
            createdAtMillis = createdAtMillis
        )
    }

    private suspend fun insertMemoryIntegrityQuarantineEvent(
        genesisCore: GenesisCoreEntity,
        previousEventHash: String?,
        firstUntrustedHash: String?,
        reason: String,
        createdAtMillis: Long
    ): String {
        val tagsJson = JSONArray(listOf("memory_integrity", "quarantine", "recovery")).toString()
        val evidenceJson = JSONObject()
            .put("schema", "morimil.memory_integrity_quarantine.v1")
            .put("reason", reason)
            .put("first_untrusted_hash", firstUntrustedHash)
            .put("recovery_previous_hash", previousEventHash)
            .put("policy", "isolate_untrusted_tail_and_continue_local_memory")
            .toString()
        val body = "ALERTA_MEMORIA_LOCAL: Se detecto una ruptura de integridad en la cola de memoria. " +
            "El tramo no confiable quedo aislado y la memoria continua desde este marcador de cuarentena. " +
            "reason=$reason; first_untrusted_hash=${firstUntrustedHash ?: "unknown"}"
        val eventHash = memoryIntegrityCore.hashMemoryEventV3(
            genesisCoreId = genesisCore.coreId,
            genesisCoreHash = genesisCore.contentSha256,
            previousEventHash = previousEventHash,
            eventType = MEMORY_INTEGRITY_QUARANTINE_EVENT_TYPE,
            actor = "system",
            source = "local_integrity",
            contextTag = "local_integrity",
            privacyVisibility = PRIVATE_LOCAL,
            memoryKind = "integrity_quarantine",
            tagsJson = tagsJson,
            evidenceJson = evidenceJson,
            confidence = 100,
            userConfirmed = false,
            body = body,
            importance = 100,
            createdAtMillis = createdAtMillis
        )
        val signature = memoryEventSigner.signEventHash(eventHash)

        memoryDao.insertMemoryEvent(
            MemoryEventEntity(
                genesisCoreId = genesisCore.coreId,
                genesisCoreHash = genesisCore.contentSha256,
                previousEventHash = previousEventHash,
                eventHash = eventHash,
                hashAlgorithm = MemoryIntegrityCore.HASH_ALGORITHM_SHA256,
                canonicalization = MemoryIntegrityCore.MEMORY_EVENT_CANONICALIZATION_V3,
                signatureAlgorithm = signature.signatureAlgorithm,
                eventSignature = signature.eventSignature,
                eventType = MEMORY_INTEGRITY_QUARANTINE_EVENT_TYPE,
                actor = "system",
                source = "local_integrity",
                contextTag = "local_integrity",
                privacyVisibility = PRIVATE_LOCAL,
                memoryKind = "integrity_quarantine",
                tagsJson = tagsJson,
                evidenceJson = evidenceJson,
                confidence = 100,
                userConfirmed = false,
                body = body,
                importance = 100,
                createdAtMillis = createdAtMillis
            )
        )
        return eventHash
    }

    companion object {
        private const val MEMORY_INTEGRITY_QUARANTINE_EVENT_TYPE = "memory_integrity.quarantine"
        private const val MEMORY_EVENT_TAIL_VERIFICATION_LIMIT = 12
        private const val PRIVATE_LOCAL = "private_local"
    }
}

data class AppendedMemoryEventReference(
    val eventHash: String,
    val genesisCoreId: String,
    val genesisCoreHash: String,
    val instanceId: String
)
