package com.morimil.app.improvements

data class ImprovementDecisionHistoryEntry(
    val historyId: String,
    val proposalId: String,
    val proposalTitle: String,
    val decision: ImprovementDecision,
    val decidedAtMillis: Long,
    val source: String = "unknown",
    val schemaVersion: Int = 0
)