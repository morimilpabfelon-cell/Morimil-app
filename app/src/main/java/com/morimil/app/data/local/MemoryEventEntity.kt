package com.morimil.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memory_events")
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
    val privacyVisibility: String,
    val body: String,
    val importance: Int,
    val createdAtMillis: Long
)
