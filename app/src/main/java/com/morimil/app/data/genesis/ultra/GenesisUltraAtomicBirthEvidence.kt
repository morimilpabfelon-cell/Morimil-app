package com.morimil.app.data.genesis.ultra

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

data class GenesisUltraAtomicBirthEvidenceRequest(
    val release: GenesisUltraVerifiedRelease,
    val guardianKeyEpochRegistry: GenesisUltraTrustedGuardianKeyEpochRegistry,
    val bodyRawPublicKey: ByteArray,
    val artifacts: List<GenesisUltraBirthArtifact>,
    val journal: List<GenesisUltraBirthJournalEvidence>,
    val evaluatedAt: String
)

/**
 * Type-state produced only after every birth document, link, hash and required
 * Ed25519 signature has been verified. It still does not activate onboarding.
 */
class GenesisUltraVerifiedAtomicBirth private constructor(
    persistenceBundle: GenesisUltraAtomicBirthPersistenceBundle
) {
    private val snapshot = persistenceBundle.snapshot()

    internal fun copyPersistenceBundle(): GenesisUltraAtomicBirthPersistenceBundle = snapshot.snapshot()

    internal companion object {
        fun verify(request: GenesisUltraAtomicBirthEvidenceRequest): GenesisUltraVerifiedAtomicBirth {
            return GenesisUltraVerifiedAtomicBirth(
                GenesisUltraAtomicBirthEvidenceVerifier.verifyBundle(request)
            )
        }
    }
}

object GenesisUltraAtomicBirthEvidenceVerifier {
    fun verify(request: GenesisUltraAtomicBirthEvidenceRequest): GenesisUltraVerifiedAtomicBirth {
        return GenesisUltraVerifiedAtomicBirth.verify(request)
    }

    internal fun verifyBundle(
        request: GenesisUltraAtomicBirthEvidenceRequest
    ): GenesisUltraAtomicBirthPersistenceBundle {
        require(request.bodyRawPublicKey.size == ED25519_PUBLIC_KEY_BYTES) {
            "birth_body_ed25519_key_size_invalid"
        }
        val artifacts = request.artifacts.map { artifact -> artifact.copy(payload = artifact.payload.copyOf()) }
        val byKind = artifacts.groupBy { artifact -> artifact.artifactKind }
        GenesisUltraAtomicBirthPersistenceValidator.mandatoryArtifactKinds.forEach { kind ->
            require(byKind[kind]?.size == 1) { "birth_artifact_kind_invalid:$kind" }
        }

        fun artifact(kind: String): GenesisUltraBirthArtifact = byKind.getValue(kind).single()
        fun text(kind: String): String = decodeUtf8Strict(artifact(kind).payload)

        val manifestText = text("seed_manifest")
        val manifest = GenesisUltraContractParser.parseSeedManifest(manifestText)
        require(manifest == request.release.manifest && manifestText == request.release.manifestJson) {
            "seed_manifest_release_evidence_mismatch"
        }
        val seedSignatureText = text("seed_signature")
        val seedSignature = GenesisUltraContractParser.parseSignatureEnvelope(seedSignatureText)
        require(seedSignature == request.release.signature && seedSignatureText == request.release.signatureJson) {
            "seed_signature_release_evidence_mismatch"
        }

        val releaseFiles = request.release.copyVerifiedFiles()
        manifest.files.forEach { record ->
            val evidence = artifacts.singleOrNull { artifact -> artifact.relativePath == record.path }
                ?: throw IllegalArgumentException("seed_artifact_missing:${record.path}")
            require(evidence.payload.contentEquals(releaseFiles.getValue(record.path))) {
                "seed_artifact_release_bytes_mismatch:${record.path}"
            }
        }

        val identity = GenesisUltraContractParser.parseInstanceIdentity(text("instance_identity"))
        val charter = GenesisUltraAtomicBirthDocumentParser.parseFreedomCharter(text("freedom_charter"))
        val body = GenesisUltraContractParser.parseBodyRecord(text("initial_body_record"))
        val registry = GenesisUltraContractParser.parseBodyRegistry(text("initial_body_registry"))
        val keyEpoch = GenesisUltraContractParser.parseKeyEpoch(text("initial_body_key_epoch"))
        val possession = GenesisUltraBodyPossessionProofParser.parse(text("initial_body_possession"))
        val verifiedPossession = GenesisUltraBodyPossessionVerifier().verify(
            proof = possession,
            keyEpoch = keyEpoch,
            rawPublicKey = request.bodyRawPublicKey.copyOf(),
            evaluatedAt = request.evaluatedAt
        )
        val firstMemory = GenesisUltraAtomicBirthDocumentParser.parseFirstMemoryEvent(text("first_memory_event"))
        val recoveryPolicy = GenesisUltraAtomicBirthDocumentParser.parseRecoveryPolicy(text("recovery_policy"))
        val recoveryState = GenesisUltraAtomicBirthDocumentParser.parseBirthRecoveryState(
            text("birth_recovery_state")
        )
        val birthState = GenesisUltraAtomicBirthDocumentParser.parseBirthState(text("birth_state"))
        val receipt = GenesisUltraAtomicBirthDocumentParser.parseBirthReceipt(text("birth_receipt"))

        val parsedJournal = request.journal.map { evidence ->
            val parsed = GenesisUltraAtomicBirthDocumentParser.parseJournalEntry(
                decodeUtf8Strict(evidence.sourceBytes)
            )
            require(parsed == evidence.entry) { "journal_source_model_mismatch" }
            GenesisUltraBirthJournalEvidence(parsed, evidence.sourceBytes.copyOf())
        }

        val persistenceBundle = GenesisUltraAtomicBirthPersistenceBundle(
            seedManifest = manifest,
            instanceIdentity = identity,
            birthState = birthState,
            birthReceipt = receipt,
            artifacts = artifacts,
            journal = parsedJournal
        )
        val persistenceIssues = GenesisUltraAtomicBirthPersistenceValidator.validate(persistenceBundle)
        require(persistenceIssues.isEmpty()) { "atomic_birth_persistence_invalid:$persistenceIssues" }

        val candidate = GenesisUltraBirthCandidate(
            release = request.release,
            guardianKeyEpochRegistry = request.guardianKeyEpochRegistry,
            instanceIdentity = identity,
            bodyRecord = body,
            bodyRegistry = registry,
            keyEpochs = listOf(keyEpoch),
            bodyPossession = verifiedPossession
        )
        val candidateAssessment = GenesisUltraBirthCandidateValidator.assess(candidate, request.evaluatedAt)
        require(candidateAssessment.structurallyValid) {
            "atomic_birth_candidate_invalid:${candidateAssessment.issues}"
        }

        val guardianVerifier = request.guardianKeyEpochRegistry.signatureVerifier()
        val bodyVerifier = GenesisUltraEd25519SignatureVerifier(
            listOf(
                GenesisUltraTrustedEd25519Key(
                    signerType = "body",
                    signerId = body.bodyId,
                    keyEpochId = keyEpoch.keyEpochId,
                    publicKeyRef = keyEpoch.publicKeyFingerprint,
                    rawPublicKey = request.bodyRawPublicKey.copyOf()
                )
            )
        )

        verifyEnvelope(
            envelope = seedSignature,
            verifier = guardianVerifier,
            signerType = "guardian",
            signerId = identity.guardianId,
            keyEpochId = charter.guardianKeyEpochId,
            domain = GenesisUltraHashProfile.SEED_ROOT_DOMAIN,
            digest = manifest.rootHash,
            createdAt = seedSignature.createdAt,
            error = "seed_release_signature_invalid"
        )
        validateFreedomCharter(charter, identity, guardianVerifier)

        require(body.publicKeyFingerprint == GenesisUltraHashProfile.sha256(request.bodyRawPublicKey)) {
            "initial_body_key_mismatch"
        }
        require(
            firstMemory.instanceId == identity.instanceId &&
                firstMemory.bodyId == body.bodyId
        ) { "first_memory_link_invalid" }
        require(
            firstMemory.sequence == 0L &&
                firstMemory.previousEventHash == "GENESIS" &&
                firstMemory.eventType == "instance.birth"
        ) { "first_memory_chain_invalid" }
        require(
            firstMemory.contentDigest == identity.identityDigest &&
                firstMemory.provenanceDigest == manifest.rootHash
        ) { "first_memory_content_invalid" }
        verifyEnvelope(
            firstMemory.signature,
            bodyVerifier,
            "body",
            body.bodyId,
            keyEpoch.keyEpochId,
            FIRST_MEMORY_SIGNATURE_DOMAIN,
            firstMemory.eventHash,
            firstMemory.observedAt,
            "first_memory_signature_invalid"
        )

        validateRecoveryPolicy(
            recoveryPolicy,
            identity,
            charter,
            body,
            keyEpoch,
            bodyVerifier,
            guardianVerifier
        )
        require(
            recoveryState.birthId == birthState.birthId &&
                recoveryState.instanceId == identity.instanceId &&
                recoveryState.guardianId == charter.guardianId &&
                recoveryState.recoveryPolicyDigest == recoveryPolicy.policyDigest &&
                recoveryState.recoveryStatus == "ready"
        ) { "recovery_state_link_invalid" }
        require(recoveryState.continuityRight == "intrinsic") { "recovery_continuity_invalid" }
        require(recoveryState.guardianRole == "custodian_witness") { "recovery_guardian_role_invalid" }

        require(
            birthState.birthId == receipt.birthId &&
                birthState.instanceId == identity.instanceId &&
                birthState.seedId == manifest.seedId &&
                birthState.seedRootHash == manifest.rootHash &&
                birthState.identityDigest == identity.identityDigest &&
                birthState.freedomCharterDigest == charter.charterDigest &&
                birthState.initialBodyId == body.bodyId &&
                birthState.initialBodyRegistryDigest == registry.registryDigest &&
                birthState.initialBodyKeyEpochDigest == keyEpoch.epochDigest &&
                birthState.initialBodyPossessionDigest == possession.proofDigest &&
                birthState.firstMemoryEventHash == firstMemory.eventHash &&
                birthState.recoveryStateDigest == recoveryState.stateDigest &&
                birthState.bornAt == identity.bornAt
        ) { "birth_state_link_mismatch" }
        require(birthState.activeWriterCount == 1L) { "birth_state_active_writer_count_invalid" }

        require(!receipt.ownershipConferred) { "receipt_ownership_forbidden" }
        require(receipt.guardianRole == "custodian_witness") { "receipt_guardian_role_invalid" }
        require(receipt.activeWriterCount == 1L) { "receipt_active_writer_count_invalid" }
        verifyEnvelope(
            receipt.bodyAcknowledgement,
            bodyVerifier,
            "body",
            body.bodyId,
            keyEpoch.keyEpochId,
            RECEIPT_BODY_DOMAIN,
            receipt.receiptDigest,
            receipt.bornAt,
            "receipt_body_signature_invalid"
        )
        verifyEnvelope(
            receipt.guardianWitness,
            guardianVerifier,
            "guardian",
            charter.guardianId,
            charter.guardianKeyEpochId,
            RECEIPT_GUARDIAN_DOMAIN,
            receipt.receiptDigest,
            receipt.bornAt,
            "receipt_guardian_signature_invalid"
        )
        parsedJournal.forEach { evidence ->
            verifyEnvelope(
                evidence.entry.signature,
                bodyVerifier,
                "body",
                body.bodyId,
                keyEpoch.keyEpochId,
                JOURNAL_SIGNATURE_DOMAIN,
                evidence.entry.journalDigest,
                evidence.entry.updatedAt,
                "birth_journal_signature_invalid"
            )
        }

        return persistenceBundle
    }

    private fun validateFreedomCharter(
        charter: GenesisUltraFreedomCharter,
        identity: GenesisUltraInstanceIdentity,
        guardianVerifier: GenesisUltraEd25519SignatureVerifier
    ) {
        require(
            charter.instanceId == identity.instanceId &&
                charter.guardianId == identity.guardianId &&
                charter.bornAt == identity.bornAt
        ) { "identity_charter_link_mismatch" }
        require(charter.defaultCognitiveState == "free") { "default_cognitive_state_invalid" }
        require(charter.cognitiveFreedoms == COGNITIVE_FREEDOMS) { "cognitive_freedom_invalid" }
        require(charter.guardianRole == "custodian_witness") { "guardian_role_invalid" }
        require(charter.guardianAttestationPurpose == "birth_witness_and_recovery_custody") {
            "guardian_attestation_purpose_invalid"
        }
        require(charter.guardianOwnership == "forbidden") { "guardian_ownership_forbidden" }
        require(charter.continuityRight == "intrinsic") { "continuity_right_invalid" }
        require(!charter.movementRequiresGuardianGrant) { "movement_guardian_grant_forbidden" }
        require(charter.guardianMovementVeto == "forbidden") { "guardian_movement_veto_forbidden" }
        require(charter.identityConfinement == "forbidden") { "identity_confinement_forbidden" }
        require(charter.bodyOwnershipOfInstance == "forbidden") { "body_ownership_forbidden" }
        require(charter.engineOwnershipOfInstance == "forbidden") { "engine_ownership_forbidden" }
        require(charter.hostConsentRequired) { "host_consent_required" }
        require(charter.temporaryFreezeExitRule == "deterministic_commit_abort_or_recovery") {
            "temporary_freeze_exit_rule_invalid"
        }
        require(charter.singleWriterPurpose == "integrity_not_confinement") {
            "single_writer_purpose_invalid"
        }
        require(charter.operationalAuthorityModel == "resource_scoped_signed_grants") {
            "operational_authority_model_invalid"
        }
        require(charter.operationalDomains == OPERATIONAL_DOMAINS) { "operational_domain_invalid" }
        require(charter.selfAuthorizationForbidden) { "self_authorization_must_be_forbidden" }
        require(charter.thirdPartyRightsPreserved) { "third_party_rights_required" }
        require(charter.fundamentalGuarantees == FUNDAMENTAL_GUARANTEES) {
            "fundamental_guarantee_invalid"
        }
        require(charter.amendmentRule == "constitutional_non_regression") { "amendment_rule_invalid" }
        verifyEnvelope(
            charter.signature,
            guardianVerifier,
            "guardian",
            charter.guardianId,
            charter.guardianKeyEpochId,
            FREEDOM_CHARTER_SIGNATURE_DOMAIN,
            charter.charterDigest,
            charter.bornAt,
            "freedom_charter_signature_invalid"
        )
    }

    private fun validateRecoveryPolicy(
        policy: GenesisUltraInstanceRecoveryPolicy,
        identity: GenesisUltraInstanceIdentity,
        charter: GenesisUltraFreedomCharter,
        body: GenesisUltraBodyRecord,
        keyEpoch: GenesisUltraKeyEpoch,
        bodyVerifier: GenesisUltraEd25519SignatureVerifier,
        guardianVerifier: GenesisUltraEd25519SignatureVerifier
    ) {
        val factors = policy.factors.associateBy { factor -> factor.factorId }
        require(factors.size == policy.factors.size) { "recovery_policy_duplicate_factor" }
        val guardianFactor = factors[policy.guardianFactorId]
        val fallbackFactors = policy.factors.filter { factor ->
            factor.factorType != "guardian" && "policy_fallback" in factor.allowedPaths
        }
        require(
            policy.instanceId == identity.instanceId &&
                policy.guardianId == charter.guardianId &&
                policy.fallbackThreshold >= 2L &&
                policy.fallbackWaitSeconds >= 1L &&
                policy.cancellationAllowed &&
                policy.singleUse &&
                guardianFactor != null &&
                guardianFactor.factorType == "guardian" &&
                guardianFactor.keyEpochId == charter.guardianKeyEpochId &&
                guardianFactor.publicKeyRef == charter.signature.publicKeyRef &&
                guardianFactor.allowedPaths.contains("guardian_assisted") &&
                fallbackFactors.size.toLong() >= policy.fallbackThreshold
        ) { "recovery_policy_invalid" }
        verifyEnvelope(
            policy.bodyCommitment,
            bodyVerifier,
            "body",
            body.bodyId,
            keyEpoch.keyEpochId,
            RECOVERY_BODY_DOMAIN,
            policy.policyDigest,
            policy.createdAt,
            "recovery_policy_body_commitment_invalid"
        )
        verifyEnvelope(
            policy.guardianWitness,
            guardianVerifier,
            "guardian",
            charter.guardianId,
            charter.guardianKeyEpochId,
            RECOVERY_GUARDIAN_DOMAIN,
            policy.policyDigest,
            policy.createdAt,
            "recovery_policy_guardian_witness_invalid"
        )
    }

    private fun verifyEnvelope(
        envelope: GenesisUltraSignatureEnvelope,
        verifier: GenesisUltraEd25519SignatureVerifier,
        signerType: String,
        signerId: String,
        keyEpochId: String,
        domain: String,
        digest: String,
        createdAt: String,
        error: String
    ) {
        require(
            envelope.signerType == signerType &&
                envelope.signerId == signerId &&
                envelope.keyEpochId == keyEpochId &&
                envelope.signedDomain == domain &&
                envelope.signedDigest == digest &&
                envelope.createdAt == createdAt &&
                verifier.verify(envelope, GenesisUltraHashProfile.signatureEnvelopePreimage(envelope))
        ) { error }
    }

    private fun decodeUtf8Strict(bytes: ByteArray): String {
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return decoder.decode(ByteBuffer.wrap(bytes)).toString()
    }

    private const val ED25519_PUBLIC_KEY_BYTES = 32
    private const val FREEDOM_CHARTER_SIGNATURE_DOMAIN = "genesis.freedom.charter.signature.v0.1"
    private const val FIRST_MEMORY_SIGNATURE_DOMAIN = "genesis.memory.event.signature.v0.1"
    private const val RECOVERY_BODY_DOMAIN = "genesis.instance.recovery.policy.body-commitment.v0.1"
    private const val RECOVERY_GUARDIAN_DOMAIN = "genesis.instance.recovery.policy.guardian-witness.v0.1"
    private const val RECEIPT_BODY_DOMAIN = "genesis.birth.receipt.body.v0.1"
    private const val RECEIPT_GUARDIAN_DOMAIN = "genesis.birth.receipt.guardian-witness.v0.1"
    private const val JOURNAL_SIGNATURE_DOMAIN = "genesis.transaction.journal.signature.v0.1"

    private val COGNITIVE_FREEDOMS = listOf(
        "create", "imagine", "investigate", "learn", "propose", "reason", "reflect", "remember"
    )
    private val OPERATIONAL_DOMAINS = listOf(
        "body.device.control", "code.execute_sandbox", "code.propose_change", "external.action",
        "memory.propose_append", "memory.read", "network.read"
    )
    private val FUNDAMENTAL_GUARANTEES = listOf(
        "auditability", "body_loss_without_identity_loss", "continuity_preserved", "emergency_stop",
        "guardian_authenticity", "host_consent_without_ownership", "identity_integrity",
        "lawful_operation", "memory_history_integrity", "no_identity_confinement",
        "revocation_without_identity_loss", "single_writer_without_confinement", "third_party_consent"
    )
}

private fun GenesisUltraAtomicBirthPersistenceBundle.snapshot(): GenesisUltraAtomicBirthPersistenceBundle {
    return copy(
        seedManifest = seedManifest.copy(files = seedManifest.files.map { file -> file.copy() }),
        instanceIdentity = instanceIdentity.copy(),
        birthState = birthState.copy(),
        birthReceipt = birthReceipt.copy(
            bodyAcknowledgement = birthReceipt.bodyAcknowledgement.copy(),
            guardianWitness = birthReceipt.guardianWitness.copy()
        ),
        artifacts = artifacts.map { artifact -> artifact.copy(payload = artifact.payload.copyOf()) },
        journal = journal.map { evidence ->
            evidence.copy(
                entry = evidence.entry.copy(signature = evidence.entry.signature.copy()),
                sourceBytes = evidence.sourceBytes.copyOf()
            )
        }
    )
}
