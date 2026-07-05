package com.morimil.app.core.memory

import com.morimil.app.data.local.MemoryEventEntity
import com.morimil.app.data.local.MigrationRecordEntity
import org.json.JSONArray
import kotlin.math.roundToInt

data class CognitiveMigrationPlan(
    val schema: String,
    val proposalId: String,
    val affectedArtifacts: List<String>,
    val steps: List<String>,
    val expectedEffect: String,
    val riskLevel: String,
    val rollbackStrategy: String,
    val executionImportance: Int
)

object CognitiveMigrationPlanner {
    const val PLAN_SCHEMA = "morimil.cognitive_migration_plan.v2"

    fun buildPlan(
        events: List<MemoryEventEntity>,
        chainVerified: Boolean,
        preSnapshotId: String,
        createdAtMillis: Long
    ): CognitiveMigrationPlan {
        val selectedEvents = events.distinctBy { event -> event.eventHash }
        val capsuleProposals = buildCapsuleProposals(selectedEvents)
        val backlinkProposals = buildBacklinkProposals(selectedEvents)
        val diffActions = selectedEvents.map { event -> buildDiffAction(event) }
        val riskLevel = scoreRisk(selectedEvents, chainVerified)
        val affectedArtifacts = buildList {
            selectedEvents.forEach { event -> add("memory_event:${event.eventHash}") }
            capsuleProposals.forEach { proposal -> add("capsule_proposal:${proposal.topic}") }
            backlinkProposals.forEach { proposal -> add("link_proposal:${proposal.sourceHash}->${proposal.targetHash}") }
        }
        val steps = listOf(
            "audit_chain:${if (chainVerified) "verified" else "needs_quarantine_review"}",
            "classify_selected_events:${selectedEvents.size}",
            "draft_capsule_proposals:${capsuleProposals.size}",
            "draft_backlink_proposals:${backlinkProposals.size}",
            "append_execution_event_without_rewriting_memory",
            "audit_chain_after_execution",
            "keep_original_events_immutable",
            "rollback_by_append_only_compensation_marker"
        )

        return CognitiveMigrationPlan(
            schema = PLAN_SCHEMA,
            proposalId = "cognitive_refinement_v2:$createdAtMillis",
            affectedArtifacts = affectedArtifacts,
            steps = steps,
            expectedEffect = buildExpectedEffect(
                selectedEvents = selectedEvents,
                capsuleProposals = capsuleProposals,
                backlinkProposals = backlinkProposals,
                diffActions = diffActions,
                chainVerified = chainVerified,
                preSnapshotId = preSnapshotId
            ),
            riskLevel = riskLevel,
            rollbackStrategy = buildRollbackStrategy(riskLevel, preSnapshotId),
            executionImportance = when (riskLevel) {
                "high" -> 96
                "medium" -> 92
                else -> 82
            }
        )
    }

    fun buildExecutionBody(record: MigrationRecordEntity): String {
        return "COGNITIVE_MIGRATION_EXECUTED_V2\n" +
            "migration_id=${record.migrationId}\n" +
            "risk=${record.riskLevel}\n" +
            "approved=${record.approvedByUser}\n" +
            "approval_id=${record.approvalId}\n" +
            "affected=${record.affectedArtifactsJson}\n" +
            "steps=${record.stepsJson}\n" +
            "post_execution_audit=pending_record_update\n" +
            "plan=\n${record.expectedEffect.take(1800)}"
    }

    fun buildRollbackBody(record: MigrationRecordEntity): String {
        return "COGNITIVE_MIGRATION_ROLLBACK_V2\n" +
            "migration_id=${record.migrationId}\n" +
            "previous_status=${record.status}\n" +
            "post_snapshot_id=${record.postSnapshotId}\n" +
            "strategy=\n${record.rollbackStrategy.take(1200)}\n" +
            "note=append_only_compensation; original_memory_events_remain_immutable"
    }

    fun buildPostExecutionAuditNotes(
        record: MigrationRecordEntity,
        executionEventHash: String?,
        chainVerified: Boolean,
        checkedAtMillis: Long
    ): List<String> {
        return listOf(
            "post_execution_audit:${if (chainVerified) "verified" else "failed"}",
            "checked_at:$checkedAtMillis",
            "execution_event_hash:${executionEventHash ?: "none"}",
            "migration_id:${record.migrationId}",
            "policy:append_only_original_memory_unchanged"
        )
    }

    private fun buildCapsuleProposals(events: List<MemoryEventEntity>): List<CapsuleProposal> {
        return events
            .groupBy { event -> event.memoryTopic() }
            .map { (topic, topicEvents) ->
                CapsuleProposal(
                    topic = topic,
                    sourceHashes = topicEvents.map { event -> event.eventHash }.take(6),
                    confidence = topicEvents.map { event -> event.confidence }.averageOrZero().roundToInt().coerceIn(1, 100),
                    summary = topicEvents
                        .sortedWith(compareByDescending<MemoryEventEntity> { it.userConfirmed }.thenByDescending { it.importance })
                        .take(3)
                        .joinToString(" | ") { event -> "${event.memoryKind}: ${event.body.cleanExcerpt(140)}" }
                )
            }
            .sortedWith(compareByDescending<CapsuleProposal> { proposal -> proposal.confidence }.thenBy { proposal -> proposal.topic })
            .take(5)
    }

    private fun buildBacklinkProposals(events: List<MemoryEventEntity>): List<BacklinkProposal> {
        val sortedEvents = events.sortedWith(
            compareByDescending<MemoryEventEntity> { event -> event.importance }
                .thenByDescending { event -> event.confidence }
                .thenByDescending { event -> event.createdAtMillis }
        )
        val proposals = mutableListOf<BacklinkProposal>()
        sortedEvents.forEachIndexed { index, source ->
            sortedEvents.drop(index + 1)
                .filter { target -> source.memoryTopic() == target.memoryTopic() }
                .take(2)
                .forEach { target ->
                    proposals += BacklinkProposal(
                        sourceHash = source.eventHash,
                        targetHash = target.eventHash,
                        relation = "cognitive_related_to",
                        strength = linkStrength(source, target),
                        reason = "shared_topic:${source.memoryTopic()}; ${source.memoryKind}->${target.memoryKind}"
                    )
                }
        }
        return proposals
            .sortedWith(compareByDescending<BacklinkProposal> { proposal -> proposal.strength }.thenBy { proposal -> proposal.sourceHash })
            .take(10)
    }

    private fun buildDiffAction(event: MemoryEventEntity): String {
        val action = when {
            event.memoryKind in setOf("decision", "preference", "correction", "learning", "identity") -> "promote_to_capsule_candidate"
            event.confidence < 65 -> "needs_user_review_before_consolidation"
            event.importance >= 80 -> "link_as_high_value_context"
            else -> "keep_as_context_only"
        }
        return "$action:${event.eventHash}:${event.memoryKind}:i${event.importance}:c${event.confidence}"
    }

    private fun buildExpectedEffect(
        selectedEvents: List<MemoryEventEntity>,
        capsuleProposals: List<CapsuleProposal>,
        backlinkProposals: List<BacklinkProposal>,
        diffActions: List<String>,
        chainVerified: Boolean,
        preSnapshotId: String
    ): String {
        return buildString {
            appendLine("COGNITIVE_MIGRATION_PLAN_V2")
            appendLine("schema=$PLAN_SCHEMA")
            appendLine("summary=refinar memoria local sin reescribir eventos originales")
            appendLine("pre_snapshot=$preSnapshotId chain_verified=$chainVerified selected_events=${selectedEvents.size}")
            appendLine("diff_logico:")
            diffActions.take(12).forEach { action -> appendLine("- $action") }
            appendLine("capsulas_propuestas:")
            capsuleProposals.forEach { proposal ->
                appendLine("- ${proposal.topic} confidence=${proposal.confidence} sources=${proposal.sourceHashes.joinToString(",") { it.take(19) }} summary=${proposal.summary}")
            }
            appendLine("backlinks_propuestos:")
            backlinkProposals.forEach { proposal ->
                appendLine("- ${proposal.sourceHash.take(19)} -> ${proposal.targetHash.take(19)} ${proposal.relation} strength=${proposal.strength} reason=${proposal.reason}")
            }
            appendLine("eventos_seleccionados:")
            selectedEvents.take(12).forEach { event ->
                appendLine("- ${event.eventHash.take(19)} ${event.memoryKind}/i${event.importance}/c${event.confidence}/confirmed=${event.userConfirmed}: ${event.body.cleanExcerpt(180)}")
            }
            appendLine("policy=append_only; no old memory event is rewritten or deleted")
        }.trim()
    }

    private fun buildRollbackStrategy(riskLevel: String, preSnapshotId: String): String {
        return buildString {
            appendLine("COGNITIVE_MIGRATION_ROLLBACK_V2")
            appendLine("risk=$riskLevel pre_snapshot=$preSnapshotId")
            appendLine("1. Append cognitive_migration.rollback as a compensation event.")
            appendLine("2. Treat the executed migration event as compensated, not deleted.")
            appendLine("3. Keep original memory events immutable and still auditable.")
            appendLine("4. Re-run explicit memory audit before a later migration if risk was high.")
            appendLine("5. Future capsule/link materialization must reference the rollback event when superseded.")
        }.trim()
    }

    private fun scoreRisk(events: List<MemoryEventEntity>, chainVerified: Boolean): String {
        if (!chainVerified) return "high"
        val touchesConfirmedCore = events.any { event -> event.userConfirmed && event.importance >= 90 }
        val touchesIdentityOrCorrection = events.any { event -> event.memoryKind in setOf("identity", "correction") }
        return if (touchesConfirmedCore || touchesIdentityOrCorrection) "medium" else "low"
    }

    private fun MemoryEventEntity.memoryTopic(): String {
        val tags = parseTags(tagsJson)
        return when {
            "reasoning" in tags -> "reasoning_motor"
            "memory_graph" in tags -> "memory_graph"
            "project" in tags -> "project_context"
            "privacy" in tags -> "privacy_security"
            "genesis" in tags || memoryKind == "identity" -> "genesis_identity"
            "memory" in tags -> "living_memory"
            else -> memoryKind.replace(Regex("[^a-zA-Z0-9_.-]+"), "_").ifBlank { "general_memory" }
        }
    }

    private fun parseTags(tagsJson: String): Set<String> {
        return runCatching {
            val array = JSONArray(tagsJson)
            (0 until array.length())
                .mapNotNull { index -> array.optString(index).takeIf { it.isNotBlank() } }
                .toSet()
        }.getOrDefault(emptySet())
    }

    private fun linkStrength(source: MemoryEventEntity, target: MemoryEventEntity): Double {
        val sourceWeight = (source.importance.coerceIn(0, 100) * 0.55) + (source.confidence.coerceIn(0, 100) * 0.45)
        val targetWeight = (target.importance.coerceIn(0, 100) * 0.55) + (target.confidence.coerceIn(0, 100) * 0.45)
        val bonus = if (source.userConfirmed || target.userConfirmed) 0.08 else 0.0
        val value = (((sourceWeight + targetWeight) / 2.0) / 100.0) + bonus
        return ((value.coerceIn(0.0, 1.0)) * 100.0).roundToInt() / 100.0
    }

    private fun List<Int>.averageOrZero(): Double {
        return if (isEmpty()) 0.0 else average()
    }

    private fun String.cleanExcerpt(limit: Int): String {
        return replace("\n", " ").replace(Regex("\\s+"), " ").trim().take(limit)
    }

    private data class CapsuleProposal(
        val topic: String,
        val sourceHashes: List<String>,
        val confidence: Int,
        val summary: String
    )

    private data class BacklinkProposal(
        val sourceHash: String,
        val targetHash: String,
        val relation: String,
        val strength: Double,
        val reason: String
    )
}
