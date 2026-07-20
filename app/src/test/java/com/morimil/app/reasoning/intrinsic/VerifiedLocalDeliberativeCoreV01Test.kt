package com.morimil.app.reasoning.intrinsic

import com.morimil.app.ai.ChatTurn
import com.morimil.app.data.genesis.ultra.GenesisUltraEd25519SignatureVerifier
import com.morimil.app.data.genesis.ultra.GenesisUltraHashProfile
import com.morimil.app.data.genesis.ultra.GenesisUltraSignatureEnvelope
import com.morimil.app.data.genesis.ultra.GenesisUltraTrustedEd25519Key
import com.morimil.app.reasoning.IntrinsicReasoningRequest
import com.morimil.app.reasoning.ReasoningMotorRole
import com.morimil.app.reasoning.growth.IntrinsicMotorTechnique
import com.morimil.app.reasoning.growth.MorimilIntrinsicMotorBlueprints
import com.morimil.app.reasoning.model.ReasoningTaskComplexity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.KeyPairGenerator
import java.security.Signature

class VerifiedLocalDeliberativeCoreV01Test {
    @Test
    fun signedExactArtifactRunsLocallyAndReleasesItsEphemeralSession() = runBlocking {
        val fixture = signedFixture()
        val verified = fixture.verify()
        val engine = RecordingEngine(
            loadedArtifactSha256 = fixture.manifest.artifactSha256
        )
        val core = VerifiedLocalDeliberativeCoreV01.create(
            artifact = verified,
            engineLoader = LocalDeliberativeEngineLoader { Result.success(engine) }
        ).getOrThrow()

        val response = DeliberativeMotorV0(core).compute(request()).getOrThrow()

        assertEquals("respuesta intrínseca local", response.content)
        assertEquals(MorimilDeliberativeArtifactContractV01.ARTIFACT_VERSION, core.artifactVersion)
        assertEquals(fixture.manifest.artifactSha256, core.artifactSha256)
        assertEquals(1, engine.sessions.size)
        assertEquals(listOf(1, 2), engine.sessions.single().refinementPasses)
        assertEquals(1, engine.sessions.single().closeCount)
        assertFalse(engine.closed)

        core.close()
        assertTrue(engine.closed)
    }

    @Test
    fun tamperedArtifactIsRejectedBeforeEngineLoading() {
        val fixture = signedFixture()
        var loaderCalled = false
        val tampered = ByteArrayArtifact("morimil-local-weights-v0.X".toByteArray())

        val error = assertThrows(IllegalArgumentException::class.java) {
            val verified = fixture.verifier.verify(
                manifest = fixture.manifest,
                signature = fixture.signature,
                localArtifact = tampered
            )
            runBlocking {
                VerifiedLocalDeliberativeCoreV01.create(
                    verified,
                    LocalDeliberativeEngineLoader {
                        loaderCalled = true
                        Result.failure(IllegalStateException("must not load"))
                    }
                ).getOrThrow()
            }
        }

        assertTrue(error.message.orEmpty().contains("artifact_sha256_mismatch"))
        assertFalse(loaderCalled)
    }

    @Test
    fun cryptographicallyValidButUntrustedSignerIsRejected() {
        val fixture = signedFixture()
        val otherKeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val otherRawKey = otherKeyPair.public.encoded.takeLast(32).toByteArray()
        val untrustedVerifier = GenesisUltraEd25519SignatureVerifier(
            listOf(
                GenesisUltraTrustedEd25519Key(
                    signerType = fixture.signature.signerType,
                    signerId = "different-signer",
                    keyEpochId = fixture.signature.keyEpochId,
                    publicKeyRef = GenesisUltraHashProfile.sha256(otherRawKey),
                    rawPublicKey = otherRawKey
                )
            )
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            DeliberativeArtifactVerifier(untrustedVerifier).verify(
                fixture.manifest,
                fixture.signature,
                fixture.localArtifact
            )
        }

        assertEquals("artifact_signature_invalid_or_untrusted", error.message)
    }

    @Test
    fun manifestWithoutRequiredDeliberativeTechniqueIsRejected() {
        val fixture = signedFixture()
        val incomplete = fixture.manifest.copy(
            techniques = setOf(IntrinsicMotorTechnique.LOOPED_LATENT_DEPTH)
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            fixture.verifier.verify(incomplete, fixture.signature, fixture.localArtifact)
        }

        assertEquals("artifact_required_techniques_missing", error.message)
    }

    @Test
    fun signatureCannotBeReusedForChangedSourceRevision() {
        val fixture = signedFixture()
        val changed = fixture.manifest.copy(sourceModelRevision = "1".repeat(40))

        val error = assertThrows(IllegalArgumentException::class.java) {
            fixture.verifier.verify(changed, fixture.signature, fixture.localArtifact)
        }

        assertEquals("artifact_signed_digest_mismatch", error.message)
    }

    @Test
    fun engineMustAttestTheBytesItActuallyLoaded() = runBlocking {
        val fixture = signedFixture()
        val engine = RecordingEngine(loadedArtifactSha256 = digest('f'))

        val result = VerifiedLocalDeliberativeCoreV01.create(
            artifact = fixture.verify(),
            engineLoader = LocalDeliberativeEngineLoader { Result.success(engine) }
        )

        assertTrue(result.isFailure)
        assertEquals("deliberative_engine_loaded_artifact_mismatch", result.exceptionOrNull()?.message)
        assertTrue(engine.closed)
    }

    @Test
    fun runtimeAbiMismatchClosesRejectedEngine() = runBlocking {
        val fixture = signedFixture()
        val engine = RecordingEngine(
            runtimeAbi = "litertlm.kotlin.v0.99",
            loadedArtifactSha256 = fixture.manifest.artifactSha256
        )

        val result = VerifiedLocalDeliberativeCoreV01.create(
            artifact = fixture.verify(),
            engineLoader = LocalDeliberativeEngineLoader { Result.success(engine) }
        )

        assertTrue(result.isFailure)
        assertEquals("deliberative_engine_runtime_abi_mismatch", result.exceptionOrNull()?.message)
        assertTrue(engine.closed)
    }

    @Test
    fun failedRecurrentPassStillClosesTheRequestSession() = runBlocking {
        val fixture = signedFixture()
        val engine = RecordingEngine(
            loadedArtifactSha256 = fixture.manifest.artifactSha256,
            failAtPass = 1
        )
        val core = VerifiedLocalDeliberativeCoreV01.create(
            fixture.verify(),
            LocalDeliberativeEngineLoader { Result.success(engine) }
        ).getOrThrow()

        val result = DeliberativeMotorV0(core).compute(request())

        assertTrue(result.isFailure)
        assertEquals(1, engine.sessions.single().closeCount)
        core.close()
    }

    @Test
    fun closingCoreClosesActiveSessionsAndRefusesNewOnes() = runBlocking {
        val fixture = signedFixture()
        val engine = RecordingEngine(
            loadedArtifactSha256 = fixture.manifest.artifactSha256
        )
        val core = VerifiedLocalDeliberativeCoreV01.create(
            fixture.verify(),
            LocalDeliberativeEngineLoader { Result.success(engine) }
        ).getOrThrow()
        val state = core.initialize(coreInput()).getOrThrow()

        core.close()

        assertEquals(1, engine.sessions.single().closeCount)
        assertTrue(engine.closed)
        assertTrue(core.refine(state, 1).isFailure)
        assertTrue(core.initialize(coreInput()).isFailure)
    }

    @Test
    fun stateFromAnotherCoreCannotCrossTheBoundary() = runBlocking {
        val fixture = signedFixture()
        val firstEngine = RecordingEngine(
            loadedArtifactSha256 = fixture.manifest.artifactSha256
        )
        val secondEngine = RecordingEngine(
            loadedArtifactSha256 = fixture.manifest.artifactSha256
        )
        val first = VerifiedLocalDeliberativeCoreV01.create(
            fixture.verify(),
            LocalDeliberativeEngineLoader { Result.success(firstEngine) }
        ).getOrThrow()
        val second = VerifiedLocalDeliberativeCoreV01.create(
            fixture.verify(),
            LocalDeliberativeEngineLoader { Result.success(secondEngine) }
        ).getOrThrow()
        val foreignState = second.initialize(coreInput()).getOrThrow()

        val result = first.refine(foreignState, 1)

        assertTrue(result.isFailure)
        assertEquals("deliberative_state_not_owned_by_core", result.exceptionOrNull()?.message)
        second.release(foreignState)
        first.close()
        second.close()
    }

    @Test
    fun coreContractsExposeNoProviderNetworkMemoryIdentityOrInstaller() {
        val forbidden = listOf(
            "Provider",
            "Endpoint",
            "Credential",
            "Http",
            "Socket",
            "URL",
            "Repository",
            "Dao",
            "MemoryUseCase",
            "IdentityWriter",
            "Lifecycle",
            "Installer",
            "Download"
        )
        val contractTypes = listOf(
            LocalDeliberativeArtifact::class.java,
            LocalDeliberativeEngineLoader::class.java,
            LocalDeliberativeEngine::class.java,
            LocalDeliberativeSession::class.java,
            VerifiedLocalDeliberativeCoreV01::class.java
        )
        val exposedNames = contractTypes.flatMap { type ->
            type.methods.flatMap { method ->
                method.parameterTypes.toList() + method.returnType
            } + type.declaredFields.map { field -> field.type }
        }.map { type -> type.name }

        assertTrue(exposedNames.isNotEmpty())
        assertTrue(
            exposedNames.none { name -> forbidden.any { token -> name.contains(token) } }
        )
    }

    private fun request(): IntrinsicReasoningRequest {
        return IntrinsicReasoningRequest(
            systemPrompt = "Razona localmente.",
            history = listOf(ChatTurn("user", "Analiza el problema.")),
            taskComplexity = ReasoningTaskComplexity.CODE_REVIEW
        )
    }

    private fun coreInput(): DeliberativeCoreInput {
        return DeliberativeCoreInput(
            systemPrompt = "Razona localmente.",
            history = listOf(ChatTurn("user", "Analiza el problema.")),
            taskComplexity = ReasoningTaskComplexity.CODE_REVIEW
        )
    }

    private fun signedFixture(): SignedFixture {
        val bytes = "morimil-local-weights-v0.1".toByteArray()
        val manifest = DeliberativeArtifactManifest(
            schemaVersion = DeliberativeArtifactHashProfile.MANIFEST_SCHEMA,
            contractVersion = MorimilDeliberativeArtifactContractV01.CONTRACT_VERSION,
            artifactVersion = MorimilDeliberativeArtifactContractV01.ARTIFACT_VERSION,
            artifactSha256 = GenesisUltraHashProfile.sha256(bytes),
            artifactSizeBytes = bytes.size.toLong(),
            formatId = MorimilDeliberativeArtifactContractV01.FORMAT_ID,
            runtimeAbi = MorimilDeliberativeArtifactContractV01.RUNTIME_ABI,
            architectureId = MorimilDeliberativeArtifactContractV01.ARCHITECTURE_ID,
            tokenizerId = MorimilDeliberativeArtifactContractV01.TOKENIZER_ID,
            tokenizerSha256 = digest('a'),
            contextWindowTokens =
                MorimilDeliberativeArtifactContractV01.CONTEXT_WINDOW_TOKENS,
            quantizationProfile =
                MorimilDeliberativeArtifactContractV01.QUANTIZATION_PROFILE,
            modality = MorimilDeliberativeArtifactContractV01.MODALITY,
            executionBackend =
                MorimilDeliberativeArtifactContractV01.EXECUTION_BACKEND,
            deliberationProfile =
                MorimilDeliberativeArtifactContractV01.DELIBERATION_PROFILE,
            sourceModelId = MorimilDeliberativeArtifactContractV01.SOURCE_MODEL_ID,
            sourceModelRevision = "0".repeat(40),
            sourceModelSnapshotSha256 = digest('b'),
            conversionRecipeSha256 = digest('c'),
            licenseId = MorimilDeliberativeArtifactContractV01.LICENSE_ID,
            blueprintVersion = MorimilIntrinsicMotorBlueprints.VERSION,
            techniques = MorimilIntrinsicMotorBlueprints
                .requireBlueprint(ReasoningMotorRole.DELIBERATIVE)
                .requiredTechniques
        )
        val manifestDigest = DeliberativeArtifactHashProfile.manifestDigest(manifest)
        val keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val rawPublicKey = keyPair.public.encoded.takeLast(32).toByteArray()
        val publicKeyRef = GenesisUltraHashProfile.sha256(rawPublicKey)
        val unsignedEnvelope = GenesisUltraSignatureEnvelope(
            schemaVersion = "genesis.signature.envelope.v0.1",
            signatureProfile = "genesis.signature.ed25519.v0.1",
            signerType = "guardian",
            signerId = "guardian-test",
            keyEpochId = "guardian-epoch-test-1",
            signedDomain = DeliberativeArtifactHashProfile.SIGNED_DOMAIN,
            signedDigest = manifestDigest,
            signatureValue = "0".repeat(128),
            createdAt = "2026-07-18T00:00:00Z",
            publicKeyRef = publicKeyRef
        )
        val signer = Signature.getInstance("Ed25519").apply {
            initSign(keyPair.private)
            update(GenesisUltraHashProfile.signatureEnvelopePreimage(unsignedEnvelope))
        }
        val signature = unsignedEnvelope.copy(signatureValue = signer.sign().toLowerHex())
        val signatureVerifier = GenesisUltraEd25519SignatureVerifier(
            listOf(
                GenesisUltraTrustedEd25519Key(
                    signerType = signature.signerType,
                    signerId = signature.signerId,
                    keyEpochId = signature.keyEpochId,
                    publicKeyRef = publicKeyRef,
                    rawPublicKey = rawPublicKey
                )
            )
        )
        return SignedFixture(
            manifest = manifest,
            signature = signature,
            localArtifact = ByteArrayArtifact(bytes),
            verifier = DeliberativeArtifactVerifier(signatureVerifier)
        )
    }

    private fun digest(character: Char): String {
        return "sha256:" + character.toString().repeat(64)
    }

    private fun ByteArray.toLowerHex(): String {
        return joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private data class SignedFixture(
        val manifest: DeliberativeArtifactManifest,
        val signature: GenesisUltraSignatureEnvelope,
        val localArtifact: LocalDeliberativeArtifact,
        val verifier: DeliberativeArtifactVerifier
    ) {
        fun verify(): VerifiedDeliberativeArtifact {
            return verifier.verify(manifest, signature, localArtifact)
        }
    }

    private class ByteArrayArtifact(bytes: ByteArray) : LocalDeliberativeArtifact {
        private val snapshot = bytes.copyOf()
        override val sizeBytes: Long = snapshot.size.toLong()

        override fun openReadOnly(): InputStream = ByteArrayInputStream(snapshot.copyOf())
    }

    private data class TensorState(val id: Int) : LocalDeliberativeTensorState

    private class RecordingSession(
        private val failAtPass: Int?
    ) : LocalDeliberativeSession {
        val refinementPasses = mutableListOf<Int>()
        var closeCount: Int = 0

        override suspend fun initialize(
            input: DeliberativeCoreInput
        ): Result<LocalDeliberativeTensorState> {
            return Result.success(TensorState(0))
        }

        override suspend fun refine(
            state: LocalDeliberativeTensorState,
            pass: Int
        ): Result<LocalDeliberativePassOutcome> {
            refinementPasses += pass
            if (pass == failAtPass) {
                return Result.failure(IllegalStateException("local recurrent failure"))
            }
            return Result.success(
                LocalDeliberativePassOutcome(
                    state = TensorState(pass),
                    certaintyPermille = 950,
                    stabilityPermille = 950
                )
            )
        }

        override suspend fun decode(state: LocalDeliberativeTensorState): Result<String> {
            return Result.success("respuesta intrínseca local")
        }

        override fun close() {
            closeCount += 1
        }
    }

    private class RecordingEngine(
        override val formatId: String =
            MorimilDeliberativeArtifactContractV01.FORMAT_ID,
        override val runtimeAbi: String =
            MorimilDeliberativeArtifactContractV01.RUNTIME_ABI,
        override val loadedArtifactSha256: String,
        private val failAtPass: Int? = null
    ) : LocalDeliberativeEngine {
        val sessions = mutableListOf<RecordingSession>()
        var closed: Boolean = false

        override suspend fun openSession(): Result<LocalDeliberativeSession> {
            check(!closed)
            val session = RecordingSession(failAtPass)
            sessions += session
            return Result.success(session)
        }

        override fun close() {
            closed = true
        }
    }
}
