package com.morimil.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "decision_log")
data class DecisionLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val status: String,
    val createdAtMillis: Long
)
