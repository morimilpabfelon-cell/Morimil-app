package com.morimil.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Operational chat transcript only. A turn is not Genesis memory, cannot
 * extend either memory-event chain, and is never used as identity evidence.
 */
@Entity(tableName = "reasoning_turns")
data class ReasoningTurnEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val author: String,
    val body: String,
    val createdAtMillis: Long
)
