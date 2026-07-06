package com.morimil.app.data.repository

import com.morimil.app.data.local.MemoryEventEntity
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

data class RestRepairCandidate(
    val kind: String,
    val riskLevel: String,
    val eventHashes: List<String>,
    val reason: String,
    val suggestedAction: String
)

data class RestRepairProposalReport(
    val candidates: List<RestRepairCandidate>,
    val scannedEventCount: Int
) {
    val hasCandidates: Boolean
        get() = candidates.isNotEmpty()

    val affectedEventHashes: List<String>
        get() = candidates.flatMap { candidate -> candidate.eventHashes }.distinct().take(MAX_AFFECTED_EVENTS)

    val riskLevel: String
        get() = candidates.maxByOrNull { candidate -> riskRank(candidate.riskLevel) }?.riskLevel ?: "low"

    fun migrationSteps(): List<String> {
        return listOf(
            "scan_recent_memory_for_repair_candidates",
            "create_human_reviewable_repair_proposal",
            "append_signed_memory_repair_proposed_event",
            "wait_for_human_approval_before_any_repair"
        )
    }

    fun expectedEffect(): String {
        return buildString {
            appendLine("rest_repair_schema=morimil.rest_repair_proposal.v1")
            appendLine("mode=proposal_only")
            appendLine("automatic_changes=false")
            appendLine("approval_required=true")
            appendLine("scanned_events=$scannedEventCount")
            appendLine("candidate_count=${candidates.size}")
            appendLine("risk_level=$riskLevel")
            candidates.forEachIndexed { index, candidate ->
                appendLine(
                    "candidate_${index + 1}=${candidate.kind} risk=${candidate.riskLevel} " +
                        "events=${candidate.eventHashes.joinToString(",").take(220)} " +
                        "reason=${candidate.reason.take(180)} action=${candidate.suggestedAction.take(180)}"
                )
            }
        }.trim()
    }

    fun eventBody(migrationId: String): String {
        return buildString {
            appendLine("REST_REPAIR_PROPOSAL_V1")
            appendLine("migration_id=$migrationId")
            appendLine("policy=proposal_only_no_automatic_memory_mutation")
            appendLine("approval_required=true")
            appendLine("candidate_count=${candidates.size}")
            candidates.take(MAX_EVENT_BODY_CANDIDATES).forEach { candidate ->
                appendLine(
                    "- ${candidate.kind}/risk=${candidate.riskLevel}: ${candidate.reason}; " +
                        "action=${candidate.suggestedAction}; events=${candidate.eventHashes.joinToString(",")}".take(320)
                )
            }
        }.trim()
    }

    fun evidenceJson(migrationId: String): String {
        val candidateJson = JSONArray()
        candidates.forEach { candidate ->
            candidateJson.put(
                JSONObject()
                    .put("kind", candidate.kind)
                    .put("risk_level", candidate.riskLevel)
                    .put("event_hashes", JSONArray(candidate.eventHashes))
                    .put("reason", candidate.reason)
                    .put("suggested_action", candidate.suggestedAction)
            )
        }
        return JSONObject()
            .put("schema", "morimil.rest_repair_proposal.v1")
            .put("migration_id", migrationId)
            .put("mode", "proposal_only")
            .put("automatic_changes", false)
            .put("approval_required", true)
            .put("scanned_event_count", scannedEventCount)
            .put("candidate_count", candidates.size)
            .put("risk_level", riskLevel)
            .put("candidates", candidateJson)
            .toString()
    }

    companion object {
        private const val MAX_AFFECTED_EVENTS = 24
        private const val MAX_EVENT_BODY_CANDIDATES = 8
    }
}

object RestRepairProposalPlanner {
    private const val MAX_CANDIDATES = 12
    private const val MIN_DUPLICATE_BODY_LENGTH = 32

    fun build(events: List<MemoryEventEntity>): RestRepairProposalReport {
        val repairableEvents = events
            .filterNot { event -> event.memoryKind in EXCLUDED_MEMORY_KINDS }
            .filterNot { event -> event.eventType.startsWith("memory.repair_") }

        val candidates = buildList {
            addAll(findDuplicateCandidates(repairableEvents))
            addAll(findImportantUnconfirmedCandidates(repairableEvents))
            addAll(findLowConfidenceImportantCandidates(repairableEvents))
            addAll(findCorrectionConflictCandidates(repairableEvents))
        }
            .distinctBy { candidate -> candidate.kind + ":" + candidate.eventHashes.joinToString(",") }
            .sortedWith(
                compareByDescending<RestRepairCandidate> { candidate -> riskRank(candidate.riskLevel) }
                    .thenBy { candidate -> candidate.kind }
            )
            .take(MAX_CANDIDATES)

        return RestRepairProposalReport(
            candidates = candidates,
            scannedEventCount = events.size
        )
    }

    private fun findDuplicateCandidates(events: List<MemoryEventEntity>): List<RestRepairCandidate> {
        return events
            .groupBy { event -> normalizedBody(event.body) }
            .filterKeys { key -> key.length >= MIN_DUPLICATE_BODY_LENGTH }
            .values
            .filter { group -> group.size >= 2 }
            .map { group -> group.sortedByDescending { event -> event.importance } }
            .map { group ->
                RestRepairCandidate(
                    kind = "duplicate_candidate",
                    riskLevel = "medium",
                    eventHashes = group.map { event -> event.eventHash }.take(4),
                    reason = "Multiple memory events have near-identical normalized text.",
                    suggestedAction = "Ask user whether to mark older duplicates as superseded by append-only review events."
                )
            }
    }

    private fun findImportantUnconfirmedCandidates(events: List<MemoryEventEntity>): List<RestRepairCandidate> {
        return events
            .filter { event -> event.importance >= 85 && !event.userConfirmed }
            .filter { event -> event.memoryKind in IMPORTANT_MEMORY_KINDS }
            .map { event ->
                RestRepairCandidate(
                    kind = "important_unconfirmed_memory",
                    riskLevel = "medium",
                    eventHashes = listOf(event.eventHash),
                    reason = "Important ${event.memoryKind} memory is not user-confirmed.",
                    suggestedAction = "Surface for explicit approve, correct, or degrade decision."
                )
            }
    }

    private fun findLowConfidenceImportantCandidates(events: List<MemoryEventEntity>): List<RestRepairCandidate> {
        return events
            .filter { event -> event.importance >= 70 && event.confidence < 60 }
            .map { event ->
                RestRepairCandidate(
                    kind = "low_confidence_important_memory",
                    riskLevel = "medium",
                    eventHashes = listOf(event.eventHash),
                    reason = "Important memory has low confidence and should not be treated as stable truth yet.",
                    suggestedAction = "Ask user to confirm, correct, or quarantine the memory."
                )
            }
    }

    private fun findCorrectionConflictCandidates(events: List<MemoryEventEntity>): List<RestRepairCandidate> {
        val corrections = events
            .filter { event -> event.memoryKind == "correction" || event.eventType.contains("correction") }
            .sortedByDescending { event -> event.createdAtMillis }
        val stableMemories = events
            .filter { event -> event.memoryKind in IMPORTANT_MEMORY_KINDS }
            .filterNot { event -> isCorrection(event) }

        return corrections.flatMap { correction ->
            val correctionTokens = meaningfulTokens(correction.body)
            if (correctionTokens.size < 3) return@flatMap emptyList()
            stableMemories
                .filter { memory -> memory.eventHash != correction.eventHash }
                .filter { memory -> tokenOverlap(correctionTokens, meaningfulTokens(memory.body)) >= 3 }
                .take(2)
                .map { memory ->
                    RestRepairCandidate(
                        kind = "possible_contradiction",
                        riskLevel = "high",
                        eventHashes = listOf(correction.eventHash, memory.eventHash),
                        reason = "A correction overlaps with an older important memory and may supersede it.",
                        suggestedAction = "Ask user which memory should be trusted before changing retrieval priority."
                    )
                }
        }
    }

    private fun isCorrection(event: MemoryEventEntity): Boolean {
        return event.memoryKind == "correction" || event.eventType.contains("correction")
    }

    private fun normalizedBody(value: String): String {
        val decomposed = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
    }

    private fun meaningfulTokens(value: String): Set<String> {
        return normalizedBody(value)
            .split(" ")
            .filter { token -> token.length >= 4 }
            .filterNot { token -> token in STOP_WORDS }
            .toSet()
    }

    private fun tokenOverlap(left: Set<String>, right: Set<String>): Int {
        return left.intersect(right).size
    }

    private val IMPORTANT_MEMORY_KINDS = setOf(
        "decision",
        "correction",
        "preference",
        "identity",
        "approval",
        "rejection",
        "learning",
        "error_detected"
    )

    private val EXCLUDED_MEMORY_KINDS = setOf(
        "chat_noise",
        "rest_cycle",
        "integrity_quarantine"
    )

    private val STOP_WORDS = setOf(
        "para",
        "pero",
        "como",
        "este",
        "esta",
        "esto",
        "tiene",
        "debe",
        "hacer",
        "memoria",
        "morimil",
        "local",
        "system",
        "user",
        "with",
        "that",
        "this",
        "from",
        "have",
        "should"
    )
}

private fun riskRank(risk: String): Int {
    return when (risk.lowercase(Locale.ROOT)) {
        "low" -> 0
        "medium" -> 1
        "high" -> 2
        "critical" -> 3
        else -> 1
    }
}
