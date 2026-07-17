package com.morimil.app.data.genesis.ultra

import java.time.Instant
import java.time.format.DateTimeParseException


data class GenesisUltraBirthCandidate(
    val release: GenesisUltraVerifiedRelease,
    val guardianKeyEpochRegistry: GenesisUltraTrustedGuardianKeyEpochRegistry,
    val instanceIdentity: GenesisUltraInstanceIdentity,
    val bodyRecord: GenesisUltraBodyRecord,
    val bodyRegistry: GenesisUltraBodyRegistry,
    val keyEpochs: List<GenesisUltraKeyEpoch>,
    val bodyPossession: GenesisUltraVerifiedBodyPossession
)

data class GenesisUltraBirthCandidateAssessment(
    val structurallyValid: Boolean,
    val birthReady: Boolean,
    val issues: List<String>,
    val remainingBlockers: List<String>
)

object GenesisUltraBirthCandidateValidator {
    /**
     * Compatibility helper for deterministic tests. Production gates must pass
     * the actual decision time to the overload below.
     */
    fun assess(candidate: GenesisUltraBirthCandidate): GenesisUltraBirthCandidateAssessment {
        return assess(candidate, candidate.bodyPossession.verifiedAt)
    }

    fun assess(
        candidate: GenesisUltraBirthCandidate,
        evaluatedAt: String
    ): GenesisUltraBirthCandidateAssessment {
        val issues = linkedSetOf<String>()
        val release = candidate.release
        val identity = candidate.instanceIdentity
        val body = candidate.bodyRecord
        val registry = candidate.bodyRegistry
        val possession = candidate.bodyPossession.proof
        val assessmentTime = parseRequiredInstant(evaluatedAt, "invalid_birth_assessment_time")

        if (release.manifest.schemaVersion != SEED_MANIFEST_SCHEMA) issues += "release_schema_mismatch"
        if (release.manifest.rootHash != release.verifiedRootHash) issues += "release_root_hash_mismatch"
        if (release.signature.signerType != "guardian") issues += "release_signer_type_invalid"
        if (release.signature.signedDigest != release.verifiedRootHash) issues += "release_signature_digest_mismatch"
        if (!candidate.guardianKeyEpochRegistry.trusts(release.signature)) {
            issues += "release_guardian_key_epoch_untrusted"
        }

        if (identity.schemaVersion != INSTANCE_IDENTITY_SCHEMA) issues += "identity_schema_mismatch"
        if (GenesisUltraHashProfile.instanceIdentityDigest(identity) != identity.identityDigest) {
            issues += "identity_digest_mismatch"
        }
        if (identity.seedId != release.manifest.seedId) issues += "seed_id_mismatch"
        if (identity.seedRootHash != release.verifiedRootHash) issues += "seed_root_hash_mismatch"
        if (identity.guardianId != release.signature.signerId) issues += "guardian_signer_mismatch"

        if (body.schemaVersion != BODY_RECORD_SCHEMA) issues += "body_schema_mismatch"
        if (identity.instanceId == body.bodyId) issues += "instance_body_id_collision"
        if (body.instanceId != identity.instanceId) issues += "body_instance_mismatch"

        if (registry.schemaVersion != BODY_REGISTRY_SCHEMA) issues += "body_registry_schema_mismatch"
        if (GenesisUltraHashProfile.bodyRegistryDigest(registry) != registry.registryDigest) {
            issues += "body_registry_digest_mismatch"
        }
        if (registry.instanceId != identity.instanceId) issues += "body_registry_instance_mismatch"

        val activeWriters = registry.bodies.filter { registered -> registered.status == "active_writer" }
        if (activeWriters.size != 1) issues += "active_writer_count_invalid"
        val registeredBody = registry.bodies.singleOrNull { registered -> registered.bodyId == body.bodyId }
        if (registeredBody == null) {
            issues += "body_not_registered"
        } else {
            if (body.status != "active_writer" || registeredBody.status != "active_writer") {
                issues += "birth_body_not_active_writer"
            }
            if (registeredBody.platformProfile != body.platformProfile) issues += "body_platform_profile_mismatch"
            if (registeredBody.publicKeyFingerprint != body.publicKeyFingerprint) {
                issues += "body_public_key_fingerprint_mismatch"
            }
            if (registeredBody.createdAt != body.createdAt) issues += "body_created_at_mismatch"
            if (registeredBody.revocationRef != null || body.revokedAt != null || body.revocationReason != null) {
                issues += "active_writer_cannot_be_revoked"
            }
        }

        val registryBodyIds = registry.bodies.map { registered -> registered.bodyId }.toSet()
        candidate.keyEpochs.forEach { epoch ->
            if (epoch.schemaVersion != KEY_EPOCH_SCHEMA) issues += "key_epoch_schema_mismatch"
            if (GenesisUltraHashProfile.keyEpochDigest(epoch) != epoch.epochDigest) {
                issues += "key_epoch_digest_mismatch"
            }
            if (epoch.instanceId != identity.instanceId) issues += "key_epoch_instance_mismatch"
            if (epoch.bodyId !in registryBodyIds) issues += "key_epoch_body_not_registered"
        }

        val activeBodyEpochs = candidate.keyEpochs.filter { epoch ->
            epoch.bodyId == body.bodyId && epoch.status == "active"
        }
        if (activeBodyEpochs.size != 1) {
            issues += "active_body_key_epoch_count_invalid"
        } else {
            val activeEpoch = activeBodyEpochs.single()
            if (activeEpoch.publicKeyFingerprint != body.publicKeyFingerprint) {
                issues += "active_body_key_fingerprint_mismatch"
            }
            if (possession.signature.keyEpochId != activeEpoch.keyEpochId) {
                issues += "possession_key_epoch_mismatch"
            }
        }

        if (possession.schemaVersion != BODY_POSSESSION_SCHEMA) issues += "possession_schema_mismatch"
        if (GenesisUltraHashProfile.bodyPossessionDigest(possession) != possession.proofDigest) {
            issues += "possession_digest_mismatch"
        }
        if (possession.instanceId != identity.instanceId) issues += "possession_instance_mismatch"
        if (possession.bodyId != body.bodyId) issues += "possession_body_mismatch"
        if (possession.publicKeyFingerprint != body.publicKeyFingerprint) {
            issues += "possession_public_key_fingerprint_mismatch"
        }

        val issuedAt = parseCandidateInstant(possession.issuedAt, "possession_issued_at_invalid", issues)
        val expiresAt = parseCandidateInstant(possession.expiresAt, "possession_expires_at_invalid", issues)
        val verifiedAt = parseCandidateInstant(
            candidate.bodyPossession.verifiedAt,
            "possession_verified_at_invalid",
            issues
        )
        if (issuedAt != null && expiresAt != null) {
            if (issuedAt >= expiresAt) issues += "possession_expiration_invalid"
            if (assessmentTime < issuedAt) issues += "possession_not_yet_valid"
            if (assessmentTime >= expiresAt) issues += "possession_expired"
            if (verifiedAt != null && (verifiedAt < issuedAt || verifiedAt >= expiresAt)) {
                issues += "possession_verification_outside_validity_window"
            }
            if (verifiedAt != null && assessmentTime < verifiedAt) {
                issues += "assessment_precedes_possession_verification"
            }
        }

        val blockers = listOf("transactional_birth_commit_not_integrated")
        return GenesisUltraBirthCandidateAssessment(
            structurallyValid = issues.isEmpty(),
            birthReady = issues.isEmpty() && blockers.isEmpty(),
            issues = issues.toList(),
            remainingBlockers = blockers
        )
    }

    private fun parseRequiredInstant(value: String, errorCode: String): Instant {
        return try {
            Instant.parse(value)
        } catch (error: DateTimeParseException) {
            throw IllegalArgumentException(errorCode, error)
        }
    }

    private fun parseCandidateInstant(
        value: String,
        errorCode: String,
        issues: MutableSet<String>
    ): Instant? {
        return try {
            Instant.parse(value)
        } catch (_: DateTimeParseException) {
            issues += errorCode
            null
        }
    }

    private const val SEED_MANIFEST_SCHEMA = "genesis.seed.manifest.v0.1"
    private const val INSTANCE_IDENTITY_SCHEMA = "genesis.instance.identity.v0.1"
    private const val BODY_RECORD_SCHEMA = "genesis.body.record.v0.1"
    private const val BODY_REGISTRY_SCHEMA = "genesis.body.registry.v0.1"
    private const val KEY_EPOCH_SCHEMA = "genesis.key.epoch.v0.1"
    private const val BODY_POSSESSION_SCHEMA = "genesis.body.possession.v0.1"
}
