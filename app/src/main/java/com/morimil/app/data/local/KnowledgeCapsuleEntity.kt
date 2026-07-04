package com.morimil.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "knowledge_capsules",
    indices = [
        Index(value = ["capsuleHash"], name = "index_knowledge_capsules_capsuleHash"),
        Index(value = ["capsuleType"], name = "index_knowledge_capsules_capsuleType"),
        Index(value = ["updatedAtMillis"], name = "index_knowledge_capsules_updatedAtMillis"),
        Index(value = ["status"], name = "index_knowledge_capsules_status"),
        Index(value = ["capsuleCategory"], name = "index_knowledge_capsules_category"),
        Index(value = ["title"], name = "index_knowledge_capsules_title")
    ]
)
data class KnowledgeCapsuleEntity(
    @PrimaryKey
    val capsuleId: String,
    val genesisCoreId: String,
    @ColumnInfo(defaultValue = "1")
    val capsuleVersion: Int,
    @ColumnInfo(defaultValue = "'general_knowledge'")
    val capsuleCategory: String,
    @ColumnInfo(defaultValue = "'knowledge_capsule'")
    val capsuleType: String,
    @ColumnInfo(defaultValue = "'active'")
    val status: String,
    val title: String,
    @ColumnInfo(defaultValue = "'user_approved_notes'")
    val source: String,
    @ColumnInfo(defaultValue = "'private_local'")
    val privacyVisibility: String,
    val summary: String,
    @ColumnInfo(defaultValue = "'[]'")
    val claimsJson: String,
    val tags: String,
    @ColumnInfo(defaultValue = "'{}'")
    val evidenceJson: String,
    val confidence: Int,
    val sourceEventHash: String?,
    val previousCapsuleHash: String?,
    @ColumnInfo(defaultValue = "'sha256:legacy-unverified'")
    val capsuleHash: String,
    @ColumnInfo(defaultValue = "'sha256'")
    val hashAlgorithm: String,
    @ColumnInfo(defaultValue = "'morimil.knowledge_capsule_hash.v1'")
    val canonicalization: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)
