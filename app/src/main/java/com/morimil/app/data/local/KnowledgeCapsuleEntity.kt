package com.morimil.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "knowledge_capsules")
data class KnowledgeCapsuleEntity(
    @PrimaryKey
    val capsuleId: String,
    val genesisCoreId: String,
    val capsuleVersion: Int,
    val capsuleCategory: String,
    val capsuleType: String,
    val status: String,
    val title: String,
    val source: String,
    val privacyVisibility: String,
    val summary: String,
    val claimsJson: String,
    val tags: String,
    val evidenceJson: String,
    val confidence: Int,
    val sourceEventHash: String?,
    val previousCapsuleHash: String?,
    val capsuleHash: String,
    val hashAlgorithm: String,
    val canonicalization: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)