package com.morimil.app.data.genesis.ultra

import com.morimil.app.data.local.GenesisUltraBirthCommitEntity
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

data class GenesisUltraAtomicBirthRecoveryRequest(
    val guardianKeyEpochRegistry: GenesisUltraTrustedGuardianKeyEpochRegistry,
    val bodyRawPublicKey: ByteArray
)

/**
 * The one normative memory root committed by the birth transaction. Its source
 * bytes are preserved exactly; callers receive defensive copies only.
 */
class GenesisUltraLivingMemoryRoot internal constructor(
    event: GenesisUltraFirstMemoryEvent,
    sourceBytes: ByteArray
) {
    val event: GenesisUltraFirstMemoryEvent = event.copy(signature = event.signature.copy())
    private val source = sourceBytes.copyOf()

    fun copySourceBytes(): ByteArray = source.copyOf()
}

class GenesisUltraRecoveredAtomicBirth internal constructor(
    verifiedBirth: GenesisUltraVerifiedAtomicBirth,
    val livingMemoryRoot: GenesisUltraLivingMemoryRoot
) {
    private val verified = verifiedBirth

    internal fun verifiedBirth(): GenesisUltraVerifiedAtomicBirth = verified
}

/** Rebuilds and re-verifies a birth from exact durable evidence after restart. */
object GenesisUltraAtomicBirthRecoveryVerifier {
    fun recover(
        artifacts: List<GenesisUltraBirthArtifact>,
        journal: List<GenesisUltraBirthJournalEvidence>,
        request: GenesisUltraAtomicBirthRecoveryRequest
    ): GenesisUltraRecoveredAtomicBirth {
        val artifactSnapshot = artifacts.map { artifact ->
            artifact.copy(payload = artifact.payload.copyOf())
        }
        val journalSnapshot = journal.map { evidence ->
            evidence.copy(
                entry = evidence.entry.copy(signature = evidence.entry.signature.copy()),
                sourceBytes = evidence.sourceBytes.copyOf()
            )
        }
        val manifestText = artifactSnapshot.exactText("seed_manifest")
        val signatureText = artifactSnapshot.exactText("seed_signature")
        val manifest = GenesisUltraContractParser.parseSeedManifest(manifestText)
        val releaseFiles = manifest.files.associate { record ->
            val evidence = artifactSnapshot.singleOrNull { artifact ->
                artifact.relativePath == record.path
            } ?: throw IllegalArgumentException("recovery_seed_artifact_missing:${record.path}")
            record.path to evidence.payload.copyOf()
        }
        val release = GenesisUltraReleaseVerifier(
            request.guardianKeyEpochRegistry.signatureVerifier()
        ).verify(
            GenesisUltraReleaseBundle(
                manifestJson = manifestText,
                signatureJson = signatureText,
                files = releaseFiles
            )
        )
        val identity = GenesisUltraContractParser.parseInstanceIdentity(
            artifactSnapshot.exactText("instance_identity")
        )
        val verifiedBirth = GenesisUltraAtomicBirthEvidenceVerifier.verify(
            GenesisUltraAtomicBirthEvidenceRequest(
                release = release,
                guardianKeyEpochRegistry = request.guardianKeyEpochRegistry,
                bodyRawPublicKey = request.bodyRawPublicKey.copyOf(),
                artifacts = artifactSnapshot,
                journal = journalSnapshot,
                evaluatedAt = identity.bornAt
            )
        )
        return GenesisUltraRecoveredAtomicBirth(
            verifiedBirth = verifiedBirth,
            livingMemoryRoot = livingMemoryRoot(verifiedBirth.copyPersistenceBundle())
        )
    }

    internal fun livingMemoryRoot(
        bundle: GenesisUltraAtomicBirthPersistenceBundle
    ): GenesisUltraLivingMemoryRoot {
        val source = bundle.artifacts.exactArtifact("first_memory_event").payload
        val event = GenesisUltraAtomicBirthDocumentParser.parseFirstMemoryEvent(decodeUtf8Strict(source))
        requireLivingMemoryRoot(
            event = event,
            manifest = bundle.seedManifest,
            identity = bundle.instanceIdentity,
            state = bundle.birthState,
            receipt = bundle.birthReceipt
        )
        return GenesisUltraLivingMemoryRoot(event, source)
    }

    internal fun structuralLivingMemoryRoot(
        commit: GenesisUltraBirthCommitEntity,
        artifacts: List<GenesisUltraBirthArtifact>
    ): GenesisUltraLivingMemoryRoot {
        require(artifacts.map { artifact -> artifact.relativePath }.distinct().size == artifacts.size) {
            "persisted_birth_artifact_path_duplicate"
        }
        GenesisUltraAtomicBirthPersistenceValidator.mandatoryArtifactKinds.forEach { kind ->
            require(artifacts.count { artifact -> artifact.artifactKind == kind } == 1) {
                "persisted_birth_artifact_kind_invalid:$kind"
            }
        }
        val manifest = GenesisUltraContractParser.parseSeedManifest(artifacts.exactText("seed_manifest"))
        manifest.files.forEach { record ->
            val artifact = artifacts.singleOrNull { candidate -> candidate.relativePath == record.path }
                ?: throw IllegalArgumentException("persisted_seed_artifact_missing:${record.path}")
            require(GenesisUltraHashProfile.sha256(artifact.payload) == record.digest) {
                "persisted_seed_artifact_digest_mismatch:${record.path}"
            }
        }
        val identity = GenesisUltraContractParser.parseInstanceIdentity(artifacts.exactText("instance_identity"))
        val state = GenesisUltraAtomicBirthDocumentParser.parseBirthState(artifacts.exactText("birth_state"))
        val receipt = GenesisUltraAtomicBirthDocumentParser.parseBirthReceipt(artifacts.exactText("birth_receipt"))
        val source = artifacts.exactArtifact("first_memory_event").payload
        val event = GenesisUltraAtomicBirthDocumentParser.parseFirstMemoryEvent(decodeUtf8Strict(source))

        require(
            commit.seedId == manifest.seedId &&
                commit.seedRootHash == manifest.rootHash &&
                commit.instanceId == identity.instanceId &&
                commit.companionName == identity.companionName &&
                commit.identityDigest == identity.identityDigest &&
                commit.bornAt == identity.bornAt &&
                commit.birthId == state.birthId &&
                commit.freedomCharterDigest == state.freedomCharterDigest &&
                commit.initialBodyId == state.initialBodyId &&
                commit.initialBodyRegistryDigest == state.initialBodyRegistryDigest &&
                commit.initialBodyKeyEpochDigest == state.initialBodyKeyEpochDigest &&
                commit.initialBodyPossessionDigest == state.initialBodyPossessionDigest &&
                commit.recoveryStateDigest == state.recoveryStateDigest &&
                commit.birthStateDigest == state.stateDigest &&
                commit.firstMemoryEventHash == state.firstMemoryEventHash &&
                commit.receiptDigest == receipt.receiptDigest &&
                commit.journalId == receipt.journalId &&
                commit.birthStatus == receipt.birthStatus &&
                commit.activeWriterBodyId == receipt.activeWriterBodyId &&
                commit.activeWriterCount == receipt.activeWriterCount &&
                commit.guardianRole == receipt.guardianRole &&
                commit.ownershipConferred == receipt.ownershipConferred
        ) { "persisted_birth_memory_root_commit_mismatch" }
        require(
            state.instanceId == identity.instanceId &&
                state.seedId == manifest.seedId &&
                state.seedRootHash == manifest.rootHash &&
                state.identityDigest == identity.identityDigest &&
                state.bornAt == identity.bornAt &&
                receipt.birthId == state.birthId &&
                receipt.instanceId == state.instanceId &&
                receipt.birthStateDigest == state.stateDigest &&
                receipt.seedRootHash == state.seedRootHash &&
                receipt.identityDigest == state.identityDigest &&
                receipt.freedomCharterDigest == state.freedomCharterDigest &&
                receipt.initialBodyRegistryDigest == state.initialBodyRegistryDigest &&
                receipt.initialBodyKeyEpochDigest == state.initialBodyKeyEpochDigest &&
                receipt.initialBodyPossessionDigest == state.initialBodyPossessionDigest &&
                receipt.firstMemoryEventHash == state.firstMemoryEventHash &&
                receipt.recoveryStateDigest == state.recoveryStateDigest &&
                receipt.bornAt == state.bornAt &&
                receipt.activeWriterBodyId == state.initialBodyId &&
                state.activeWriterCount == 1L &&
                receipt.activeWriterCount == 1L &&
                receipt.birthStatus == "born" &&
                receipt.guardianRole == "custodian_witness" &&
                !receipt.ownershipConferred
        ) { "persisted_birth_memory_root_graph_mismatch" }
        requireLivingMemoryRoot(event, manifest, identity, state, receipt)
        return GenesisUltraLivingMemoryRoot(event, source)
    }

    internal fun requireCommitMatchesVerifiedBirth(
        commit: GenesisUltraBirthCommitEntity,
        verifiedBirth: GenesisUltraVerifiedAtomicBirth
    ) {
        val bundle = verifiedBirth.copyPersistenceBundle()
        require(
            commit.birthId == bundle.birthState.birthId &&
                commit.instanceId == bundle.instanceIdentity.instanceId &&
                commit.companionName == bundle.instanceIdentity.companionName &&
                commit.seedId == bundle.seedManifest.seedId &&
                commit.seedRootHash == bundle.seedManifest.rootHash &&
                commit.identityDigest == bundle.instanceIdentity.identityDigest &&
                commit.firstMemoryEventHash == bundle.birthState.firstMemoryEventHash &&
                commit.birthStateDigest == bundle.birthState.stateDigest &&
                commit.receiptDigest == bundle.birthReceipt.receiptDigest &&
                commit.journalId == bundle.birthReceipt.journalId &&
                commit.artifactCount == bundle.artifacts.size.toLong() &&
                commit.journalEntryCount == bundle.journal.size.toLong()
        ) { "recovered_birth_commit_mismatch" }
    }

    private fun requireLivingMemoryRoot(
        event: GenesisUltraFirstMemoryEvent,
        manifest: GenesisUltraSeedManifest,
        identity: GenesisUltraInstanceIdentity,
        state: GenesisUltraBirthState,
        receipt: GenesisUltraBirthReceipt
    ) {
        require(
            event.instanceId == identity.instanceId &&
                event.bodyId == state.initialBodyId &&
                event.sequence == 0L &&
                event.previousEventHash == "GENESIS" &&
                event.eventType == "instance.birth" &&
                event.actor == "system" &&
                event.contentDigest == identity.identityDigest &&
                event.contentType == "application/vnd.genesis.birth+json" &&
                event.contentRef == null &&
                event.observedAt == identity.bornAt &&
                event.provenanceDigest == manifest.rootHash &&
                event.provenanceRef == null &&
                event.privacy == "private_local" &&
                event.eventHash == state.firstMemoryEventHash &&
                event.eventHash == receipt.firstMemoryEventHash
        ) { "canonical_living_memory_root_invalid" }
    }

    private fun List<GenesisUltraBirthArtifact>.exactArtifact(kind: String): GenesisUltraBirthArtifact {
        return singleOrNull { artifact -> artifact.artifactKind == kind }
            ?: throw IllegalArgumentException("recovery_birth_artifact_kind_invalid:$kind")
    }

    private fun List<GenesisUltraBirthArtifact>.exactText(kind: String): String {
        return decodeUtf8Strict(exactArtifact(kind).payload)
    }

    private fun decodeUtf8Strict(bytes: ByteArray): String {
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return decoder.decode(ByteBuffer.wrap(bytes)).toString()
    }
}
