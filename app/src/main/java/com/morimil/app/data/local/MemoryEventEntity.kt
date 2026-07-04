package com.morimil.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memory_events",
    indices = [
        Index(value = ["eventHash"], name = "index_memory_events_eventHash"),
        Index(value = ["createdAtMillis"], name = "index_memory_events_createdAtMillis"),
        Index(value = ["memoryKind"], name = "index_memory_events_memoryKind"),
        Index(value = ["importance"], name = "index_memory_events_importance")
    ]
)
data class MemoryEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val genesisCoreId: String,
    @ColumnInfo(defaultValue = "'sha256:legacy-unverified'")
    val genesisCoreHash: String,
    val previousEventHash: String?,
    @ColumnInfo(defaultValue = "'sha256:legacy-unverified'")
    val eventHash: String,
    @ColumnInfo(defaultValue = "'sha256'")
    val hashAlgorithm: String,
    @ColumnInfo(defaultValue = "'morimil.memory_event_hash.v1'")
    val canonicalization: String,
    val signatureAlgorithm: String?,
    val eventSignature: String?,
    val eventType: String,
    val actor: String,
    @ColumnInfo(defaultValue = "'system'")
    val source: String,
    @ColumnInfo(defaultValue = "'local_runtime'")
    val contextTag: String,
    @ColumnInfo(defaultValue = "'private_local'")
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
