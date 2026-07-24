package com.morimil.app.ui

import com.morimil.app.ai.ReasoningHelperSlot
import com.morimil.app.runtime.RestCycleScheduleStatus

data class OrganismHealthUiState(
    val level: HealthStatusLevel = HealthStatusLevel.Attention,
    val overallLabel: String = "salud pendiente",
    val healthSentence: String = "Morimil aun no tiene reporte de salud local.",
    val helperLabel: String = "auxiliar: no configurado (opcional)",
    val helperModelLabel: String = "sin modelo auxiliar",
    val helperNeedsAttention: Boolean = false,
    val memoryLabel: String = "memoria: sin auditar",
    val memoryNeedsAttention: Boolean = false,
    val eventCount: Int = 0,
    val eventCountLabel: String = "0 eventos",
    val auditAgeLabel: String = "auditoria: sin registro",
    val auditSourceLabel: String = "fuente: sin auditoria",
    val auditNeedsAttention: Boolean = true,
    val recallPendingCount: Int = 0,
    val recallOverdueCount: Int = 0,
    val recallLabel: String = "recalls: 0 activos",
    val recallNeedsAttention: Boolean = false,
    val restCycleLabel: String = "descanso: sin registro",
    val restCycleNeedsAttention: Boolean = false,
    val internalIssueLabel: String = "fallos internos: ninguno",
    val internalIssueDetailLabel: String = "",
    val internalIssueNeedsAttention: Boolean = false,
    val recommendedActionLabel: String = "accion: auditar memoria",
    val checkedAtMillis: Long? = null
)

data class InternalRuntimeIssueUiState(
    val component: String,
    val message: String,
    val failureCount: Int,
    val occurredAtMillis: Long
) {
    val label: String
        get() = "fallo interno: $component"
}

object OrganismHealthUiStateBuilder {
    fun build(
        activeHelper: ReasoningHelperSlot,
        audit: MemoryIntegrityAuditUiState,
        restCycleAudit: RestCycleAuditSignal? = null,
        hasQuarantine: Boolean,
        internalIssue: InternalRuntimeIssueUiState? = null,
        eventCount: Int,
        recallPendingCount: Int = 0,
        recallOverdueCount: Int = 0,
        restCycleScheduleStatus: RestCycleScheduleStatus,
        latestRestCycleAtMillis: Long?,
        nowMillis: Long
    ): OrganismHealthUiState {
        val helperModelLabel = activeHelper.config.model.ifBlank { "sin modelo auxiliar" }
        val helperConfigured =
            activeHelper.config.baseUrl.isNotBlank() && activeHelper.config.model.isNotBlank()
        val helperIsLocal = !activeHelper.config.requiresRuntimeKey && helperConfigured
        val helperLabel = when {
            !helperConfigured -> "auxiliar: no configurado (opcional)"
            helperIsLocal -> "auxiliar: local disponible"
            else -> "auxiliar: remoto disponible"
        }
        val effectiveMemoryVerified = audit.memoryChainVerified ?: restCycleAudit?.memoryChainVerified
        val effectiveCapsulesVerified = audit.capsuleChainVerified ?: restCycleAudit?.capsuleChainVerified
        val effectiveAuditAtMillis = audit.checkedAtMillis ?: restCycleAudit?.checkedAtMillis
        val auditSourceLabel = when {
            audit.checkedAtMillis != null -> "fuente: auditoria manual"
            restCycleAudit?.checkedAtMillis != null -> "fuente: descanso"
            else -> "fuente: sin auditoria"
        }
        val memoryLabel = when {
            hasQuarantine -> "memoria: cuarentena"
            audit.errorMessage != null -> "memoria: error de auditoria"
            effectiveMemoryVerified == true && effectiveCapsulesVerified == true -> "memoria: integra"
            effectiveMemoryVerified == false || effectiveCapsulesVerified == false -> "memoria: revisar"
            else -> "memoria: sin auditar"
        }
        val memoryNeedsAttention = hasQuarantine ||
            audit.errorMessage != null ||
            effectiveMemoryVerified == false ||
            effectiveCapsulesVerified == false
        val auditNeedsAttention = effectiveAuditAtMillis == null ||
            nowMillis - effectiveAuditAtMillis > AUDIT_STALE_AFTER_MILLIS
        val safeRecallPendingCount = recallPendingCount.coerceAtLeast(0)
        val safeRecallOverdueCount = recallOverdueCount.coerceAtLeast(0)
        val recallNeedsAttention = safeRecallOverdueCount > 0
        val recallLabel = if (recallNeedsAttention) {
            "recalls: $safeRecallOverdueCount vencidos / $safeRecallPendingCount activos"
        } else {
            "recalls: $safeRecallPendingCount activos"
        }
        val auditAgeLabel = effectiveAuditAtMillis?.let { checkedAt ->
            "auditoria: hace ${ageLabel(nowMillis - checkedAt)}"
        } ?: "auditoria: sin registro"
        val restCycleLabel = latestRestCycleAtMillis?.let { checkedAt ->
            "descanso: hace ${ageLabel(nowMillis - checkedAt)}"
        } ?: if (restCycleScheduleStatus.isScheduled) {
            "descanso: agendado"
        } else {
            "descanso: no agendado"
        }
        val overallLabel = when {
            memoryNeedsAttention -> "salud: revisar memoria"
            internalIssue != null -> "salud: revisar runtime"
            auditNeedsAttention -> "salud: auditar"
            recallNeedsAttention -> "salud: revisar recalls"
            restCycleScheduleStatus.needsAttention -> "salud: revisar descanso"
            else -> "salud: estable"
        }
        val level = when {
            hasQuarantine || audit.errorMessage != null ||
                effectiveMemoryVerified == false || effectiveCapsulesVerified == false -> HealthStatusLevel.Critical
            internalIssue != null -> HealthStatusLevel.Attention
            auditNeedsAttention || recallNeedsAttention || restCycleScheduleStatus.needsAttention -> HealthStatusLevel.Attention
            else -> HealthStatusLevel.Stable
        }
        val recommendedActionLabel = when {
            hasQuarantine -> "accion: revisar cuarentena"
            audit.errorMessage != null -> "accion: repetir auditoria"
            effectiveMemoryVerified == false || effectiveCapsulesVerified == false -> "accion: aislar memoria"
            internalIssue != null -> "accion: revisar fallos internos"
            auditNeedsAttention -> "accion: auditar memoria"
            recallNeedsAttention -> "accion: revisar recalls"
            restCycleScheduleStatus.needsAttention -> "accion: activar descanso"
            else -> "accion: continuar"
        }
        val healthSentence = listOf(
            memoryLabel.removePrefix("memoria: "),
            formatEventCount(eventCount),
            recallLabel.removePrefix("recalls: "),
            auditAgeLabel.removePrefix("auditoria: "),
            helperLabel,
            internalIssue?.label ?: "sin fallos internos"
        ).joinToString(", ")

        return OrganismHealthUiState(
            level = level,
            overallLabel = overallLabel,
            healthSentence = healthSentence,
            helperLabel = "${activeHelper.displayName}: $helperLabel",
            helperModelLabel = helperModelLabel,
            helperNeedsAttention = false,
            memoryLabel = memoryLabel,
            memoryNeedsAttention = memoryNeedsAttention,
            eventCount = eventCount,
            eventCountLabel = formatEventCount(eventCount),
            auditAgeLabel = auditAgeLabel,
            auditSourceLabel = auditSourceLabel,
            auditNeedsAttention = auditNeedsAttention,
            recallPendingCount = safeRecallPendingCount,
            recallOverdueCount = safeRecallOverdueCount,
            recallLabel = recallLabel,
            recallNeedsAttention = recallNeedsAttention,
            restCycleLabel = restCycleLabel,
            restCycleNeedsAttention = restCycleScheduleStatus.needsAttention,
            internalIssueLabel = internalIssue?.let { issue ->
                "${issue.label} x${issue.failureCount}"
            } ?: "fallos internos: ninguno",
            internalIssueDetailLabel = internalIssue?.let { issue ->
                "detalle: ${issue.message}; hace ${ageLabel(nowMillis - issue.occurredAtMillis)}"
            }.orEmpty(),
            internalIssueNeedsAttention = internalIssue != null,
            recommendedActionLabel = recommendedActionLabel,
            checkedAtMillis = nowMillis
        )
    }

    private fun ageLabel(ageMillis: Long): String {
        val safeAgeMillis = ageMillis.coerceAtLeast(0L)
        val minutes = safeAgeMillis / 60_000L
        val hours = safeAgeMillis / 3_600_000L
        val days = safeAgeMillis / 86_400_000L
        return when {
            minutes < 1L -> "ahora"
            minutes < 60L -> "${minutes}m"
            hours < 24L -> "${hours}h"
            else -> "${days}d"
        }
    }

    private fun formatEventCount(eventCount: Int): String {
        return "${eventCount.formatSpanishThousands()} eventos"
    }

    private fun Int.formatSpanishThousands(): String {
        return toString().reversed().chunked(3).joinToString(".").reversed()
    }

    private const val AUDIT_STALE_AFTER_MILLIS = 24L * 60L * 60L * 1000L
}

enum class HealthStatusLevel(val label: String) {
    Stable("nivel: estable"),
    Watch("nivel: observar"),
    Attention("nivel: atencion"),
    Critical("nivel: critico")
}

data class RestCycleAuditSignal(
    val memoryChainVerified: Boolean?,
    val capsuleChainVerified: Boolean?,
    val checkedAtMillis: Long?
)
