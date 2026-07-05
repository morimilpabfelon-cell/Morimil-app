package com.morimil.app.data.repository

import com.morimil.app.core.memory.MemoryOrganReconciliation
import com.morimil.app.core.memory.MemoryOrganReconciliationReport
import com.morimil.app.core.memory.MemoryIntegrityCore
import com.morimil.app.data.local.MemoryOrganDatabase

class MemoryOrganReconciliationRepository(
    organDatabase: MemoryOrganDatabase,
    private val memoryIntegrityCore: MemoryIntegrityCore
) {
    private val organDao = organDatabase.memoryOrganDao()
    private val reconciliation = MemoryOrganReconciliation()

    suspend fun reconcileAgainstMemoryEvents(validMemoryEventHashes: Set<String>): MemoryOrganReconciliationReport {
        val capsuleChain = organDao.loadKnowledgeCapsuleChain()
        val report = reconciliation.buildReport(
            validMemoryEventHashes = validMemoryEventHashes,
            links = organDao.loadMemoryLinksForReconciliation(),
            recalls = organDao.loadActiveRecallSchedulesForReconciliation(),
            capsules = organDao.loadKnowledgeCapsulesWithSourceEvents(),
            migrations = organDao.loadMigrationRecordsForReconciliation(),
            capsuleChainVerified = memoryIntegrityCore.verifyCapsuleChain(capsuleChain)
        )

        val markedOrphanedLinks = if (report.orphanedLinkIds.isEmpty()) {
            0
        } else {
            organDao.markMemoryLinksOrphaned(report.orphanedLinkIds)
        }
        var degradedRecalls = 0
        report.orphanedRecallIds.forEach { recallId ->
            degradedRecalls += organDao.markRecallScheduleDegraded(
                recallId = recallId,
                updatedAtMillis = System.currentTimeMillis()
            )
        }

        return report.copy(
            markedOrphanedLinks = markedOrphanedLinks,
            degradedRecalls = degradedRecalls
        )
    }
}
