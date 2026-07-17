package com.morimil.app.data.genesis.ultra

import java.time.Instant


data class GenesisUltraTrustedGuardianKeyEpoch(
    val guardianId: String,
    val keyEpochId: String,
    val publicKeyRef: String,
    val status: String,
    val rawPublicKey: ByteArray
)

class GenesisUltraTrustedGuardianKeyEpochRegistry(
    epochs: Collection<GenesisUltraTrustedGuardianKeyEpoch>
) {
    private val entries: List<GenesisUltraTrustedGuardianKeyEpoch> = epochs.map { epoch ->
        require(epoch.status in KEY_EPOCH_STATUSES) { "trusted_guardian_epoch_status_invalid" }
        require(epoch.rawPublicKey.size == 32) { "trusted_guardian_ed25519_key_size_invalid" }
        require(GenesisUltraHashProfile.sha256(epoch.rawPublicKey) == epoch.publicKeyRef) {
            "trusted_guardian_public_key_ref_mismatch"
        }
        epoch.copy(rawPublicKey = epoch.rawPublicKey.copyOf())
    }

    init {
        val identities = entries.map { epoch -> epoch.guardianId to epoch.keyEpochId }
        require(identities.distinct().size == identities.size) { "duplicate_trusted_guardian_key_epoch" }
        val activeCounts = entries
            .filter { epoch -> epoch.status == "active" }
            .groupingBy { epoch -> epoch.guardianId }
            .eachCount()
        require(activeCounts.values.all { count -> count == 1 }) { "multiple_active_guardian_key_epochs" }
    }

    fun trusts(envelope: GenesisUltraSignatureEnvelope): Boolean {
        if (envelope.signerType != "guardian") return false
        return entries.any { epoch ->
            epoch.status == "active" &&
                epoch.guardianId == envelope.signerId &&
                epoch.keyEpochId == envelope.keyEpochId &&
                epoch.publicKeyRef == envelope.publicKeyRef
        }
    }

    fun signatureVerifier(): GenesisUltraSignatureVerifier {
        val trusted = entries.filter { epoch -> epoch.status == "active" }.map { epoch ->
            GenesisUltraTrustedEd25519Key(
                signerType = "guardian",
                signerId = epoch.guardianId,
                keyEpochId = epoch.keyEpochId,
                publicKeyRef = epoch.publicKeyRef,
                rawPublicKey = epoch.rawPublicKey
            )
        }
        return GenesisUltraEd25519SignatureVerifier(trusted)
    }

    private companion object {
        val KEY_EPOCH_STATUSES = setOf("active", "retired", "revoked", "compromised")
    }
}

data class GenesisUltraBodyPossessionSignature(
    val profile: String,
    val keyEpochId: String,
    val value: String
)

data class GenesisUltraBodyPossessionProof(
    val schemaVersion: String,
    val proofId: String,
    val instanceId: String,
    val bodyId: String,
    val challengeNonce: String,
    val issuedAt: String,
    val expiresAt: String,
    val publicKeyFingerprint: String,
    val proofDigest: String,
    val signature: GenesisUltraBodyPossessionSignature
)

class GenesisUltraVerifiedBodyPossession internal constructor(
    val proof: GenesisUltraBodyPossessionProof,
    val verifiedAt: String
)

object GenesisUltraBodyPossessionProofParser {
    fun parse(jsonText: String): GenesisUltraBodyPossessionProof {
        val root = GenesisUltraStrictJson.parseObject(jsonText)
        val keys = root.keys().asSequence().toSet()
        require(keys == PROOF_KEYS) { "body_possession_unexpected_or_missing_fields" }
        val signatureJson = root.getJSONObject("signature")
        require(signatureJson.keys().asSequence().toSet() == SIGNATURE_KEYS) {
            "body_possession_signature_unexpected_or_missing_fields"
        }
        val proof = GenesisUltraBodyPossessionProof(
            schemaVersion = requiredConst(root, "schema_version", "genesis.body.possession.v0.1"),
            proofId = requiredText(root, "proof_id", 16, 128),
            instanceId = requiredText(root, "instance_id", 16, 128),
            bodyId = requiredText(root, "body_id", 16, 128),
            challengeNonce = requiredText(root, "challenge_nonce", 16, 256),
            issuedAt = requiredTimestamp(root, "issued_at"),
            expiresAt = requiredTimestamp(root, "expires_at"),
            publicKeyFingerprint = requiredText(root, "public_key_fingerprint", 16, 256),
            proofDigest = requiredSha256(root, "proof_digest"),
            signature = GenesisUltraBodyPossessionSignature(
                profile = requiredConst(signatureJson, "profile", "genesis.signature.ed25519.v0.1"),
                keyEpochId = requiredText(signatureJson, "key_epoch_id", 16, 128),
                value = requiredLowerHex(signatureJson, "value", 128)
            )
        )
        require(Instant.parse(proof.issuedAt) < Instant.parse(proof.expiresAt)) {
            "body_possession_expiration_invalid"
        }
        require(GenesisUltraHashProfile.bodyPossessionDigest(proof) == proof.proofDigest) {
            "body_possession_digest_mismatch"
        }
        return proof
    }

    private fun requiredConst(root: org.json.JSONObject, name: String, expected: String): String {
        val value = requiredText(root, name, expected.length, expected.length)
        require(value == expected) { "body_possession_${name}_invalid" }
        return value
    }

    private fun requiredText(root: org.json.JSONObject, name: String, min: Int, max: Int): String {
        val rawValue = root.get(name)
        require(rawValue is String) { "body_possession_${name}_invalid" }
        val value = rawValue
        GenesisUltraHashProfile.requireNfc(value)
        require(value.length in min..max) { "body_possession_${name}_invalid" }
        return value
    }

    private fun requiredTimestamp(root: org.json.JSONObject, name: String): String {
        val value = requiredText(root, name, 20, 20)
        require(CANONICAL_TIMESTAMP.matches(value)) { "body_possession_${name}_invalid" }
        Instant.parse(value)
        return value
    }

    private fun requiredSha256(root: org.json.JSONObject, name: String): String {
        val value = requiredText(root, name, 71, 71)
        require(SHA256.matches(value)) { "body_possession_${name}_invalid" }
        return value
    }

    private fun requiredLowerHex(root: org.json.JSONObject, name: String, length: Int): String {
        val value = requiredText(root, name, length, length)
        require(LOWER_HEX.matches(value)) { "body_possession_${name}_invalid" }
        return value
    }

    private val PROOF_KEYS = setOf(
        "schema_version", "proof_id", "instance_id", "body_id", "challenge_nonce", "issued_at",
        "expires_at", "public_key_fingerprint", "proof_digest", "signature"
    )
    private val SIGNATURE_KEYS = setOf("profile", "key_epoch_id", "value")
    private val SHA256 = Regex("^sha256:[a-f0-9]{64}$")
    private val LOWER_HEX = Regex("^[a-f0-9]+$")
    private val CANONICAL_TIMESTAMP = Regex(
        "^[0-9]{4}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])T([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]Z$"
    )
}

class GenesisUltraBodyPossessionVerifier {
    fun verify(
        proof: GenesisUltraBodyPossessionProof,
        keyEpoch: GenesisUltraKeyEpoch,
        rawPublicKey: ByteArray,
        evaluatedAt: String
    ): GenesisUltraVerifiedBodyPossession {
        require(keyEpoch.status == "active") { "body_possession_key_epoch_inactive" }
        require(keyEpoch.instanceId == proof.instanceId) { "body_possession_instance_mismatch" }
        require(keyEpoch.bodyId == proof.bodyId) { "body_possession_body_mismatch" }
        require(keyEpoch.keyEpochId == proof.signature.keyEpochId) { "body_possession_key_epoch_mismatch" }
        require(keyEpoch.publicKeyFingerprint == proof.publicKeyFingerprint) {
            "body_possession_fingerprint_mismatch"
        }
        require(GenesisUltraHashProfile.sha256(rawPublicKey) == proof.publicKeyFingerprint) {
            "body_possession_public_key_mismatch"
        }
        val evaluated = Instant.parse(evaluatedAt)
        require(evaluated >= Instant.parse(proof.issuedAt)) { "body_possession_not_yet_valid" }
        require(evaluated < Instant.parse(proof.expiresAt)) { "body_possession_expired" }

        val envelope = GenesisUltraSignatureEnvelope(
            schemaVersion = "genesis.signature.envelope.v0.1",
            signatureProfile = proof.signature.profile,
            signerType = "body",
            signerId = proof.bodyId,
            keyEpochId = proof.signature.keyEpochId,
            signedDomain = BODY_POSSESSION_SIGNATURE_DOMAIN,
            signedDigest = proof.proofDigest,
            signatureValue = proof.signature.value,
            createdAt = proof.issuedAt,
            publicKeyRef = proof.publicKeyFingerprint
        )
        val verifier = GenesisUltraEd25519SignatureVerifier(
            listOf(
                GenesisUltraTrustedEd25519Key(
                    signerType = envelope.signerType,
                    signerId = envelope.signerId,
                    keyEpochId = envelope.keyEpochId,
                    publicKeyRef = envelope.publicKeyRef,
                    rawPublicKey = rawPublicKey
                )
            )
        )
        require(verifier.verify(envelope, GenesisUltraHashProfile.signatureEnvelopePreimage(envelope))) {
            "body_possession_signature_invalid"
        }
        return GenesisUltraVerifiedBodyPossession(proof = proof, verifiedAt = evaluatedAt)
    }

    companion object {
        const val BODY_POSSESSION_SIGNATURE_DOMAIN = "genesis.body.possession.signature.v0.1"
    }
}
