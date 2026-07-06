package com.morimil.app.core.constitution

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreConstitutionGuardTest {
    @Test
    fun readingAndThinkingAreAllowed() {
        val read = CoreConstitutionGuard.evaluateIntent(
            scope = CoreConstitutionScope.READ_CONTEXT,
            humanApproved = false,
            throughReviewedMigration = false,
            chainVerified = false
        )
        val think = CoreConstitutionGuard.evaluateIntent(
            scope = CoreConstitutionScope.THINK,
            humanApproved = false,
            throughReviewedMigration = false,
            chainVerified = false
        )

        assertEquals(CoreConstitutionDecision.ALLOW, read.decision)
        assertEquals(CoreConstitutionDecision.ALLOW, think.decision)
    }

    @Test
    fun directGenesisMutationIsDenied() {
        val result = CoreConstitutionGuard.evaluateIntent(
            scope = CoreConstitutionScope.MUTATE_GENESIS_CORE,
            humanApproved = true,
            throughReviewedMigration = true,
            chainVerified = true
        )

        assertEquals(CoreConstitutionDecision.DENY, result.decision)
        assertEquals("critical", result.riskLevel)
        assertTrue("genesis_core_is_immutable" in result.reasons)
    }

    @Test
    fun doctrineMigrationWithoutControlsIsDenied() {
        val result = CoreConstitutionGuard.evaluateMigrationPlan(
            migrationType = "doctrine_amendment",
            affectedArtifacts = listOf("core_doctrine"),
            chainVerified = false,
            backupRequired = false,
            approvalRequired = false,
            rollbackAvailable = false,
            approvedByUser = false
        )

        assertEquals(CoreConstitutionDecision.DENY, result.decision)
        assertTrue("core_migration_missing_controls" in result.reasons)
        assertTrue("verified_memory_chain" in result.requiredControls)
        assertTrue("human_approval_required" in result.requiredControls)
    }

    @Test
    fun doctrineMigrationWithControlsRequiresReviewUntilApproved() {
        val result = CoreConstitutionGuard.evaluateMigrationPlan(
            migrationType = "doctrine_amendment",
            affectedArtifacts = listOf("core_doctrine"),
            chainVerified = true,
            backupRequired = true,
            approvalRequired = true,
            rollbackAvailable = true,
            approvedByUser = false
        )

        assertEquals(CoreConstitutionDecision.REVIEW_REQUIRED, result.decision)
        assertEquals("high", result.riskLevel)
        assertTrue("human_approval" in result.requiredControls)
    }

    @Test
    fun approvedPolicyMigrationIsAllowed() {
        val result = CoreConstitutionGuard.evaluateMigrationPlan(
            migrationType = "policy_amendment",
            affectedArtifacts = listOf("core_policy"),
            chainVerified = true,
            backupRequired = true,
            approvalRequired = true,
            rollbackAvailable = true,
            approvedByUser = true
        )

        assertEquals(CoreConstitutionDecision.ALLOW, result.decision)
        assertTrue("approved_core_amendment_migration" in result.reasons)
    }

    @Test
    fun normalRestCycleMigrationIsAllowedAsNonCore() {
        val result = CoreConstitutionGuard.evaluateMigrationPlan(
            migrationType = "rest_cycle.local_consolidation",
            affectedArtifacts = listOf("sha256:abc"),
            chainVerified = true,
            backupRequired = false,
            approvalRequired = false,
            rollbackAvailable = true,
            approvedByUser = false
        )

        assertEquals(CoreConstitutionDecision.ALLOW, result.decision)
        assertTrue("non_core_migration" in result.reasons)
    }
}
