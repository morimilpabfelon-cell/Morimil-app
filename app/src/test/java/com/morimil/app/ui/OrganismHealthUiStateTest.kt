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

        assertEquals(HealthStatusLevel.Stable, health.level)
        assertEquals("salud: estable", health.overallLabel)
        assertEquals("memoria: integra", health.memoryLabel)
        assertEquals("1.240 eventos", health.eventCountLabel)
        assertEquals("recalls: 0 activos", health.recallLabel)
        assertEquals("auditoria: hace 2h", health.auditAgeLabel)
        assertEquals("descanso: hace 45m", health.restCycleLabel)
        assertEquals("Motor auxiliar configurado: motor local activo", health.motorLabel)
        assertEquals("accion: continuar", health.recommendedActionLabel)
        assertFalse(health.memoryNeedsAttention)
        assertFalse(health.auditNeedsAttention)
        assertTrue(health.healthSentence.contains("integra, 1.240 eventos, 0 activos, hace 2h, motor local activo"))
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

        assertEquals(HealthStatusLevel.Critical, health.level)
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

        assertEquals(HealthStatusLevel.Attention, health.level)
        assertEquals("salud: auditar", health.overallLabel)
        assertEquals("accion: auditar memoria", health.recommendedActionLabel)
        assertTrue(health.auditNeedsAttention)
        assertFalse(health.memoryNeedsAttention)
    }

    @Test
    fun overdueRecallsRaiseAttentionAndRecommendReview() {
        val health = OrganismHealthUiStateBuilder.build(
            activeSlot = localSlot(),
            audit = MemoryIntegrityAuditUiState(
                memoryChainVerified = true,
                capsuleChainVerified = true,
                checkedAtMillis = NOW - 2L * 60L * 60L * 1000L
            ),
            hasQuarantine = false,
            eventCount = 1240,
            recallPendingCount = 8,
            recallOverdueCount = 3,
            restCycleScheduleStatus = RestCycleScheduleStatus.fromWorkStates(listOf("ENQUEUED")),
            latestRestCycleAtMillis = NOW - 2L * 60L * 60L * 1000L,
            nowMillis = NOW
        )

        assertEquals(HealthStatusLevel.Attention, health.level)
        assertEquals("salud: revisar recalls", health.overallLabel)
        assertEquals("recalls: 3 vencidos / 8 activos", health.recallLabel)
        assertEquals("accion: revisar recalls", health.recommendedActionLabel)
        assertTrue(health.recallNeedsAttention)
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

        assertEquals(HealthStatusLevel.Stable, health.level)
        assertEquals("salud: estable", health.overallLabel)
        assertEquals("memoria: integra", health.memoryLabel)
        assertEquals("auditoria: hace 30m", health.auditAgeLabel)
        assertEquals("fuente: descanso", health.auditSourceLabel)
        assertEquals("accion: continuar", health.recommendedActionLabel)
        assertFalse(health.memoryNeedsAttention)
        assertFalse(health.auditNeedsAttention)
    }

    @Test
    fun internalRuntimeIssueRaisesAttentionAndRecommendedAction() {
        val health = OrganismHealthUiStateBuilder.build(
            activeSlot = localSlot(),
            audit = MemoryIntegrityAuditUiState(
                memoryChainVerified = true,
                capsuleChainVerified = true,
                checkedAtMillis = NOW
            ),
            hasQuarantine = false,
            internalIssue = InternalRuntimeIssueUiState(
                component = "rest_cycle.after_message",
                message = "link creation failed",
                failureCount = 2,
                occurredAtMillis = NOW
            ),
            eventCount = 42,
            restCycleScheduleStatus = RestCycleScheduleStatus.fromWorkStates(listOf("ENQUEUED")),
            latestRestCycleAtMillis = NOW,
            nowMillis = NOW
        )

        assertEquals(HealthStatusLevel.Attention, health.level)
        assertEquals("salud: revisar runtime", health.overallLabel)
        assertEquals("fallo interno: rest_cycle.after_message x2", health.internalIssueLabel)
        assertEquals("detalle: link creation failed; hace ahora", health.internalIssueDetailLabel)
        assertEquals("accion: revisar fallos internos", health.recommendedActionLabel)
        assertTrue(health.internalIssueNeedsAttention)
    }

    private fun localSlot(): ReasoningMotorSlot {
        return ReasoningMotorSlot(
            config = ReasoningProviderConfig(
                preset = ReasoningPreset.LOCAL_USB_HELPER,
                baseUrl = "http://127.0.0.1:11434/v1/chat/completions",
                model = "local-model"
            )
        )
    }

    companion object {
        private const val NOW = 1_700_000_000_000L
    }
}
