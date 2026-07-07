package com.morimil.app.ui

import com.morimil.app.ai.ReasoningMotorSlot
import com.morimil.app.runtime.RestCycleScheduleStatus

data class OrganismHealthUiState(
    val level: HealthStatusLevel = HealthStatusLevel.Attention,
    val overallLabel: String = "salud pendiente",
    val healthSentence: String = "Morimil aun no tiene reporte de salud local.",
    val motorLabel: String = "motor pendiente",
    val modelLabel: String = "modelo pendiente",
    val motorNeedsAttention: Boolean = true,
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
    val checkedAtMillis: Long? = null,
    val organHealthSnapshot: OrganHealthSnapshot = OrganHealthSnapshot.empty()
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
        activeSlot: ReasoningMotorSlot,
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
        val modelLabel = activeSlot.config.model.ifBlank { "modelo pendiente" }
        val motorIsConfigured = activeSlot.config.baseUrl.isNotBlank() && activeSlot.config.model.isNotBlank()
        val motorIsLocal = !activeSlot.config.requiresRuntimeKey && motorIsConfigured
        val motorLabel = when {
            !motorIsConfigured -> "motor pendiente"
            motorIsLocal -> "motor local activo"
            else -> "motor API activo"
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
            !motorIsConfigured -> "salud: configurar motor"
            else -> "salud: estable"
        }
        val level = when {
            hasQuarantine || audit.errorMessage != null ||
                effectiveMemoryVerified == false || effectiveCapsulesVerified == false -> HealthStatusLevel.Critical
            internalIssue != null -> HealthStatusLevel.Attention
            auditNeedsAttention || recallNeedsAttention || restCycleScheduleStatus.needsAttention -> HealthStatusLevel.Attention
            !motorIsConfigured -> HealthStatusLevel.Watch
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
            !motorIsConfigured -> "accion: configurar motor"
            else -> "accion: continuar"
        }
        val healthSentence = listOf(
            memoryLabel.removePrefix("memoria: "),
            formatEventCount(eventCount),
            recallLabel.removePrefix("recalls: "),
            auditAgeLabel.removePrefix("auditoria: "),
            motorLabel,
            internalIssue?.label ?: "sin fallos internos"
        ).joinToString(", ")
        val organHealthSnapshot = OrganHealthSnapshotBuilder.build(
            motorConfigured = motorIsConfigured,
            hasQuarantine = hasQuarantine,
            auditErrorMessage = audit.errorMessage,
            memoryChainVerified = effectiveMemoryVerified,
            capsuleChainVerified = effectiveCapsulesVerified,
            auditNeedsAttention = auditNeedsAttention,
            restCycleScheduleStatus = restCycleScheduleStatus,
            recallOverdueCount = safeRecallOverdueCount,
            internalIssue = internalIssue,
            nowMillis = nowMillis
        )

        return OrganismHealthUiState(
            level = level,
            overallLabel = overallLabel,
            healthSentence = healthSentence,
            motorLabel = "${activeSlot.displayName}: $motorLabel",
            modelLabel = modelLabel,
            motorNeedsAttention = !motorIsConfigured,
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
            checkedAtMillis = nowMillis,
            organHealthSnapshot = organHealthSnapshot
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

enum class OrganHealthLevel(val label: String) {
    Green("verde"),
    Yellow("amarillo"),
    Red("rojo")
}

data class OrganHealthStatus(
    val organId: String,
    val displayName: String,
    val level: OrganHealthLevel,
    val reason: String,
    val nextControl: String
) {
    val isCritical: Boolean
        get() = level == OrganHealthLevel.Red
}

data class OrganHealthSnapshot(
    val organs: List<OrganHealthStatus>,
    val checkedAtMillis: Long
) {
    val greenCount: Int
        get() = organs.count { organ -> organ.level == OrganHealthLevel.Green }
    val yellowCount: Int
        get() = organs.count { organ -> organ.level == OrganHealthLevel.Yellow }
    val redCount: Int
        get() = organs.count { organ -> organ.level == OrganHealthLevel.Red }
    val overallLevel: OrganHealthLevel
        get() = when {
            redCount > 0 -> OrganHealthLevel.Red
            yellowCount > 0 -> OrganHealthLevel.Yellow
            else -> OrganHealthLevel.Green
        }
    val overallLabel: String
        get() = "organos: $greenCount verdes / $yellowCount amarillos / $redCount rojos"

    companion object {
        fun empty(): OrganHealthSnapshot {
            return OrganHealthSnapshot(
                organs = emptyList(),
                checkedAtMillis = 0L
            )
        }
    }
}

object OrganHealthSnapshotBuilder {
    fun build(
        motorConfigured: Boolean,
        hasQuarantine: Boolean,
        auditErrorMessage: String?,
        memoryChainVerified: Boolean?,
        capsuleChainVerified: Boolean?,
        auditNeedsAttention: Boolean,
        restCycleScheduleStatus: RestCycleScheduleStatus,
        recallOverdueCount: Int,
        internalIssue: InternalRuntimeIssueUiState?,
        nowMillis: Long
    ): OrganHealthSnapshot {
        val signingIssue = internalIssue?.component.orEmpty().contains("sign", ignoreCase = true) ||
            internalIssue?.component.orEmpty().contains("keystore", ignoreCase = true)
        return OrganHealthSnapshot(
            checkedAtMillis = nowMillis,
            organs = listOf(
                healthy(
                    organId = "android_root_ui",
                    displayName = "Android root UI",
                    reason = "Main UI runtime reached health builder.",
                    nextControl = "Keep navigation changes mapped before merge."
                ),
                memoryStatus(
                    organId = "living_memory_chain",
                    displayName = "Living Memory Chain",
                    hasQuarantine = hasQuarantine,
                    auditErrorMessage = auditErrorMessage,
                    chainVerified = memoryChainVerified,
                    auditNeedsAttention = auditNeedsAttention
                ),
                capsuleStatus(
                    organId = "knowledge_capsules",
                    displayName = "Knowledge Capsules",
                    auditErrorMessage = auditErrorMessage,
                    capsuleChainVerified = capsuleChainVerified,
                    auditNeedsAttention = auditNeedsAttention
                ),
                status(
                    organId = "memory_signer",
                    displayName = "Memory Signer / Epoch",
                    level = if (signingIssue) OrganHealthLevel.Yellow else OrganHealthLevel.Green,
                    reason = if (signingIssue) "Signing or keystore issue observed in runtime." else "Signer is injected and no signing issue is active.",
                    nextControl = "Audit key fallback and epoch trust boundary before stronger threat model."
                ),
                status(
                    organId = "reasoning_kernel",
                    displayName = "Reasoning Kernel",
                    level = if (motorConfigured) OrganHealthLevel.Green else OrganHealthLevel.Yellow,
                    reason = if (motorConfigured) "Runtime has configured endpoint and model." else "Runtime model or endpoint is pending.",
                    nextControl = "Keep fallback honest and remote escalation approval-gated."
                ),
                restCycleStatus(restCycleScheduleStatus),
                status(
                    organId = "recall_schedule",
                    displayName = "Recall Schedule",
                    level = if (recallOverdueCount > 0) OrganHealthLevel.Yellow else OrganHealthLevel.Green,
                    reason = if (recallOverdueCount > 0) "$recallOverdueCount recall(s) overdue." else "No overdue recall signal.",
                    nextControl = "Expose overdue badge and review actions in UI."
                ),
                healthy(
                    organId = "cognitive_migration",
                    displayName = "Cognitive Migration",
                    reason = "Plan, approve, execute and rollback path exists through migration records.",
                    nextControl = "Add pending-count and cooldown enforcement tests."
                ),
                healthy(
                    organId = "improvements_governance",
                    displayName = "Improvements Governance",
                    reason = "Approval records decisions but does not execute code automatically.",
                    nextControl = "Link approved plans to exact validation checks."
                ),
                healthy(
                    organId = "secret_vault",
                    displayName = "Secret Vault",
                    reason = "Reasoning access is read from vault path, not memory context.",
                    nextControl = "Audit traces and logs for secret leakage."
                ),
                review(
                    organId = "project_vault",
                    displayName = "Project Vault",
                    reason = "Registered and routed, but not fully audited in organ map.",
                    nextControl = "Audit create, complete and archive transitions."
                ),
                review(
                    organId = "agent_orchestration",
                    displayName = "Agent Orchestration",
                    reason = "Seeded and routed, but external action boundary needs deeper proof.",
                    nextControl = "Verify no irreversible action without owner approval."
                ),
                critical(
                    organId = "encrypted_export_restore",
                    displayName = "Encrypted Signed Export / Restore",
                    reason = "Continuity can fail if the phone or local DB is lost.",
                    nextControl = "Implement manifest, hashes, signature verification, dry-run restore and explicit restore approval."
                ),
                critical(
                    organId = "pc_executor_boundary",
                    displayName = "PC Executor Boundary",
                    reason = "PC executor automation is not implemented and would have high blast radius if rushed.",
                    nextControl = "Design read-only-by-default executor with explicit owner approval."
                ),
                review(
                    organId = "on_device_runtime",
                    displayName = "On-device Runtime",
                    reason = "Strategic target, but should wait until reincarnation/export exists.",
                    nextControl = "Do not start before continuity protection."
                )
            )
        )
    }

    private fun memoryStatus(
        organId: String,
        displayName: String,
        hasQuarantine: Boolean,
        auditErrorMessage: String?,
        chainVerified: Boolean?,
        auditNeedsAttention: Boolean
    ): OrganHealthStatus {
        return when {
            hasQuarantine -> critical(organId, displayName, "Memory quarantine is present.", "Review quarantine before new core writes.")
            auditErrorMessage != null -> critical(organId, displayName, "Audit error: ${auditErrorMessage.take(120)}", "Repeat audit and isolate broken tail if needed.")
            chainVerified == false -> critical(organId, displayName, "Memory chain verification failed.", "Block core changes and investigate chain.")
            chainVerified == null || auditNeedsAttention -> review(organId, displayName, "Memory audit is missing or stale.", "Run local memory audit.")
            else -> healthy(organId, displayName, "Memory chain verified.", "Keep append-only writes.")
        }
    }

    private fun capsuleStatus(
        organId: String,
        displayName: String,
        auditErrorMessage: String?,
        capsuleChainVerified: Boolean?,
        auditNeedsAttention: Boolean
    ): OrganHealthStatus {
        return when {
            auditErrorMessage != null -> critical(organId, displayName, "Audit error: ${auditErrorMessage.take(120)}", "Repeat capsule audit.")
            capsuleChainVerified == false -> critical(organId, displayName, "Capsule chain verification failed.", "Refuse capsule writes until repaired.")
            capsuleChainVerified == null || auditNeedsAttention -> review(organId, displayName, "Capsule audit is missing or stale.", "Run capsule chain audit.")
            else -> healthy(organId, displayName, "Capsule chain verified.", "Keep explicit capsule intake only.")
        }
    }

    private fun restCycleStatus(restCycleScheduleStatus: RestCycleScheduleStatus): OrganHealthStatus {
        return when {
            restCycleScheduleStatus.errorMessage != null -> critical(
                organId = "rest_cycle_runtime",
                displayName = "Rest Cycle Runtime",
                reason = "Scheduler error: ${restCycleScheduleStatus.errorMessage.take(120)}",
                nextControl = "Refresh or re-enable rest cycle scheduler."
            )
            restCycleScheduleStatus.needsAttention -> review(
                organId = "rest_cycle_runtime",
                displayName = "Rest Cycle Runtime",
                reason = "Rest cycle scheduler state: ${restCycleScheduleStatus.stateLabel}.",
                nextControl = "Confirm WorkManager state on real device."
            )
            else -> healthy(
                organId = "rest_cycle_runtime",
                displayName = "Rest Cycle Runtime",
                reason = "Rest cycle scheduler is active.",
                nextControl = "Keep consolidation approval-gated when policy requires it."
            )
        }
    }

    private fun healthy(
        organId: String,
        displayName: String,
        reason: String,
        nextControl: String
    ): OrganHealthStatus = status(organId, displayName, OrganHealthLevel.Green, reason, nextControl)

    private fun review(
        organId: String,
        displayName: String,
        reason: String,
        nextControl: String
    ): OrganHealthStatus = status(organId, displayName, OrganHealthLevel.Yellow, reason, nextControl)

    private fun critical(
        organId: String,
        displayName: String,
        reason: String,
        nextControl: String
    ): OrganHealthStatus = status(organId, displayName, OrganHealthLevel.Red, reason, nextControl)

    private fun status(
        organId: String,
        displayName: String,
        level: OrganHealthLevel,
        reason: String,
        nextControl: String
    ): OrganHealthStatus {
        return OrganHealthStatus(
            organId = organId,
            displayName = displayName,
            level = level,
            reason = reason,
            nextControl = nextControl
        )
    }
}
