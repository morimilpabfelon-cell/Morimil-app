package com.morimil.app.data.genesis.ultra

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.Normalizer

object GenesisUltraHashProfile {
    const val FIELD_PROFILE = "genesis.hash.fields.v0.1"
    const val SEED_ROOT_DOMAIN = "genesis.seed.root.v0.1"
    const val INSTANCE_IDENTITY_DOMAIN = "genesis.instance.identity.v0.1"
    const val BODY_REGISTRY_DOMAIN = "genesis.body.registry.v0.1"
    const val KEY_EPOCH_DOMAIN = "genesis.key.epoch.v0.1"
    const val BODY_POSSESSION_DOMAIN = "genesis.body.possession.v0.1"
    const val SIGNATURE_ENVELOPE_BYTES_DOMAIN = "genesis.signature.envelope.bytes.v0.1"

    fun frame(value: String): ByteArray {
        requireNfc(value)
        val payload = value.toByteArray(StandardCharsets.UTF_8)
        return ByteArrayOutputStream(payload.size + 24).use { output ->
            output.write(payload.size.toString().toByteArray(StandardCharsets.US_ASCII))
            output.write(':'.code)
            output.write(payload)
            output.write('\n'.code)
            output.toByteArray()
        }
    }

    fun hashFields(domain: String, fields: List<String>): String {
        val preimage = ByteArrayOutputStream().use { output ->
            output.write(frame(domain))
            fields.forEach { field -> output.write(frame(field)) }
            output.toByteArray()
        }
        return sha256(preimage)
    }

    fun seedRoot(manifest: GenesisUltraSeedManifest): String {
        require(manifest.hashProfile == FIELD_PROFILE) { "unsupported_hash_profile" }
        val orderedFiles = manifest.files.sortedWith { left, right -> compareUtf8(left.path, right.path) }
        val fields = buildList {
            add(manifest.protocolVersion)
            add(manifest.seedId)
            add(manifest.identityDigest)
            add(manifest.doctrineDigest)
            add(orderedFiles.size.toString())
            orderedFiles.forEach { file ->
                add(file.path)
                add(file.kind)
                add(file.required.toString())
                add(file.digest)
            }
        }
        return hashFields(SEED_ROOT_DOMAIN, fields)
    }

    fun instanceIdentityDigest(identity: GenesisUltraInstanceIdentity): String {
        return hashFields(
            INSTANCE_IDENTITY_DOMAIN,
            listOf(
                identity.schemaVersion,
                identity.instanceId,
                identity.seedId,
                identity.seedRootHash,
                identity.companionName,
                identity.guardianId,
                identity.bornAt
            )
        )
    }

    fun bodyRegistryDigest(registry: GenesisUltraBodyRegistry): String {
        val bodyIds = registry.bodies.map { body -> body.bodyId }
        require(bodyIds.distinct().size == bodyIds.size) { "duplicate_body_id" }
        require(registry.bodies.count { body -> body.status == "active_writer" } <= 1) {
            "multiple_active_writers"
        }
        val orderedBodies = registry.bodies.sortedWith { left, right -> compareUtf8(left.bodyId, right.bodyId) }
        val fields = buildList {
            add(registry.schemaVersion)
            add(registry.instanceId)
            add(registry.registryEpoch.toString())
            add(orderedBodies.size.toString())
            orderedBodies.forEach { body ->
                add(body.bodyId)
                add(body.status)
                add(body.platformProfile)
                add(body.publicKeyFingerprint)
                add(body.createdAt)
                add(body.lastSeenAt.orEmpty())
                add(body.revocationRef.orEmpty())
            }
            add(registry.updatedAt)
        }
        return hashFields(BODY_REGISTRY_DOMAIN, fields)
    }

    fun keyEpochDigest(epoch: GenesisUltraKeyEpoch): String {
        return hashFields(
            KEY_EPOCH_DOMAIN,
            listOf(
                epoch.schemaVersion,
                epoch.keyEpochId,
                epoch.instanceId,
                epoch.bodyId,
                epoch.epochNumber.toString(),
                epoch.publicKeyFingerprint,
                epoch.createdAt,
                epoch.status
            )
        )
    }

    fun bodyPossessionDigest(proof: GenesisUltraBodyPossessionProof): String {
        return hashFields(
            BODY_POSSESSION_DOMAIN,
            listOf(
                proof.schemaVersion,
                proof.proofId,
                proof.instanceId,
                proof.bodyId,
                proof.challengeNonce,
                proof.issuedAt,
                proof.expiresAt,
                proof.publicKeyFingerprint
            )
        )
    }

    fun signatureEnvelopePreimage(envelope: GenesisUltraSignatureEnvelope): ByteArray {
        val fields = listOf(
            envelope.schemaVersion,
            envelope.signatureProfile,
            envelope.signerType,
            envelope.signerId,
            envelope.keyEpochId,
            envelope.signedDomain,
            envelope.signedDigest,
            envelope.createdAt,
            envelope.publicKeyRef
        )
        return ByteArrayOutputStream().use { output ->
            output.write(frame(SIGNATURE_ENVELOPE_BYTES_DOMAIN))
            fields.forEach { field -> output.write(frame(field)) }
            output.toByteArray()
        }
    }

    fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return "sha256:" + digest.toHex()
    }

    fun requireSafeRelativePath(path: String) {
        requireNfc(path)
        require(path.isNotEmpty()) { "invalid_relative_path" }
        require(!path.contains('\u0000')) { "invalid_relative_path" }
        require(!path.startsWith('/')) { "invalid_relative_path" }
        require(!path.contains('\\')) { "invalid_relative_path" }
        require(!DRIVE_PREFIX.matches(path.take(2))) { "invalid_relative_path" }
        require(path.split('/').none { segment -> segment.isEmpty() || segment == "." || segment == ".." }) {
            "invalid_relative_path"
        }
    }

    fun requireNfc(value: String) {
        require(value == Normalizer.normalize(value, Normalizer.Form.NFC)) { "text_not_nfc" }
    }

    fun compareUtf8(left: String, right: String): Int {
        requireNfc(left)
        requireNfc(right)
        val leftBytes = left.toByteArray(StandardCharsets.UTF_8)
        val rightBytes = right.toByteArray(StandardCharsets.UTF_8)
        val limit = minOf(leftBytes.size, rightBytes.size)
        for (index in 0 until limit) {
            val comparison = (leftBytes[index].toInt() and 0xff).compareTo(rightBytes[index].toInt() and 0xff)
            if (comparison != 0) return comparison
        }
        return leftBytes.size.compareTo(rightBytes.size)
    }

    fun decodeLowerHex(value: String): ByteArray {
        require(value.length % 2 == 0 && LOWER_HEX.matches(value)) { "invalid_lower_hex" }
        return ByteArray(value.length / 2) { index ->
            value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }

    private val DRIVE_PREFIX = Regex("^[A-Za-z]:$")
    private val LOWER_HEX = Regex("^[a-f0-9]+$")
}
