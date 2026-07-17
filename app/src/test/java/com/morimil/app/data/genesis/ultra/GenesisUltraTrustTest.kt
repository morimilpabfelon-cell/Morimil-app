package com.morimil.app.data.genesis.ultra

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature

class GenesisUltraTrustTest {
    @Test
    fun bodyPossessionDigestMatchesOfficialCryptoVector() {
        val proof = GenesisUltraBodyPossessionProof(
            schemaVersion = "genesis.body.possession.v0.1",
            proofId = "proof_01HNEUTRAL0000000000001",
            instanceId = "inst_01HNEUTRAL00000000000001",
            bodyId = "body_01HNEUTRAL00000000000001",
            challengeNonce = "nonce_01HNEUTRAL0000000000001",
            issuedAt = "2026-07-12T00:00:00Z",
            expiresAt = "2026-07-12T00:05:00Z",
            publicKeyFingerprint = "sha256:" + "a".repeat(64),
            proofDigest = "sha256:43949d320e32c16e34ca2e06c4e8de72188f79c7b6927478844db5d73bc30abf",
            signature = GenesisUltraBodyPossessionSignature(
                profile = "genesis.signature.ed25519.v0.1",
                keyEpochId = "epoch_01HNEUTRAL0000000000001",
                value = "0".repeat(128)
            )
        )

        assertEquals(proof.proofDigest, GenesisUltraHashProfile.bodyPossessionDigest(proof))
    }

    @Test
    fun verifiesFreshBodyPossessionAgainstActiveKeyEpoch() {
        val fixture = signedPossessionFixture()

        val verified = GenesisUltraBodyPossessionVerifier().verify(
            proof = fixture.proof,
            keyEpoch = fixture.keyEpoch,
            rawPublicKey = fixture.rawPublicKey,
            evaluatedAt = "2026-07-17T00:03:00Z"
        )

        assertEquals(fixture.proof.proofId, verified.proof.proofId)
        assertEquals("2026-07-17T00:03:00Z", verified.verifiedAt)
    }

    @Test
    fun rejectsExpiredPossessionProof() {
        val fixture = signedPossessionFixture()

        val error = assertThrows(IllegalArgumentException::class.java) {
            GenesisUltraBodyPossessionVerifier().verify(
                proof = fixture.proof,
                keyEpoch = fixture.keyEpoch,
                rawPublicKey = fixture.rawPublicKey,
                evaluatedAt = fixture.proof.expiresAt
            )
        }

        assertEquals("body_possession_expired", error.message)
    }

    @Test
    fun rejectsPossessionSignatureMutation() {
        val fixture = signedPossessionFixture()
        val mutated = fixture.proof.copy(
            signature = fixture.proof.signature.copy(
                value = "00" + fixture.proof.signature.value.drop(2)
            )
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            GenesisUltraBodyPossessionVerifier().verify(
                proof = mutated,
                keyEpoch = fixture.keyEpoch,
                rawPublicKey = fixture.rawPublicKey,
                evaluatedAt = "2026-07-17T00:03:00Z"
            )
        }

        assertEquals("body_possession_signature_invalid", error.message)
    }

    @Test
    fun guardianRegistryTrustRequiresExactActiveEpochTuple() {
        val rawKey = ByteArray(32) { index -> (index + 1).toByte() }
        val publicKeyRef = GenesisUltraHashProfile.sha256(rawKey)
        val registry = GenesisUltraTrustedGuardianKeyEpochRegistry(
            listOf(
                GenesisUltraTrustedGuardianKeyEpoch(
                    guardianId = "guardian_01HTRUST000000000001",
                    keyEpochId = "guardian_epoch_01HTRUST000001",
                    publicKeyRef = publicKeyRef,
                    status = "active",
                    rawPublicKey = rawKey
                )
            )
        )
        val envelope = GenesisUltraSignatureEnvelope(
            schemaVersion = "genesis.signature.envelope.v0.1",
            signatureProfile = "genesis.signature.ed25519.v0.1",
            signerType = "guardian",
            signerId = "guardian_01HTRUST000000000001",
            keyEpochId = "guardian_epoch_01HTRUST000001",
            signedDomain = GenesisUltraHashProfile.SEED_ROOT_DOMAIN,
            signedDigest = "sha256:" + "a".repeat(64),
            signatureValue = "0".repeat(128),
            createdAt = "2026-07-17T00:00:00Z",
            publicKeyRef = publicKeyRef
        )

        assertTrue(registry.trusts(envelope))
        assertFalse(registry.trusts(envelope.copy(keyEpochId = "guardian_epoch_01HTRUST000002")))
        assertFalse(registry.trusts(envelope.copy(signerId = "guardian_01HOTHER000000000001")))
    }

    @Test
    fun parserRejectsAdditionalPossessionField() {
        val fixture = signedPossessionFixture()
        val json = proofJson(fixture.proof).put("authority", "not allowed")

        val error = assertThrows(IllegalArgumentException::class.java) {
            GenesisUltraBodyPossessionProofParser.parse(json.toString())
        }

        assertEquals("body_possession_unexpected_or_missing_fields", error.message)
    }

    private fun signedPossessionFixture(): PossessionFixture {
        val keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val encodedPublicKey = keyPair.public.encoded
        val rawPublicKey = encodedPublicKey.copyOfRange(encodedPublicKey.size - 32, encodedPublicKey.size)
        val fingerprint = GenesisUltraHashProfile.sha256(rawPublicKey)
        val keyEpochWithoutDigest = GenesisUltraKeyEpoch(
            schemaVersion = "genesis.key.epoch.v0.1",
            keyEpochId = "epoch_01HPOSSESSION0000000001",
            instanceId = "inst_01HPOSSESSION0000000001",
            bodyId = "body_01HPOSSESSION0000000001",
            epochNumber = 0,
            publicKeyFingerprint = fingerprint,
            createdAt = "2026-07-17T00:00:00Z",
            status = "active",
            previousEpochId = null,
            rotationAuthorizationRef = null,
            epochDigest = "sha256:" + "0".repeat(64),
            signature = null
        )
        val keyEpoch = keyEpochWithoutDigest.copy(
            epochDigest = GenesisUltraHashProfile.keyEpochDigest(keyEpochWithoutDigest)
        )
        val unsignedProof = GenesisUltraBodyPossessionProof(
            schemaVersion = "genesis.body.possession.v0.1",
            proofId = "proof_01HPOSSESSION000000001",
            instanceId = keyEpoch.instanceId,
            bodyId = keyEpoch.bodyId,
            challengeNonce = "nonce_01HPOSSESSION000000001",
            issuedAt = "2026-07-17T00:01:00Z",
            expiresAt = "2026-07-17T00:06:00Z",
            publicKeyFingerprint = fingerprint,
            proofDigest = "sha256:" + "0".repeat(64),
            signature = GenesisUltraBodyPossessionSignature(
                profile = "genesis.signature.ed25519.v0.1",
                keyEpochId = keyEpoch.keyEpochId,
                value = "0".repeat(128)
            )
        )
        val proofDigest = GenesisUltraHashProfile.bodyPossessionDigest(unsignedProof)
        val envelope = GenesisUltraSignatureEnvelope(
            schemaVersion = "genesis.signature.envelope.v0.1",
            signatureProfile = unsignedProof.signature.profile,
            signerType = "body",
            signerId = unsignedProof.bodyId,
            keyEpochId = unsignedProof.signature.keyEpochId,
            signedDomain = GenesisUltraBodyPossessionVerifier.BODY_POSSESSION_SIGNATURE_DOMAIN,
            signedDigest = proofDigest,
            signatureValue = "0".repeat(128),
            createdAt = unsignedProof.issuedAt,
            publicKeyRef = fingerprint
        )
        val signer = Signature.getInstance("Ed25519")
        signer.initSign(keyPair.private)
        signer.update(GenesisUltraHashProfile.signatureEnvelopePreimage(envelope))
        val signatureValue = signer.sign().toHex()
        val proof = unsignedProof.copy(
            proofDigest = proofDigest,
            signature = unsignedProof.signature.copy(value = signatureValue)
        )
        val parsed = GenesisUltraBodyPossessionProofParser.parse(proofJson(proof).toString())
        return PossessionFixture(parsed, keyEpoch, rawPublicKey)
    }

    private fun proofJson(proof: GenesisUltraBodyPossessionProof): JSONObject {
        return JSONObject()
            .put("schema_version", proof.schemaVersion)
            .put("proof_id", proof.proofId)
            .put("instance_id", proof.instanceId)
            .put("body_id", proof.bodyId)
            .put("challenge_nonce", proof.challengeNonce)
            .put("issued_at", proof.issuedAt)
            .put("expires_at", proof.expiresAt)
            .put("public_key_fingerprint", proof.publicKeyFingerprint)
            .put("proof_digest", proof.proofDigest)
            .put(
                "signature",
                JSONObject()
                    .put("profile", proof.signature.profile)
                    .put("key_epoch_id", proof.signature.keyEpochId)
                    .put("value", proof.signature.value)
            )
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }

    private data class PossessionFixture(
        val proof: GenesisUltraBodyPossessionProof,
        val keyEpoch: GenesisUltraKeyEpoch,
        val rawPublicKey: ByteArray
    )
}
