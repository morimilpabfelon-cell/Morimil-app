package com.morimil.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "improvement_decision_history",
    indices = [
        Index(value = ["proposalId"]),
        Index(value = ["decidedAtMillis"])
    ]
)
data class ImprovementDecisionHistoryEntity(
    @PrimaryKey val historyId: String,
    val proposalId: String,
    val proposalTitle: String,
    val decision: String,
    val decidedAtMillis: Long,
    val source: String,
    val schemaVersion: Int = 1
)
