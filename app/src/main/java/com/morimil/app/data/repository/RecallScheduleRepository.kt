package com.morimil.app.data.repository

import com.morimil.app.data.local.MemoryEventEntity
import com.morimil.app.data.local.MemoryOrganDatabase
import com.morimil.app.data.local.MorimilDatabase
import com.morimil.app.data.local.RecallScheduleEntity
import kotlinx.coroutines.flow.Flow

class RecallScheduleRepository(
    organDatabase: MemoryOrganDatabase,
    memoryDatabase: MorimilDatabase
) {
    private val organDao = organDatabase.memoryOrganDao()
    private val memoryDao = memoryDatabase.memoryDao()
    private val memoryLinkRepository = MemoryLinkRepository(organDatabase)

    val activeRecallSchedules: Flow<List<RecallScheduleEntity>> = organDao.observeActiveRecallSchedules()

    suspend fun seedFromRecentMemoryIfNeeded(limit: Int = 10): Int {
        val genesisCore = memoryDao.loadGenesisCore() ?: return 0
        val localIdentity = memoryDao.loadLocalIdentity()
        val now = System.currentTimeMillis()
        val candidates = memoryDao.loadMemoryContext(60)
            .filter { event ->
                RecallSchedulePolicy.shouldSchedule(
                    memoryKind = event.memoryKind,
                    importance = event.importance,
                    confidence = event.confidence,
                    userConfirmed = event.userConfirmed
                )
            }
            .sortedWith(
                compareByDescending<MemoryEventEntity> { it.userConfirmed }
                    .thenByDescending { it.importance }
                    .thenByDescending { it.confidence }
                    .thenByDescending { it.createdAtMillis }
            )
            .take(limit)

        var created = 0
        candidates.forEach { event ->
            val priority = RecallSchedulePolicy.priority(
                memoryKind = event.memoryKind,
                importance = event.importance,
                confidence = event.confidence,
                userConfirmed = event.userConfirmed
            )
            val intervalDays = RecallSchedulePolicy.initialIntervalDays(priority)
            val insertedId = organDao.insertRecallSchedule(
                RecallScheduleEntity(
                    genesisCoreId = genesisCore.coreId,
                    targetEventHash = event.eventHash,
                    targetMemoryKind = event.memoryKind,
                    prompt = buildPrompt(event),
                    reason = "local_recall_schedule_v1:${event.memoryKind}/i${event.importance}/c${event.confidence}",
                    priority = priority,
                    intervalDays = intervalDays,
                    dueAtMillis = now + RecallSchedulePolicy.delayMillis(intervalDays),
                    status = "active",
                    lastAction = "created",
                    source = "local_memory_event",
                    createdAtMillis = now,
                    updatedAtMillis = now,
                    lastReviewedAtMillis = null
                )
            )
            if (insertedId > 0) {
                created += 1
                memoryLinkRepository.createMemoryLink(
                    instanceId = localIdentity?.instanceId ?: "local_instance_pending",
                    genesisCoreHash = genesisCore.contentSha256,
                    sourceId = "recall:$insertedId",
                    sourceType = RECALL_NODE_TYPE,
                    targetId = event.eventHash,
                    targetType = MemoryLinkRepository.MEMORY_EVENT_NODE_TYPE,
                    relation = RELATION_SCHEDULES_REVIEW_FOR,
                    strength = priority / 100.0,
                    reason = "recall_schedule:${event.memoryKind}/priority=$priority"
                )
            }
        }
        return created
    }

    suspend fun reinforceRecall(recallId: Long) {
        val schedule = requireNotNull(organDao.loadRecallSchedule(recallId)) {
            "Recall schedule not found."
        }
        val now = System.currentTimeMillis()
        val nextInterval = RecallSchedulePolicy.nextIntervalDays(
            currentIntervalDays = schedule.intervalDays,
            priority = schedule.priority
        )
        val rows = organDao.updateRecallSchedule(
            recallId = recallId,
            dueAtMillis = now + RecallSchedulePolicy.delayMillis(nextInterval),
            intervalDays = nextInterval,
            status = "active",
            lastAction = "reinforced",
            lastReviewedAtMillis = now,
            updatedAtMillis = now
        )
        require(rows > 0) { "Recall schedule update failed." }
    }

    suspend fun postponeRecall(recallId: Long) {
        val schedule = requireNotNull(organDao.loadRecallSchedule(recallId)) {
            "Recall schedule not found."
        }
        val now = System.currentTimeMillis()
        val intervalDays = RecallSchedulePolicy.postponedIntervalDays(schedule.priority)
        val rows = organDao.updateRecallSchedule(
            recallId = recallId,
            dueAtMillis = now + RecallSchedulePolicy.delayMillis(intervalDays),
            intervalDays = schedule.intervalDays.coerceAtLeast(intervalDays),
            status = "active",
            lastAction = "postponed",
            lastReviewedAtMillis = schedule.lastReviewedAtMillis,
            updatedAtMillis = now
        )
        require(rows > 0) { "Recall schedule postpone failed." }
    }

    suspend fun degradeRecall(recallId: Long) {
        val now = System.currentTimeMillis()
        val rows = organDao.markRecallScheduleDegraded(
            recallId = recallId,
            updatedAtMillis = now
        )
        require(rows > 0) { "Recall schedule degrade failed." }
    }

    private fun buildPrompt(event: MemoryEventEntity): String {
        val label = event.memoryKind.ifBlank { event.eventType }
        return "Repasar $label: " + event.body
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(360)
    }

    companion object {
        const val RECALL_NODE_TYPE = "recall_schedule"
        const val RELATION_SCHEDULES_REVIEW_FOR = "schedules_review_for"
    }
}
