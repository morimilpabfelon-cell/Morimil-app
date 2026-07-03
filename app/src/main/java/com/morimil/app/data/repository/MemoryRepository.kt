package com.morimil.app.data.repository

import androidx.room.withTransaction
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
import java.security.MessageDigest

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

    suspend fun addAssistantMessage(body: String) {
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
            Treat these local memory events as the valid phone memory. Prefer user-confirmed decisions, corrections and preferences over generic conversation text.
        """.trimIndent()
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
    private suspend fun appendMemoryEvent(
        eventType: String,
        actor: String,
        body: String,
        importance: Int
    ) {
        if (body.isBlank()) return
        require(memoryDao.countGenesisCore() > 0) {
            "Cannot append living memory without a local Genesis Core."
        }
        insertMemoryEventAndRebuildSnapshot(eventType, actor, body, importance)
    }

    private suspend fun insertMemoryEventAndRebuildSnapshot(
        eventType: String,
        actor: String,
        body: String,
        importance: Int
    ) {
        val cleanBody = body.trim()
        val createdAtMillis = System.currentTimeMillis()
        val genesisCore = requireNotNull(memoryDao.loadGenesisCore()) {
            "Cannot append living memory without a local Genesis Core."
        }
        val existingChain = memoryDao.loadMemoryEventChain()
        require(verifyMemoryEventChain(existingChain)) {
            "Living memory chain integrity failed. Refusing to append a new event."
        }
        val source = if (actor == "user" || actor == "morimil") "chat" else "system"
        val contextTag = "local_runtime"
        val privacyVisibility = PRIVATE_LOCAL
        val classification = classifyMemoryEvent(eventType, actor, cleanBody)
        val cleanImportance = maxOf(importance, classification.importance).coerceIn(1, 100)
        val tagsJson = JSONArray(classification.tags).toString()
        val evidenceJson = buildEvidenceJson(
            eventType = eventType,
            actor = actor,
            source = source,
            classification = classification,
            body = cleanBody
        )
        val previousEventHash = existingChain.lastOrNull()?.eventHash
        val eventHash = hashMemoryEventV3(
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

        memoryDao.insertMemoryEvent(
            MemoryEventEntity(
                genesisCoreId = genesisCore.coreId,
                genesisCoreHash = genesisCore.contentSha256,
                previousEventHash = previousEventHash,
                eventHash = eventHash,
                hashAlgorithm = "sha256",
                canonicalization = MEMORY_EVENT_CANONICALIZATION_V3,
                signatureAlgorithm = null,
                eventSignature = null,
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
    }

    private suspend fun rebuildLivingMemorySnapshot() {
        val events = memoryDao.loadMemoryContext(limit = 20)
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
        return classifyMemoryEvent("conversation.user_message", "user", body).importance
    }

    private fun classifyMemoryEvent(eventType: String, actor: String, body: String): MemoryClassification {
        val lower = body.lowercase()
        val tags = linkedSetOf<String>()
        if (lower.contains("genesis") || lower.contains("gÃ©nesis")) tags += "genesis"
        if (lower.contains("memoria") || lower.contains("recuerd")) tags += "memory"
        if (lower.contains("api") || lower.contains("motor") || lower.contains("razon")) tags += "reasoning"
        if (lower.contains("app") || lower.contains("celular") || lower.contains("local")) tags += "local_app"
        if (lower.contains("proyecto") || lower.contains("repo") || lower.contains("github")) tags += "project"
        if (lower.contains("privad") || lower.contains("segur")) tags += "privacy"
        if (lower.contains("grafo") || lower.contains("backlink") || lower.contains("obsidian")) tags += "memory_graph"

        val isUser = actor == "user"
        val confirmed = isUser && listOf("correcto", "confirmo", "aprob", "sÃ­", "si,").any { lower.contains(it) }

        val kind = when {
            eventType.startsWith("genesis") -> "identity"
            listOf("corrige", "correccion", "correcciÃ³n", "no es asi", "no es asÃ­", "te equivocas").any { lower.contains(it) } -> "correction"
            listOf("error", "fallo", "bug", "no funciona").any { lower.contains(it) } -> "error_detected"
            listOf("apruebo", "aprobado", "dale", "correcto").any { lower.contains(it) } && isUser -> "approval"
            listOf("rechazo", "no apruebo", "cancel", "no lo hagas").any { lower.contains(it) } -> "rejection"
            listOf("decid", "queda", "regla", "nunca", "siempre", "se define").any { lower.contains(it) } -> "decision"
            listOf("prefiero", "me gusta", "quiero", "no quiero", "mi forma").any { lower.contains(it) } -> "preference"
            listOf("aprend", "actualiza", "libro", "program", "investiga").any { lower.contains(it) } -> "learning"
            eventType.startsWith("decision") -> "decision"
            else -> "conversation"
        }

        tags += when (kind) {
            "identity" -> "identity"
            "correction" -> "correction"
            "error_detected" -> "error"
            "approval" -> "approval"
            "rejection" -> "rejection"
            "decision" -> "decision"
            "preference" -> "preference"
            "learning" -> "learning"
            else -> "conversation"
        }

        val importance = when (kind) {
            "identity" -> 100
            "decision", "correction", "approval", "rejection" -> 92
            "preference" -> 88
            "learning", "error_detected" -> 82
            "conversation" -> if (body.length > 240) 62 else 38
            else -> 50
        }
        val confidence = when {
            confirmed -> 95
            isUser && kind != "conversation" -> 88
            actor == "system" -> 90
            else -> 70
        }

        return MemoryClassification(
            memoryKind = kind,
            tags = tags.toList(),
            confidence = confidence,
            userConfirmed = confirmed,
            importance = importance
        )
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

    private fun verifyMemoryEventChain(events: List<MemoryEventEntity>): Boolean {
        var expectedPreviousHash: String? = null
        events.forEach { event ->
            if (event.eventHash == LEGACY_EVENT_HASH) {
                expectedPreviousHash = event.eventHash
                return@forEach
            }
            if (event.previousEventHash != expectedPreviousHash) return false
            if (event.hashAlgorithm != "sha256") return false

            val expectedHash = when (event.canonicalization) {
                MEMORY_EVENT_CANONICALIZATION_V1 -> hashMemoryEventV1(
                    genesisCoreId = event.genesisCoreId,
                    genesisCoreHash = event.genesisCoreHash,
                    previousEventHash = event.previousEventHash,
                    eventType = event.eventType,
                    actor = event.actor,
                    body = event.body,
                    importance = event.importance,
                    createdAtMillis = event.createdAtMillis
                )
                MEMORY_EVENT_CANONICALIZATION_V2 -> hashMemoryEventV2(
                    genesisCoreId = event.genesisCoreId,
                    genesisCoreHash = event.genesisCoreHash,
                    previousEventHash = event.previousEventHash,
                    eventType = event.eventType,
                    actor = event.actor,
                    source = event.source,
                    contextTag = event.contextTag,
                    privacyVisibility = event.privacyVisibility,
                    body = event.body,
                    importance = event.importance,
                    createdAtMillis = event.createdAtMillis
                )
                MEMORY_EVENT_CANONICALIZATION_V3 -> hashMemoryEventV3(
                    genesisCoreId = event.genesisCoreId,
                    genesisCoreHash = event.genesisCoreHash,
                    previousEventHash = event.previousEventHash,
                    eventType = event.eventType,
                    actor = event.actor,
                    source = event.source,
                    contextTag = event.contextTag,
                    privacyVisibility = event.privacyVisibility,
                    memoryKind = event.memoryKind,
                    tagsJson = event.tagsJson,
                    evidenceJson = event.evidenceJson,
                    confidence = event.confidence,
                    userConfirmed = event.userConfirmed,
                    body = event.body,
                    importance = event.importance,
                    createdAtMillis = event.createdAtMillis
                )
                else -> return false
            }
            if (event.eventHash != expectedHash) return false
            expectedPreviousHash = event.eventHash
        }
        return true
    }

    private fun hashMemoryEventV1(
        genesisCoreId: String,
        genesisCoreHash: String,
        previousEventHash: String?,
        eventType: String,
        actor: String,
        body: String,
        importance: Int,
        createdAtMillis: Long
    ): String {
        return hashFields(
            mapOf(
                "actor" to actor,
                "body" to body,
                "canonicalization" to MEMORY_EVENT_CANONICALIZATION_V1,
                "createdAtMillis" to createdAtMillis,
                "eventType" to eventType,
                "genesisCoreId" to genesisCoreId,
                "genesisCoreHash" to genesisCoreHash,
                "hashAlgorithm" to "sha256",
                "importance" to importance,
                "previousEventHash" to previousEventHash
            )
        )
    }

    private fun hashMemoryEventV2(
        genesisCoreId: String,
        genesisCoreHash: String,
        previousEventHash: String?,
        eventType: String,
        actor: String,
        source: String,
        contextTag: String,
        privacyVisibility: String,
        body: String,
        importance: Int,
        createdAtMillis: Long
    ): String {
        return hashFields(
            mapOf(
                "actor" to actor,
                "body" to body,
                "canonicalization" to MEMORY_EVENT_CANONICALIZATION_V2,
                "contextTag" to contextTag,
                "createdAtMillis" to createdAtMillis,
                "eventType" to eventType,
                "genesisCoreId" to genesisCoreId,
                "genesisCoreHash" to genesisCoreHash,
                "hashAlgorithm" to "sha256",
                "importance" to importance,
                "previousEventHash" to previousEventHash,
                "privacyVisibility" to privacyVisibility,
                "source" to source
            )
        )
    }

    private fun hashMemoryEventV3(
        genesisCoreId: String,
        genesisCoreHash: String,
        previousEventHash: String?,
        eventType: String,
        actor: String,
        source: String,
        contextTag: String,
        privacyVisibility: String,
        memoryKind: String,
        tagsJson: String,
        evidenceJson: String,
        confidence: Int,
        userConfirmed: Boolean,
        body: String,
        importance: Int,
        createdAtMillis: Long
    ): String {
        return hashFields(
            mapOf(
                "actor" to actor,
                "body" to body,
                "canonicalization" to MEMORY_EVENT_CANONICALIZATION_V3,
                "confidence" to confidence,
                "contextTag" to contextTag,
                "createdAtMillis" to createdAtMillis,
                "eventType" to eventType,
                "evidenceJson" to evidenceJson,
                "genesisCoreId" to genesisCoreId,
                "genesisCoreHash" to genesisCoreHash,
                "hashAlgorithm" to "sha256",
                "importance" to importance,
                "memoryKind" to memoryKind,
                "previousEventHash" to previousEventHash,
                "privacyVisibility" to privacyVisibility,
                "source" to source,
                "tagsJson" to tagsJson,
                "userConfirmed" to userConfirmed
            )
        )
    }

    private fun hashFields(fields: Map<String, Any?>): String {
        val canonical = stableStringify(fields)
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return "sha256:" + digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun stableStringify(value: Any?): String {
        return when (value) {
            null -> "null"
            is String -> quoteJsonString(value)
            is Number, is Boolean -> value.toString()
            is Map<*, *> -> value.keys
                .filterIsInstance<String>()
                .sorted()
                .joinToString(prefix = "{", postfix = "}", separator = ",") { key ->
                    "${quoteJsonString(key)}:${stableStringify(value[key])}"
                }
            else -> quoteJsonString(value.toString())
        }
    }

    private fun quoteJsonString(value: String): String {
        val output = StringBuilder(value.length + 2)
        output.append('"')
        value.forEach { char ->
            when (char) {
                '"' -> output.append("\\\"")
                '\\' -> output.append("\\\\")
                '\b' -> output.append("\\b")
                '\u000C' -> output.append("\\f")
                '\n' -> output.append("\\n")
                '\r' -> output.append("\\r")
                '\t' -> output.append("\\t")
                else -> {
                    if (char.code < 0x20) {
                        output.append("\\u")
                        output.append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        output.append(char)
                    }
                }
            }
        }
        output.append('"')
        return output.toString()
    }

    private data class MemoryClassification(
        val memoryKind: String,
        val tags: List<String>,
        val confidence: Int,
        val userConfirmed: Boolean,
        val importance: Int
    )

    companion object {
        private const val LEGACY_EVENT_HASH = "sha256:legacy-unverified"
        private const val MEMORY_EVENT_CANONICALIZATION_V1 = "morimil.memory_event_hash.v1"
        private const val MEMORY_EVENT_CANONICALIZATION_V2 = "morimil.memory_event_hash.v2"
        private const val MEMORY_EVENT_CANONICALIZATION_V3 = "morimil.memory_event_hash.v3"
        private const val PRIVATE_LOCAL = "private_local"
    }
}