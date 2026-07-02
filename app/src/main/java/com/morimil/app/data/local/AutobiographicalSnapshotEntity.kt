package com.morimil.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "autobiographical_snapshots")
data class AutobiographicalSnapshotEntity(
    @PrimaryKey
    val snapshotId: String,
    val genesisCoreId: String,
    val alias: String,
    val selfSummary: String,
    val stableTraits: String,
    val activeGoals: String,
    val importantConstraints: String,
    val sourceEventHash: String?,
    val updatedAtMillis: Long
)
