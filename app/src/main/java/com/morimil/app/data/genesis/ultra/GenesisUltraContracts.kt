package com.morimil.app.data.genesis.ultra

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.time.Instant
import java.time.format.DateTimeParseException


data class GenesisUltraSeedFileRecord(
    val path: String,
    val kind: String,
    val required: Boolean,
    val digest: String
)

data class GenesisUltraSeedManifest(
    val schemaVersion: String,
    val protocolVersion: String,
    val hashProfile: String,
    val seedId: String,
    val identityDigest: String,
    val doctrineDigest: String,
    val files: List<GenesisUltraSeedFileRecord>,
    val rootHash: String
)

data class GenesisUltraSignatureEnvelope(
    val schemaVersion: String,
    val signatureProfile: String,
    val signerType: String,
    val signerId: String,
    val keyEpochId: String,
    val signedDomain: String,
    val signedDigest: String,
    val signatureValue: String,
    val createdAt: String,
    val publicKeyRef: String
)

data class GenesisUltraInstanceIdentity(
    val schemaVersion: String,
    val instanceId: String,
    val seedId: String,
    val seedRootHash: String,
    val companionName: String,
    val guardianId: String,
    val bornAt: String,
    val identityDigest: String
)

data class GenesisUltraBodyRecord(
    val schemaVersion: String,
    val instanceId: String,
    val bodyId: String,
    val status: String,
    val createdAt: String,
    val platformProfile: String,
    val publicKeyFingerprint: String,
    val revokedAt: String?,
    val revocationReason: String?
)

data class GenesisUltraRegisteredBody(
    val bodyId: String,
    val status: String,
    val platformProfile: String,
    val publicKeyFingerprint: String,
    val createdAt: String,
    val lastSeenAt: String?,
    val revocationRef: String?
)

data class GenesisUltraBodyRegistry(
    val schemaVersion: String,
    val instanceId: String,
    val registryEpoch: Long,
    val bodies: List<GenesisUltraRegisteredBody>,
    val updatedAt: String,
    val registryDigest: String
)

data class GenesisUltraKeyEpoch(
    val schemaVersion: String,
    val keyEpochId: String,
    val instanceId: String,
    val bodyId: String,
    val epochNumber: Long,
    val publicKeyFingerprint: String,
    val createdAt: String,
    val status: String,
    val previousEpochId: String?,
    val rotationAuthorizationRef: String?,
    val epochDigest: String,
    val signature: GenesisUltraSignatureEnvelope?
)

object GenesisUltraContractParser {
    fun parseSeedManifest(jsonText: String): GenesisUltraSeedManifest {
        val root = GenesisUltraStrictJson.parseObject(jsonText)
        root.requireExactKeys(SEED_MANIFEST_KEYS)
        val schemaVersion = root.requiredConst("schema_version", SEED_MANIFEST_SCHEMA)
        val protocolVersion = root.requiredConst("protocol_version", PROTOCOL_VERSION)
        val hashProfile = root.requiredConst("hash_profile", GenesisUltraHashProfile.FIELD_PROFILE)
        val seedId = root.requiredText("seed_id", minLength = 16, maxLength = 128)
        val identityDigest = root.requiredSha256("identity_digest")
        val doctrineDigest = root.requiredSha256("doctrine_digest")
        val filesArray = root.getJSONArray("files")
        require(filesArray.length() >= 2) { "seed_manifest_files_too_short" }
        val files = List(filesArray.length()) { index -> parseSeedFile(filesArray.getJSONObject(index)) }
        val paths = files.map { file -> file.path }
        require(paths.distinct().size == paths.size) { "duplicate_seed_path" }
        require(files.any { file -> file.kind == "identity" && file.digest == identityDigest }) {
            "identity_digest_not_bound_to_file"
        }
        require(files.any { file -> file.kind == "doctrine" && file.digest == doctrineDigest }) {
            "doctrine_digest_not_bound_to_file"
        }
        val rootHash = root.requiredSha256("root_hash")
        return GenesisUltraSeedManifest(
            schemaVersion = schemaVersion,
            protocolVersion = protocolVersion,
            hashProfile = hashProfile,
            seedId = seedId,
            identityDigest = identityDigest,
            doctrineDigest = doctrineDigest,
            files = files,
            rootHash = rootHash
        )
    }

    fun parseSignatureEnvelope(jsonText: String): GenesisUltraSignatureEnvelope {
        return parseSignatureEnvelope(GenesisUltraStrictJson.parseObject(jsonText))
    }

    fun parseInstanceIdentity(jsonText: String): GenesisUltraInstanceIdentity {
        val root = GenesisUltraStrictJson.parseObject(jsonText)
        root.requireExactKeys(INSTANCE_IDENTITY_KEYS)
        val identity = GenesisUltraInstanceIdentity(
            schemaVersion = root.requiredConst("schema_version", INSTANCE_IDENTITY_SCHEMA),
            instanceId = root.requiredText("instance_id", minLength = 16),
            seedId = root.requiredText("seed_id", minLength = 16),
            seedRootHash = root.requiredSha256("seed_root_hash"),
            companionName = root.requiredText("companion_name", minLength = 1, maxLength = 128),
            guardianId = root.requiredText("guardian_id", minLength = 1),
            bornAt = root.requiredCanonicalTimestamp("born_at"),
            identityDigest = root.requiredSha256("identity_digest")
        )
        require(GenesisUltraHashProfile.instanceIdentityDigest(identity) == identity.identityDigest) {
            "identity_digest_mismatch"
        }
        return identity
    }

    fun parseBodyRecord(jsonText: String): GenesisUltraBodyRecord {
        val root = GenesisUltraStrictJson.parseObject(jsonText)
        root.requireAllowedAndRequiredKeys(BODY_RECORD_KEYS, BODY_RECORD_REQUIRED_KEYS)
        return GenesisUltraBodyRecord(
            schemaVersion = root.requiredConst("schema_version", BODY_RECORD_SCHEMA),
            instanceId = root.requiredText("instance_id", minLength = 16),
            bodyId = root.requiredText("body_id", minLength = 16),
            status = root.requiredEnum("status", BODY_STATUSES),
            createdAt = root.requiredCanonicalTimestamp("created_at"),
            platformProfile = root.requiredText("platform_profile", minLength = 1),
            publicKeyFingerprint = root.requiredText("public_key_fingerprint", minLength = 16),
            revokedAt = root.optionalCanonicalTimestamp("revoked_at"),
            revocationReason = root.optionalText("revocation_reason")
        )
    }

    fun parseBodyRegistry(jsonText: String): GenesisUltraBodyRegistry {
        val root = GenesisUltraStrictJson.parseObject(jsonText)
        root.requireExactKeys(BODY_REGISTRY_KEYS)
        val bodiesArray = root.getJSONArray("bodies")
        require(bodiesArray.length() >= 1) { "body_registry_empty" }
        val bodies = List(bodiesArray.length()) { index -> parseRegisteredBody(bodiesArray.getJSONObject(index)) }
        val ids = bodies.map { body -> body.bodyId }
        require(ids.distinct().size == ids.size) { "duplicate_body_id" }
        require(bodies.count { body -> body.status == "active_writer" } <= 1) { "multiple_active_writers" }
        val registry = GenesisUltraBodyRegistry(
            schemaVersion = root.requiredConst("schema_version", BODY_REGISTRY_SCHEMA),
            instanceId = root.requiredText("instance_id", minLength = 16, maxLength = 128),
            registryEpoch = root.requiredPortableNonNegativeLong("registry_epoch"),
            bodies = bodies,
            updatedAt = root.requiredCanonicalTimestamp("updated_at"),
            registryDigest = root.requiredSha256("registry_digest")
        )
        require(GenesisUltraHashProfile.bodyRegistryDigest(registry) == registry.registryDigest) {
            "body_registry_digest_mismatch"
        }
        return registry
    }

    fun parseKeyEpoch(jsonText: String): GenesisUltraKeyEpoch {
        val root = GenesisUltraStrictJson.parseObject(jsonText)
        root.requireAllowedAndRequiredKeys(KEY_EPOCH_KEYS, KEY_EPOCH_REQUIRED_KEYS)
        val epoch = GenesisUltraKeyEpoch(
            schemaVersion = root.requiredConst("schema_version", KEY_EPOCH_SCHEMA),
            keyEpochId = root.requiredText("key_epoch_id", minLength = 16, maxLength = 128),
            instanceId = root.requiredText("instance_id", minLength = 16, maxLength = 128),
            bodyId = root.requiredText("body_id", minLength = 16, maxLength = 128),
            epochNumber = root.requiredPortableNonNegativeLong("epoch_number"),
            publicKeyFingerprint = root.requiredText("public_key_fingerprint", minLength = 16, maxLength = 256),
            createdAt = root.requiredCanonicalTimestamp("created_at"),
            status = root.requiredEnum("status", KEY_EPOCH_STATUSES),
            previousEpochId = root.optionalText("previous_epoch_id", maxLength = 128),
            rotationAuthorizationRef = root.optionalText("rotation_authorization_ref", maxLength = 256),
            epochDigest = root.requiredSha256("epoch_digest"),
            signature = root.optionalObject("signature")?.let(::parseSignatureEnvelope)
        )
        require(GenesisUltraHashProfile.keyEpochDigest(epoch) == epoch.epochDigest) {
            "key_epoch_digest_mismatch"
        }
        return epoch
    }

    internal fun parseSignatureEnvelope(root: JSONObject): GenesisUltraSignatureEnvelope {
        root.requireExactKeys(SIGNATURE_ENVELOPE_KEYS)
        return GenesisUltraSignatureEnvelope(
            schemaVersion = root.requiredConst("schema_version", SIGNATURE_ENVELOPE_SCHEMA),
            signatureProfile = root.requiredConst("signature_profile", SIGNATURE_PROFILE),
            signerType = root.requiredEnum("signer_type", SIGNER_TYPES),
            signerId = root.requiredText("signer_id", minLength = 1, maxLength = 128),
            keyEpochId = root.requiredText("key_epoch_id", minLength = 16, maxLength = 128),
            signedDomain = root.requiredText("signed_domain", minLength = 1, maxLength = 256),
            signedDigest = root.requiredSignedDigest("signed_digest"),
            signatureValue = root.requiredLowerHex("signature_value", exactLength = 128),
            createdAt = root.requiredCanonicalTimestamp("created_at"),
            publicKeyRef = root.requiredSha256("public_key_ref")
        )
    }

    private fun parseSeedFile(root: JSONObject): GenesisUltraSeedFileRecord {
        root.requireExactKeys(SEED_FILE_KEYS)
        val path = root.requiredText("path", minLength = 1, maxLength = 1024)
        GenesisUltraHashProfile.requireSafeRelativePath(path)
        return GenesisUltraSeedFileRecord(
            path = path,
            kind = root.requiredEnum("kind", SEED_FILE_KINDS),
            required = root.requiredBoolean("required"),
            digest = root.requiredSha256("digest")
        )
    }

    private fun parseRegisteredBody(root: JSONObject): GenesisUltraRegisteredBody {
        root.requireAllowedAndRequiredKeys(REGISTERED_BODY_KEYS, REGISTERED_BODY_REQUIRED_KEYS)
        return GenesisUltraRegisteredBody(
            bodyId = root.requiredText("body_id", minLength = 16, maxLength = 128),
            status = root.requiredEnum("status", BODY_STATUSES),
            platformProfile = root.requiredText("platform_profile", minLength = 1, maxLength = 128),
            publicKeyFingerprint = root.requiredText("public_key_fingerprint", minLength = 16, maxLength = 256),
            createdAt = root.requiredCanonicalTimestamp("created_at"),
            lastSeenAt = root.optionalCanonicalTimestamp("last_seen_at"),
            revocationRef = root.optionalText("revocation_ref", maxLength = 256)
        )
    }

    private fun JSONObject.requireExactKeys(expected: Set<String>) {
        val actual = keyNames()
        require(actual == expected) { "unexpected_or_missing_fields:expected=${expected.sorted()}:actual=${actual.sorted()}" }
    }

    private fun JSONObject.requireAllowedAndRequiredKeys(allowed: Set<String>, required: Set<String>) {
        val actual = keyNames()
        require(actual.all { key -> key in allowed }) { "unexpected_fields:${actual.minus(allowed).sorted()}" }
        require(actual.containsAll(required)) { "missing_fields:${required.minus(actual).sorted()}" }
    }

    private fun JSONObject.keyNames(): Set<String> = buildSet {
        val iterator = keys()
        while (iterator.hasNext()) add(iterator.next())
    }

    private fun JSONObject.requiredConst(name: String, expected: String): String {
        val value = requiredText(name, minLength = expected.length, maxLength = expected.length)
        require(value == expected) { "unsupported_$name:$value" }
        return value
    }

    private fun JSONObject.requiredText(
        name: String,
        minLength: Int,
        maxLength: Int = Int.MAX_VALUE
    ): String {
        val rawValue = get(name)
        require(rawValue is String) { "invalid_$name" }
        val value = rawValue
        GenesisUltraHashProfile.requireNfc(value)
        require(value.length in minLength..maxLength) { "invalid_$name" }
        return value
    }

    private fun JSONObject.optionalText(name: String, maxLength: Int = Int.MAX_VALUE): String? {
        if (!has(name) || isNull(name)) return null
        return requiredText(name, minLength = 0, maxLength = maxLength)
    }

    private fun JSONObject.optionalObject(name: String): JSONObject? {
        if (!has(name) || isNull(name)) return null
        return getJSONObject(name)
    }

    private fun JSONObject.requiredBoolean(name: String): Boolean {
        val rawValue = get(name)
        require(rawValue is Boolean) { "invalid_$name" }
        return rawValue
    }

    private fun JSONObject.requiredEnum(name: String, values: Set<String>): String {
        val value = requiredText(name, minLength = 1)
        require(value in values) { "invalid_$name:$value" }
        return value
    }

    private fun JSONObject.requiredSha256(name: String): String {
        val value = requiredText(name, minLength = 71, maxLength = 71)
        require(SHA256.matches(value)) { "invalid_$name" }
        return value
    }

    private fun JSONObject.requiredSignedDigest(name: String): String {
        val value = requiredText(name, minLength = 71, maxLength = 73)
        require(SIGNED_DIGEST.matches(value)) { "invalid_$name" }
        return value
    }

    private fun JSONObject.requiredLowerHex(name: String, exactLength: Int): String {
        val value = requiredText(name, minLength = exactLength, maxLength = exactLength)
        require(LOWER_HEX.matches(value)) { "invalid_$name" }
        return value
    }

    private fun JSONObject.requiredCanonicalTimestamp(name: String): String {
        val value = requiredText(name, minLength = 20, maxLength = 20)
        require(CANONICAL_TIMESTAMP.matches(value)) { "invalid_$name" }
        try {
            Instant.parse(value)
        } catch (_: DateTimeParseException) {
            throw IllegalArgumentException("invalid_$name")
        }
        return value
    }

    private fun JSONObject.optionalCanonicalTimestamp(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return requiredCanonicalTimestamp(name)
    }

    private fun JSONObject.requiredPortableNonNegativeLong(name: String): Long {
        val rawValue = get(name)
        require(rawValue is Number) { "invalid_$name" }
        val decimal = try {
            BigDecimal(rawValue.toString())
        } catch (_: NumberFormatException) {
            throw IllegalArgumentException("invalid_$name")
        }
        val value = try {
            decimal.longValueExact()
        } catch (_: ArithmeticException) {
            throw IllegalArgumentException("invalid_$name")
        }
        require(value in 0..MAX_SAFE_INTEGER) { "invalid_$name" }
        return value
    }

    private const val MAX_SAFE_INTEGER = 9_007_199_254_740_991L
    private const val PROTOCOL_VERSION = "genesis.protocol.v0.1"
    private const val SEED_MANIFEST_SCHEMA = "genesis.seed.manifest.v0.1"
    private const val SIGNATURE_ENVELOPE_SCHEMA = "genesis.signature.envelope.v0.1"
    private const val SIGNATURE_PROFILE = "genesis.signature.ed25519.v0.1"
    private const val INSTANCE_IDENTITY_SCHEMA = "genesis.instance.identity.v0.1"
    private const val BODY_RECORD_SCHEMA = "genesis.body.record.v0.1"
    private const val BODY_REGISTRY_SCHEMA = "genesis.body.registry.v0.1"
    private const val KEY_EPOCH_SCHEMA = "genesis.key.epoch.v0.1"

    private val SHA256 = Regex("^sha256:[a-f0-9]{64}$")
    private val SIGNED_DIGEST = Regex("^(sha256|evsha256):[a-f0-9]{64}$")
    private val LOWER_HEX = Regex("^[a-f0-9]+$")
    private val CANONICAL_TIMESTAMP = Regex(
        "^[0-9]{4}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])T([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]Z$"
    )

    private val SEED_FILE_KINDS = setOf("identity", "doctrine", "policy", "contract", "ontology", "other")
    private val SIGNER_TYPES = setOf("guardian", "body", "recovery_authority")
    private val BODY_STATUSES = setOf("candidate", "active_writer", "read_only", "suspended", "revoked", "lost")
    private val KEY_EPOCH_STATUSES = setOf("active", "retired", "revoked", "compromised")

    private val SEED_MANIFEST_KEYS = setOf(
        "schema_version", "protocol_version", "hash_profile", "seed_id", "identity_digest",
        "doctrine_digest", "files", "root_hash"
    )
    private val SEED_FILE_KEYS = setOf("path", "kind", "required", "digest")
    private val SIGNATURE_ENVELOPE_KEYS = setOf(
        "schema_version", "signature_profile", "signer_type", "signer_id", "key_epoch_id",
        "signed_domain", "signed_digest", "signature_value", "created_at", "public_key_ref"
    )
    private val INSTANCE_IDENTITY_KEYS = setOf(
        "schema_version", "instance_id", "seed_id", "seed_root_hash", "companion_name",
        "guardian_id", "born_at", "identity_digest"
    )
    private val BODY_RECORD_KEYS = setOf(
        "schema_version", "instance_id", "body_id", "status", "created_at", "platform_profile",
        "public_key_fingerprint", "revoked_at", "revocation_reason"
    )
    private val BODY_RECORD_REQUIRED_KEYS = setOf(
        "schema_version", "instance_id", "body_id", "status", "created_at", "platform_profile",
        "public_key_fingerprint"
    )
    private val BODY_REGISTRY_KEYS = setOf(
        "schema_version", "instance_id", "registry_epoch", "bodies", "updated_at", "registry_digest"
    )
    private val REGISTERED_BODY_KEYS = setOf(
        "body_id", "status", "platform_profile", "public_key_fingerprint", "created_at",
        "last_seen_at", "revocation_ref"
    )
    private val REGISTERED_BODY_REQUIRED_KEYS = setOf(
        "body_id", "status", "platform_profile", "public_key_fingerprint", "created_at"
    )
    private val KEY_EPOCH_KEYS = setOf(
        "schema_version", "key_epoch_id", "instance_id", "body_id", "epoch_number",
        "public_key_fingerprint", "created_at", "status", "previous_epoch_id",
        "rotation_authorization_ref", "epoch_digest", "signature"
    )
    private val KEY_EPOCH_REQUIRED_KEYS = setOf(
        "schema_version", "key_epoch_id", "instance_id", "body_id", "epoch_number",
        "public_key_fingerprint", "created_at", "status", "epoch_digest"
    )
}
