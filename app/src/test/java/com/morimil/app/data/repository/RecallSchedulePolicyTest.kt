package com.morimil.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecallSchedulePolicyTest {
    @Test
    fun chatNoiseIsNeverScheduled() {
        assertFalse(
            RecallSchedulePolicy.shouldSchedule(
                memoryKind = "chat_noise",
                importance = 100,
                confidence = 100,
                userConfirmed = true
            )
        )
    }

    @Test
    fun importantDecisionIsScheduled() {
        assertTrue(
            RecallSchedulePolicy.shouldSchedule(
                memoryKind = "decision",
                importance = 85,
                confidence = 90,
                userConfirmed = false
            )
        )
    }

    @Test
    fun intervalGrowsConservatively() {
        assertEquals(1, RecallSchedulePolicy.nextIntervalDays(0))
        assertEquals(3, RecallSchedulePolicy.nextIntervalDays(1))
        assertEquals(7, RecallSchedulePolicy.nextIntervalDays(2))
        assertEquals(11, RecallSchedulePolicy.nextIntervalDays(4))
        assertEquals(30, RecallSchedulePolicy.nextIntervalDays(30))
    }

    @Test
    fun highPriorityMemoryStaysCloser() {
        assertEquals(1, RecallSchedulePolicy.initialIntervalDays(95))
        assertEquals(3, RecallSchedulePolicy.initialIntervalDays(60))
        assertEquals(2, RecallSchedulePolicy.nextIntervalDays(currentIntervalDays = 1, priority = 95))
        assertEquals(21, RecallSchedulePolicy.nextIntervalDays(currentIntervalDays = 30, priority = 95))
    }

    @Test
    fun confirmedMemoryGetsPriorityBoost() {
        val normal = RecallSchedulePolicy.priority(
            importance = 70,
            confidence = 70,
            userConfirmed = false
        )
        val confirmed = RecallSchedulePolicy.priority(
            importance = 70,
            confidence = 70,
            userConfirmed = true
        )
        assertTrue(confirmed > normal)
    }

    @Test
    fun priorityBandExplainsRecallImportance() {
        assertEquals("critical", RecallSchedulePolicy.priorityBand(95))
        assertEquals("high", RecallSchedulePolicy.priorityBand(80))
        assertEquals("medium", RecallSchedulePolicy.priorityBand(60))
        assertEquals("low", RecallSchedulePolicy.priorityBand(30))
    }

    @Test
    fun overdueRecallsBecomeMoreUrgentThanFutureRecalls() {
        val now = 10L * RecallSchedulePolicy.ONE_DAY_MILLIS
        val future = RecallSchedulePolicy.urgencyScore(
            priority = 80,
            dueAtMillis = now + (5L * RecallSchedulePolicy.ONE_DAY_MILLIS),
            nowMillis = now
        )
        val overdue = RecallSchedulePolicy.urgencyScore(
            priority = 80,
            dueAtMillis = now - (2L * RecallSchedulePolicy.ONE_DAY_MILLIS),
            nowMillis = now
        )

        assertTrue(overdue > future)
    }

    @Test
    fun dueSoonMeansWithinThreeDaysButNotOverdue() {
        val now = 20L * RecallSchedulePolicy.ONE_DAY_MILLIS

        assertTrue(
            RecallSchedulePolicy.isDueSoon(
                dueAtMillis = now + RecallSchedulePolicy.ONE_DAY_MILLIS,
                nowMillis = now
            )
        )
        assertFalse(
            RecallSchedulePolicy.isDueSoon(
                dueAtMillis = now - RecallSchedulePolicy.ONE_DAY_MILLIS,
                nowMillis = now
            )
        )
        assertFalse(
            RecallSchedulePolicy.isDueSoon(
                dueAtMillis = now + (4L * RecallSchedulePolicy.ONE_DAY_MILLIS),
                nowMillis = now
            )
        )
    }
}
