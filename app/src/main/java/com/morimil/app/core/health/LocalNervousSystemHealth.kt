package com.morimil.app.core.health

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

enum class LocalHealthStatus {
    HEALTHY,
    DEGRADED,
    CRITICAL
}

data class LocalHealthSignal(
    val name: String,
    val status: LocalHealthStatus,
    val probableCause: String,
    val note: String
)

data class LocalNervousSystemInput(
    val genesisCoreCount: Int,
    val localIdentityCount: Int,
    val memoryEventCount: Int,
    val messageCount: Int,
    val livingSnapshotCount: Int,
    val recentContextCount: Int,
    val memoryChainVerified: Boolean,
    val capsuleChainVerified: Boolean,
    val organReconciliationHasIssues: Boolean,
    val orphanedLinkCount: Int,
    val orphanedRecallCount: Int,
    val orphanedCapsuleCount: Int,
    val migrationMissingRefCount: Int,
    val chainScanLatencyMillis: Long,
    val healthCheckLatencyMillis: Long
)

data class LocalNervousSystemReport(
    val status: LocalHealthStatus,
    val riskLevel: String,
    val signals: List<LocalHealthSignal>,
    val generatedAtMillis: Long
) {
    val hasAlert: Boolean
        get() = status != LocalHealthStatus.HEALTHY

    fun eventType(): String {
        return when (status) {
            LocalHealthStatus.CRITICAL -> "nervous_system.health_critical"
            LocalHealthStatus.DEGRADED -> "nervous_system.health_degraded"
            LocalHealthStatus.HEALTHY -> "nervous_system.health_ok"
        }
    }

    fun eventBody(): String {
        val findings = signals
            .filter { signal -> signal.status != LocalHealthStatus.HEALTHY }
            .joinToString(separator = "; ") { signal ->
                "${signal.name}=${signal.status.name.lowercase(Locale.ROOT)} cause=${signal.probableCause}"
            }
            .ifBlank { "all_local_sensors_healthy" }
        return "Sistema nervioso local: status=${status.name.lowercase(Locale.ROOT)} risk=$riskLevel; $findings"
    }

    fun evidenceJson(source: String): String {
        return JSONObject()
            .put("schema", "morimil.local_nervous_system.v1")
            .put("source", source)
            .put("status", status.name.lowercase(Locale.ROOT))
            .put("risk_level", riskLevel)
            .put("generated_at_millis", generatedAtMillis)
            .put("signals", JSONArray(signals.map { signal -> signal.toJson() }))
            .toString()
    }

    private fun LocalHealthSignal.toJson(): JSONObject {
        return JSONObject()
            .put("name", name)
            .put("status", status.name.lowercase(Locale.ROOT))
            .put("probable_cause", probableCause)
            .put("note", note)
    }
}

object LocalNervousSystemHealth {
    private const val CHAIN_SCAN_DEGRADED_LATENCY_MILLIS = 1_500L
    private const val HEALTH_CHECK_DEGRADED_LATENCY_MILLIS = 750L

    fun build(input: LocalNervousSystemInput, generatedAtMillis: Long): LocalNervousSystemReport {
        val signals = listOf(
            genesisSignal(input.genesisCoreCount),
            identitySignal(input.localIdentityCount),
            memoryEventsSignal(input),
            livingSnapshotSignal(input),
            memoryChainSignal(input.memoryChainVerified),
            capsuleChainSignal(input.capsuleChainVerified),
            organReconciliationSignal(input),
            latencySignal(
                name = "memory_chain_scan_latency",
                latencyMillis = input.chainScanLatencyMillis,
                thresholdMillis = CHAIN_SCAN_DEGRADED_LATENCY_MILLIS
            ),
            latencySignal(
                name = "health_check_latency",
                latencyMillis = input.healthCheckLatencyMillis,
                thresholdMillis = HEALTH_CHECK_DEGRADED_LATENCY_MILLIS
            )
        )
        val status = signals.maxOf { signal -> signal.status }
        return LocalNervousSystemReport(
            status = status,
            riskLevel = status.toRiskLevel(),
            signals = signals,
            generatedAtMillis = generatedAtMillis
        )
    }

    private fun genesisSignal(count: Int): LocalHealthSignal {
        return when (count) {
            1 -> healthy("genesis_core", "one_birth_block_present")
            0 -> critical("genesis_core", "missing_genesis_core", "Morimil cannot trust identity or append memory before birth.")
            else -> critical("genesis_core", "duplicate_genesis_core", "More than one birth block breaks identity invariants.")
        }
    }

    private fun identitySignal(count: Int): LocalHealthSignal {
        return when (count) {
            1 -> healthy("local_identity", "one_local_identity_present")
            0 -> critical("local_identity", "missing_local_identity", "Local identity is absent after birth.")
            else -> critical("local_identity", "duplicate_local_identity", "Multiple local identities would confuse memory ownership.")
        }
    }

    private fun memoryEventsSignal(input: LocalNervousSystemInput): LocalHealthSignal {
        return when {
            input.genesisCoreCount == 1 && input.memoryEventCount == 0 -> critical(
                "memory_events",
                "birth_without_events",
                "Genesis exists but the append-only memory chain is empty."
            )
            input.messageCount > 0 && input.memoryEventCount == 0 -> critical(
                "memory_events",
                "messages_without_memory_events",
                "Chat exists but living memory did not receive events."
            )
            input.memoryEventCount > 0 && input.recentContextCount == 0 -> degraded(
                "memory_context",
                "recent_context_empty",
                "Memory events exist but context retrieval returned nothing."
            )
            else -> healthy("memory_events", "events_available=${input.memoryEventCount}")
        }
    }

    private fun livingSnapshotSignal(input: LocalNervousSystemInput): LocalHealthSignal {
        return when {
            input.genesisCoreCount == 1 && input.memoryEventCount > 0 && input.livingSnapshotCount == 0 -> degraded(
                "living_memory_snapshot",
                "snapshot_missing",
                "Events exist but compact living snapshot is missing."
            )
            input.livingSnapshotCount > 1 -> degraded(
                "living_memory_snapshot",
                "duplicate_snapshot",
                "More than one current snapshot row should not exist."
            )
            else -> healthy("living_memory_snapshot", "snapshot_count=${input.livingSnapshotCount}")
        }
    }

    private fun memoryChainSignal(verified: Boolean): LocalHealthSignal {
        return if (verified) {
            healthy("memory_chain", "hash_chain_and_signatures_verified")
        } else {
            critical("memory_chain", "integrity_verification_failed", "Hash chain or required signatures did not verify.")
        }
    }

    private fun capsuleChainSignal(verified: Boolean): LocalHealthSignal {
        return if (verified) {
            healthy("knowledge_capsule_chain", "capsule_hash_chain_verified")
        } else {
            critical("knowledge_capsule_chain", "capsule_integrity_failed", "Stable knowledge capsule chain did not verify.")
        }
    }

    private fun organReconciliationSignal(input: LocalNervousSystemInput): LocalHealthSignal {
        return if (!input.organReconciliationHasIssues) {
            healthy("organ_reconciliation", "organs_match_living_memory")
        } else {
            degraded(
                "organ_reconciliation",
                "orphaned_or_missing_memory_refs",
                "links=${input.orphanedLinkCount}, recalls=${input.orphanedRecallCount}, capsules=${input.orphanedCapsuleCount}, migrations=${input.migrationMissingRefCount}"
            )
        }
    }

    private fun latencySignal(name: String, latencyMillis: Long, thresholdMillis: Long): LocalHealthSignal {
        return if (latencyMillis <= thresholdMillis) {
            healthy(name, "latency_millis=$latencyMillis")
        } else {
            degraded(name, "latency_above_threshold", "latency_millis=$latencyMillis threshold_millis=$thresholdMillis")
        }
    }

    private fun healthy(name: String, note: String): LocalHealthSignal {
        return LocalHealthSignal(name, LocalHealthStatus.HEALTHY, "none", note)
    }

    private fun degraded(name: String, probableCause: String, note: String): LocalHealthSignal {
        return LocalHealthSignal(name, LocalHealthStatus.DEGRADED, probableCause, note)
    }

    private fun critical(name: String, probableCause: String, note: String): LocalHealthSignal {
        return LocalHealthSignal(name, LocalHealthStatus.CRITICAL, probableCause, note)
    }

    private fun LocalHealthStatus.toRiskLevel(): String {
        return when (this) {
            LocalHealthStatus.HEALTHY -> "low"
            LocalHealthStatus.DEGRADED -> "medium"
            LocalHealthStatus.CRITICAL -> "critical"
        }
    }
}
