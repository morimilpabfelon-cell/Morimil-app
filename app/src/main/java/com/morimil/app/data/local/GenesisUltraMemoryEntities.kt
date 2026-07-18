package com.morimil.app.data.local

import androidx.room.Entity
import androidx.room.Index

/**
 * One post-birth event in the neutral Genesis Ultra memory stream.
 *
 * Sequence zero remains the exact `first_memory_event` artifact committed by
 * birth. This table therefore starts at sequence one and never duplicates or
 * rewrites the living root. No update or delete DAO is exposed.
 */
@Entity(
    tableName = "genesis_ultra_memory_events",
    primaryKeys = ["instanceId", "sequence"],
    indices = [
        Index(
            value = ["eventId"],
            unique = true,
            name = "index_genesis_ultra_memory_events_eventId"
        ),
        Index(
            value = ["eventHash"],
            unique = true,
            name = "index_genesis_ultra_memory_events_eventHash"
        )
    ]
)
data class GenesisUltraMemoryEventEntity(
    val instanceId: String,
    val sequence: Long,
    val schemaVersion: String,
    val hashProfile: String,
    val eventId: String,
    val bodyId: String,
    val previousEventHash: String,
    val eventType: String,
    val actor: String,
    val contentDigest: String,
    val contentType: String,
    val contentRef: String?,
    val observedAt: String,
    val provenanceDigest: String,
    val provenanceRef: String?,
    val privacy: String,
    val eventHash: String,
    val signatureSchemaVersion: String,
    val signatureProfile: String,
    val signerType: String,
    val signerId: String,
    val keyEpochId: String,
    val signedDomain: String,
    val signedDigest: String,
    val signatureValue: String,
    val signatureCreatedAt: String,
    val publicKeyRef: String,
    val sourceDigest: String,
    val sourceBytes: ByteArray
)
