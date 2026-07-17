package com.morimil.app.data.genesis.ultra


data class GenesisUltraBirthCandidate(
    val release: GenesisUltraVerifiedRelease,
    val instanceIdentity: GenesisUltraInstanceIdentity,
    val bodyRecord: GenesisUltraBodyRecord,
    val bodyRegistry: GenesisUltraBodyRegistry,
    val keyEpochs: List<GenesisUltraKeyEpoch>
)

data class GenesisUltraBirthCandidateAssessment(
    val structurallyValid: Boolean,
    val birthReady: Boolean,
    val issues: List<String>,
    val remainingBlockers: List<String>
)

object GenesisUltraBirthCandidateValidator {
    fun assess(candidate: GenesisUltraBirthCandidate): GenesisUltraBirthCandidateAssessment {
        val issues = linkedSetOf<String>()
        val release = candidate.release
        val identity = candidate.instanceIdentity
        val body = candidate.bodyRecord
        val registry = candidate.bodyRegistry

        if (identity.seedId != release.manifest.seedId) issues += "seed_id_mismatch"
        if (identity.seedRootHash != release.verifiedRootHash) issues += "seed_root_hash_mismatch"
        if (identity.guardianId != release.signature.signerId) issues += "guardian_signer_mismatch"
        if (identity.instanceId == body.bodyId) issues += "instance_body_id_collision"
        if (body.instanceId != identity.instanceId) issues += "body_instance_mismatch"
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
            if (epoch.instanceId != identity.instanceId) issues += "key_epoch_instance_mismatch"
            if (epoch.bodyId !in registryBodyIds) issues += "key_epoch_body_not_registered"
        }

        val activeBodyEpochs = candidate.keyEpochs.filter { epoch ->
            epoch.bodyId == body.bodyId && epoch.status == "active"
        }
        if (activeBodyEpochs.size != 1) {
            issues += "active_body_key_epoch_count_invalid"
        } else if (activeBodyEpochs.single().publicKeyFingerprint != body.publicKeyFingerprint) {
            issues += "active_body_key_fingerprint_mismatch"
        }

        val blockers = listOf(
            "trusted_guardian_key_epoch_registry_not_integrated",
            "body_possession_proof_not_integrated",
            "transactional_birth_commit_not_integrated"
        )
        return GenesisUltraBirthCandidateAssessment(
            structurallyValid = issues.isEmpty(),
            birthReady = issues.isEmpty() && blockers.isEmpty(),
            issues = issues.toList(),
            remainingBlockers = blockers
        )
    }
}
