package com.morimil.app.data.genesis.ultra

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature

class GenesisUltraBirthCandidateTest {
    @Test
    fun matchingContractsAreStructurallyValidButTransactionStillBlocksBirth() {
        val candidate = validCandidate()

        val assessment = GenesisUltraBirthCandidateValidator.assess(candidate)

        assertTrue(assessment.structurallyValid)
        assertFalse(assessment.birthReady)
        assertTrue(assessment.issues.isEmpty())
        assertTrue(assessment.remainingBlockers == listOf("transactional_birth_commit_not_integrated"))
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
        assertTrue("identity_digest_mismatch" in assessment.issues)
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
        assertTrue("body_registry_digest_mismatch" in assessment.issues)
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
        assertTrue("key_epoch_digest_mismatch" in assessment.issues)
    }

    @Test
    fun rejectsGuardianEpochThatDoesNotTrustReleaseSigner() {
        val base = validCandidate()
        val otherKey = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val otherRawKey = rawPublicKey(otherKey)
        val otherRegistry = GenesisUltraTrustedGuardianKeyEpochRegistry(
            listOf(
                GenesisUltraTrustedGuardianKeyEpoch(
                    guardianId = "guardian_01HOTHER00000000000001",
                    keyEpochId = base.release.signature.keyEpochId,
                    publicKeyRef = GenesisUltraHashProfile.sha256(otherRawKey),
                    status = "active",
                    rawPublicKey = otherRawKey
                )
            )
        )

        val assessment = GenesisUltraBirthCandidateValidator.assess(
            base.copy(guardianKeyEpochRegistry = otherRegistry)
        )

        assertFalse(assessment.structurallyValid)
        assertTrue("release_guardian_key_epoch_untrusted" in assessment.issues)
    }

    @Test
    fun rejectsVerifiedPossessionBoundToAnotherBody() {
        val base = validCandidate()
        val (_, wrongPossession) = verifiedPossession(
            instanceId = base.instanceIdentity.instanceId,
            bodyId = "body_01HOTHER000000000000001",
            epochId = "epoch_01HOTHER00000000000001"
        )

        val assessment = GenesisUltraBirthCandidateValidator.assess(
            base.copy(bodyPossession = wrongPossession)
        )

        assertFalse(assessment.structurallyValid)
        assertTrue("possession_body_mismatch" in assessment.issues)
        assertTrue("possession_public_key_fingerprint_mismatch" in assessment.issues)
    }

    @Test
    fun rejectsPossessionThatExpiredAfterInitialVerification() {
        val candidate = validCandidate()

        val assessment = GenesisUltraBirthCandidateValidator.assess(
            candidate,
            candidate.bodyPossession.proof.expiresAt
        )

        assertFalse(assessment.structurallyValid)
        assertTrue("possession_expired" in assessment.issues)
    }

    private fun validCandidate(): GenesisUltraBirthCandidate {
        val seedId = "seed_01HBIRTH00000000000000001"
        val guardianId = "guardian_01HBIRTH000000000001"
        val guardianEpochId = "guardian_epoch_01HBIRTH000001"
        val instanceId = "inst_01HBIRTH000000000000001"
        val bodyId = "body_01HBIRTH000000000000001"
        val bodyEpochId = "epoch_01HBIRTH000000000000001"

        val guardianKeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val guardianRawKey = rawPublicKey(guardianKeyPair)
        val guardianPublicKeyRef = GenesisUltraHashProfile.sha256(guardianRawKey)
        val guardianRegistry = GenesisUltraTrustedGuardianKeyEpochRegistry(
            listOf(
                GenesisUltraTrustedGuardianKeyEpoch(
                    guardianId = guardianId,
                    keyEpochId = guardianEpochId,
                    publicKeyRef = guardianPublicKeyRef,
                    status = "active",
                    rawPublicKey = guardianRawKey
                )
            )
        )
        val release = verifiedRelease(
            seedId = seedId,
            guardianId = guardianId,
            guardianEpochId = guardianEpochId,
            guardianPublicKeyRef = guardianPublicKeyRef,
            guardianKeyPair = guardianKeyPair,
            guardianRegistry = guardianRegistry
        )

        val identityWithoutDigest = GenesisUltraInstanceIdentity(
            schemaVersion = "genesis.instance.identity.v0.1",
            instanceId = instanceId,
            seedId = seedId,
            seedRootHash = release.verifiedRootHash,
            companionName = "Genesis Birth Test",
            guardianId = guardianId,
            bornAt = "2026-07-17T00:01:00Z",
            identityDigest = "sha256:" + "0".repeat(64)
        )
        val identity = identityWithoutDigest.copy(
            identityDigest = GenesisUltraHashProfile.instanceIdentityDigest(identityWithoutDigest)
        )

        val (bodyEpoch, possession) = verifiedPossession(
            instanceId = instanceId,
            bodyId = bodyId,
            epochId = bodyEpochId
        )
        val body = GenesisUltraBodyRecord(
            schemaVersion = "genesis.body.record.v0.1",
            instanceId = instanceId,
            bodyId = bodyId,
            status = "active_writer",
            createdAt = bodyEpoch.createdAt,
            platformProfile = "android-kotlin",
            publicKeyFingerprint = bodyEpoch.publicKeyFingerprint,
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
                    publicKeyFingerprint = body.publicKeyFingerprint,
                    createdAt = body.createdAt,
                    lastSeenAt = null,
                    revocationRef = null
                )
            ),
            updatedAt = body.createdAt,
            registryDigest = "sha256:" + "0".repeat(64)
        )
        val registry = registryWithoutDigest.copy(
            registryDigest = GenesisUltraHashProfile.bodyRegistryDigest(registryWithoutDigest)
        )

        return GenesisUltraBirthCandidate(
            release = release,
            guardianKeyEpochRegistry = guardianRegistry,
            instanceIdentity = identity,
            bodyRecord = body,
            bodyRegistry = registry,
            keyEpochs = listOf(bodyEpoch),
            bodyPossession = possession
        )
    }

    private fun verifiedRelease(
        seedId: String,
        guardianId: String,
        guardianEpochId: String,
        guardianPublicKeyRef: String,
        guardianKeyPair: KeyPair,
        guardianRegistry: GenesisUltraTrustedGuardianKeyEpochRegistry
    ): GenesisUltraVerifiedRelease {
        val identityBytes = "{\"schema_version\":\"test.identity.v0.1\"}".toByteArray()
        val doctrineBytes = "Genesis Ultra birth test doctrine\n".toByteArray()
        val identityDigest = GenesisUltraHashProfile.sha256(identityBytes)
        val doctrineDigest = GenesisUltraHashProfile.sha256(doctrineBytes)
        val manifestWithoutRoot = GenesisUltraSeedManifest(
            schemaVersion = "genesis.seed.manifest.v0.1",
            protocolVersion = "genesis.protocol.v0.1",
            hashProfile = GenesisUltraHashProfile.FIELD_PROFILE,
            seedId = seedId,
            identityDigest = identityDigest,
            doctrineDigest = doctrineDigest,
            files = listOf(
                GenesisUltraSeedFileRecord(IDENTITY_PATH, "identity", true, identityDigest),
                GenesisUltraSeedFileRecord(DOCTRINE_PATH, "doctrine", true, doctrineDigest)
            ),
            rootHash = "sha256:" + "0".repeat(64)
        )
        val rootHash = GenesisUltraHashProfile.seedRoot(manifestWithoutRoot)
        val manifestJson = JSONObject()
            .put("schema_version", manifestWithoutRoot.schemaVersion)
            .put("protocol_version", manifestWithoutRoot.protocolVersion)
            .put("hash_profile", manifestWithoutRoot.hashProfile)
            .put("seed_id", manifestWithoutRoot.seedId)
            .put("identity_digest", identityDigest)
            .put("doctrine_digest", doctrineDigest)
            .put(
                "files",
                JSONArray().apply {
                    manifestWithoutRoot.files.forEach { file ->
                        put(
                            JSONObject()
                                .put("path", file.path)
                                .put("kind", file.kind)
                                .put("required", file.required)
                                .put("digest", file.digest)
                        )
                    }
                }
            )
            .put("root_hash", rootHash)

        val unsignedEnvelope = GenesisUltraSignatureEnvelope(
            schemaVersion = "genesis.signature.envelope.v0.1",
            signatureProfile = "genesis.signature.ed25519.v0.1",
            signerType = "guardian",
            signerId = guardianId,
            keyEpochId = guardianEpochId,
            signedDomain = GenesisUltraHashProfile.SEED_ROOT_DOMAIN,
            signedDigest = rootHash,
            signatureValue = "0".repeat(128),
            createdAt = "2026-07-17T00:00:00Z",
            publicKeyRef = guardianPublicKeyRef
        )
        val signatureValue = signEnvelope(unsignedEnvelope, guardianKeyPair)
        val signatureJson = envelopeJson(unsignedEnvelope.copy(signatureValue = signatureValue))

        return GenesisUltraReleaseVerifier(guardianRegistry.signatureVerifier()).verify(
            GenesisUltraReleaseBundle(
                manifestJson = manifestJson.toString(),
                signatureJson = signatureJson.toString(),
                files = mapOf(
                    IDENTITY_PATH to identityBytes,
                    DOCTRINE_PATH to doctrineBytes
                )
            )
        )
    }

    private fun verifiedPossession(
        instanceId: String,
        bodyId: String,
        epochId: String
    ): Pair<GenesisUltraKeyEpoch, GenesisUltraVerifiedBodyPossession> {
        val bodyKeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val bodyRawKey = rawPublicKey(bodyKeyPair)
        val fingerprint = GenesisUltraHashProfile.sha256(bodyRawKey)
        val epochWithoutDigest = GenesisUltraKeyEpoch(
            schemaVersion = "genesis.key.epoch.v0.1",
            keyEpochId = epochId,
            instanceId = instanceId,
            bodyId = bodyId,
            epochNumber = 0,
            publicKeyFingerprint = fingerprint,
            createdAt = "2026-07-17T00:01:00Z",
            status = "active",
            previousEpochId = null,
            rotationAuthorizationRef = null,
            epochDigest = "sha256:" + "0".repeat(64),
            signature = null
        )
        val epoch = epochWithoutDigest.copy(
            epochDigest = GenesisUltraHashProfile.keyEpochDigest(epochWithoutDigest)
        )
        val proofWithoutDigest = GenesisUltraBodyPossessionProof(
            schemaVersion = "genesis.body.possession.v0.1",
            proofId = "proof_${bodyId.takeLast(24)}",
            instanceId = instanceId,
            bodyId = bodyId,
            challengeNonce = "nonce_${bodyId.takeLast(24)}",
            issuedAt = "2026-07-17T00:01:00Z",
            expiresAt = "2026-07-17T00:06:00Z",
            publicKeyFingerprint = fingerprint,
            proofDigest = "sha256:" + "0".repeat(64),
            signature = GenesisUltraBodyPossessionSignature(
                profile = "genesis.signature.ed25519.v0.1",
                keyEpochId = epochId,
                value = "0".repeat(128)
            )
        )
        val proofDigest = GenesisUltraHashProfile.bodyPossessionDigest(proofWithoutDigest)
        val envelope = GenesisUltraSignatureEnvelope(
            schemaVersion = "genesis.signature.envelope.v0.1",
            signatureProfile = proofWithoutDigest.signature.profile,
            signerType = "body",
            signerId = bodyId,
            keyEpochId = epochId,
            signedDomain = GenesisUltraBodyPossessionVerifier.BODY_POSSESSION_SIGNATURE_DOMAIN,
            signedDigest = proofDigest,
            signatureValue = "0".repeat(128),
            createdAt = proofWithoutDigest.issuedAt,
            publicKeyRef = fingerprint
        )
        val proof = proofWithoutDigest.copy(
            proofDigest = proofDigest,
            signature = proofWithoutDigest.signature.copy(
                value = signEnvelope(envelope, bodyKeyPair)
            )
        )
        val verified = GenesisUltraBodyPossessionVerifier().verify(
            proof = proof,
            keyEpoch = epoch,
            rawPublicKey = bodyRawKey,
            evaluatedAt = "2026-07-17T00:02:00Z"
        )
        return epoch to verified
    }

    private fun signEnvelope(envelope: GenesisUltraSignatureEnvelope, keyPair: KeyPair): String {
        val signer = Signature.getInstance("Ed25519")
        signer.initSign(keyPair.private)
        signer.update(GenesisUltraHashProfile.signatureEnvelopePreimage(envelope))
        return signer.sign().toHex()
    }

    private fun envelopeJson(envelope: GenesisUltraSignatureEnvelope): JSONObject {
        return JSONObject()
            .put("schema_version", envelope.schemaVersion)
            .put("signature_profile", envelope.signatureProfile)
            .put("signer_type", envelope.signerType)
            .put("signer_id", envelope.signerId)
            .put("key_epoch_id", envelope.keyEpochId)
            .put("signed_domain", envelope.signedDomain)
            .put("signed_digest", envelope.signedDigest)
            .put("signature_value", envelope.signatureValue)
            .put("created_at", envelope.createdAt)
            .put("public_key_ref", envelope.publicKeyRef)
    }

    private fun rawPublicKey(keyPair: KeyPair): ByteArray {
        val encoded = keyPair.public.encoded
        return encoded.copyOfRange(encoded.size - 32, encoded.size)
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }

    private companion object {
        const val IDENTITY_PATH = "identity/companion.identity.json"
        const val DOCTRINE_PATH = "doctrine/doctrine.md"
    }
}
