package com.morimil.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "native_web_search_audit",
    indices = [
        Index(value = ["createdAtMillis"]),
        Index(value = ["queryOriginal"])
    ]
)
data class NativeWebSearchAuditEntity(
    @PrimaryKey
    val auditId: String,
    val queryOriginal: String,
    val querySearch: String,
    val intent: String,
    val strategy: String,
    val primaryUrl: String?,
    val primaryHost: String?,
    val primaryScore: Int?,
    val primaryReason: String?,
    val secondaryUrl: String?,
    val secondaryHost: String?,
    val secondaryScore: Int?,
    val secondaryReason: String?,
    val verifierStatus: String,
    val verifierConfidence: String,
    val verifierReason: String,
    val retryCount: Int,
    val fallbackCount: Int,
    val navigationEventCount: Int,
    val result: String,
    val createdAtMillis: Long
)
