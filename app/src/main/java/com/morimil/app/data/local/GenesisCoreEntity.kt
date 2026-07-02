package com.morimil.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The copied Genesis Core for this local instance. This is the birth record:
 * copied once, then treated as immutable. Living memory grows beside it.
 */
@Entity(tableName = "genesis_core")
data class GenesisCoreEntity(
    @PrimaryKey
    val coreId: String = "primary_genesis",
    val instanceId: String,
    val aliasAtBirth: String,
    val copiedAtMillis: Long,
    val sourceOrigin: String,
    val schemaVersion: String,
    val agentId: String,
    val role: String,
    val owner: String,
    val riskTier: String,
    val doctrineRef: String,
    val policyRef: String,
    val allowedActionsJson: String,
    val disallowedActionsJson: String,
    val doctrineText: String?,
    val policyText: String?,
    val contentSha256: String
)
