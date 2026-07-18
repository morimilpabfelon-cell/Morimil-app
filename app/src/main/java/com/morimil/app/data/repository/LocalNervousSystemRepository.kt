package com.morimil.app.data.repository

import com.morimil.app.core.health.LocalHealthStatus
import com.morimil.app.core.health.LocalNervousSystemHealth
import com.morimil.app.core.health.LocalNervousSystemInput
import com.morimil.app.core.health.LocalNervousSystemReport
import com.morimil.app.core.memory.MemoryOrganReconciliationReport
import com.morimil.app.data.local.MemoryDao
import com.morimil.app.data.local.MemoryEventEntity
import com.morimil.app.data.local.MorimilDatabase

class LocalNervousSystemRepository(
    database: MorimilDatabase,
    private val memoryRepository: MemoryRepository
) {
    private val memoryDao: MemoryDao = database.memoryDao()

    suspend fun recordHealthCheckIfDegraded(
        source: String,
        fullMemoryChain: List<MemoryEventEntity>,
        memoryChainVerified: Boolean,
        chainScanLatencyMillis: Long,
        organReconciliation: MemoryOrganReconciliationReport,
        nowMillis: Long = System.currentTimeMillis()
    ): LocalNervousSystemReport {
        val startedAtMillis = System.currentTimeMillis()
        val genesisCoreCount = memoryDao.countGenesisCore()
        val localIdentityCount = memoryDao.countLocalIdentity()
        val memoryEventCount = memoryDao.countMemoryEvents()
        val livingSnapshotCount = memoryDao.countLivingMemorySnapshot()
        val recentContextCount = memoryDao.loadMemoryContext(20).size
        val healthCheckLatencyMillis = System.currentTimeMillis() - startedAtMillis

        val report = LocalNervousSystemHealth.build(
            input = LocalNervousSystemInput(
                genesisCoreCount = genesisCoreCount,
                localIdentityCount = localIdentityCount,
                memoryEventCount = memoryEventCount,
                // Legacy health field: the reasoning transcript is not memory.
                messageCount = 0,
                livingSnapshotCount = livingSnapshotCount,
                recentContextCount = recentContextCount,
                memoryChainVerified = memoryChainVerified,
                capsuleChainVerified = organReconciliation.capsuleChainVerified,
                organReconciliationHasIssues = organReconciliation.hasIssues,
                orphanedLinkCount = organReconciliation.orphanedLinkIds.size,
                orphanedRecallCount = organReconciliation.orphanedRecallIds.size,
                orphanedCapsuleCount = organReconciliation.orphanedCapsuleIds.size,
                migrationMissingRefCount = organReconciliation.migrationMissingRefs.size,
                chainScanLatencyMillis = chainScanLatencyMillis,
                healthCheckLatencyMillis = healthCheckLatencyMillis
            ),
            generatedAtMillis = nowMillis
        )
        if (report.hasAlert && genesisCoreCount > 0 && shouldRecordAlert(report, nowMillis)) {
            memoryRepository.recordSystemMemoryEvent(
                eventType = report.eventType(),
                body = report.eventBody() + "; scanned_events=${fullMemoryChain.size}",
                importance = report.importance(),
                evidenceJson = report.evidenceJson(source)
            )
        }
        return report
    }

    private suspend fun shouldRecordAlert(report: LocalNervousSystemReport, nowMillis: Long): Boolean {
        val latest = memoryDao.loadLatestMemoryEventByType(report.eventType()) ?: return true
        return nowMillis - latest.createdAtMillis >= HEALTH_ALERT_MIN_INTERVAL_MILLIS
    }

    private fun LocalNervousSystemReport.importance(): Int {
        return when (status) {
            LocalHealthStatus.CRITICAL -> 100
            LocalHealthStatus.DEGRADED -> 90
            LocalHealthStatus.HEALTHY -> 50
        }
    }

    companion object {
        private const val HEALTH_ALERT_MIN_INTERVAL_MILLIS = 60L * 60L * 1000L
    }
}
