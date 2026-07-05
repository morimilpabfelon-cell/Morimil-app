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
}
