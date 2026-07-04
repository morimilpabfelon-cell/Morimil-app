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
        assertEquals(2, RecallSchedulePolicy.nextIntervalDays(1))
        assertEquals(4, RecallSchedulePolicy.nextIntervalDays(2))
        assertEquals(8, RecallSchedulePolicy.nextIntervalDays(4))
        assertEquals(30, RecallSchedulePolicy.nextIntervalDays(30))
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