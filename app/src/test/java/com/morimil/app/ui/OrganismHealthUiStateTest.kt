package com.morimil.app.ui

import com.morimil.app.ai.ReasoningMotorSlot
import com.morimil.app.ai.ReasoningPreset
import com.morimil.app.ai.ReasoningProviderConfig
import com.morimil.app.runtime.RestCycleScheduleStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrganismHealthUiStateTest {
    @Test
    fun healthyLocalMotorReportUsesHumanReadableSentence() {
        val health = OrganismHealthUiStateBuilder.build(
            activeSlot = localSlot(),
            audit = MemoryIntegrityAuditUiState(
                memoryChainVerified = true,
                capsuleChainVerified = true,
                checkedAtMillis = NOW - 2L * 60L * 60L * 1000L
            ),
            hasQuarantine = false,
            eventCount = 1240,
            restCycleScheduleStatus = RestCycleScheduleStatus.fromWorkStates(listOf("ENQUEUED")),
            latestRestCycleAtMillis = NOW - 45L * 60L * 1000L,
            nowMillis = NOW
        )

        assertEquals("salud: estable", health.overallLabel)
        assertEquals("memoria: integra", health.memoryLabel)
        assertEquals("1.240 eventos", health.eventCountLabel)
        assertEquals("auditoria: hace 2h", health.auditAgeLabel)
        assertEquals("descanso: hace 45m", health.restCycleLabel)
        assertEquals("Local: motor local activo", health.motorLabel)
        assertEquals("accion: continuar", health.recommendedActionLabel)
        assertFalse(health.memoryNeedsAttention)
        assertFalse(health.auditNeedsAttention)
        assertTrue(health.healthSentence.contains("integra, 1.240 eventos, hace 2h, motor local activo"))
    }

    @Test
    fun quarantineTakesPriorityOverStableScheduler() {
        val health = OrganismHealthUiStateBuilder.build(
            activeSlot = localSlot(),
            audit = MemoryIntegrityAuditUiState(
                memoryChainVerified = true,
                capsuleChainVerified = true,
                checkedAtMillis = NOW
            ),
            hasQuarantine = true,
            eventCount = 12,
            restCycleScheduleStatus = RestCycleScheduleStatus.fromWorkStates(listOf("ENQUEUED")),
            latestRestCycleAtMillis = null,
            nowMillis = NOW
        )

        assertEquals("salud: revisar memoria", health.overallLabel)
        assertEquals("memoria: cuarentena", health.memoryLabel)
        assertTrue(health.memoryNeedsAttention)
    }

    @Test
    fun staleAuditRecommendsRunningMemoryAudit() {
        val health = OrganismHealthUiStateBuilder.build(
            activeSlot = localSlot(),
            audit = MemoryIntegrityAuditUiState(
                memoryChainVerified = true,
                capsuleChainVerified = true,
                checkedAtMillis = NOW - 25L * 60L * 60L * 1000L
            ),
            hasQuarantine = false,
            eventCount = 1240,
            restCycleScheduleStatus = RestCycleScheduleStatus.fromWorkStates(listOf("ENQUEUED")),
            latestRestCycleAtMillis = NOW - 2L * 60L * 60L * 1000L,
            nowMillis = NOW
        )

        assertEquals("salud: auditar", health.overallLabel)
        assertEquals("accion: auditar memoria", health.recommendedActionLabel)
        assertTrue(health.auditNeedsAttention)
        assertFalse(health.memoryNeedsAttention)
    }

    @Test
    fun completedRestCycleCanProvideLastKnownAuditWhenManualAuditIsMissing() {
        val health = OrganismHealthUiStateBuilder.build(
            activeSlot = localSlot(),
            audit = MemoryIntegrityAuditUiState(),
            restCycleAudit = RestCycleAuditSignal(
                memoryChainVerified = true,
                capsuleChainVerified = true,
                checkedAtMillis = NOW - 30L * 60L * 1000L
            ),
            hasQuarantine = false,
            eventCount = 42,
            restCycleScheduleStatus = RestCycleScheduleStatus.fromWorkStates(listOf("ENQUEUED")),
            latestRestCycleAtMillis = NOW - 30L * 60L * 1000L,
            nowMillis = NOW
        )

        assertEquals("salud: estable", health.overallLabel)
        assertEquals("memoria: integra", health.memoryLabel)
        assertEquals("auditoria: hace 30m", health.auditAgeLabel)
        assertEquals("fuente: descanso", health.auditSourceLabel)
        assertEquals("accion: continuar", health.recommendedActionLabel)
        assertFalse(health.memoryNeedsAttention)
        assertFalse(health.auditNeedsAttention)
    }

    private fun localSlot(): ReasoningMotorSlot {
        return ReasoningMotorSlot(
            id = 1,
            label = "Local",
            config = ReasoningProviderConfig(
                preset = ReasoningPreset.LOCAL_COMPATIBLE,
                baseUrl = "http://127.0.0.1:11434/v1/chat/completions",
                model = "local-model"
            ),
            enabled = true
        )
    }

    companion object {
        private const val NOW = 1_700_000_000_000L
    }
}
