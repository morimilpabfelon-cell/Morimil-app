package com.morimil.app.core.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNervousSystemHealthTest {
    @Test
    fun healthyLocalMemoryReportsHealthy() {
        val report = LocalNervousSystemHealth.build(
            input = baseInput(),
            generatedAtMillis = 1000L
        )

        assertEquals(LocalHealthStatus.HEALTHY, report.status)
        assertEquals("low", report.riskLevel)
        assertEquals("nervous_system.health_ok", report.eventType())
        assertTrue(!report.hasAlert)
    }

    @Test
    fun brokenMemoryChainReportsCritical() {
        val report = LocalNervousSystemHealth.build(
            input = baseInput(memoryChainVerified = false),
            generatedAtMillis = 1000L
        )

        assertEquals(LocalHealthStatus.CRITICAL, report.status)
        assertEquals("critical", report.riskLevel)
        assertEquals("nervous_system.health_critical", report.eventType())
        assertTrue(report.signals.any { signal -> signal.probableCause == "integrity_verification_failed" })
    }

    @Test
    fun missingSnapshotReportsDegraded() {
        val report = LocalNervousSystemHealth.build(
            input = baseInput(livingSnapshotCount = 0),
            generatedAtMillis = 1000L
        )

        assertEquals(LocalHealthStatus.DEGRADED, report.status)
        assertEquals("medium", report.riskLevel)
        assertEquals("nervous_system.health_degraded", report.eventType())
        assertTrue(report.signals.any { signal -> signal.probableCause == "snapshot_missing" })
    }

    @Test
    fun slowChainScanReportsDegraded() {
        val report = LocalNervousSystemHealth.build(
            input = baseInput(chainScanLatencyMillis = 2_000L),
            generatedAtMillis = 1000L
        )

        assertEquals(LocalHealthStatus.DEGRADED, report.status)
        assertTrue(report.signals.any { signal -> signal.name == "memory_chain_scan_latency" && signal.probableCause == "latency_above_threshold" })
    }

    private fun baseInput(
        genesisCoreCount: Int = 1,
        localIdentityCount: Int = 1,
        memoryEventCount: Int = 4,
        messageCount: Int = 2,
        livingSnapshotCount: Int = 1,
        recentContextCount: Int = 4,
        memoryChainVerified: Boolean = true,
        capsuleChainVerified: Boolean = true,
        organReconciliationHasIssues: Boolean = false,
        orphanedLinkCount: Int = 0,
        orphanedRecallCount: Int = 0,
        orphanedCapsuleCount: Int = 0,
        migrationMissingRefCount: Int = 0,
        chainScanLatencyMillis: Long = 100L,
        healthCheckLatencyMillis: Long = 20L
    ): LocalNervousSystemInput {
        return LocalNervousSystemInput(
            genesisCoreCount = genesisCoreCount,
            localIdentityCount = localIdentityCount,
            memoryEventCount = memoryEventCount,
            messageCount = messageCount,
            livingSnapshotCount = livingSnapshotCount,
            recentContextCount = recentContextCount,
            memoryChainVerified = memoryChainVerified,
            capsuleChainVerified = capsuleChainVerified,
            organReconciliationHasIssues = organReconciliationHasIssues,
            orphanedLinkCount = orphanedLinkCount,
            orphanedRecallCount = orphanedRecallCount,
            orphanedCapsuleCount = orphanedCapsuleCount,
            migrationMissingRefCount = migrationMissingRefCount,
            chainScanLatencyMillis = chainScanLatencyMillis,
            healthCheckLatencyMillis = healthCheckLatencyMillis
        )
    }
}
