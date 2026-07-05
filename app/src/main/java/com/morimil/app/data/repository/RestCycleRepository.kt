package com.morimil.app.data.repository

import androidx.room.withTransaction
import com.morimil.app.core.memory.MemoryEventIntegrity
import com.morimil.app.core.memory.MemoryIntegrityVerifier
import com.morimil.app.data.local.MemoryDao
import com.morimil.app.data.local.MemoryEventEntity
import com.morimil.app.data.local.MemoryOrganDatabase
import com.morimil.app.data.local.MemorySnapshotEntity
import com.morimil.app.data.local.MorimilDatabase
import org.json.JSONArray
import org.json.JSONObject

class RestCycleRepository(
    private val database: MorimilDatabase,
    organDatabase: MemoryOrganDatabase
) {
    private val memoryDao: MemoryDao = database.memoryDao()
    private val memoryEventIntegrity = MemoryEventIntegrity()
    private val memoryIntegrityVerifier = MemoryIntegrityVerifier(memoryEventIntegrity)
    private val memoryLinkRepository = MemoryLinkRepository(organDatabase)
    private val migrationRecordRepository = MigrationRecordRepository(organDatabase)

    suspend fun runLocalRestCycleIfDue(force: Boolean = false): Boolean {
        return runLocalRestCycleIfDue(force = force, approvedMigrationId = null)
    }

    suspend fun approvePlannedRestCycle(migrationId: String): Boolean {
        val record = migrationRecordRepository.loadMigration(migrationId) ?: return false
        if (record.migrationType != REST_CYCLE_MIGRATION_TYPE || record.status != MIGRATION_STATUS_PLANNED) {
            return false
        }

        migrationRecordRepository.markMigrationApproved(
            migrationId = migrationId,
            approvalId = "user_approved:${System.currentTimeMillis()}"
        )
        return runLocalRestCycleIfDue(force = true, approvedMigrationId = migrationId)
    }

    private suspend fun runLocalRestCycleIfDue(
        force: Boolean,
        approvedMigrationId: String?
    ): Boolean {
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

        val approvalRequired = !force && RestCyclePolicy.requiresHumanApproval(meaningfulEvents)
        if (approvalRequired) {
            planImportantRestCycleIfNeeded(
                summary = summary,
                meaningfulEvents = meaningfulEvents,
                preSnapshotId = latestRestCycle?.eventHash ?: "none"
            )
            return false
        }

        val migrationId = approvedMigrationId ?: planRestCycleMigration(
            summary = summary,
            meaningfulEvents = meaningfulEvents,
            preSnapshotId = latestRestCycle?.eventHash ?: "none",
            approvalRequired = false,
            approvedByUser = force,
            approvalId = if (force) "manual_force:${System.currentTimeMillis()}" else null
        )

        return runCatching {
            val restCycle = appendRestCycleEvent(
                summary = summary,
                migrationId = migrationId,
                approvalId = approvedMigrationId
            ) ?: error("Rest cycle append skipped because memory tail integrity was not trusted.")
            memoryLinkRepository.linkRestCycleToEvents(
                instanceId = restCycle.instanceId,
                genesisCoreHash = restCycle.genesisCoreHash,
                restCycleEventHash = restCycle.eventHash,
                sourceEvents = meaningfulEvents.sortedWith(
                    compareByDescending<MemoryEventEntity> { it.userConfirmed }
                        .thenByDescending { it.importance }
                        .thenByDescending { it.confidence }
                        .thenByDescending { it.createdAtMillis }
                ),
                createdAtMillis = restCycle.createdAtMillis
            )
            migrationRecordRepository.markMigrationCompleted(
                migrationId = migrationId,
                postSnapshotId = restCycle.eventHash
            )
            true
        }.getOrElse { error ->
            migrationRecordRepository.markMigrationFailed(
                migrationId = migrationId,
                errors = listOf(error.message ?: error::class.java.simpleName)
            )
            false
        }
    }

    private suspend fun appendRestCycleEvent(
        summary: String,
        migrationId: String,
        approvalId: String?
    ): RestCycleAppendResult? {
        return database.withTransaction {
            val genesisCore = requireNotNull(memoryDao.loadGenesisCore()) {
                "Cannot run rest cycle without a local Genesis Core."
            }
            val localIdentity = memoryDao.loadLocalIdentity()
            val recoveryBoundary = memoryDao.loadLatestMemoryEventByType(MEMORY_INTEGRITY_QUARANTINE_EVENT_TYPE)
            val eventTail = if (recoveryBoundary == null) {
                memoryDao.loadMemoryEventTail(MEMORY_EVENT_TAIL_VERIFICATION_LIMIT)
            } else {
                memoryDao.loadMemoryEventTailAfterLatestEventType(
                    eventType = MEMORY_INTEGRITY_QUARANTINE_EVENT_TYPE,
                    limit = MEMORY_EVENT_TAIL_VERIFICATION_LIMIT
                )
            }.asReversed()

            val createdAtMillis = System.currentTimeMillis()
            val tailTrusted = memoryIntegrityVerifier.verifyMemoryEventChain(eventTail, requireGenesisStart = false)
            if (!tailTrusted && recoveryBoundary == null) return@withTransaction null
            val previousEventHash = if (tailTrusted) {
                eventTail.lastOrNull()?.eventHash ?: recoveryBoundary?.eventHash
            } else {
                recoveryBoundary?.eventHash
            }
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
                .put("migration_id", migrationId)
                .put("approval_id", approvalId)
                .put("excerpt", summary.take(240))
                .toString()

            val eventHash = memoryEventIntegrity.hashMemoryEventV3(
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
                    signatureAlgorithm = MEMORY_EVENT_SIGNATURE_ALGORITHM_UNSIGNED,
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
            RestCycleAppendResult(
                eventHash = eventHash,
                instanceId = localIdentity?.instanceId ?: "local_instance_pending",
                genesisCoreHash = genesisCore.contentSha256,
                createdAtMillis = createdAtMillis
            )
        }
    }

    private suspend fun planImportantRestCycleIfNeeded(
        summary: String,
        meaningfulEvents: List<MemoryEventEntity>,
        preSnapshotId: String
    ): String? {
        val existing = migrationRecordRepository.loadLatestPlannedMigration(REST_CYCLE_MIGRATION_TYPE)
        if (existing != null) return existing.migrationId

        return planRestCycleMigration(
            summary = summary,
            meaningfulEvents = meaningfulEvents,
            preSnapshotId = preSnapshotId,
            approvalRequired = true,
            approvedByUser = false,
            approvalId = null
        )
    }

    private suspend fun planRestCycleMigration(
        summary: String,
        meaningfulEvents: List<MemoryEventEntity>,
        preSnapshotId: String,
        approvalRequired: Boolean,
        approvedByUser: Boolean,
        approvalId: String?
    ): String {
        val genesisCore = requireNotNull(memoryDao.loadGenesisCore()) {
            "Cannot plan rest cycle without a local Genesis Core."
        }
        val localIdentity = memoryDao.loadLocalIdentity()
        val riskLevel = RestCyclePolicy.riskLevel(meaningfulEvents)
        return migrationRecordRepository.planMigration(
            instanceId = localIdentity?.instanceId ?: "local_instance_pending",
            genesisCoreHash = genesisCore.contentSha256,
            proposalId = null,
            migrationType = REST_CYCLE_MIGRATION_TYPE,
            fromVersion = "living_memory_current",
            toVersion = "living_memory_after_rest_cycle",
            affectedArtifacts = meaningfulEvents
                .sortedByDescending { event -> event.importance }
                .take(12)
                .map { event -> event.eventHash },
            preSnapshotId = preSnapshotId,
            chainVerified = true,
            backupRequired = approvalRequired,
            steps = listOf(
                "verify_recent_memory_tail",
                "append_rest_cycle_event",
                "link_rest_cycle_to_source_events",
                "rebuild_living_memory_snapshot"
            ),
            expectedEffect = summary.take(500) + "\npolicy_reason=" + RestCyclePolicy.approvalReason(meaningfulEvents),
            riskLevel = riskLevel,
            approvalRequired = approvalRequired,
            rollbackAvailable = true,
            rollbackStrategy = "append_only: failed plans do not mutate memory; completed rest cycles can be superseded by a compensating correction/quarantine event",
            approvedByUser = approvedByUser,
            approvalId = approvalId
        )
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

    private data class RestCycleAppendResult(
        val eventHash: String,
        val instanceId: String,
        val genesisCoreHash: String,
        val createdAtMillis: Long
    )

    companion object {
        private const val REST_CYCLE_EVENT_TYPE = "rest_cycle.local_consolidation"
        const val REST_CYCLE_MIGRATION_TYPE = "rest_cycle.local_consolidation"
        private const val REST_CYCLE_MIN_INTERVAL_MILLIS = 6L * 60L * 60L * 1000L
        private const val REST_CYCLE_MIN_EVENTS = 6
        private const val MIGRATION_STATUS_PLANNED = "planned"
        private const val MEMORY_INTEGRITY_QUARANTINE_EVENT_TYPE = "memory_integrity.quarantine"
        private const val MEMORY_EVENT_CANONICALIZATION_V3 = "morimil.memory_event_hash.v3"
        private const val MEMORY_EVENT_SIGNATURE_ALGORITHM_UNSIGNED = "unsigned_runtime_v1"
        private const val MEMORY_EVENT_TAIL_VERIFICATION_LIMIT = 12
        private const val PRIVATE_LOCAL = "private_local"
    }
}
