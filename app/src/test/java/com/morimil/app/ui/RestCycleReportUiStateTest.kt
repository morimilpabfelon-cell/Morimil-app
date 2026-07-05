package com.morimil.app.ui

import com.morimil.app.data.local.MigrationRecordEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class RestCycleReportUiStateTest {
    @Test
    fun buildKeepsMaintenanceReportVisible() {
        val migration = migrationRecord(
            expectedEffect = """
                rest_cycle_mode=rest_cycle
                source_events=42
                meaningful_events=8
                full_chain_verified=true
                organ_reconciliation_has_issues=false
                organ_reconciliation_capsule_chain_verified=true
                maintenance_risk=medium
                maintenance_approval_required=true
                policy_reason=meaningful_event_threshold
                task=full_chain_audit status=completed risk=low note=verified from genesis
                task=organ_reconciliation status=completed risk=medium note=checked linked capsules
                source_events=8
            """.trimIndent(),
            errorsJson = """
                [
                  "rest_cycle_completed",
                  "maintenance_full_chain_audit_completed",
                  "organ_reconciliation_clean",
                  "unrelated_note"
                ]
            """.trimIndent()
        )

        val report = RestCycleReportUiStateBuilder.build(migration)

        assertEquals("rest_cycle", report.mode)
        assertEquals("medium", report.risk)
        assertEquals("true", report.approvalRequired)
        assertEquals("meaningful_event_threshold", report.policyReason)
        assertEquals("true", report.fullChainVerified)
        assertEquals("false", report.organReconciliation)
        assertEquals("true", report.capsuleChainVerified)
        assertEquals("42", report.sourceEvents)
        assertEquals("8", report.meaningfulEvents)
        assertEquals(2, report.tasks.size)
        assertEquals("full_chain_audit", report.tasks.first().name)
        assertEquals("completed", report.tasks.first().status)
        assertEquals("low", report.tasks.first().risk)
        assertEquals("verified from genesis", report.tasks.first().note)
        assertEquals(
            listOf(
                "rest_cycle_completed",
                "maintenance_full_chain_audit_completed",
                "organ_reconciliation_clean"
            ),
            report.resultNotes
        )
    }

    private fun migrationRecord(
        expectedEffect: String,
        errorsJson: String
    ): MigrationRecordEntity {
        return MigrationRecordEntity(
            migrationId = "rest-1",
            instanceId = "instance",
            genesisCoreHash = "genesis",
            proposalId = null,
            migrationType = "rest_cycle",
            fromVersion = "v1",
            toVersion = "v1",
            affectedArtifactsJson = "[]",
            preSnapshotId = "snapshot-before",
            chainVerified = false,
            backupRequired = true,
            stepsJson = "[]",
            expectedEffect = expectedEffect,
            riskLevel = "high",
            approvalRequired = false,
            approvedByUser = false,
            approvalId = null,
            status = "applied",
            postSnapshotId = "snapshot-after",
            errorsJson = errorsJson,
            rollbackAvailable = true,
            rollbackStrategy = "restore snapshot-before",
            createdBy = "test",
            createdAtMillis = 1L,
            updatedAtMillis = 2L
        )
    }
}
