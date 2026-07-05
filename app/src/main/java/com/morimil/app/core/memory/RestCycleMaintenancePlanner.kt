package com.morimil.app.core.memory

enum class RestCycleMode(val id: String) {
    Normal("normal"),
    Deep("deep")
}

data class RestCycleTaskResult(
    val name: String,
    val status: String,
    val risk: String,
    val note: String
)

data class RestCycleMaintenanceReport(
    val mode: RestCycleMode,
    val fullChainVerified: Boolean,
    val organReconciliationHasIssues: Boolean,
    val sourceEventCount: Int,
    val meaningfulEventCount: Int,
    val policyApprovalRequired: Boolean,
    val policyReason: String,
    val approvalRequired: Boolean,
    val riskLevel: String,
    val tasks: List<RestCycleTaskResult>
) {
    fun migrationSteps(approvalRequiredForRun: Boolean): List<String> {
        return buildList {
            tasks.forEach { task -> add("${task.name}:${task.status}") }
            add("verify_recent_memory_tail:append_gate")
            if (approvalRequiredForRun) add("human_approval_required_for_sensitive_consolidation")
            add("append_rest_cycle_event")
            add("link_rest_cycle_to_source_events")
            add("compact_living_memory_snapshot")
            add("record_rest_cycle_audit_notes")
        }
    }

    fun expectedEffectLines(): List<String> {
        return listOf(
            "rest_cycle_mode=${mode.id}",
            "maintenance_risk=$riskLevel",
            "maintenance_approval_required=$approvalRequired",
            "policy_reason=$policyReason",
            "full_chain_verified=$fullChainVerified",
            "organ_reconciliation_has_issues=$organReconciliationHasIssues",
            "source_events=$sourceEventCount",
            "meaningful_events=$meaningfulEventCount"
        ) + tasks.map { task ->
            "task=${task.name} status=${task.status} risk=${task.risk} note=${task.note}"
        }
    }

    fun resultNotes(): List<String> {
        return listOf(
            "rest_cycle_mode:${mode.id}",
            "maintenance_risk:$riskLevel",
            "maintenance_approval_required:$approvalRequired",
            "maintenance_source_events:$sourceEventCount",
            "maintenance_meaningful_events:$meaningfulEventCount"
        ) + tasks.map { task ->
            "maintenance_task:${task.name}:${task.status}:${task.risk}"
        }
    }
}

object RestCycleMaintenancePlanner {
    fun build(
        mode: RestCycleMode,
        fullChainVerified: Boolean,
        organReconciliation: MemoryOrganReconciliationReport,
        sourceEventCount: Int,
        meaningfulEventCount: Int,
        policyApprovalRequired: Boolean,
        policyReason: String
    ): RestCycleMaintenanceReport {
        val approvalRequired = !fullChainVerified || organReconciliation.hasIssues || policyApprovalRequired
        val riskLevel = when {
            !fullChainVerified -> "high"
            organReconciliation.hasIssues -> "high"
            policyApprovalRequired -> "medium"
            else -> "low"
        }
        val tasks = listOf(
            RestCycleTaskResult(
                name = "audit_full_memory_chain",
                status = if (fullChainVerified) "verified" else "needs_quarantine_review",
                risk = if (fullChainVerified) "low" else "high",
                note = "full append-only memory chain"
            ),
            RestCycleTaskResult(
                name = "reconcile_memory_organs",
                status = if (organReconciliation.hasIssues) "issues_found" else "clean",
                risk = if (organReconciliation.hasIssues) "high" else "low",
                note = "links recalls capsules migrations"
            ),
            RestCycleTaskResult(
                name = "select_consolidation_sources",
                status = if (meaningfulEventCount > 0) "ready" else "empty",
                risk = if (policyApprovalRequired) "medium" else "low",
                note = "meaningful_events=$meaningfulEventCount"
            ),
            RestCycleTaskResult(
                name = "compact_living_memory_snapshot",
                status = "planned",
                risk = "low",
                note = "snapshot rebuild after append"
            )
        )

        return RestCycleMaintenanceReport(
            mode = mode,
            fullChainVerified = fullChainVerified,
            organReconciliationHasIssues = organReconciliation.hasIssues,
            sourceEventCount = sourceEventCount,
            meaningfulEventCount = meaningfulEventCount,
            policyApprovalRequired = policyApprovalRequired,
            policyReason = policyReason,
            approvalRequired = approvalRequired,
            riskLevel = riskLevel,
            tasks = tasks
        )
    }
}
