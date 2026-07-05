package com.morimil.app.core.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestCycleMaintenancePlannerTest {
    @Test
    fun cleanNormalCycleIsLowRiskAndExecutable() {
        val report = RestCycleMaintenancePlanner.build(
            mode = RestCycleMode.Normal,
            fullChainVerified = true,
            organReconciliation = cleanReconciliation(),
            sourceEventCount = 20,
            meaningfulEventCount = 7,
            policyApprovalRequired = false,
            policyReason = "confirmed=1 high_impact=0 critical_kinds=0"
        )

        assertEquals("low", report.riskLevel)
        assertFalse(report.approvalRequired)
        assertTrue(report.migrationSteps(approvalRequiredForRun = false).contains("audit_full_memory_chain:verified"))
        assertTrue(report.migrationSteps(approvalRequiredForRun = false).contains("compact_living_memory_snapshot"))
    }

    @Test
    fun brokenChainRequiresHighRiskHumanReview() {
        val report = RestCycleMaintenancePlanner.build(
            mode = RestCycleMode.Normal,
            fullChainVerified = false,
            organReconciliation = cleanReconciliation(),
            sourceEventCount = 20,
            meaningfulEventCount = 7,
            policyApprovalRequired = false,
            policyReason = "confirmed=0 high_impact=0 critical_kinds=0"
        )

        assertEquals("high", report.riskLevel)
        assertTrue(report.approvalRequired)
        assertTrue(report.migrationSteps(approvalRequiredForRun = true).contains("audit_full_memory_chain:needs_quarantine_review"))
        assertTrue(report.migrationSteps(approvalRequiredForRun = true).contains("human_approval_required_for_sensitive_consolidation"))
    }

    @Test
    fun organIssuesRequireHighRiskHumanReview() {
        val report = RestCycleMaintenancePlanner.build(
            mode = RestCycleMode.Deep,
            fullChainVerified = true,
            organReconciliation = cleanReconciliation().copy(orphanedLinkIds = listOf("link-1")),
            sourceEventCount = 20,
            meaningfulEventCount = 7,
            policyApprovalRequired = false,
            policyReason = "confirmed=0 high_impact=0 critical_kinds=0"
        )

        assertEquals("high", report.riskLevel)
        assertTrue(report.approvalRequired)
        assertTrue(report.expectedEffectLines().any { line -> line == "rest_cycle_mode=deep" })
        assertTrue(report.expectedEffectLines().any { line -> line.contains("reconcile_memory_organs") && line.contains("issues_found") })
    }

    @Test
    fun brokenCapsuleChainRequiresHighRiskHumanReview() {
        val report = RestCycleMaintenancePlanner.build(
            mode = RestCycleMode.Deep,
            fullChainVerified = true,
            organReconciliation = cleanReconciliation().copy(capsuleChainVerified = false),
            sourceEventCount = 20,
            meaningfulEventCount = 7,
            policyApprovalRequired = false,
            policyReason = "confirmed=0 high_impact=0 critical_kinds=0"
        )

        assertEquals("high", report.riskLevel)
        assertTrue(report.approvalRequired)
        assertTrue(report.expectedEffectLines().any { line -> line == "organ_reconciliation_has_issues=true" })
    }

    @Test
    fun sensitiveCleanCycleRequiresMediumRiskReview() {
        val report = RestCycleMaintenancePlanner.build(
            mode = RestCycleMode.Normal,
            fullChainVerified = true,
            organReconciliation = cleanReconciliation(),
            sourceEventCount = 20,
            meaningfulEventCount = 7,
            policyApprovalRequired = true,
            policyReason = "confirmed=3 high_impact=3 critical_kinds=1"
        )

        assertEquals("medium", report.riskLevel)
        assertTrue(report.approvalRequired)
        assertTrue(report.resultNotes().contains("maintenance_risk:medium"))
    }

    private fun cleanReconciliation(): MemoryOrganReconciliationReport {
        return MemoryOrganReconciliationReport(
            scannedLinks = 0,
            orphanedLinkIds = emptyList(),
            scannedRecalls = 0,
            orphanedRecallIds = emptyList(),
            scannedCapsules = 0,
            orphanedCapsuleIds = emptyList(),
            scannedMigrations = 0,
            migrationMissingRefs = emptyMap()
        )
    }
}
