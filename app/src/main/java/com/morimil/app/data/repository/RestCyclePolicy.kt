package com.morimil.app.data.repository

import com.morimil.app.data.local.MemoryEventEntity

object RestCyclePolicy {
    fun requiresHumanApproval(events: List<MemoryEventEntity>): Boolean {
        val highImpactEvents = events.count { event -> event.importance >= HIGH_IMPACT_IMPORTANCE }
        val confirmedCriticalEvent = events.any { event ->
            event.userConfirmed && event.importance >= CONFIRMED_CRITICAL_IMPORTANCE
        }
        val criticalKinds = events.any { event ->
            event.importance >= CRITICAL_KIND_IMPORTANCE &&
                event.memoryKind in CRITICAL_MEMORY_KINDS
        }

        return confirmedCriticalEvent || criticalKinds || highImpactEvents >= HIGH_IMPACT_BATCH_SIZE
    }

    fun riskLevel(events: List<MemoryEventEntity>): String {
        return if (requiresHumanApproval(events)) "medium" else "low"
    }

    fun approvalReason(events: List<MemoryEventEntity>): String {
        val confirmed = events.count { event -> event.userConfirmed }
        val highImpact = events.count { event -> event.importance >= HIGH_IMPACT_IMPORTANCE }
        val criticalKinds = events.count { event -> event.memoryKind in CRITICAL_MEMORY_KINDS }
        return "confirmed=$confirmed high_impact=$highImpact critical_kinds=$criticalKinds"
    }

    private const val HIGH_IMPACT_IMPORTANCE = 80
    private const val CONFIRMED_CRITICAL_IMPORTANCE = 85
    private const val CRITICAL_KIND_IMPORTANCE = 90
    private const val HIGH_IMPACT_BATCH_SIZE = 3

    private val CRITICAL_MEMORY_KINDS = setOf(
        "decision",
        "correction",
        "identity",
        "approval",
        "rejection",
        "integrity_quarantine"
    )
}
