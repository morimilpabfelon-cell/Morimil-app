package com.morimil.app.data.repository

import androidx.room.withTransaction
import com.morimil.app.core.memory.MemoryEventSigner
import com.morimil.app.core.memory.MemoryIntegrityCore
import com.morimil.app.core.memory.MemoryOrganReconciliationReport
import com.morimil.app.core.memory.RestCycleMaintenancePlanner
import com.morimil.app.core.memory.RestCycleMaintenanceReport
import com.morimil.app.core.memory.RestCycleMode
import com.morimil.app.data.local.AutobiographicalSnapshotEntity
import com.morimil.app.data.local.MemoryDao
import com.morimil.app.data.local.MemoryEventEntity
import com.morimil.app.data.local.MemoryOrganDao
import com.morimil.app.data.local.MemoryOrganDatabase
import com.morimil.app.data.local.MemorySnapshotEntity
import com.morimil.app.data.local.MorimilDatabase
import org.json.JSONArray
import org.json.JSONObject

class RestCycleRepository(
    private val database: MorimilDatabase,
    organDatabase: MemoryOrganDatabase,
    private val memoryIntegrityCore: MemoryIntegrityCore,
    private val memoryEventSigner: MemoryEventSigner,
    private val memoryRepository: MemoryRepository
) {
    private val memoryDao: MemoryDao = database.memoryDao()
    private val organDao: MemoryOrganDao = organDatabase.memoryOrganDao()
    private val memoryLinkRepository = MemoryLinkRepository(organDatabase)
    private val migrationRecordRepository = MigrationRecordRepository(organDatabase)
    private val organReconciliationRepository = MemoryOrganReconciliationRepository(
        organDatabase = organDatabase,
        memoryIntegrityCore = memoryIntegrityCore
    )

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

        val fullChainEvents = memoryDao.loadMemoryEventAuditChain()
        val fullChainVerified = memoryIntegrityCore.verifyMemoryEventChain(fullChainEvents)
        val organReconciliation = organReconciliationRepository.reconcileAgainstMemoryEvents(
            validMemoryEventHashes = fullChainEvents.map { event -> event.eventHash }.toSet(),
            memoryChainVerified = fullChainVerified
        )
        val maintenanceReport = RestCycleMaintenancePlanner.build(
            mode = if (force) RestCycleMode.Deep else RestCycleMode.Normal,
            fullChainVerified = fullChainVerified,
            organReconciliation = organReconciliation,
            sourceEventCount = events.size,
            meaningfulEventCount = meaningfulEvents.size,
            policyApprovalRequired = RestCyclePolicy.requiresHumanApproval(meaningfulEvents),
            policyReason = RestCyclePolicy.approvalReason(meaningfulEvents)
        )
        val approvalRequired = !force && maintenanceReport.approvalRequired
        if (approvalRequired) {
            planImportantRestCycleIfNeeded(
                summary = summary,
                meaningfulEvents = meaningfulEvents,
                preSnapshotId = latestRestCycle?.eventHash ?: "none",
                maintenanceReport = maintenanceReport,
                organReconciliation = organReconciliation
            )
            return false
        }

        val migrationId = approvedMigrationId ?: planRestCycleMigration(
            summary = summary,
            meaningfulEvents = meaningfulEvents,
            preSnapshotId = latestRestCycle?.eventHash ?: "none",
            approvalRequired = false,
            approvedByUser = force,
            approvalId = if (force) "manual_force:${System.currentTimeMillis()}" else null,
            maintenanceReport = maintenanceReport,
            organReconciliation = organReconciliation
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
            val autobiographyEventHash = if (maintenanceReport.fullChainVerified) {
                consolidateAutobiographyFromRestCycle(
                    restCycle = restCycle,
                    events = events
                )
            } else {
                null
            }
            migrationRecordRepository.markMigrationCompleted(
                migrationId = migrationId,
                postSnapshotId = restCycle.eventHash,
                resultNotes = buildRestCycleResultNotes(
                    restCycle = restCycle,
                    sourceEventCount = meaningfulEvents.size,
                    linkedEventCount = meaningfulEvents.take(12).size,
                    approvedMigrationId = approvedMigrationId,
                    maintenanceReport = maintenanceReport,
                    organReconciliation = organReconciliation,
                    autobiographyEventHash = autobiographyEventHash
                )
            )
            true
        }.getOrElse { error ->
            val failureMessage = error.message ?: error::class.java.simpleName
            migrationRecordRepository.markMigrationFailed(
                migrationId = migrationId,
                errors = listOf(failureMessage)
            )
            throw RestCycleExecutionException("Rest cycle failed: $failureMessage", error)
        }
    }

    private suspend fun appendRestCycleEvent(
        summary: String,
        migrationId: String,
        approvalId: String?
    ): RestCycleAppendResult? {
        return MemoryAppendGate.withAppendLock {
            database.withTransaction {
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
                val tailTrusted = memoryIntegrityCore.verifyMemoryEventChain(eventTail, requireGenesisStart = false)
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

                val eventHash = memoryIntegrityCore.hashMemoryEventV3(
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
    }

    private suspend fun consolidateAutobiographyFromRestCycle(
        restCycle: RestCycleAppendResult,
        events: List<MemoryEventEntity>
    ): String? {
        val genesisCore = memoryDao.loadGenesisCore() ?: return null
        val alias = memoryDao.loadLocalIdentity()?.alias ?: "Morimil"
        val draft = AutobiographicalMemoryConsolidator.build(
            alias = alias,
            sourceRestCycleEventHash = restCycle.eventHash,
            events = events,
            generatedAtMillis = restCycle.createdAtMillis
        )
        val autobiographyEventHash = memoryRepository.recordSystemMemoryEvent(
            eventType = MEMORY_AUTOBIOGRAPHY_EVENT_TYPE,
            body = AutobiographicalMemoryConsolidator.eventBody(draft),
            importance = 90,
            evidenceJson = draft.evidenceJson
        ) ?: return null

        organDao.upsertSelfSnapshot(
            AutobiographicalSnapshotEntity(
                snapshotId = "current",
                genesisCoreId = genesisCore.coreId,
                alias = alias,
                selfSummary = draft.selfSummary,
                stableTraits = draft.stableTraits,
                activeGoals = draft.activeGoals,
                importantConstraints = draft.importantConstraints,
                sourceEventHash = autobiographyEventHash,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
        return autobiographyEventHash
    }

    private suspend fun planImportantRestCycleIfNeeded(
        summary: String,
        meaningfulEvents: List<MemoryEventEntity>,
        preSnapshotId: String,
        maintenanceReport: RestCycleMaintenanceReport,
        organReconciliation: MemoryOrganReconciliationReport
    ): String? {
        val existing = migrationRecordRepository.loadLatestPlannedMigration(REST_CYCLE_MIGRATION_TYPE)
        if (existing != null) return existing.migrationId

        return planRestCycleMigration(
            summary = summary,
            meaningfulEvents = meaningfulEvents,
            preSnapshotId = preSnapshotId,
            approvalRequired = true,
            approvedByUser = false,
            approvalId = null,
            maintenanceReport = maintenanceReport,
            organReconciliation = organReconciliation
        )
    }

    private suspend fun planRestCycleMigration(
        summary: String,
        meaningfulEvents: List<MemoryEventEntity>,
        preSnapshotId: String,
        approvalRequired: Boolean,
        approvedByUser: Boolean,
        approvalId: String?,
        maintenanceReport: RestCycleMaintenanceReport,
        organReconciliation: MemoryOrganReconciliationReport
    ): String {
        val genesisCore = requireNotNull(memoryDao.loadGenesisCore()) {
            "Cannot plan rest cycle without a local Genesis Core."
        }
        val localIdentity = memoryDao.loadLocalIdentity()
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
            chainVerified = maintenanceReport.fullChainVerified,
            backupRequired = approvalRequired,
            steps = maintenanceReport.migrationSteps(approvalRequired),
            expectedEffect = buildRestCycleExpectedEffect(
                summary = summary,
                meaningfulEvents = meaningfulEvents,
                approvalRequired = approvalRequired,
                maintenanceReport = maintenanceReport,
                organReconciliation = organReconciliation
            ),
            riskLevel = maintenanceReport.riskLevel,
            approvalRequired = approvalRequired,
            rollbackAvailable = true,
            rollbackStrategy = "append_only: failed plans do not mutate memory; completed rest cycles can be superseded by a compensating correction/quarantine event",
            approvedByUser = approvedByUser,
            approvalId = approvalId
        )
    }

    private fun buildRestCycleExpectedEffect(
        summary: String,
        meaningfulEvents: List<MemoryEventEntity>,
        approvalRequired: Boolean,
        maintenanceReport: RestCycleMaintenanceReport,
        organReconciliation: MemoryOrganReconciliationReport
    ): String {
        return buildString {
            appendLine(summary.take(500))
            maintenanceReport.expectedEffectLines().forEach { line -> appendLine(line) }
            appendLine("organ_reconciliation_has_issues=${organReconciliation.hasIssues}")
            appendLine("organ_reconciliation_orphaned_links=${organReconciliation.orphanedLinkIds.size}")
            appendLine("organ_reconciliation_orphaned_recalls=${organReconciliation.orphanedRecallIds.size}")
            appendLine("organ_reconciliation_orphaned_capsules=${organReconciliation.orphanedCapsuleIds.size}")
            appendLine("organ_reconciliation_memory_chain_verified=${organReconciliation.memoryChainVerified}")
            appendLine("organ_reconciliation_capsule_chain_verified=${organReconciliation.capsuleChainVerified}")
            appendLine("organ_reconciliation_compensating_writes_allowed=${organReconciliation.compensatingWritesAllowed}")
            appendLine("organ_reconciliation_migrations_with_missing_refs=${organReconciliation.migrationMissingRefs.size}")
            appendLine("approval_required=$approvalRequired")
            appendLine("source_events=${meaningfulEvents.size}")
            appendLine("execution=workmanager_or_manual_trigger")
            appendLine("scope=local_only_append_only")
        }.trim()
    }

    private fun buildRestCycleResultNotes(
        restCycle: RestCycleAppendResult,
        sourceEventCount: Int,
        linkedEventCount: Int,
        approvedMigrationId: String?,
        maintenanceReport: RestCycleMaintenanceReport,
        organReconciliation: MemoryOrganReconciliationReport,
        autobiographyEventHash: String?
    ): List<String> {
        return listOf(
            "rest_cycle_result:completed",
            "rest_cycle_event_hash:${restCycle.eventHash}",
            "full_chain_verified:${maintenanceReport.fullChainVerified}",
            "source_events:$sourceEventCount",
            "links_created_for_sources:$linkedEventCount",
            "autobiography_event_hash:${autobiographyEventHash ?: "skipped"}",
            "approval_id:${approvedMigrationId ?: "none"}",
            "completed_at_millis:${restCycle.createdAtMillis}"
        ) + maintenanceReport.resultNotes() + organReconciliation.toAuditNotes()
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
        private const val MEMORY_AUTOBIOGRAPHY_EVENT_TYPE = "memory.autobiography_updated"
        const val REST_CYCLE_MIGRATION_TYPE = "rest_cycle.local_consolidation"
        private const val REST_CYCLE_MIN_INTERVAL_MILLIS = 6L * 60L * 60L * 1000L
        private const val REST_CYCLE_MIN_EVENTS = 6
        private const val MIGRATION_STATUS_PLANNED = "planned"
        private const val MEMORY_INTEGRITY_QUARANTINE_EVENT_TYPE = "memory_integrity.quarantine"
        private const val MEMORY_EVENT_TAIL_VERIFICATION_LIMIT = 12
        private const val PRIVATE_LOCAL = "private_local"
    }
}

class RestCycleExecutionException(
    message: String,
    cause: Throwable
) : RuntimeException(message, cause)
