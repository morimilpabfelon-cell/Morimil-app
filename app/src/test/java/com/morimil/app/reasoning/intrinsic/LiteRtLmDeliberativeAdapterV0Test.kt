package com.morimil.app.reasoning.intrinsic

import com.morimil.app.ai.ChatTurn
import com.morimil.app.data.genesis.ultra.GenesisUltraHashProfile
import com.morimil.app.data.genesis.ultra.GenesisUltraSignatureEnvelope
import com.morimil.app.reasoning.IntrinsicReasoningRequest
import com.morimil.app.reasoning.ReasoningMotorRole
import com.morimil.app.reasoning.growth.MorimilIntrinsicMotorBlueprints
import com.morimil.app.reasoning.model.ReasoningTaskComplexity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

class LiteRtLmDeliberativeAdapterV0Test {
    @Test
    fun exactVerifiedFileRunsTwoStableLocalPassesAndReleasesConversation() = runBlocking {
        withFixture { fixture ->
            val runtime = RecordingRuntime(replies = listOf("respuesta local", "respuesta local"))
            val loader = testLoader(runtime)
            val core = VerifiedLocalDeliberativeCoreV01.create(
                fixture.verified,
                loader
            ).getOrThrow()

            val response = DeliberativeMotorV0(core).compute(request()).getOrThrow()

            assertEquals("respuesta local", response.content)
            assertTrue(response.findings.contains("deliberation_passes:2"))
            assertTrue(response.findings.contains("deliberation_stop:converged"))
            assertEquals(fixture.file.canonicalPath, runtime.loadedPaths.single())
            assertEquals(1, runtime.engine.conversations.size)
            assertEquals(1, runtime.engine.conversations.single().closeCount)
            assertFalse(runtime.engine.closed)

            core.close()
            assertTrue(runtime.engine.closed)
        }
    }

    @Test
    fun nonFileArtifactIsRejectedBeforeRuntimeLoading() = runBlocking {
        val bytes = "not-file-backed".toByteArray()
        val manifest = manifestFor(bytes)
        val verified = verified(
            manifest = manifest,
            artifact = ByteArrayArtifact(bytes)
        )
        val runtime = RecordingRuntime(replies = listOf("unused"))

        val result = LiteRtLmDeliberativeEngineLoaderV0.forRuntime(runtime).load(verified)

        assertTrue(result.isFailure)
        assertEquals("artifact_file_backing_required", result.exceptionOrNull()?.message)
        assertTrue(runtime.loadedPaths.isEmpty())
    }

    @Test
    fun artifactMutationDuringNativeLoadFailsClosed() = runBlocking {
        withFixture { fixture ->
            val runtime = RecordingRuntime(
                replies = listOf("unused"),
                onLoad = { path ->
                    val file = File(path)
                    assertTrue(file.setWritable(true))
                    file.writeText("mutated during load")
                }
            )

            val result = testLoader(runtime).load(fixture.verified)

            assertTrue(result.isFailure)
            assertEquals(
                "litertlm_artifact_changed_during_load",
                result.exceptionOrNull()?.message
            )
            assertTrue(runtime.engine.closed)
        }
    }

    @Test
    fun incompatibleRuntimeAbiIsRejectedBeforeNativeLoad() = runBlocking {
        withFixture(runtimeAbi = "litertlm.kotlin.android.v0.15.0") { fixture ->
            val runtime = RecordingRuntime(replies = listOf("unused"))

            val result = testLoader(runtime).load(fixture.verified)

            assertTrue(result.isFailure)
            assertEquals("litertlm_runtime_abi_mismatch", result.exceptionOrNull()?.message)
            assertTrue(runtime.loadedPaths.isEmpty())
        }
    }

    @Test
    fun recurrentFailureStillClosesRequestConversation() = runBlocking {
        withFixture { fixture ->
            val runtime = RecordingRuntime(
                replies = emptyList(),
                failure = IllegalStateException("native inference failed")
            )
            val core = VerifiedLocalDeliberativeCoreV01.create(
                fixture.verified,
                testLoader(runtime)
            ).getOrThrow()

            val result = DeliberativeMotorV0(core).compute(request())

            assertTrue(result.isFailure)
            assertEquals(1, runtime.engine.conversations.single().closeCount)
            core.close()
        }
    }

    @Test
    fun oversizedResponseFailsAndClosesRequestConversation() = runBlocking {
        withFixture { fixture ->
            val runtime = RecordingRuntime(replies = listOf("12345"))
            val loader = LiteRtLmDeliberativeEngineLoaderV0.forRuntime(
                runtime = runtime,
                promptPolicy = LiteRtLmDeliberativePromptPolicy(maximumResponseChars = 4)
            )
            val core = VerifiedLocalDeliberativeCoreV01.create(
                fixture.verified,
                loader
            ).getOrThrow()

            val result = DeliberativeMotorV0(core).compute(request())

            assertTrue(result.isFailure)
            assertEquals("litertlm_response_too_large", result.exceptionOrNull()?.message)
            assertEquals(1, runtime.engine.conversations.single().closeCount)
            core.close()
        }
    }

    @Test
    fun passNumbersMustBeSequentialAndStateCannotCrossSessions() = runBlocking {
        withFixture { fixture ->
            val runtime = RecordingRuntime(replies = listOf("draft", "draft"))
            val engine = testLoader(runtime).load(fixture.verified).getOrThrow()
            val first = engine.openSession().getOrThrow()
            val second = engine.openSession().getOrThrow()
            val firstState = first.initialize(coreInput()).getOrThrow()

            val outOfSequence = first.refine(firstState, 2)
            val foreign = second.refine(firstState, 1)

            assertTrue(outOfSequence.isFailure)
            assertEquals("litertlm_pass_out_of_sequence", outOfSequence.exceptionOrNull()?.message)
            assertTrue(foreign.isFailure)
            assertEquals("litertlm_state_not_owned_by_session", foreign.exceptionOrNull()?.message)
            first.close()
            second.close()
            engine.close()
        }
    }

    @Test
    fun emptyHistoryIsRejectedAndOpenedConversationIsClosedByCoreCleanup() = runBlocking {
        withFixture { fixture ->
            val runtime = RecordingRuntime(replies = listOf("unused"))
            val core = VerifiedLocalDeliberativeCoreV01.create(
                fixture.verified,
                testLoader(runtime)
            ).getOrThrow()
            val emptyRequest = IntrinsicReasoningRequest(
                systemPrompt = "Razona localmente.",
                history = emptyList(),
                taskComplexity = ReasoningTaskComplexity.DEEP_ANALYSIS
            )

            val result = DeliberativeMotorV0(core).compute(emptyRequest)

            assertTrue(result.isFailure)
            assertEquals("litertlm_history_empty", result.exceptionOrNull()?.message)
            assertTrue(runtime.engine.conversations.isEmpty())
            core.close()
        }
    }

    @Test
    fun adapterContractsExposeNoProviderNetworkMemoryIdentityOrInstaller() {
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
            LiteRtLmDeliberativeEngineLoaderV0::class.java,
            LiteRtLmDeliberativePromptPolicy::class.java,
            ReadOnlyFileDeliberativeArtifact::class.java
        )
        val exposedNames = contractTypes.flatMap { type ->
            type.methods.flatMap { method ->
                method.parameterTypes.toList() + method.returnType
            }
        }.map { type -> type.name }

        assertTrue(exposedNames.isNotEmpty())
        assertTrue(
            exposedNames.none { name -> forbidden.any { token -> name.contains(token) } }
        )
    }

    @Test
    fun runtimeContractPinsExactOfficialVersion() {
        assertEquals("0.14.0", LiteRtLmDeliberativeRuntimeV0.DEPENDENCY_VERSION)
        assertEquals("litertlm.v1", LiteRtLmDeliberativeRuntimeV0.FORMAT_ID)
        assertEquals(
            "litertlm.kotlin.android.v0.14.0",
            LiteRtLmDeliberativeRuntimeV0.RUNTIME_ABI
        )
    }

    private fun testLoader(runtime: LiteRtLmRuntime): LiteRtLmDeliberativeEngineLoaderV0 {
        return LiteRtLmDeliberativeEngineLoaderV0.forRuntime(runtime)
    }

    private fun request(): IntrinsicReasoningRequest {
        return IntrinsicReasoningRequest(
            systemPrompt = "Razona localmente sin usar servicios externos.",
            history = listOf(ChatTurn("user", "Analiza esta decisión.")),
            taskComplexity = ReasoningTaskComplexity.DEEP_ANALYSIS
        )
    }

    private fun coreInput(): DeliberativeCoreInput {
        return DeliberativeCoreInput(
            systemPrompt = "Razona localmente.",
            history = listOf(ChatTurn("user", "Analiza esta decisión.")),
            taskComplexity = ReasoningTaskComplexity.DEEP_ANALYSIS
        )
    }

    private inline fun withFixture(
        runtimeAbi: String = LiteRtLmDeliberativeRuntimeV0.RUNTIME_ABI,
        block: (FileFixture) -> Unit
    ) {
        val file = File.createTempFile("morimil-deliberative-", ".litertlm")
        try {
            val bytes = "local-morimil-test-model".toByteArray()
            file.writeBytes(bytes)
            assertTrue(file.setReadOnly())
            val manifest = manifestFor(bytes, runtimeAbi)
            val artifact = ReadOnlyFileDeliberativeArtifact.open(file)
            block(
                FileFixture(
                    file = file,
                    verified = verified(manifest, artifact)
                )
            )
        } finally {
            file.setWritable(true)
            file.delete()
        }
    }

    private fun manifestFor(
        bytes: ByteArray,
        runtimeAbi: String = LiteRtLmDeliberativeRuntimeV0.RUNTIME_ABI
    ): DeliberativeArtifactManifest {
        return DeliberativeArtifactManifest(
            schemaVersion = DeliberativeArtifactHashProfile.MANIFEST_SCHEMA,
            artifactVersion = "morimil-deliberative-test-v0",
            artifactSha256 = GenesisUltraHashProfile.sha256(bytes),
            artifactSizeBytes = bytes.size.toLong(),
            formatId = LiteRtLmDeliberativeRuntimeV0.FORMAT_ID,
            runtimeAbi = runtimeAbi,
            blueprintVersion = MorimilIntrinsicMotorBlueprints.VERSION,
            techniques = MorimilIntrinsicMotorBlueprints
                .requireBlueprint(ReasoningMotorRole.DELIBERATIVE)
                .requiredTechniques
        )
    }

    private fun verified(
        manifest: DeliberativeArtifactManifest,
        artifact: LocalDeliberativeArtifact
    ): VerifiedDeliberativeArtifact {
        val digest = DeliberativeArtifactHashProfile.manifestDigest(manifest)
        val signature = GenesisUltraSignatureEnvelope(
            schemaVersion = "genesis.signature.envelope.v0.1",
            signatureProfile = "genesis.signature.ed25519.v0.1",
            signerType = "guardian",
            signerId = "adapter-test",
            keyEpochId = "adapter-test-epoch",
            signedDomain = DeliberativeArtifactHashProfile.SIGNED_DOMAIN,
            signedDigest = digest,
            signatureValue = "0".repeat(128),
            createdAt = "2026-07-18T00:00:00Z",
            publicKeyRef = "sha256:" + "0".repeat(64)
        )
        return VerifiedDeliberativeArtifact.create(
            manifest = manifest,
            manifestDigest = digest,
            signature = signature,
            localArtifact = artifact
        )
    }

    private data class FileFixture(
        val file: File,
        val verified: VerifiedDeliberativeArtifact
    )

    private class ByteArrayArtifact(bytes: ByteArray) : LocalDeliberativeArtifact {
        private val snapshot = bytes.copyOf()
        override val sizeBytes: Long = snapshot.size.toLong()

        override fun openReadOnly(): InputStream = ByteArrayInputStream(snapshot.copyOf())
    }

    private class RecordingRuntime(
        replies: List<String>,
        failure: Throwable? = null,
        private val onLoad: (String) -> Unit = {}
    ) : LiteRtLmRuntime {
        val loadedPaths = mutableListOf<String>()
        val engine = RecordingEngine(replies, failure)

        override suspend fun load(modelPath: String): LiteRtLmRuntimeEngine {
            loadedPaths += modelPath
            onLoad(modelPath)
            return engine
        }
    }

    private class RecordingEngine(
        private val replies: List<String>,
        private val failure: Throwable?
    ) : LiteRtLmRuntimeEngine {
        val conversations = mutableListOf<RecordingConversation>()
        var closed: Boolean = false

        override suspend fun openConversation(
            systemInstruction: String
        ): LiteRtLmRuntimeConversation {
            check(!closed)
            val conversation = RecordingConversation(replies, failure)
            conversations += conversation
            return conversation
        }

        override fun close() {
            closed = true
            conversations.forEach(RecordingConversation::close)
        }
    }

    private class RecordingConversation(
        replies: List<String>,
        private val failure: Throwable?
    ) : LiteRtLmRuntimeConversation {
        private val remaining = ArrayDeque(replies)
        val prompts = mutableListOf<String>()
        var closeCount: Int = 0

        override suspend fun send(prompt: String): String {
            prompts += prompt
            failure?.let { throw it }
            return remaining.removeFirst()
        }

        override fun close() {
            closeCount += 1
        }
    }
}
