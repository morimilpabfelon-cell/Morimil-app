package com.morimil.app.data.local

import androidx.room.Entity
import androidx.room.Index

/**
 * Durable commit marker for the one Genesis Ultra birth admitted by this body.
 *
 * [slotId] is intentionally the primary key instead of [birthId]. The store only
 * accepts [PRIMARY_SLOT], so SQLite itself rejects a second birth even when the
 * second candidate uses a different birth or Instance identifier.
 */
@Entity(
    tableName = "genesis_ultra_birth_commit",
    indices = [
        Index(
            value = ["birthId"],
            unique = true,
            name = "index_genesis_ultra_birth_commit_birthId"
        ),
        Index(
            value = ["instanceId"],
            unique = true,
            name = "index_genesis_ultra_birth_commit_instanceId"
        ),
        Index(
            value = ["journalId"],
            unique = true,
            name = "index_genesis_ultra_birth_commit_journalId"
        )
    ],
    primaryKeys = ["slotId"]
)
data class GenesisUltraBirthCommitEntity(
    val slotId: String,
    val schemaVersion: String,
    val birthId: String,
    val instanceId: String,
    val companionName: String,
    val seedId: String,
    val seedRootHash: String,
    val identityDigest: String,
    val freedomCharterDigest: String,
    val initialBodyId: String,
    val initialBodyRegistryDigest: String,
    val initialBodyKeyEpochDigest: String,
    val initialBodyPossessionDigest: String,
    val firstMemoryEventHash: String,
    val recoveryStateDigest: String,
    val birthStateDigest: String,
    val receiptDigest: String,
    val journalId: String,
    val bornAt: String,
    val birthStatus: String,
    val activeWriterBodyId: String,
    val activeWriterCount: Long,
    val guardianRole: String,
    val ownershipConferred: Boolean,
    val artifactCount: Long,
    val journalEntryCount: Long,
    val persistedAtMillis: Long
) {
    companion object {
        const val PRIMARY_SLOT = "genesis_ultra_primary_birth"
    }
}

/** Exact input bytes are preserved so a later audit or transfer never has to
 * reconstruct the original Seed or birth evidence from Android-only models. */
@Entity(
    tableName = "genesis_ultra_birth_artifacts",
    primaryKeys = ["slotId", "relativePath"],
    indices = [
        Index(
            value = ["slotId", "artifactKind"],
            name = "index_genesis_ultra_birth_artifacts_slotId_artifactKind"
        )
    ]
)
data class GenesisUltraBirthArtifactEntity(
    val slotId: String,
    val relativePath: String,
    val artifactKind: String,
    val contentDigest: String,
    val byteCount: Long,
    val payload: ByteArray
)

/** Parsed journal fields and their exact source bytes live together. No update
 * DAO is exposed: continuity is append-only from the first committed record. */
@Entity(
    tableName = "genesis_ultra_birth_journal",
    primaryKeys = ["slotId", "sequence"],
    indices = [
        Index(
            value = ["journalId", "sequence"],
            unique = true,
            name = "index_genesis_ultra_birth_journal_journalId_sequence"
        ),
        Index(
            value = ["journalDigest"],
            unique = true,
            name = "index_genesis_ultra_birth_journal_journalDigest"
        )
    ]
)
data class GenesisUltraBirthJournalEntity(
    val slotId: String,
    val schemaVersion: String,
    val journalId: String,
    val sequence: Long,
    val previousJournalDigest: String,
    val operationKind: String,
    val operationId: String,
    val instanceId: String,
    val coordinatorBodyId: String,
    val phase: String,
    val status: String,
    val previousStateDigest: String,
    val candidateStateDigest: String?,
    val finalizationDigest: String?,
    val commitMarkerDigest: String?,
    val updatedAt: String,
    val journalDigest: String,
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
