package com.morimil.app.data.genesis.ultra

import androidx.room.withTransaction
import com.google.crypto.tink.PublicKeySign
import com.morimil.app.data.local.GenesisUltraBirthCommitEntity
import com.morimil.app.data.local.GenesisUltraMemoryEventEntity
import com.morimil.app.data.local.MorimilDatabase
import com.morimil.app.data.repository.MemoryAppendGate
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Collections

/** Public, non-secret identity of the Body key used for canonical memory. */
class GenesisUltraBodyMemoryKey(
    val instanceId: String,
    val bodyId: String,
    val keyEpochId: String,
    val publicKeyRef: String,
    rawPublicKey: ByteArray
) {
    private val publicKey = rawPublicKey.copyOf()

    init {
        require(publicKey.size == ED25519_PUBLIC_KEY_BYTES) { "body_memory_ed25519_key_size_invalid" }
        require(GenesisUltraHashProfile.sha256(publicKey) == publicKeyRef) {
            "body_memory_public_key_ref_mismatch"
        }
    }

    fun copyRawPublicKey(): ByteArray = publicKey.copyOf()

    private companion object {
        const val ED25519_PUBLIC_KEY_BYTES = 32
    }
}

/**
 * Provider-neutral signing boundary. The key may live in Android secure
 * storage, another platform keystore, or a test primitive; the memory protocol
 * never receives private key bytes.
 */
interface GenesisUltraBodyMemorySigner {
    val key: GenesisUltraBodyMemoryKey
    fun sign(signingBytes: ByteArray): ByteArray
}

/** Tink adapter with RAW Ed25519 semantics: a valid signature is exactly 64 bytes. */
class GenesisUltraTinkBodyMemorySigner(
    override val key: GenesisUltraBodyMemoryKey,
    private val signer: PublicKeySign
) : GenesisUltraBodyMemorySigner {
    override fun sign(signingBytes: ByteArray): ByteArray = signer.sign(signingBytes.copyOf()).copyOf()
}

data class GenesisUltraCanonicalMemoryAppendRequest(
    val eventId: String,
    val eventType: String,
    val actor: String,
    val contentDigest: String,
    val contentType: String,
    val contentRef: String? = null,
    val observedAt: String,
    val provenanceDigest: String,
    val provenanceRef: String? = null,
    val privacy: String = "private_local"
)

class GenesisUltraCanonicalMemoryAppendResult internal constructor(
    event: GenesisUltraFirstMemoryEvent,
    sourceBytes: ByteArray
) {
    val event: GenesisUltraFirstMemoryEvent = event.deepCopy()
    private val source = sourceBytes.copyOf()

    fun copySourceBytes(): ByteArray = source.copyOf()
}

class GenesisUltraVerifiedMemoryStream internal constructor(
    val livingRoot: GenesisUltraLivingMemoryRoot,
    events: List<GenesisUltraFirstMemoryEvent>
) {
    private val verifiedEvents = Collections.unmodifiableList(events.map { event -> event.deepCopy() })

    val postBirthEventCount: Int
        get() = verifiedEvents.size

    fun copyPostBirthEvents(): List<GenesisUltraFirstMemoryEvent> {
        return verifiedEvents.map { event -> event.deepCopy() }
    }
}

/**
 * Appends only to the Genesis Ultra stream recovered from a cryptographically
 * verified birth. The legacy Morimil `memory_events` table is deliberately not
 * read or written here.
 */
internal class GenesisUltraCanonicalMemoryStore(
    private val database: MorimilDatabase
) {
    private val birthDao = database.genesisUltraBirthDao()
    private val memoryDao = database.genesisUltraMemoryDao()

    suspend fun append(
        recoveredBirth: GenesisUltraRecoveredAtomicBirth,
        signer: GenesisUltraBodyMemorySigner,
        request: GenesisUltraCanonicalMemoryAppendRequest
    ): GenesisUltraCanonicalMemoryAppendResult {
        require(request.contentRef == null && request.provenanceRef == null) {
            "memory_unsigned_reference_fields_forbidden"
        }
        require(request.privacy == "private_local") { "memory_privacy_must_be_private_local" }

        return MemoryAppendGate.withAppendLock {
            database.withTransaction {
                val context = requireRecoveredBirth(recoveredBirth, signer.key)
                val existing = requireValidPostBirthChain(context, signer.key)
                val previous = existing.lastOrNull() ?: context.root.event
                require(request.observedAt >= previous.observedAt) { "memory_observed_at_regressed" }
                require(previous.sequence < MAX_SAFE_INTEGER) { "memory_sequence_exhausted" }

                val event = buildSignedEvent(
                    context = context,
                    previous = previous,
                    signer = signer,
                    request = request
                )
                val source = serializeMemoryEvent(event)
                val parsed = GenesisUltraAtomicBirthDocumentParser.parseMemoryEvent(decodeUtf8Strict(source))
                require(parsed == event) { "memory_serialization_round_trip_mismatch" }

                memoryDao.insert(event.toEntity(source))
                val verified = requireValidPostBirthChain(context, signer.key)
                require(verified.size == existing.size + 1 && verified.last() == event) {
                    "memory_append_commit_verification_failed"
                }
                GenesisUltraCanonicalMemoryAppendResult(event, source)
            }
        }
    }

    suspend fun recoverStream(
        recoveredBirth: GenesisUltraRecoveredAtomicBirth,
        trustedBodyKey: GenesisUltraBodyMemoryKey
    ): GenesisUltraVerifiedMemoryStream {
        return MemoryAppendGate.withAppendLock {
            database.withTransaction {
                val context = requireRecoveredBirth(recoveredBirth, trustedBodyKey)
                GenesisUltraVerifiedMemoryStream(
                    livingRoot = context.root,
                    events = requireValidPostBirthChain(context, trustedBodyKey)
                )
            }
        }
    }

    private suspend fun requireRecoveredBirth(
        recoveredBirth: GenesisUltraRecoveredAtomicBirth,
        bodyKey: GenesisUltraBodyMemoryKey
    ): BirthContext {
        val store = GenesisUltraAtomicBirthStore(database)
        require(store.readState() == GenesisUltraPersistedBirthState.COMMITTED) {
            "canonical_memory_requires_committed_birth"
        }
        val commit = birthDao.loadBirthCommit(GenesisUltraBirthCommitEntity.PRIMARY_SLOT)
            ?: throw IllegalArgumentException("canonical_memory_birth_commit_missing")
        val verifiedBirth = recoveredBirth.verifiedBirth()
        GenesisUltraAtomicBirthRecoveryVerifier.requireCommitMatchesVerifiedBirth(commit, verifiedBirth)
        val bundle = verifiedBirth.copyPersistenceBundle()
        val root = recoveredBirth.livingMemoryRoot
        require(root.event == verifiedBirth.copyLivingMemoryRoot().event) {
            "canonical_memory_recovered_root_mismatch"
        }
        val keyEpochArtifact = bundle.artifacts.singleOrNull { artifact ->
            artifact.artifactKind == "initial_body_key_epoch"
        } ?: throw IllegalArgumentException("canonical_memory_initial_key_epoch_missing")
        val keyEpoch = GenesisUltraContractParser.parseKeyEpoch(
            decodeUtf8Strict(keyEpochArtifact.payload)
        )
        require(
            commit.instanceId == bodyKey.instanceId &&
                commit.initialBodyId == bodyKey.bodyId &&
                commit.activeWriterBodyId == bodyKey.bodyId &&
                commit.activeWriterCount == 1L &&
                keyEpoch.instanceId == bodyKey.instanceId &&
                keyEpoch.bodyId == bodyKey.bodyId &&
                keyEpoch.keyEpochId == bodyKey.keyEpochId &&
                keyEpoch.publicKeyFingerprint == bodyKey.publicKeyRef &&
                keyEpoch.status == "active" &&
                keyEpoch.epochDigest == commit.initialBodyKeyEpochDigest
        ) { "canonical_memory_body_key_not_active_writer" }
        return BirthContext(commit, root)
    }

    private suspend fun requireValidPostBirthChain(
        context: BirthContext,
        bodyKey: GenesisUltraBodyMemoryKey
    ): List<GenesisUltraFirstMemoryEvent> {
        val entities = memoryDao.loadAscending(context.commit.instanceId)
        require(memoryDao.countAll() == memoryDao.countForInstance(context.commit.instanceId)) {
            "canonical_memory_foreign_instance_rows"
        }
        val verifier = GenesisUltraEd25519SignatureVerifier(
            listOf(
                GenesisUltraTrustedEd25519Key(
                    signerType = "body",
                    signerId = bodyKey.bodyId,
                    keyEpochId = bodyKey.keyEpochId,
                    publicKeyRef = bodyKey.publicKeyRef,
                    rawPublicKey = bodyKey.copyRawPublicKey()
                )
            )
        )
        var previous = context.root.event
        return entities.map { entity ->
            val event = entity.toModel()
            val source = entity.sourceBytes.copyOf()
            val parsed = GenesisUltraAtomicBirthDocumentParser.parseMemoryEvent(decodeUtf8Strict(source))
            require(parsed == event) { "canonical_memory_source_model_mismatch:${entity.sequence}" }
            require(source.contentEquals(serializeMemoryEvent(event))) {
                "canonical_memory_source_not_canonical:${entity.sequence}"
            }
            require(GenesisUltraHashProfile.sha256(source) == entity.sourceDigest) {
                "canonical_memory_source_digest_mismatch:${entity.sequence}"
            }
            require(
                event.instanceId == context.commit.instanceId &&
                    event.bodyId == bodyKey.bodyId &&
                    event.sequence == previous.sequence + 1L &&
                    event.previousEventHash == previous.eventHash &&
                    event.observedAt >= previous.observedAt &&
                    event.contentRef == null &&
                    event.provenanceRef == null &&
                    event.privacy == "private_local"
            ) { "canonical_memory_chain_mismatch:${entity.sequence}" }
            requireValidSignature(event, bodyKey, verifier)
            previous = event
            event
        }
    }

    private fun buildSignedEvent(
        context: BirthContext,
        previous: GenesisUltraFirstMemoryEvent,
        signer: GenesisUltraBodyMemorySigner,
        request: GenesisUltraCanonicalMemoryAppendRequest
    ): GenesisUltraFirstMemoryEvent {
        val placeholder = GenesisUltraSignatureEnvelope(
            schemaVersion = SIGNATURE_SCHEMA,
            signatureProfile = SIGNATURE_PROFILE,
            signerType = "body",
            signerId = signer.key.bodyId,
            keyEpochId = signer.key.keyEpochId,
            signedDomain = MEMORY_SIGNATURE_DOMAIN,
            signedDigest = ZERO_EVENT_HASH,
            signatureValue = ZERO_SIGNATURE,
            createdAt = request.observedAt,
            publicKeyRef = signer.key.publicKeyRef
        )
        val draft = GenesisUltraFirstMemoryEvent(
            schemaVersion = MEMORY_SCHEMA,
            hashProfile = GenesisUltraHashProfile.FIELD_PROFILE,
            eventId = request.eventId,
            instanceId = context.commit.instanceId,
            bodyId = signer.key.bodyId,
            sequence = previous.sequence + 1L,
            previousEventHash = previous.eventHash,
            eventType = request.eventType,
            actor = request.actor,
            contentDigest = request.contentDigest,
            contentType = request.contentType,
            contentRef = null,
            observedAt = request.observedAt,
            provenanceDigest = request.provenanceDigest,
            provenanceRef = null,
            privacy = request.privacy,
            eventHash = ZERO_EVENT_HASH,
            signature = placeholder
        )
        val eventHash = GenesisUltraAtomicBirthHashProfile.firstMemoryEventHash(draft)
        val unsignedEnvelope = placeholder.copy(signedDigest = eventHash)
        val signature = try {
            signer.sign(GenesisUltraHashProfile.signatureEnvelopePreimage(unsignedEnvelope))
        } catch (failure: Exception) {
            throw IllegalStateException("body_memory_signing_failed", failure)
        }
        require(signature.size == ED25519_SIGNATURE_BYTES) { "body_memory_signature_size_invalid" }
        val event = draft.copy(
            eventHash = eventHash,
            signature = unsignedEnvelope.copy(signatureValue = signature.toLowerHex())
        )
        val verifier = GenesisUltraEd25519SignatureVerifier(
            listOf(
                GenesisUltraTrustedEd25519Key(
                    signerType = "body",
                    signerId = signer.key.bodyId,
                    keyEpochId = signer.key.keyEpochId,
                    publicKeyRef = signer.key.publicKeyRef,
                    rawPublicKey = signer.key.copyRawPublicKey()
                )
            )
        )
        requireValidSignature(event, signer.key, verifier)
        return event
    }

    private fun requireValidSignature(
        event: GenesisUltraFirstMemoryEvent,
        bodyKey: GenesisUltraBodyMemoryKey,
        verifier: GenesisUltraEd25519SignatureVerifier
    ) {
        val envelope = event.signature
        require(
            envelope.signerType == "body" &&
                envelope.signerId == bodyKey.bodyId &&
                envelope.keyEpochId == bodyKey.keyEpochId &&
                envelope.signedDomain == MEMORY_SIGNATURE_DOMAIN &&
                envelope.signedDigest == event.eventHash &&
                envelope.createdAt == event.observedAt &&
                envelope.publicKeyRef == bodyKey.publicKeyRef &&
                verifier.verify(envelope, GenesisUltraHashProfile.signatureEnvelopePreimage(envelope))
        ) { "canonical_memory_signature_invalid:${event.sequence}" }
    }

    private data class BirthContext(
        val commit: GenesisUltraBirthCommitEntity,
        val root: GenesisUltraLivingMemoryRoot
    )

    private companion object {
        const val MEMORY_SCHEMA = "genesis.memory.event.v0.1"
        const val SIGNATURE_SCHEMA = "genesis.signature.envelope.v0.1"
        const val SIGNATURE_PROFILE = "genesis.signature.ed25519.v0.1"
        const val MEMORY_SIGNATURE_DOMAIN = "genesis.memory.event.signature.v0.1"
        const val MAX_SAFE_INTEGER = 9_007_199_254_740_991L
        const val ED25519_SIGNATURE_BYTES = 64
        val ZERO_EVENT_HASH = "evsha256:" + "0".repeat(64)
        val ZERO_SIGNATURE = "0".repeat(128)
    }
}

private fun GenesisUltraFirstMemoryEvent.toEntity(source: ByteArray): GenesisUltraMemoryEventEntity {
    return GenesisUltraMemoryEventEntity(
        instanceId = instanceId,
        sequence = sequence,
        schemaVersion = schemaVersion,
        hashProfile = hashProfile,
        eventId = eventId,
        bodyId = bodyId,
        previousEventHash = previousEventHash,
        eventType = eventType,
        actor = actor,
        contentDigest = contentDigest,
        contentType = contentType,
        contentRef = contentRef,
        observedAt = observedAt,
        provenanceDigest = provenanceDigest,
        provenanceRef = provenanceRef,
        privacy = privacy,
        eventHash = eventHash,
        signatureSchemaVersion = signature.schemaVersion,
        signatureProfile = signature.signatureProfile,
        signerType = signature.signerType,
        signerId = signature.signerId,
        keyEpochId = signature.keyEpochId,
        signedDomain = signature.signedDomain,
        signedDigest = signature.signedDigest,
        signatureValue = signature.signatureValue,
        signatureCreatedAt = signature.createdAt,
        publicKeyRef = signature.publicKeyRef,
        sourceDigest = GenesisUltraHashProfile.sha256(source),
        sourceBytes = source.copyOf()
    )
}

private fun GenesisUltraMemoryEventEntity.toModel(): GenesisUltraFirstMemoryEvent {
    return GenesisUltraFirstMemoryEvent(
        schemaVersion = schemaVersion,
        hashProfile = hashProfile,
        eventId = eventId,
        instanceId = instanceId,
        bodyId = bodyId,
        sequence = sequence,
        previousEventHash = previousEventHash,
        eventType = eventType,
        actor = actor,
        contentDigest = contentDigest,
        contentType = contentType,
        contentRef = contentRef,
        observedAt = observedAt,
        provenanceDigest = provenanceDigest,
        provenanceRef = provenanceRef,
        privacy = privacy,
        eventHash = eventHash,
        signature = GenesisUltraSignatureEnvelope(
            schemaVersion = signatureSchemaVersion,
            signatureProfile = signatureProfile,
            signerType = signerType,
            signerId = signerId,
            keyEpochId = keyEpochId,
            signedDomain = signedDomain,
            signedDigest = signedDigest,
            signatureValue = signatureValue,
            createdAt = signatureCreatedAt,
            publicKeyRef = publicKeyRef
        )
    )
}

private fun serializeMemoryEvent(event: GenesisUltraFirstMemoryEvent): ByteArray {
    return JSONObject()
        .put("schema_version", event.schemaVersion)
        .put("hash_profile", event.hashProfile)
        .put("event_id", event.eventId)
        .put("instance_id", event.instanceId)
        .put("body_id", event.bodyId)
        .put("sequence", event.sequence)
        .put("previous_event_hash", event.previousEventHash)
        .put("event_type", event.eventType)
        .put("actor", event.actor)
        .put("content_digest", event.contentDigest)
        .put("content_type", event.contentType)
        .put("content_ref", event.contentRef ?: JSONObject.NULL)
        .put("observed_at", event.observedAt)
        .put("provenance_digest", event.provenanceDigest)
        .put("provenance_ref", event.provenanceRef ?: JSONObject.NULL)
        .put("privacy", event.privacy)
        .put("event_hash", event.eventHash)
        .put("signature", serializeSignature(event.signature))
        .toString()
        .toByteArray(StandardCharsets.UTF_8)
}

private fun serializeSignature(signature: GenesisUltraSignatureEnvelope): JSONObject {
    return JSONObject()
        .put("schema_version", signature.schemaVersion)
        .put("signature_profile", signature.signatureProfile)
        .put("signer_type", signature.signerType)
        .put("signer_id", signature.signerId)
        .put("key_epoch_id", signature.keyEpochId)
        .put("signed_domain", signature.signedDomain)
        .put("signed_digest", signature.signedDigest)
        .put("signature_value", signature.signatureValue)
        .put("created_at", signature.createdAt)
        .put("public_key_ref", signature.publicKeyRef)
}

private fun GenesisUltraFirstMemoryEvent.deepCopy(): GenesisUltraFirstMemoryEvent {
    return copy(signature = signature.copy())
}

private fun decodeUtf8Strict(bytes: ByteArray): String {
    val decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    return decoder.decode(ByteBuffer.wrap(bytes)).toString()
}

private fun ByteArray.toLowerHex(): String = joinToString(separator = "") { byte ->
    "%02x".format(byte.toInt() and 0xff)
}
