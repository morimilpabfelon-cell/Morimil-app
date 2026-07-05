package com.morimil.app.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestCycleScheduleStatusTest {
    @Test
    fun emptyStatesNeedAttention() {
        val status = RestCycleScheduleStatus.fromWorkStates(emptyList())

        assertEquals("no_agendado", status.stateLabel)
        assertFalse(status.isScheduled)
        assertTrue(status.needsAttention)
    }

    @Test
    fun enqueuedStateIsScheduled() {
        val status = RestCycleScheduleStatus.fromWorkStates(listOf("enqueued"))

        assertEquals("ENQUEUED", status.stateLabel)
        assertTrue(status.isScheduled)
        assertFalse(status.needsAttention)
    }

    @Test
    fun cancelledStateNeedsAttention() {
        val status = RestCycleScheduleStatus.fromWorkStates(listOf("cancelled"))

        assertEquals("CANCELLED", status.stateLabel)
        assertFalse(status.isScheduled)
        assertTrue(status.needsAttention)
    }

    @Test
    fun duplicateStatesAreNormalized() {
        val status = RestCycleScheduleStatus.fromWorkStates(listOf(" enqueued ", "ENQUEUED", "running"))

        assertEquals("ENQUEUED,RUNNING", status.stateLabel)
        assertTrue(status.isScheduled)
        assertFalse(status.needsAttention)
    }
}
