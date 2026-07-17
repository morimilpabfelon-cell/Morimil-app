package com.morimil.app.data.genesis.ultra

import androidx.room.withTransaction
import com.morimil.app.data.local.GenesisUltraBirthArtifactEntity
import com.morimil.app.data.local.GenesisUltraBirthCommitEntity
import com.morimil.app.data.local.GenesisUltraBirthJournalEntity
import com.morimil.app.data.local.MorimilDatabase
import com.morimil.app.data.repository.MemoryAppendGate
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

data class GenesisUltraBirthArtifact(
    val relativePath: String,
    val artifactKind: String,
    val payload: ByteArray
)

data class GenesisUltraBirthJournalEvidence(
    val entry: GenesisUltraBirthJournalEntry,
    val sourceBytes: ByteArray
)

/**
 * Persistence boundary only. Construction of this value does not prove the
 * Ed25519 signatures; callers must pass it through the full cryptographic birth
 * validator before this store is ever connected to UI or onboarding.
 */
data class GenesisUltraAtomicBirthPersistenceBundle(
    val seedManifest: GenesisUltraSeedManifest,
    val instanceIdentity: GenesisUltraInstanceIdentity,
    val birthState: GenesisUltraBirthState,
    val birthReceipt: GenesisUltraBirthReceipt,
    val artifacts: List<GenesisUltraBirthArtifact>,
    val journal: List<GenesisUltraBirthJournalEvidence>
)

enum class GenesisUltraPersistedBirthState {
    ABSENT,
    COMMITTED,
    INCONSISTENT
}

object GenesisUltraAtomicBirthPersistenceValidator {
    val mandatoryArtifactKinds: Set<String> = setOf(
        "seed_manifest",
        "instance_identity",
        "freedom_charter",
        "initial_body_record",
        "initial_body_registry",
        "initial_body_key_epoch",
        "initial_body_possession",
        "first_memory_event",
        "recovery_policy",
        "birth_recovery_state",
        "birth_state",
        "birth_receipt"
    )

    fun validate(bundle: GenesisUltraAtomicBirthPersistenceBundle): List<String> {
        val issues = linkedSetOf<String>()
        val manifest = bundle.seedManifest
        val identity = bundle.instanceIdentity
        val state = bundle.birthState
        val receipt = bundle.birthReceipt
        val artifacts = bundle.artifacts
        val entries = bundle.journal.map { evidence -> evidence.entry }

        if (GenesisUltraHashProfile.seedRoot(manifest) != manifest.rootHash) {
            issues += "seed_root_digest_mismatch"
        }
        if (GenesisUltraHashProfile.instanceIdentityDigest(identity) != identity.identityDigest) {
            issues += "identity_digest_mismatch"
        }
        if (
            identity.seedId != manifest.seedId ||
            identity.seedRootHash != manifest.rootHash
        ) {
            issues += "identity_seed_link_mismatch"
        }

        val paths = artifacts.map { artifact -> artifact.relativePath }
        if (paths.distinct().size != paths.size) issues += "birth_artifact_path_duplicate"
        artifacts.forEach { artifact ->
            try {
                GenesisUltraHashProfile.requireSafeRelativePath(artifact.relativePath)
                GenesisUltraHashProfile.requireNfc(artifact.artifactKind)
            } catch (_: IllegalArgumentException) {
                issues += "birth_artifact_path_or_kind_invalid"
            }
            if (artifact.payload.isEmpty()) issues += "birth_artifact_empty"
        }

        mandatoryArtifactKinds.forEach { kind ->
            if (artifacts.count { artifact -> artifact.artifactKind == kind } != 1) {
                issues += "birth_artifact_kind_invalid:$kind"
            }
        }

        val artifactsByPath = artifacts.associateBy { artifact -> artifact.relativePath }
        manifest.files.forEach { record ->
            val artifact = artifactsByPath[record.path]
            if (artifact == null) {
                issues += "seed_artifact_missing:${record.path}"
            } else if (GenesisUltraHashProfile.sha256(artifact.payload) != record.digest) {
                issues += "seed_artifact_digest_mismatch:${record.path}"
            }
        }

        val manifestArtifact = artifacts.singleOrNull { artifact -> artifact.artifactKind == "seed_manifest" }
        if (manifestArtifact != null) {
            val parsed = runCatching {
                GenesisUltraContractParser.parseSeedManifest(decodeUtf8Strict(manifestArtifact.payload))
            }.getOrNull()
            if (parsed != manifest) issues += "stored_seed_manifest_mismatch"
        }

        val identityArtifact = artifacts.singleOrNull { artifact -> artifact.artifactKind == "instance_identity" }
        if (identityArtifact != null) {
            val parsed = runCatching {
                GenesisUltraContractParser.parseInstanceIdentity(decodeUtf8Strict(identityArtifact.payload))
            }.getOrNull()
            if (parsed != identity) issues += "stored_instance_identity_mismatch"
        }

        if (GenesisUltraAtomicBirthHashProfile.birthStateDigest(state) != state.stateDigest) {
            issues += "birth_state_digest_mismatch"
        }
        if (
            state.instanceId != identity.instanceId ||
            state.seedId != manifest.seedId ||
            state.seedRootHash != manifest.rootHash ||
            state.identityDigest != identity.identityDigest ||
            state.bornAt != identity.bornAt
        ) {
            issues += "birth_state_link_mismatch"
        }
        if (state.activeWriterCount != 1L) issues += "birth_state_active_writer_count_invalid"

        if (GenesisUltraAtomicBirthHashProfile.birthReceiptDigest(receipt) != receipt.receiptDigest) {
            issues += "receipt_digest_mismatch"
        }
        if (
            receipt.birthId != state.birthId ||
            receipt.instanceId != state.instanceId ||
            receipt.birthStateDigest != state.stateDigest ||
            receipt.seedRootHash != state.seedRootHash ||
            receipt.identityDigest != state.identityDigest ||
            receipt.freedomCharterDigest != state.freedomCharterDigest ||
            receipt.initialBodyRegistryDigest != state.initialBodyRegistryDigest ||
            receipt.initialBodyKeyEpochDigest != state.initialBodyKeyEpochDigest ||
            receipt.initialBodyPossessionDigest != state.initialBodyPossessionDigest ||
            receipt.firstMemoryEventHash != state.firstMemoryEventHash ||
            receipt.recoveryStateDigest != state.recoveryStateDigest ||
            receipt.bornAt != state.bornAt ||
            receipt.activeWriterBodyId != state.initialBodyId
        ) {
            issues += "receipt_link_mismatch"
        }
        if (
            receipt.birthStatus != "born" ||
            receipt.activeWriterCount != 1L ||
            receipt.guardianRole != "custodian_witness" ||
            receipt.ownershipConferred
        ) {
            issues += "receipt_birth_invariant_invalid"
        }

        if (bundle.journal.any { evidence -> evidence.sourceBytes.isEmpty() }) {
            issues += "journal_source_empty"
        }
        if (
            entries.any { entry ->
                entry.operationId != state.birthId ||
                    entry.instanceId != state.instanceId ||
                    entry.coordinatorBodyId != state.initialBodyId ||
                    entry.journalId != receipt.journalId
            }
        ) {
            issues += "birth_journal_link_mismatch"
        }
        issues += GenesisUltraBirthJournalValidator.validate(
            entries = entries,
            absentStateDigest = GenesisUltraAtomicBirthHashProfile.absentStateDigest(identity.instanceId),
            birthStateDigest = state.stateDigest,
            receiptDigest = receipt.receiptDigest
        )

        return issues.toList()
    }

    private fun decodeUtf8Strict(bytes: ByteArray): String {
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return decoder.decode(ByteBuffer.wrap(bytes)).toString()
    }
}

internal enum class GenesisUltraBirthPersistenceCheckpoint {
    AFTER_ARTIFACTS,
    AFTER_JOURNAL,
    AFTER_COMMIT_MARKER
}

/**
 * Not wired to a UI path. The commit marker is inserted last in one SQLite
 * transaction; an exception or process interruption before commit restores the
 * prior ABSENT state instead of leaving a half-born Instance.
 */
internal class GenesisUltraAtomicBirthStore(
    private val database: MorimilDatabase,
    private val checkpoint: suspend (GenesisUltraBirthPersistenceCheckpoint) -> Unit = {}
) {
    private val dao = database.genesisUltraBirthDao()

    suspend fun persistPrevalidated(
        bundle: GenesisUltraAtomicBirthPersistenceBundle,
        persistedAtMillis: Long
    ): GenesisUltraBirthCommitEntity {
        require(persistedAtMillis >= 0L) { "persisted_at_invalid" }
        val issues = GenesisUltraAtomicBirthPersistenceValidator.validate(bundle)
        require(issues.isEmpty()) { "atomic_birth_persistence_invalid:$issues" }

        val slotId = GenesisUltraBirthCommitEntity.PRIMARY_SLOT
        val artifacts = bundle.artifacts.map { artifact -> artifact.toEntity(slotId) }
        val journal = bundle.journal.map { evidence -> evidence.toEntity(slotId) }
        val commit = bundle.toCommitEntity(
            slotId = slotId,
            persistedAtMillis = persistedAtMillis,
            artifactCount = artifacts.size.toLong(),
            journalEntryCount = journal.size.toLong()
        )

        return MemoryAppendGate.withAppendLock {
            database.withTransaction {
                require(dao.countBirthCommits() == 0) { "genesis_ultra_birth_already_committed" }
                require(dao.countBirthArtifacts() == 0) { "genesis_ultra_birth_artifacts_not_absent" }
                require(dao.countBirthJournalEntries() == 0) { "genesis_ultra_birth_journal_not_absent" }
                require(database.memoryDao().countLocalIdentity() == 0) {
                    "legacy_local_identity_conflicts_with_genesis_ultra_birth"
                }
                require(database.memoryDao().countGenesisCore() == 0) {
                    "legacy_genesis_core_conflicts_with_genesis_ultra_birth"
                }

                dao.insertBirthArtifacts(artifacts)
                checkpoint(GenesisUltraBirthPersistenceCheckpoint.AFTER_ARTIFACTS)
                dao.insertBirthJournal(journal)
                checkpoint(GenesisUltraBirthPersistenceCheckpoint.AFTER_JOURNAL)
                dao.insertBirthCommit(commit)
                checkpoint(GenesisUltraBirthPersistenceCheckpoint.AFTER_COMMIT_MARKER)

                require(dao.countBirthCommits() == 1) { "birth_commit_marker_missing" }
                require(dao.countBirthArtifacts().toLong() == commit.artifactCount) {
                    "birth_artifact_count_mismatch"
                }
                require(dao.countBirthJournalEntries().toLong() == commit.journalEntryCount) {
                    "birth_journal_count_mismatch"
                }
                commit
            }
        }
    }

    suspend fun readState(): GenesisUltraPersistedBirthState {
        val commitCount = dao.countBirthCommits()
        val artifactCount = dao.countBirthArtifacts()
        val journalCount = dao.countBirthJournalEntries()
        if (commitCount == 0 && artifactCount == 0 && journalCount == 0) {
            return GenesisUltraPersistedBirthState.ABSENT
        }
        if (commitCount != 1) return GenesisUltraPersistedBirthState.INCONSISTENT
        val commit = dao.loadBirthCommit(GenesisUltraBirthCommitEntity.PRIMARY_SLOT)
            ?: return GenesisUltraPersistedBirthState.INCONSISTENT
        if (
            artifactCount.toLong() != commit.artifactCount ||
            journalCount.toLong() != commit.journalEntryCount ||
            commit.journalEntryCount != GenesisUltraBirthJournalValidator.phases.size.toLong()
        ) {
            return GenesisUltraPersistedBirthState.INCONSISTENT
        }
        val artifacts = dao.loadBirthArtifacts(commit.slotId)
        if (
            artifacts.any { artifact ->
                artifact.byteCount != artifact.payload.size.toLong() ||
                    GenesisUltraHashProfile.sha256(artifact.payload) != artifact.contentDigest
            }
        ) {
            return GenesisUltraPersistedBirthState.INCONSISTENT
        }
        val journal = dao.loadBirthJournal(commit.slotId)
        val journalModels = journal.map { entry -> entry.toModel() }
        val journalIssues = GenesisUltraBirthJournalValidator.validate(
            entries = journalModels,
            absentStateDigest = GenesisUltraAtomicBirthHashProfile.absentStateDigest(commit.instanceId),
            birthStateDigest = commit.birthStateDigest,
            receiptDigest = commit.receiptDigest
        )
        if (
            journal.map { entry -> entry.phase } != GenesisUltraBirthJournalValidator.phases ||
            journal.lastOrNull()?.status != "committed" ||
            journal.lastOrNull()?.commitMarkerDigest != commit.receiptDigest ||
            journal.any { entry -> GenesisUltraHashProfile.sha256(entry.sourceBytes) != entry.sourceDigest } ||
            journal.any { entry ->
                entry.operationId != commit.birthId ||
                    entry.instanceId != commit.instanceId ||
                    entry.coordinatorBodyId != commit.initialBodyId ||
                    entry.journalId != commit.journalId
            } ||
            journalIssues.isNotEmpty()
        ) {
            return GenesisUltraPersistedBirthState.INCONSISTENT
        }
        return GenesisUltraPersistedBirthState.COMMITTED
    }
}

private fun GenesisUltraBirthJournalEntity.toModel(): GenesisUltraBirthJournalEntry {
    return GenesisUltraBirthJournalEntry(
        schemaVersion = schemaVersion,
        journalId = journalId,
        sequence = sequence,
        previousJournalDigest = previousJournalDigest,
        operationKind = operationKind,
        operationId = operationId,
        instanceId = instanceId,
        coordinatorBodyId = coordinatorBodyId,
        phase = phase,
        status = status,
        previousStateDigest = previousStateDigest,
        candidateStateDigest = candidateStateDigest,
        finalizationDigest = finalizationDigest,
        commitMarkerDigest = commitMarkerDigest,
        updatedAt = updatedAt,
        journalDigest = journalDigest,
        signature = GenesisUltraSignatureEnvelope(
            schemaVersion = signatureSchemaVersion,
            signatureProfile = signatureProfile,
            signerType = signerType,
            signerId = signerId,
            keyEpochId = keyEpochId,
            signedDomain = signedDomain,
            signedDigest = signedDigest,
            signatureValue = signatureValue,
            createdAt = signatureCreatedAt,
            publicKeyRef = publicKeyRef
        )
    )
}

private fun GenesisUltraBirthArtifact.toEntity(slotId: String): GenesisUltraBirthArtifactEntity {
    return GenesisUltraBirthArtifactEntity(
        slotId = slotId,
        relativePath = relativePath,
        artifactKind = artifactKind,
        contentDigest = GenesisUltraHashProfile.sha256(payload),
        byteCount = payload.size.toLong(),
        payload = payload.copyOf()
    )
}

private fun GenesisUltraBirthJournalEvidence.toEntity(slotId: String): GenesisUltraBirthJournalEntity {
    val envelope = entry.signature
    return GenesisUltraBirthJournalEntity(
        slotId = slotId,
        schemaVersion = entry.schemaVersion,
        journalId = entry.journalId,
        sequence = entry.sequence,
        previousJournalDigest = entry.previousJournalDigest,
        operationKind = entry.operationKind,
        operationId = entry.operationId,
        instanceId = entry.instanceId,
        coordinatorBodyId = entry.coordinatorBodyId,
        phase = entry.phase,
        status = entry.status,
        previousStateDigest = entry.previousStateDigest,
        candidateStateDigest = entry.candidateStateDigest,
        finalizationDigest = entry.finalizationDigest,
        commitMarkerDigest = entry.commitMarkerDigest,
        updatedAt = entry.updatedAt,
        journalDigest = entry.journalDigest,
        signatureSchemaVersion = envelope.schemaVersion,
        signatureProfile = envelope.signatureProfile,
        signerType = envelope.signerType,
        signerId = envelope.signerId,
        keyEpochId = envelope.keyEpochId,
        signedDomain = envelope.signedDomain,
        signedDigest = envelope.signedDigest,
        signatureValue = envelope.signatureValue,
        signatureCreatedAt = envelope.createdAt,
        publicKeyRef = envelope.publicKeyRef,
        sourceDigest = GenesisUltraHashProfile.sha256(sourceBytes),
        sourceBytes = sourceBytes.copyOf()
    )
}

private fun GenesisUltraAtomicBirthPersistenceBundle.toCommitEntity(
    slotId: String,
    persistedAtMillis: Long,
    artifactCount: Long,
    journalEntryCount: Long
): GenesisUltraBirthCommitEntity {
    return GenesisUltraBirthCommitEntity(
        slotId = slotId,
        schemaVersion = "genesis.android.birth.commit.v0.1",
        birthId = birthState.birthId,
        instanceId = instanceIdentity.instanceId,
        companionName = instanceIdentity.companionName,
        seedId = seedManifest.seedId,
        seedRootHash = seedManifest.rootHash,
        identityDigest = instanceIdentity.identityDigest,
        freedomCharterDigest = birthState.freedomCharterDigest,
        initialBodyId = birthState.initialBodyId,
        initialBodyRegistryDigest = birthState.initialBodyRegistryDigest,
        initialBodyKeyEpochDigest = birthState.initialBodyKeyEpochDigest,
        initialBodyPossessionDigest = birthState.initialBodyPossessionDigest,
        firstMemoryEventHash = birthState.firstMemoryEventHash,
        recoveryStateDigest = birthState.recoveryStateDigest,
        birthStateDigest = birthState.stateDigest,
        receiptDigest = birthReceipt.receiptDigest,
        journalId = birthReceipt.journalId,
        bornAt = instanceIdentity.bornAt,
        birthStatus = birthReceipt.birthStatus,
        activeWriterBodyId = birthReceipt.activeWriterBodyId,
        activeWriterCount = birthReceipt.activeWriterCount,
        guardianRole = birthReceipt.guardianRole,
        ownershipConferred = birthReceipt.ownershipConferred,
        artifactCount = artifactCount,
        journalEntryCount = journalEntryCount,
        persistedAtMillis = persistedAtMillis
    )
}
