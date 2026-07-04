package com.morimil.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memory_links",
    indices = [
        Index(value = ["sourceId", "sourceType"], name = "index_memory_links_sourceId_sourceType"),
        Index(value = ["targetId", "targetType"], name = "index_memory_links_targetId_targetType"),
        Index(value = ["relation"], name = "index_memory_links_relation"),
        Index(value = ["verificationState"], name = "index_memory_links_verificationState"),
        Index(
            value = ["sourceId", "sourceType", "targetId", "targetType", "relation"],
            unique = true,
            name = "index_memory_links_sourceId_sourceType_targetId_targetType_relation"
        )
    ]
)
data class MemoryLinkEntity(
    @PrimaryKey
    val linkId: String,
    val instanceId: String,
    val genesisCoreHash: String,
    val sourceId: String,
    val sourceType: String,
    val targetId: String,
    val targetType: String,
    val relation: String,
    val strength: Double,
    val reason: String,
    val createdBy: String,
    val privacyVisibility: String,
    val cloudSyncAllowed: Boolean,
    val exportAllowed: Boolean,
    val verificationState: String,
    val createdAtMillis: Long
)
