package com.morimil.app.data.repository

import androidx.room.withTransaction
import com.morimil.app.data.local.MemoryDao
import com.morimil.app.data.local.MemoryEventEntity
import com.morimil.app.data.local.MemorySnapshotEntity
import com.morimil.app.data.local.MorimilDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

class RestCycleRepository(private val database: MorimilDatabase) {
    private val memoryDao: MemoryDao = database.memoryDao()

    suspend fun runLocalRestCycleIfDue(force: Boolean = false): Boolean {
        if (memoryDao.countGenesisCore() == 0) return false

        val now = System.currentTimeMillis()
        val latestRestCycle = memoryDao.loadLatestRestCycleEvent()
        if (!force && latestRestCycle != null &&
            now - latestRestCycle.createdAtMillis < REST_CYCLE_MIN_INTERVAL_MILLIS
        ) {
            return false
        }

        val events = memoryDao.loadMemoryContext(80)
            .filter { it.eventType != REST_CYCLE_EVENT_TYPE }
            .sortedWith(compareBy<MemoryEventEntity> { it.createdAtMillis }.thenBy { it.id })

        val meaningfulEvents = events.filter { event ->
            event.memoryKind != "conversation" || event.importance >= 60
        }
        if (!force && meaningfulEvents.size < REST_CYCLE_MIN_EVENTS) return false

        val summary = buildRestCycleSummary(events, now)
        if (summary.isBlank()) return false

        appendRestCycleEvent(summary)
        return true
    }

    private suspend fun appendRestCycleEvent(summary: String) {
        database.withTransaction {
            val genesisCore = requireNotNull(memoryDao.loadGenesisCore()) {
                "Cannot run rest cycle without a local Genesis Core."
            }
            val eventTail = memoryDao.loadMemoryEventTail(MEMORY_EVENT_TAIL_VERIFICATION_LIMIT).asReversed()
            require(verifyMemoryEventChain(eventTail, requireGenesisStart = false)) {
                "Living memory chain integrity failed. Refusing to append rest cycle."
            }

            val createdAtMillis = System.currentTimeMillis()
            val previousEventHash = eventTail.lastOrNull()?.eventHash
            val tagsJson = JSONArray(listOf("rest_cycle", "local_consolidation", "snapshot")).toString()
            val evidenceJson = JSONObject()
                .put("schema", "morimil.memory_evidence.v1")
                .put("classifier", "local_rest_cycle_v1")
                .put("event_type", REST_CYCLE_EVENT_TYPE)
                .put("actor", "system")
                .put("source", "local_rest_cycle")
                .put("memory_kind", "rest_cycle")
                .put("user_confirmed", false)
                .put("confidence", 90)
                .put("excerpt", summary.take(240))
                .toString()

            val eventHash = hashMemoryEventV3(
                genesisCoreId = genesisCore.coreId,
                genesisCoreHash = genesisCore.contentSha256,
                previousEventHash = previousEventHash,
                eventType = REST_CYCLE_EVENT_TYPE,
                actor = "system",
                source = "local_rest_cycle",
                contextTag = "local_rest_cycle",
                privacyVisibility = PRIVATE_LOCAL,
                memoryKind = "rest_cycle",
                tagsJson = tagsJson,
                evidenceJson = evidenceJson,
                confidence = 90,
                userConfirmed = false,
                body = summary,
                importance = 88,
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
                    eventType = REST_CYCLE_EVENT_TYPE,
                    actor = "system",
                    source = "local_rest_cycle",
                    contextTag = "local_rest_cycle",
                    privacyVisibility = PRIVATE_LOCAL,
                    memoryKind = "rest_cycle",
                    tagsJson = tagsJson,
                    evidenceJson = evidenceJson,
                    confidence = 90,
                    userConfirmed = false,
                    body = summary,
                    importance = 88,
                    createdAtMillis = createdAtMillis
                )
            )
            rebuildLivingMemorySnapshot()
        }
    }

    private fun buildRestCycleSummary(events: List<MemoryEventEntity>, now: Long): String {
        val prioritized = events.sortedWith(
            compareByDescending<MemoryEventEntity> { it.userConfirmed }
                .thenByDescending { it.importance }
                .thenByDescending { it.confidence }
                .thenByDescending { it.createdAtMillis }
        )

        return buildString {
            appendLine("REST_CYCLE_LOCAL_V1")
            appendLine("generated_at_millis=$now")
            appendLine("policy=local_only_no_network_no_external_actions")
            appendLine("purpose=consolidate_local_memory_for_future_reasoning_context")
            appendLine()
            appendRestSection("decisions", prioritized.filter { it.memoryKind == "decision" }, 6)
            appendRestSection("corrections", prioritized.filter { it.memoryKind == "correction" }, 6)
            appendRestSection("preferences", prioritized.filter { it.memoryKind == "preference" }, 6)
            appendRestSection("learning", prioritized.filter { it.memoryKind == "learning" }, 6)
            appendRestSection("errors", prioritized.filter { it.memoryKind == "error_detected" }, 6)
            appendRestSection(
                "approvals_rejections",
                prioritized.filter { it.memoryKind == "approval" || it.memoryKind == "rejection" },
                6
            )
            appendRestSection("identity", prioritized.filter { it.memoryKind == "identity" }, 4)
            appendRestSection("recent_context", events.takeLast(10), 10)
        }.trim()
    }

    private fun StringBuilder.appendRestSection(
        title: String,
        events: List<MemoryEventEntity>,
        limit: Int
    ) {
        appendLine("[$title]")
        val selected = events.take(limit)
        if (selected.isEmpty()) {
            appendLine("- none")
        } else {
            selected.forEach { event ->
                appendLine(
                    "- ${event.memoryKind}/i${event.importance}/c${event.confidence}/${event.eventHash.take(19)}: " +
                        event.body.replace("\n", " ").take(260)
                )
            }
        }
        appendLine()
    }

    private suspend fun rebuildLivingMemorySnapshot() {
        val events = memoryDao.loadMemoryContext(limit = 24)
        val eventCount = memoryDao.countMemoryEvents()
        val messageCount = memoryDao.countMessages()
        val prioritized = events
            .sortedWith(
                compareByDescending<MemoryEventEntity> { it.memoryKind == "rest_cycle" }
                    .thenByDescending { it.userConfirmed }
                    .thenByDescending { it.importance }
                    .thenByDescending { it.confidence }
                    .thenByDescending { it.createdAtMillis }
            )
            .take(8)
            .joinToString("\n") { event ->
                "- ${event.memoryKind}: ${event.body.take(220)} (${event.eventHash.take(19)})"
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

    private fun verifyMemoryEventChain(
        events: List<MemoryEventEntity>,
        requireGenesisStart: Boolean = true
    ): Boolean {
        var expectedPreviousHash = if (requireGenesisStart) null else events.firstOrNull()?.previousEventHash
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

    companion object {
        private const val REST_CYCLE_EVENT_TYPE = "rest_cycle.local_consolidation"
        private const val REST_CYCLE_MIN_INTERVAL_MILLIS = 6L * 60L * 60L * 1000L
        private const val REST_CYCLE_MIN_EVENTS = 6
        private const val LEGACY_EVENT_HASH = "sha256:legacy-unverified"
        private const val MEMORY_EVENT_CANONICALIZATION_V1 = "morimil.memory_event_hash.v1"
        private const val MEMORY_EVENT_CANONICALIZATION_V2 = "morimil.memory_event_hash.v2"
        private const val MEMORY_EVENT_CANONICALIZATION_V3 = "morimil.memory_event_hash.v3"
        private const val MEMORY_EVENT_TAIL_VERIFICATION_LIMIT = 12
        private const val PRIVATE_LOCAL = "private_local"
    }
}
