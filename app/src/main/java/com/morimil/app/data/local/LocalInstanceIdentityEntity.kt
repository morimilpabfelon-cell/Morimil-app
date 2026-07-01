package com.morimil.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The phone's own named instance, born exactly once. Mirrors the
 * local_identity.mjs concept from the Morimil control-plane repo: the
 * Genesis contract (role, allowed/disallowed actions) is universal and
 * inherited verbatim at birth; only the alias and instanceId are local to
 * this device and never leave it.
 */
@Entity(tableName = "local_instance_identity")
data class LocalInstanceIdentityEntity(
    @PrimaryKey
    val instanceId: String,
    val alias: String,
    val bornAtMillis: Long,
    val genesisAgentId: String,
    val genesisRole: String,
    val genesisRiskTier: String,
    val genesisSchemaVersion: String,
    val forkOwner: String,
    val forkRepo: String,
    val forkHtmlUrl: String
)
