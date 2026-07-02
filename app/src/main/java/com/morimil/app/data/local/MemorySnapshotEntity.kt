package com.morimil.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memory_snapshots")
data class MemorySnapshotEntity(
    @PrimaryKey
    val snapshotId: String = "living_memory_current",
    val genesisCoreId: String,
    val summary: String,
    val eventCount: Int,
    val messageCount: Int,
    val updatedAtMillis: Long
)
