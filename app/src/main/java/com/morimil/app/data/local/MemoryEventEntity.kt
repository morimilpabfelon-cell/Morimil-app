package com.morimil.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memory_events",
    indices = [
        Index(value = ["memoryKind"], name = "index_memory_events_memoryKind"),
        Index(value = ["importance"], name = "index_memory_events_importance")
    ]
)
data class MemoryEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val genesisCoreId: String,
    val genesisCoreHash: String,
    val previousEventHash: String?,
    val eventHash: String,
    val hashAlgorithm: String,
    val canonicalization: String,
    val signatureAlgorithm: String?,
    val eventSignature: String?,
    val eventType: String,
    val actor: String,
    val source: String,
    val contextTag: String,
    val privacyVisibility: String,
    @ColumnInfo(defaultValue = "'observation'")
    val memoryKind: String,
    @ColumnInfo(defaultValue = "'[]'")
    val tagsJson: String,
    @ColumnInfo(defaultValue = "'{}'")
    val evidenceJson: String,
    @ColumnInfo(defaultValue = "70")
    val confidence: Int,
    @ColumnInfo(defaultValue = "0")
    val userConfirmed: Boolean,
    val body: String,
    val importance: Int,
    val createdAtMillis: Long
)