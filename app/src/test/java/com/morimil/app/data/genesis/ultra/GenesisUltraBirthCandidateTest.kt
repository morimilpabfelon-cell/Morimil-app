package com.morimil.app.data.genesis.ultra

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenesisUltraBirthCandidateTest {
    @Test
    fun matchingContractsAreStructurallyValidButBirthRemainsBlocked() {
        val candidate = validCandidate()

        val assessment = GenesisUltraBirthCandidateValidator.assess(candidate)

        assertTrue(assessment.structurallyValid)
        assertFalse(assessment.birthReady)
        assertTrue(assessment.issues.isEmpty())
        assertTrue("body_possession_proof_not_integrated" in assessment.remainingBlockers)
        assertTrue("transactional_birth_commit_not_integrated" in assessment.remainingBlockers)
    }

    @Test
    fun rejectsInstanceBodyCollisionAndSeedMismatch() {
        val base = validCandidate()
        val invalidIdentity = base.instanceIdentity.copy(
            instanceId = base.bodyRecord.bodyId,
            seedId = "seed_01HWRONG00000000000000001"
        )

        val assessment = GenesisUltraBirthCandidateValidator.assess(
            base.copy(instanceIdentity = invalidIdentity)
        )

        assertFalse(assessment.structurallyValid)
        assertTrue("seed_id_mismatch" in assessment.issues)
        assertTrue("instance_body_id_collision" in assessment.issues)
    }

    @Test
    fun rejectsRegistryWithoutExactlyOneActiveWriter() {
        val base = validCandidate()
        val readOnlyBody = base.bodyRegistry.bodies.single().copy(status = "read_only")
        val registry = base.bodyRegistry.copy(bodies = listOf(readOnlyBody))

        val assessment = GenesisUltraBirthCandidateValidator.assess(base.copy(bodyRegistry = registry))

        assertFalse(assessment.structurallyValid)
        assertTrue("active_writer_count_invalid" in assessment.issues)
        assertTrue("birth_body_not_active_writer" in assessment.issues)
    }

    @Test
    fun rejectsBodyKeyFingerprintMismatch() {
        val base = validCandidate()
        val epoch = base.keyEpochs.single().copy(
            publicKeyFingerprint = "sha256:" + "c".repeat(64)
        )

        val assessment = GenesisUltraBirthCandidateValidator.assess(base.copy(keyEpochs = listOf(epoch)))

        assertFalse(assessment.structurallyValid)
        assertTrue("active_body_key_fingerprint_mismatch" in assessment.issues)
    }

    private fun validCandidate(): GenesisUltraBirthCandidate {
        val seedId = "seed_01HBIRTH00000000000000001"
        val seedRoot = "sha256:" + "a".repeat(64)
        val guardianId = "guardian_01HBIRTH000000000001"
        val instanceId = "inst_01HBIRTH000000000000001"
        val bodyId = "body_01HBIRTH000000000000001"
        val fingerprint = "sha256:" + "b".repeat(64)
        val signature = GenesisUltraSignatureEnvelope(
            schemaVersion = "genesis.signature.envelope.v0.1",
            signatureProfile = "genesis.signature.ed25519.v0.1",
            signerType = "guardian",
            signerId = guardianId,
            keyEpochId = "guardian_epoch_01HBIRTH000001",
            signedDomain = GenesisUltraHashProfile.SEED_ROOT_DOMAIN,
            signedDigest = seedRoot,
            signatureValue = "1".repeat(128),
            createdAt = "2026-07-17T00:00:00Z",
            publicKeyRef = "sha256:" + "d".repeat(64)
        )
        val manifest = GenesisUltraSeedManifest(
            schemaVersion = "genesis.seed.manifest.v0.1",
            protocolVersion = "genesis.protocol.v0.1",
            hashProfile = GenesisUltraHashProfile.FIELD_PROFILE,
            seedId = seedId,
            identityDigest = "sha256:" + "e".repeat(64),
            doctrineDigest = "sha256:" + "f".repeat(64),
            files = emptyList(),
            rootHash = seedRoot
        )
        val release = GenesisUltraVerifiedRelease(
            manifest = manifest,
            signature = signature,
            verifiedRootHash = seedRoot,
            verifiedFileCount = 2
        )
        val identityWithoutDigest = GenesisUltraInstanceIdentity(
            schemaVersion = "genesis.instance.identity.v0.1",
            instanceId = instanceId,
            seedId = seedId,
            seedRootHash = seedRoot,
            companionName = "Genesis Birth Test",
            guardianId = guardianId,
            bornAt = "2026-07-17T00:01:00Z",
            identityDigest = "sha256:" + "0".repeat(64)
        )
        val identity = identityWithoutDigest.copy(
            identityDigest = GenesisUltraHashProfile.instanceIdentityDigest(identityWithoutDigest)
        )
        val body = GenesisUltraBodyRecord(
            schemaVersion = "genesis.body.record.v0.1",
            instanceId = instanceId,
            bodyId = bodyId,
            status = "active_writer",
            createdAt = "2026-07-17T00:01:00Z",
            platformProfile = "android-kotlin",
            publicKeyFingerprint = fingerprint,
            revokedAt = null,
            revocationReason = null
        )
        val registryWithoutDigest = GenesisUltraBodyRegistry(
            schemaVersion = "genesis.body.registry.v0.1",
            instanceId = instanceId,
            registryEpoch = 0,
            bodies = listOf(
                GenesisUltraRegisteredBody(
                    bodyId = bodyId,
                    status = "active_writer",
                    platformProfile = body.platformProfile,
                    publicKeyFingerprint = fingerprint,
                    createdAt = body.createdAt,
                    lastSeenAt = null,
                    revocationRef = null
                )
            ),
            updatedAt = "2026-07-17T00:01:00Z",
            registryDigest = "sha256:" + "0".repeat(64)
        )
        val registry = registryWithoutDigest.copy(
            registryDigest = GenesisUltraHashProfile.bodyRegistryDigest(registryWithoutDigest)
        )
        val epochWithoutDigest = GenesisUltraKeyEpoch(
            schemaVersion = "genesis.key.epoch.v0.1",
            keyEpochId = "epoch_01HBIRTH000000000000001",
            instanceId = instanceId,
            bodyId = bodyId,
            epochNumber = 0,
            publicKeyFingerprint = fingerprint,
            createdAt = body.createdAt,
            status = "active",
            previousEpochId = null,
            rotationAuthorizationRef = null,
            epochDigest = "sha256:" + "0".repeat(64),
            signature = null
        )
        val epoch = epochWithoutDigest.copy(
            epochDigest = GenesisUltraHashProfile.keyEpochDigest(epochWithoutDigest)
        )
        return GenesisUltraBirthCandidate(
            release = release,
            instanceIdentity = identity,
            bodyRecord = body,
            bodyRegistry = registry,
            keyEpochs = listOf(epoch)
        )
    }
}
