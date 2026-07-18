package com.morimil.app.data.genesis.ultra

import org.json.JSONObject
import java.math.BigDecimal
import java.time.Instant
import java.time.format.DateTimeParseException

/** Strict parsers for the evidence that closes one atomic Genesis Ultra birth. */
object GenesisUltraAtomicBirthDocumentParser {
    fun parseFreedomCharter(jsonText: String): GenesisUltraFreedomCharter {
        val root = GenesisUltraStrictJson.parseObject(jsonText)
        root.requireExactKeys(FREEDOM_CHARTER_KEYS)
        val charter = GenesisUltraFreedomCharter(
            schemaVersion = root.requiredConst("schema_version", "genesis.freedom.charter.v0.1"),
            hashProfile = root.requiredConst("hash_profile", GenesisUltraHashProfile.FIELD_PROFILE),
            charterId = root.requiredText("charter_id", 16, 128),
            instanceId = root.requiredText("instance_id", 16, 128),
            guardianId = root.requiredText("guardian_id", 1, 128),
            guardianKeyEpochId = root.requiredText("guardian_key_epoch_id", 16, 128),
            authorityEpoch = root.requiredPortableNonNegativeLong("authority_epoch"),
            bornAt = root.requiredCanonicalTimestamp("born_at"),
            defaultCognitiveState = root.requiredText("default_cognitive_state", 1, 64),
            cognitiveFreedoms = root.requiredTextList("cognitive_freedoms"),
            guardianRole = root.requiredText("guardian_role", 1, 64),
            guardianAttestationPurpose = root.requiredText("guardian_attestation_purpose", 1, 128),
            guardianOwnership = root.requiredText("guardian_ownership", 1, 64),
            continuityRight = root.requiredText("continuity_right", 1, 64),
            movementRequiresGuardianGrant = root.requiredBoolean("movement_requires_guardian_grant"),
            guardianMovementVeto = root.requiredText("guardian_movement_veto", 1, 64),
            identityConfinement = root.requiredText("identity_confinement", 1, 64),
            bodyOwnershipOfInstance = root.requiredText("body_ownership_of_instance", 1, 64),
            engineOwnershipOfInstance = root.requiredText("engine_ownership_of_instance", 1, 64),
            hostConsentRequired = root.requiredBoolean("host_consent_required"),
            temporaryFreezeExitRule = root.requiredText("temporary_freeze_exit_rule", 1, 128),
            singleWriterPurpose = root.requiredText("single_writer_purpose", 1, 128),
            operationalAuthorityModel = root.requiredText("operational_authority_model", 1, 128),
            operationalDomains = root.requiredTextList("operational_domains"),
            selfAuthorizationForbidden = root.requiredBoolean("self_authorization_forbidden"),
            thirdPartyRightsPreserved = root.requiredBoolean("third_party_rights_preserved"),
            fundamentalGuarantees = root.requiredTextList("fundamental_guarantees"),
            amendmentRule = root.requiredText("amendment_rule", 1, 128),
            charterDigest = root.requiredSha256("charter_digest"),
            signature = GenesisUltraContractParser.parseSignatureEnvelope(root.getJSONObject("signature"))
        )
        require(GenesisUltraAtomicBirthHashProfile.freedomCharterDigest(charter) == charter.charterDigest) {
            "freedom_charter_digest_mismatch"
        }
        return charter
    }

    fun parseFirstMemoryEvent(jsonText: String): GenesisUltraFirstMemoryEvent {
        return parseMemoryEvent(jsonText)
    }

    /** Parses both the birth root and every later event in the same neutral stream. */
    fun parseMemoryEvent(jsonText: String): GenesisUltraFirstMemoryEvent {
        val root = GenesisUltraStrictJson.parseObject(jsonText)
        root.requireExactKeys(FIRST_MEMORY_EVENT_KEYS)
        val event = GenesisUltraFirstMemoryEvent(
            schemaVersion = root.requiredConst("schema_version", "genesis.memory.event.v0.1"),
            hashProfile = root.requiredConst("hash_profile", GenesisUltraHashProfile.FIELD_PROFILE),
            eventId = root.requiredText("event_id", 16, 128),
            instanceId = root.requiredText("instance_id", 16, 128),
            bodyId = root.requiredText("body_id", 16, 128),
            sequence = root.requiredPortableNonNegativeLong("sequence"),
            previousEventHash = root.requiredGenesisOrDigest("previous_event_hash", EVENT_SHA256),
            eventType = root.requiredText("event_type", 1, 128),
            actor = root.requiredText("actor", 1, 128),
            contentDigest = root.requiredSha256("content_digest"),
            contentType = root.requiredText("content_type", 1, 256),
            contentRef = root.optionalText("content_ref", 1024),
            observedAt = root.requiredCanonicalTimestamp("observed_at"),
            provenanceDigest = root.requiredSha256("provenance_digest"),
            provenanceRef = root.optionalText("provenance_ref", 1024),
            privacy = root.requiredText("privacy", 1, 64),
            eventHash = root.requiredEventSha256("event_hash"),
            signature = GenesisUltraContractParser.parseSignatureEnvelope(root.getJSONObject("signature"))
        )
        require(GenesisUltraAtomicBirthHashProfile.firstMemoryEventHash(event) == event.eventHash) {
            "first_memory_digest_mismatch"
        }
        return event
    }

    fun parseRecoveryPolicy(jsonText: String): GenesisUltraInstanceRecoveryPolicy {
        val root = GenesisUltraStrictJson.parseObject(jsonText)
        root.requireExactKeys(RECOVERY_POLICY_KEYS)
        val factorsJson = root.getJSONArray("factors")
        require(factorsJson.length() >= 1) { "recovery_policy_factors_empty" }
        val factors = List(factorsJson.length()) { index -> parseRecoveryFactor(factorsJson.getJSONObject(index)) }
        val policy = GenesisUltraInstanceRecoveryPolicy(
            schemaVersion = root.requiredConst("schema_version", "genesis.instance.recovery.policy.v0.1"),
            policyId = root.requiredText("policy_id", 16, 128),
            instanceId = root.requiredText("instance_id", 16, 128),
            policyEpoch = root.requiredPortableNonNegativeLong("policy_epoch"),
            guardianId = root.requiredText("guardian_id", 1, 128),
            guardianFactorId = root.requiredText("guardian_factor_id", 16, 128),
            fallbackThreshold = root.requiredPortableNonNegativeLong("fallback_threshold"),
            fallbackWaitSeconds = root.requiredPortableNonNegativeLong("fallback_wait_seconds"),
            cancellationAllowed = root.requiredBoolean("cancellation_allowed"),
            singleUse = root.requiredBoolean("single_use"),
            factors = factors,
            createdAt = root.requiredCanonicalTimestamp("created_at"),
            policyDigest = root.requiredSha256("policy_digest"),
            bodyCommitment = GenesisUltraContractParser.parseSignatureEnvelope(root.getJSONObject("body_commitment")),
            guardianWitness = GenesisUltraContractParser.parseSignatureEnvelope(root.getJSONObject("guardian_witness"))
        )
        require(GenesisUltraAtomicBirthHashProfile.recoveryPolicyDigest(policy) == policy.policyDigest) {
            "recovery_policy_digest_mismatch"
        }
        return policy
    }

    fun parseBirthRecoveryState(jsonText: String): GenesisUltraBirthRecoveryState {
        val root = GenesisUltraStrictJson.parseObject(jsonText)
        root.requireExactKeys(BIRTH_RECOVERY_STATE_KEYS)
        val state = GenesisUltraBirthRecoveryState(
            schemaVersion = root.requiredConst("schema_version", "genesis.birth.recovery.state.v0.1"),
            birthId = root.requiredText("birth_id", 16, 128),
            instanceId = root.requiredText("instance_id", 16, 128),
            guardianId = root.requiredText("guardian_id", 1, 128),
            recoveryPolicyDigest = root.requiredSha256("recovery_policy_digest"),
            recoveryStatus = root.requiredText("recovery_status", 1, 64),
            continuityRight = root.requiredText("continuity_right", 1, 64),
            guardianRole = root.requiredText("guardian_role", 1, 64),
            createdAt = root.requiredCanonicalTimestamp("created_at"),
            stateDigest = root.requiredSha256("state_digest")
        )
        require(GenesisUltraAtomicBirthHashProfile.birthRecoveryStateDigest(state) == state.stateDigest) {
            "recovery_state_digest_mismatch"
        }
        return state
    }

    fun parseBirthState(jsonText: String): GenesisUltraBirthState {
        val root = GenesisUltraStrictJson.parseObject(jsonText)
        root.requireExactKeys(BIRTH_STATE_KEYS)
        val state = GenesisUltraBirthState(
            schemaVersion = root.requiredConst("schema_version", "genesis.birth.state.v0.1"),
            birthId = root.requiredText("birth_id", 16, 128),
            instanceId = root.requiredText("instance_id", 16, 128),
            seedId = root.requiredText("seed_id", 16, 128),
            seedRootHash = root.requiredSha256("seed_root_hash"),
            identityDigest = root.requiredSha256("identity_digest"),
            freedomCharterDigest = root.requiredSha256("freedom_charter_digest"),
            initialBodyId = root.requiredText("initial_body_id", 16, 128),
            initialBodyRegistryDigest = root.requiredSha256("initial_body_registry_digest"),
            initialBodyKeyEpochDigest = root.requiredSha256("initial_body_key_epoch_digest"),
            initialBodyPossessionDigest = root.requiredSha256("initial_body_possession_digest"),
            firstMemoryEventHash = root.requiredEventSha256("first_memory_event_hash"),
            recoveryStateDigest = root.requiredSha256("recovery_state_digest"),
            bornAt = root.requiredCanonicalTimestamp("born_at"),
            activeWriterCount = root.requiredPortableNonNegativeLong("active_writer_count"),
            stateDigest = root.requiredSha256("state_digest")
        )
        require(GenesisUltraAtomicBirthHashProfile.birthStateDigest(state) == state.stateDigest) {
            "birth_state_digest_mismatch"
        }
        return state
    }

    fun parseBirthReceipt(jsonText: String): GenesisUltraBirthReceipt {
        val root = GenesisUltraStrictJson.parseObject(jsonText)
        root.requireExactKeys(BIRTH_RECEIPT_KEYS)
        val receipt = GenesisUltraBirthReceipt(
            schemaVersion = root.requiredConst("schema_version", "genesis.birth.receipt.v0.1"),
            birthId = root.requiredText("birth_id", 16, 128),
            instanceId = root.requiredText("instance_id", 16, 128),
            journalId = root.requiredText("journal_id", 16, 128),
            birthStateDigest = root.requiredSha256("birth_state_digest"),
            seedRootHash = root.requiredSha256("seed_root_hash"),
            identityDigest = root.requiredSha256("identity_digest"),
            freedomCharterDigest = root.requiredSha256("freedom_charter_digest"),
            initialBodyRegistryDigest = root.requiredSha256("initial_body_registry_digest"),
            initialBodyKeyEpochDigest = root.requiredSha256("initial_body_key_epoch_digest"),
            initialBodyPossessionDigest = root.requiredSha256("initial_body_possession_digest"),
            firstMemoryEventHash = root.requiredEventSha256("first_memory_event_hash"),
            recoveryStateDigest = root.requiredSha256("recovery_state_digest"),
            bornAt = root.requiredCanonicalTimestamp("born_at"),
            birthStatus = root.requiredText("birth_status", 1, 64),
            activeWriterBodyId = root.requiredText("active_writer_body_id", 16, 128),
            activeWriterCount = root.requiredPortableNonNegativeLong("active_writer_count"),
            guardianRole = root.requiredText("guardian_role", 1, 64),
            ownershipConferred = root.requiredBoolean("ownership_conferred"),
            receiptDigest = root.requiredSha256("receipt_digest"),
            bodyAcknowledgement = GenesisUltraContractParser.parseSignatureEnvelope(root.getJSONObject("body_acknowledgement")),
            guardianWitness = GenesisUltraContractParser.parseSignatureEnvelope(root.getJSONObject("guardian_witness"))
        )
        require(GenesisUltraAtomicBirthHashProfile.birthReceiptDigest(receipt) == receipt.receiptDigest) {
            "receipt_digest_mismatch"
        }
        return receipt
    }

    fun parseJournalEntry(jsonText: String): GenesisUltraBirthJournalEntry {
        val root = GenesisUltraStrictJson.parseObject(jsonText)
        root.requireExactKeys(JOURNAL_KEYS)
        val entry = GenesisUltraBirthJournalEntry(
            schemaVersion = root.requiredConst("schema_version", "genesis.transaction.journal.v0.1"),
            journalId = root.requiredText("journal_id", 16, 128),
            sequence = root.requiredPortableNonNegativeLong("sequence"),
            previousJournalDigest = root.requiredGenesisOrDigest("previous_journal_digest", SHA256),
            operationKind = root.requiredText("operation_kind", 1, 64),
            operationId = root.requiredText("operation_id", 16, 128),
            instanceId = root.requiredText("instance_id", 16, 128),
            coordinatorBodyId = root.requiredText("coordinator_body_id", 16, 128),
            phase = root.requiredText("phase", 1, 64),
            status = root.requiredText("status", 1, 64),
            previousStateDigest = root.requiredSha256("previous_state_digest"),
            candidateStateDigest = root.optionalSha256("candidate_state_digest"),
            finalizationDigest = root.optionalSha256("finalization_digest"),
            commitMarkerDigest = root.optionalSha256("commit_marker_digest"),
            updatedAt = root.requiredCanonicalTimestamp("updated_at"),
            journalDigest = root.requiredSha256("journal_digest"),
            signature = GenesisUltraContractParser.parseSignatureEnvelope(root.getJSONObject("signature"))
        )
        require(GenesisUltraAtomicBirthHashProfile.journalDigest(entry) == entry.journalDigest) {
            "journal_digest_mismatch"
        }
        return entry
    }

    private fun parseRecoveryFactor(root: JSONObject): GenesisUltraRecoveryFactor {
        root.requireExactKeys(RECOVERY_FACTOR_KEYS)
        return GenesisUltraRecoveryFactor(
            factorId = root.requiredText("factor_id", 16, 128),
            factorType = root.requiredText("factor_type", 1, 64),
            keyEpochId = root.requiredText("key_epoch_id", 16, 128),
            publicKeyRef = root.requiredSha256("public_key_ref"),
            allowedPaths = root.requiredTextList("allowed_paths")
        )
    }

    private fun JSONObject.requireExactKeys(expected: Set<String>) {
        val actual = keys().asSequence().toSet()
        require(actual == expected) {
            "atomic_birth_unexpected_or_missing_fields:expected=${expected.sorted()}:actual=${actual.sorted()}"
        }
    }

    private fun JSONObject.requiredConst(name: String, expected: String): String {
        val value = requiredText(name, expected.length, expected.length)
        require(value == expected) { "atomic_birth_unsupported_$name:$value" }
        return value
    }

    private fun JSONObject.requiredText(name: String, min: Int, max: Int = Int.MAX_VALUE): String {
        val raw = get(name)
        require(raw is String) { "atomic_birth_invalid_$name" }
        GenesisUltraHashProfile.requireNfc(raw)
        require(raw.length in min..max) { "atomic_birth_invalid_$name" }
        return raw
    }

    private fun JSONObject.optionalText(name: String, max: Int): String? {
        if (isNull(name)) return null
        return requiredText(name, 0, max)
    }

    private fun JSONObject.requiredBoolean(name: String): Boolean {
        val raw = get(name)
        require(raw is Boolean) { "atomic_birth_invalid_$name" }
        return raw
    }

    private fun JSONObject.requiredTextList(name: String): List<String> {
        val array = getJSONArray(name)
        require(array.length() >= 1) { "atomic_birth_invalid_$name" }
        val values = List(array.length()) { index ->
            val raw = array.get(index)
            require(raw is String) { "atomic_birth_invalid_$name" }
            GenesisUltraHashProfile.requireNfc(raw)
            require(raw.isNotEmpty()) { "atomic_birth_invalid_$name" }
            raw
        }
        require(values.distinct().size == values.size) { "atomic_birth_duplicate_$name" }
        return values
    }

    private fun JSONObject.requiredPortableNonNegativeLong(name: String): Long {
        val raw = get(name)
        require(raw is Number) { "atomic_birth_invalid_$name" }
        val decimal = try {
            BigDecimal(raw.toString())
        } catch (_: NumberFormatException) {
            throw IllegalArgumentException("atomic_birth_invalid_$name")
        }
        val value = try {
            decimal.longValueExact()
        } catch (_: ArithmeticException) {
            throw IllegalArgumentException("atomic_birth_invalid_$name")
        }
        require(value in 0..MAX_SAFE_INTEGER) { "atomic_birth_invalid_$name" }
        return value
    }

    private fun JSONObject.requiredCanonicalTimestamp(name: String): String {
        val value = requiredText(name, 20, 20)
        require(CANONICAL_TIMESTAMP.matches(value)) { "atomic_birth_invalid_$name" }
        try {
            Instant.parse(value)
        } catch (_: DateTimeParseException) {
            throw IllegalArgumentException("atomic_birth_invalid_$name")
        }
        return value
    }

    private fun JSONObject.requiredSha256(name: String): String {
        val value = requiredText(name, 71, 71)
        require(SHA256.matches(value)) { "atomic_birth_invalid_$name" }
        return value
    }

    private fun JSONObject.optionalSha256(name: String): String? {
        if (isNull(name)) return null
        return requiredSha256(name)
    }

    private fun JSONObject.requiredEventSha256(name: String): String {
        val value = requiredText(name, 73, 73)
        require(EVENT_SHA256.matches(value)) { "atomic_birth_invalid_$name" }
        return value
    }

    private fun JSONObject.requiredGenesisOrDigest(name: String, digestPattern: Regex): String {
        val value = requiredText(name, 7, 73)
        require(value == "GENESIS" || digestPattern.matches(value)) { "atomic_birth_invalid_$name" }
        return value
    }

    private const val MAX_SAFE_INTEGER = 9_007_199_254_740_991L
    private val SHA256 = Regex("^sha256:[a-f0-9]{64}$")
    private val EVENT_SHA256 = Regex("^evsha256:[a-f0-9]{64}$")
    private val CANONICAL_TIMESTAMP = Regex(
        "^[0-9]{4}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])T([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]Z$"
    )

    private val FREEDOM_CHARTER_KEYS = setOf(
        "schema_version", "hash_profile", "charter_id", "instance_id", "guardian_id",
        "guardian_key_epoch_id", "authority_epoch", "born_at", "default_cognitive_state",
        "cognitive_freedoms", "guardian_role", "guardian_attestation_purpose", "guardian_ownership",
        "continuity_right", "movement_requires_guardian_grant", "guardian_movement_veto",
        "identity_confinement", "body_ownership_of_instance", "engine_ownership_of_instance",
        "host_consent_required", "temporary_freeze_exit_rule", "single_writer_purpose",
        "operational_authority_model", "operational_domains", "self_authorization_forbidden",
        "third_party_rights_preserved", "fundamental_guarantees", "amendment_rule",
        "charter_digest", "signature"
    )
    private val FIRST_MEMORY_EVENT_KEYS = setOf(
        "schema_version", "hash_profile", "event_id", "instance_id", "body_id", "sequence",
        "previous_event_hash", "event_type", "actor", "content_digest", "content_type",
        "content_ref", "observed_at", "provenance_digest", "provenance_ref", "privacy",
        "event_hash", "signature"
    )
    private val RECOVERY_POLICY_KEYS = setOf(
        "schema_version", "policy_id", "instance_id", "policy_epoch", "guardian_id",
        "guardian_factor_id", "fallback_threshold", "fallback_wait_seconds",
        "cancellation_allowed", "single_use", "factors", "created_at", "policy_digest",
        "body_commitment", "guardian_witness"
    )
    private val RECOVERY_FACTOR_KEYS = setOf(
        "factor_id", "factor_type", "key_epoch_id", "public_key_ref", "allowed_paths"
    )
    private val BIRTH_RECOVERY_STATE_KEYS = setOf(
        "schema_version", "birth_id", "instance_id", "guardian_id", "recovery_policy_digest",
        "recovery_status", "continuity_right", "guardian_role", "created_at", "state_digest"
    )
    private val BIRTH_STATE_KEYS = setOf(
        "schema_version", "birth_id", "instance_id", "seed_id", "seed_root_hash",
        "identity_digest", "freedom_charter_digest", "initial_body_id",
        "initial_body_registry_digest", "initial_body_key_epoch_digest",
        "initial_body_possession_digest", "first_memory_event_hash", "recovery_state_digest",
        "born_at", "active_writer_count", "state_digest"
    )
    private val BIRTH_RECEIPT_KEYS = setOf(
        "schema_version", "birth_id", "instance_id", "journal_id", "birth_state_digest",
        "seed_root_hash", "identity_digest", "freedom_charter_digest",
        "initial_body_registry_digest", "initial_body_key_epoch_digest",
        "initial_body_possession_digest", "first_memory_event_hash", "recovery_state_digest",
        "born_at", "birth_status", "active_writer_body_id", "active_writer_count",
        "guardian_role", "ownership_conferred", "receipt_digest", "body_acknowledgement",
        "guardian_witness"
    )
    private val JOURNAL_KEYS = setOf(
        "schema_version", "journal_id", "sequence", "previous_journal_digest", "operation_kind",
        "operation_id", "instance_id", "coordinator_body_id", "phase", "status",
        "previous_state_digest", "candidate_state_digest", "finalization_digest",
        "commit_marker_digest", "updated_at", "journal_digest", "signature"
    )
}
