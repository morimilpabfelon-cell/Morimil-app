package com.morimil.app.ui

import com.morimil.app.ai.ReasoningHelperSlot
import com.morimil.app.ai.ReasoningPreset
import com.morimil.app.ai.ReasoningProviderConfig
import com.morimil.app.runtime.RestCycleScheduleStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrganismHealthUiStateTest {
    @Test
    fun healthyOrganismReportsOptionalLocalHelperSeparately() {
        val health = OrganismHealthUiStateBuilder.build(
            activeSlot = localHelper(),
            audit = verifiedAudit(NOW - 2L * HOUR),
            hasQuarantine = false,
            eventCount = 1240,
            restCycleScheduleStatus = RestCycleScheduleStatus.fromWorkStates(listOf("ENQUEUED")),
            latestRestCycleAtMillis = NOW - 45L * MINUTE,
            nowMillis = NOW
        )

        assertEquals(HealthStatusLevel.Stable, health.level)
        assertEquals("salud: estable", health.overallLabel)
        assertEquals("memoria: integra", health.memoryLabel)
        assertEquals("1.240 eventos", health.eventCountLabel)
        assertEquals("recalls: 0 activos", health.recallLabel)
        assertEquals("auditoria: hace 2h", health.auditAgeLabel)
        assertEquals("descanso: hace 45m", health.restCycleLabel)
        assertEquals(
            "Auxiliar temporal configurado: auxiliar: local disponible",
            health.helperLabel
        )
        assertEquals("accion: continuar", health.recommendedActionLabel)
        assertFalse(health.helperNeedsAttention)
        assertFalse(health.memoryNeedsAttention)
        assertFalse(health.auditNeedsAttention)
        assertTrue(health.healthSentence.contains("auxiliar: local disponible"))
    }

    @Test
    fun missingHelperDoesNotDegradeOrganismHealth() {
        val health = OrganismHealthUiStateBuilder.build(
            activeSlot = unconfiguredHelper(),
            audit = verifiedAudit(NOW),
            hasQuarantine = false,
            eventCount = 42,
            restCycleScheduleStatus = RestCycleScheduleStatus.fromWorkStates(listOf("ENQUEUED")),
            latestRestCycleAtMillis = NOW,
            nowMillis = NOW
        )

        assertEquals(HealthStatusLevel.Stable, health.level)
        assertEquals("salud: estable", health.overallLabel)
        assertEquals("accion: continuar", health.recommendedActionLabel)
        assertFalse(health.helperNeedsAttention)
        assertTrue(health.helperLabel.contains("no configurado (opcional)"))
    }

    @Test
    fun quarantineTakesPriorityOverStableScheduler() {
        val health = OrganismHealthUiStateBuilder.build(
            activeSlot = localHelper(),
            audit = verifiedAudit(NOW),
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
            activeSlot = localHelper(),
            audit = verifiedAudit(NOW - 25L * HOUR),
            hasQuarantine = false,
            eventCount = 1240,
            restCycleScheduleStatus = RestCycleScheduleStatus.fromWorkStates(listOf("ENQUEUED")),
            latestRestCycleAtMillis = NOW - 2L * HOUR,
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
            activeSlot = localHelper(),
            audit = verifiedAudit(NOW - 2L * HOUR),
            hasQuarantine = false,
            eventCount = 1240,
            recallPendingCount = 8,
            recallOverdueCount = 3,
            restCycleScheduleStatus = RestCycleScheduleStatus.fromWorkStates(listOf("ENQUEUED")),
            latestRestCycleAtMillis = NOW - 2L * HOUR,
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
            activeSlot = localHelper(),
            audit = MemoryIntegrityAuditUiState(),
            restCycleAudit = RestCycleAuditSignal(
                memoryChainVerified = true,
                capsuleChainVerified = true,
                checkedAtMillis = NOW - 30L * MINUTE
            ),
            hasQuarantine = false,
            eventCount = 42,
            restCycleScheduleStatus = RestCycleScheduleStatus.fromWorkStates(listOf("ENQUEUED")),
            latestRestCycleAtMillis = NOW - 30L * MINUTE,
            nowMillis = NOW
        )

        assertEquals(HealthStatusLevel.Stable, health.level)
        assertEquals("salud: estable", health.overallLabel)
        assertEquals("memoria: integra", health.memoryLabel)
        assertEquals("auditoria: hace 30m", health.auditAgeLabel)
        assertEquals("fuente: descanso", health.auditSourceLabel)
        assertEquals("accion: continuar", health.recommendedActionLabel)
    }

    @Test
    fun internalRuntimeIssueRaisesAttentionAndRecommendedAction() {
        val health = OrganismHealthUiStateBuilder.build(
            activeSlot = localHelper(),
            audit = verifiedAudit(NOW),
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

    private fun verifiedAudit(atMillis: Long): MemoryIntegrityAuditUiState {
        return MemoryIntegrityAuditUiState(
            memoryChainVerified = true,
            capsuleChainVerified = true,
            checkedAtMillis = atMillis
        )
    }

    private fun localHelper(): ReasoningHelperSlot {
        return ReasoningHelperSlot(
            config = ReasoningProviderConfig(
                preset = ReasoningPreset.LOCAL_USB_HELPER,
                baseUrl = "http://127.0.0.1:11434/v1/chat/completions",
                model = "local-model"
            )
        )
    }

    private fun unconfiguredHelper(): ReasoningHelperSlot {
        return ReasoningHelperSlot(
            config = ReasoningProviderConfig(
                preset = ReasoningPreset.CUSTOM,
                baseUrl = "",
                model = ""
            )
        )
    }

    companion object {
        private const val NOW = 1_700_000_000_000L
        private const val MINUTE = 60_000L
        private const val HOUR = 60L * MINUTE
    }
}
