package com.morimil.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recall_schedules",
    indices = [
        Index(value = ["targetEventHash"], unique = true),
        Index(value = ["status"]),
        Index(value = ["dueAtMillis"])
    ]
)
data class RecallScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    val recallId: Long = 0,
    val genesisCoreId: String,
    val targetEventHash: String,
    val targetMemoryKind: String,
    val prompt: String,
    val reason: String,
    val priority: Int,
    val intervalDays: Int,
    val dueAtMillis: Long,
    val status: String,
    val lastAction: String,
    val source: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val lastReviewedAtMillis: Long?
)