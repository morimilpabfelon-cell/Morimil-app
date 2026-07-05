package com.morimil.app.ui

import com.morimil.app.data.local.MigrationRecordEntity
import org.json.JSONArray

data class RestCycleReportUiState(
    val mode: String,
    val risk: String,
    val approvalRequired: String,
    val policyReason: String,
    val fullChainVerified: String,
    val organReconciliation: String,
    val capsuleChainVerified: String,
    val sourceEvents: String,
    val meaningfulEvents: String,
    val tasks: List<RestCycleTaskUiState>,
    val resultNotes: List<String>
)

data class RestCycleTaskUiState(
    val name: String,
    val status: String,
    val risk: String,
    val note: String
)

object RestCycleReportUiStateBuilder {
    fun build(migration: MigrationRecordEntity): RestCycleReportUiState {
        val fields = linkedMapOf<String, String>()
        migration.expectedEffect
            .lines()
            .mapNotNull { line -> line.toKeyValueOrNull() }
            .forEach { (key, value) ->
                if (key !in fields) fields[key] = value
            }
        val tasks = migration.expectedEffect
            .lines()
            .filter { line -> line.startsWith("task=") }
            .mapNotNull { line -> line.toTaskOrNull() }
        val resultNotes = parseJsonArray(migration.errorsJson)
            .filter { note ->
                note.startsWith("rest_cycle_") ||
                    note.startsWith("maintenance_") ||
                    note.startsWith("organ_reconciliation")
            }

        return RestCycleReportUiState(
            mode = fields["rest_cycle_mode"].orUnknown(),
            risk = fields["maintenance_risk"] ?: migration.riskLevel,
            approvalRequired = fields["maintenance_approval_required"]
                ?: migration.approvalRequired.toString(),
            policyReason = fields["policy_reason"].orUnknown(),
            fullChainVerified = fields["full_chain_verified"]
                ?: migration.chainVerified.toString(),
            organReconciliation = fields["organ_reconciliation_has_issues"].orUnknown(),
            capsuleChainVerified = fields["organ_reconciliation_capsule_chain_verified"].orUnknown(),
            sourceEvents = fields["source_events"].orUnknown(),
            meaningfulEvents = fields["meaningful_events"].orUnknown(),
            tasks = tasks,
            resultNotes = resultNotes
        )
    }

    private fun String.toKeyValueOrNull(): Pair<String, String>? {
        if (startsWith("task=")) return null
        val separatorIndex = indexOf('=')
        if (separatorIndex <= 0 || separatorIndex == lastIndex) return null
        return take(separatorIndex) to drop(separatorIndex + 1)
    }

    private fun String.toTaskOrNull(): RestCycleTaskUiState? {
        if (!startsWith("task=")) return null
        val name = substringAfter("task=").substringBefore(" status=")
        val status = substringAfter(" status=", missingDelimiterValue = "").substringBefore(" risk=")
        val risk = substringAfter(" risk=", missingDelimiterValue = "").substringBefore(" note=")
        val note = substringAfter(" note=", missingDelimiterValue = "")
        if (name.isBlank() || status.isBlank() || risk.isBlank()) return null
        return RestCycleTaskUiState(
            name = name,
            status = status,
            risk = risk,
            note = note
        )
    }

    private fun parseJsonArray(value: String): List<String> {
        return runCatching {
            val array = JSONArray(value)
            (0 until array.length()).mapNotNull { index ->
                array.optString(index).takeIf { item -> item.isNotBlank() }
            }
        }.getOrDefault(emptyList())
    }

    private fun String?.orUnknown(): String = this?.takeIf { it.isNotBlank() } ?: "unknown"
}
