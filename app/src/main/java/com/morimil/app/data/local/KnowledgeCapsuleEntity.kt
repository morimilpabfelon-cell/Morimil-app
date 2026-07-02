package com.morimil.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "knowledge_capsules")
data class KnowledgeCapsuleEntity(
    @PrimaryKey
    val capsuleId: String,
    val genesisCoreId: String,
    val title: String,
    val summary: String,
    val tags: String,
    val confidence: Int,
    val sourceEventHash: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)
