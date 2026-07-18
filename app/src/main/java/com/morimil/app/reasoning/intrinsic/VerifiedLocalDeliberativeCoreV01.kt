package com.morimil.app.reasoning.intrinsic

import com.morimil.app.data.genesis.ultra.GenesisUltraEd25519SignatureVerifier
import com.morimil.app.data.genesis.ultra.GenesisUltraHashProfile
import com.morimil.app.data.genesis.ultra.GenesisUltraSignatureEnvelope
import com.morimil.app.reasoning.growth.IntrinsicMotorTechnique
import com.morimil.app.reasoning.growth.MorimilIntrinsicMotorBlueprints
import com.morimil.app.reasoning.ReasoningMotorRole
import java.io.InputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

data class DeliberativeArtifactManifest(
    val schemaVersion: String,
    val artifactVersion: String,
    val artifactSha256: String,
    val artifactSizeBytes: Long,
    val formatId: String,
    val runtimeAbi: String,
    val blueprintVersion: String,
    val techniques: Set<IntrinsicMotorTechnique>
)

/** A pre-resolved local handle. It has no URL, provider or download capability. */
interface LocalDeliberativeArtifact {
    val sizeBytes: Long

    fun openReadOnly(): InputStream
}

data class DeliberativeArtifactVerificationPolicy(
    val maximumArtifactBytes: Long = 4L * 1024L * 1024L * 1024L,
    val allowedFormatIds: Set<String> = setOf(
        "litertlm.v1",
        "morimil.looped.v1"
    )
) {
    init {
        require(maximumArtifactBytes > 0L) { "artifact_maximum_size_invalid" }
        require(allowedFormatIds.isNotEmpty()) { "artifact_format_allowlist_empty" }
        require(allowedFormatIds.all(FORMAT_ID_PATTERN::matches)) {
            "artifact_format_allowlist_invalid"
        }
    }

    private companion object {
        val FORMAT_ID_PATTERN = Regex("[a-z0-9][a-z0-9._-]{0,63}")
    }
}

object DeliberativeArtifactHashProfile {
    const val MANIFEST_SCHEMA = "morimil.deliberative.artifact.manifest.v0.1"
    const val SIGNED_DOMAIN = "morimil.deliberative.artifact.signature.v0.1"

    fun manifestDigest(manifest: DeliberativeArtifactManifest): String {
        val orderedTechniques = manifest.techniques.sortedBy { technique -> technique.name }
        return GenesisUltraHashProfile.hashFields(
            SIGNED_DOMAIN,
            buildList {
                add(manifest.schemaVersion)
                add(manifest.artifactVersion)
                add(manifest.artifactSha256)
                add(manifest.artifactSizeBytes.toString())
                add(manifest.formatId)
                add(manifest.runtimeAbi)
                add(manifest.blueprintVersion)
                add(orderedTechniques.size.toString())
                orderedTechniques.forEach { technique -> add(technique.name) }
            }
        )
    }
}

class VerifiedDeliberativeArtifact private constructor(
    manifest: DeliberativeArtifactManifest,
    val manifestDigest: String,
    val signature: GenesisUltraSignatureEnvelope,
    internal val localArtifact: LocalDeliberativeArtifact
) {
    val manifest: DeliberativeArtifactManifest = manifest.copy(
        techniques = Collections.unmodifiableSet(manifest.techniques.toSet())
    )

    internal companion object {
        fun create(
            manifest: DeliberativeArtifactManifest,
            manifestDigest: String,
            signature: GenesisUltraSignatureEnvelope,
            localArtifact: LocalDeliberativeArtifact
        ): VerifiedDeliberativeArtifact {
            return VerifiedDeliberativeArtifact(
                manifest = manifest,
                manifestDigest = manifestDigest,
                signature = signature.copy(),
                localArtifact = localArtifact
            )
        }
    }
}

/**
 * Fail-closed verifier for one already-local artifact. Verification authorizes
 * reading only; it cannot download, install, promote or persist a model.
 */
class DeliberativeArtifactVerifier(
    private val signatureVerifier: GenesisUltraEd25519SignatureVerifier,
    private val policy: DeliberativeArtifactVerificationPolicy =
        DeliberativeArtifactVerificationPolicy()
) {
    fun verify(
        manifest: DeliberativeArtifactManifest,
        signature: GenesisUltraSignatureEnvelope,
        localArtifact: LocalDeliberativeArtifact
    ): VerifiedDeliberativeArtifact {
        validateManifest(manifest)
        require(localArtifact.sizeBytes == manifest.artifactSizeBytes) {
            "artifact_reported_size_mismatch"
        }

        val actualDigest = digestLocalArtifact(
            localArtifact = localArtifact,
            expectedSize = manifest.artifactSizeBytes
        )
        require(actualDigest == manifest.artifactSha256) {
            "artifact_sha256_mismatch"
        }

        val manifestDigest = DeliberativeArtifactHashProfile.manifestDigest(manifest)
        validateSignatureEnvelope(signature, manifestDigest)
        require(
            signatureVerifier.verify(
                signature,
                GenesisUltraHashProfile.signatureEnvelopePreimage(signature)
            )
        ) { "artifact_signature_invalid_or_untrusted" }

        return VerifiedDeliberativeArtifact.create(
            manifest = manifest,
            manifestDigest = manifestDigest,
            signature = signature,
            localArtifact = localArtifact
        )
    }

    private fun validateManifest(manifest: DeliberativeArtifactManifest) {
        require(manifest.schemaVersion == DeliberativeArtifactHashProfile.MANIFEST_SCHEMA) {
            "artifact_manifest_schema_unsupported"
        }
        require(VERSION_PATTERN.matches(manifest.artifactVersion)) {
            "artifact_version_invalid"
        }
        require(SHA256_PATTERN.matches(manifest.artifactSha256)) {
            "artifact_sha256_invalid"
        }
        require(manifest.artifactSizeBytes in 1L..policy.maximumArtifactBytes) {
            "artifact_size_out_of_policy"
        }
        require(manifest.formatId in policy.allowedFormatIds) {
            "artifact_format_not_allowed"
        }
        require(RUNTIME_ABI_PATTERN.matches(manifest.runtimeAbi)) {
            "artifact_runtime_abi_invalid"
        }
        require(manifest.blueprintVersion == MorimilIntrinsicMotorBlueprints.VERSION) {
            "artifact_blueprint_version_mismatch"
        }

        val requiredTechniques = MorimilIntrinsicMotorBlueprints
            .requireBlueprint(ReasoningMotorRole.DELIBERATIVE)
            .requiredTechniques
        require(manifest.techniques.containsAll(requiredTechniques)) {
            "artifact_required_techniques_missing"
        }
    }

    private fun validateSignatureEnvelope(
        signature: GenesisUltraSignatureEnvelope,
        manifestDigest: String
    ) {
        require(signature.schemaVersion == SIGNATURE_ENVELOPE_SCHEMA) {
            "artifact_signature_schema_unsupported"
        }
        require(signature.signatureProfile == ED25519_SIGNATURE_PROFILE) {
            "artifact_signature_profile_unsupported"
        }
        require(signature.signerType.isNotBlank()) { "artifact_signer_type_missing" }
        require(signature.signerId.isNotBlank()) { "artifact_signer_id_missing" }
        require(signature.keyEpochId.isNotBlank()) { "artifact_key_epoch_missing" }
        require(signature.signedDomain == DeliberativeArtifactHashProfile.SIGNED_DOMAIN) {
            "artifact_signed_domain_mismatch"
        }
        require(signature.signedDigest == manifestDigest) {
            "artifact_signed_digest_mismatch"
        }
        require(SHA256_PATTERN.matches(signature.publicKeyRef)) {
            "artifact_public_key_ref_invalid"
        }
        require(ED25519_SIGNATURE_PATTERN.matches(signature.signatureValue)) {
            "artifact_signature_value_invalid"
        }
        require(isCanonicalTimestamp(signature.createdAt)) {
            "artifact_signature_created_at_invalid"
        }
    }

    private fun digestLocalArtifact(
        localArtifact: LocalDeliberativeArtifact,
        expectedSize: Long
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        var totalBytes = 0L
        localArtifact.openReadOnly().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(count > 0) { "artifact_stream_made_no_progress" }
                totalBytes += count
                require(totalBytes <= expectedSize) { "artifact_stream_exceeds_declared_size" }
                digest.update(buffer, 0, count)
            }
        }
        require(totalBytes == expectedSize) { "artifact_stream_size_mismatch" }
        return "sha256:" + digest.digest().joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    private fun isCanonicalTimestamp(value: String): Boolean {
        return try {
            Instant.parse(value).toString() == value
        } catch (_: DateTimeParseException) {
            false
        }
    }

    private companion object {
        const val SIGNATURE_ENVELOPE_SCHEMA = "genesis.signature.envelope.v0.1"
        const val ED25519_SIGNATURE_PROFILE = "genesis.signature.ed25519.v0.1"

        val VERSION_PATTERN = Regex("[a-z0-9][a-z0-9._-]{0,63}")
        val RUNTIME_ABI_PATTERN = Regex("[a-z0-9][a-z0-9._+-]{0,95}")
        val SHA256_PATTERN = Regex("sha256:[0-9a-f]{64}")
        val ED25519_SIGNATURE_PATTERN = Regex("[0-9a-f]{128}")
    }
}

interface LocalDeliberativeTensorState

data class LocalDeliberativePassOutcome(
    val state: LocalDeliberativeTensorState,
    val certaintyPermille: Int,
    val stabilityPermille: Int
) {
    init {
        require(certaintyPermille in 0..1_000) { "local_certainty_invalid" }
        require(stabilityPermille in 0..1_000) { "local_stability_invalid" }
    }
}

interface LocalDeliberativeSession : AutoCloseable {
    suspend fun initialize(input: DeliberativeCoreInput): Result<LocalDeliberativeTensorState>

    suspend fun refine(
        state: LocalDeliberativeTensorState,
        pass: Int
    ): Result<LocalDeliberativePassOutcome>

    suspend fun decode(state: LocalDeliberativeTensorState): Result<String>
}

interface LocalDeliberativeEngine : AutoCloseable {
    val formatId: String
    val runtimeAbi: String
    val loadedArtifactSha256: String

    suspend fun openSession(): Result<LocalDeliberativeSession>
}

fun interface LocalDeliberativeEngineLoader {
    suspend fun load(artifact: VerifiedDeliberativeArtifact): Result<LocalDeliberativeEngine>
}

/**
 * Morimil-owned core backed by one verified local artifact and isolated
 * request-scoped sessions. The engine may remain loaded; reasoning state may not.
 */
class VerifiedLocalDeliberativeCoreV01 private constructor(
    private val artifact: VerifiedDeliberativeArtifact,
    private val engine: LocalDeliberativeEngine
) : MorimilDeliberativeCore, AutoCloseable {
    override val artifactVersion: String = artifact.manifest.artifactVersion
    override val artifactSha256: String = artifact.manifest.artifactSha256

    private val closed = AtomicBoolean(false)
    private val activeLeases = ConcurrentHashMap.newKeySet<SessionLease>()
    private val leaseLock = Any()

    override suspend fun initialize(
        input: DeliberativeCoreInput
    ): Result<DeliberativeLatentState> = runCatching {
        check(!closed.get()) { "deliberative_core_closed" }
        val session = engine.openSession().getOrThrow()
        try {
            val tensorState = session.initialize(input).getOrThrow()
            val lease = SessionLease(session)
            synchronized(leaseLock) {
                check(!closed.get()) { "deliberative_core_closed" }
                activeLeases += lease
            }
            SessionBoundState(owner = this, lease = lease, tensorState = tensorState)
        } catch (error: Throwable) {
            session.close()
            throw error
        }
    }

    override suspend fun refine(
        state: DeliberativeLatentState,
        pass: Int
    ): Result<DeliberativePassOutcome> = runCatching {
        require(pass in 1..MAX_RECURRENT_PASSES) { "deliberative_pass_out_of_range" }
        val bound = requireOwnedState(state)
        val outcome = bound.lease.session.refine(bound.tensorState, pass).getOrThrow()
        DeliberativePassOutcome(
            state = SessionBoundState(
                owner = this,
                lease = bound.lease,
                tensorState = outcome.state
            ),
            certaintyPermille = outcome.certaintyPermille,
            stabilityPermille = outcome.stabilityPermille
        )
    }

    override suspend fun decode(state: DeliberativeLatentState): Result<String> = runCatching {
        val bound = requireOwnedState(state)
        bound.lease.session.decode(bound.tensorState).getOrThrow()
    }

    override suspend fun release(state: DeliberativeLatentState) {
        val bound = requireOwnedState(state, allowClosedLease = true)
        bound.lease.close()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val leases = synchronized(leaseLock) { activeLeases.toList() }
        leases.forEach(SessionLease::close)
        engine.close()
    }

    private fun requireOwnedState(
        state: DeliberativeLatentState,
        allowClosedLease: Boolean = false
    ): SessionBoundState {
        require(state is SessionBoundState && state.owner === this) {
            "deliberative_state_not_owned_by_core"
        }
        if (!allowClosedLease) {
            check(!state.lease.isClosed()) { "deliberative_session_closed" }
            check(!closed.get()) { "deliberative_core_closed" }
        }
        return state
    }

    private inner class SessionLease(
        val session: LocalDeliberativeSession
    ) {
        private val leaseClosed = AtomicBoolean(false)

        fun isClosed(): Boolean = leaseClosed.get()

        fun close() {
            if (!leaseClosed.compareAndSet(false, true)) return
            try {
                session.close()
            } finally {
                activeLeases.remove(this)
            }
        }
    }

    private data class SessionBoundState(
        val owner: VerifiedLocalDeliberativeCoreV01,
        val lease: VerifiedLocalDeliberativeCoreV01.SessionLease,
        val tensorState: LocalDeliberativeTensorState
    ) : DeliberativeLatentState

    companion object {
        private const val MAX_RECURRENT_PASSES = 8

        suspend fun create(
            artifact: VerifiedDeliberativeArtifact,
            engineLoader: LocalDeliberativeEngineLoader
        ): Result<VerifiedLocalDeliberativeCoreV01> = runCatching {
            val engine = engineLoader.load(artifact).getOrThrow()
            try {
                require(engine.formatId == artifact.manifest.formatId) {
                    "deliberative_engine_format_mismatch"
                }
                require(engine.runtimeAbi == artifact.manifest.runtimeAbi) {
                    "deliberative_engine_runtime_abi_mismatch"
                }
                require(engine.loadedArtifactSha256 == artifact.manifest.artifactSha256) {
                    "deliberative_engine_loaded_artifact_mismatch"
                }
                VerifiedLocalDeliberativeCoreV01(artifact, engine)
            } catch (error: Throwable) {
                engine.close()
                throw error
            }
        }
    }
}
