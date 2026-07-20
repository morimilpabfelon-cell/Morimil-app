package com.morimil.app.reasoning.intrinsic

import android.os.Build
import android.os.Process
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Instant

/**
 * Opt-in physical-device harness for the exact Gemma 3n E2B research candidate.
 *
 * This test is not production authorization. It bypasses neither the signed artifact verifier
 * nor the installation boundary: it calls the official LiteRT-LM runtime directly from the
 * instrumentation APK and accepts only the already-local, exact candidate identity.
 */
@RunWith(AndroidJUnit4::class)
class Gemma3nE2bArm64CandidateHarnessV0Test {
    @Test
    fun candidateProfileAndHarnessRemainFailClosed() {
        assertEquals(
            "morimil-deliberative-v0.2.candidate.litertlm",
            MorimilDeliberativeArtifactContractV02Candidate.LOCAL_CANDIDATE_FILENAME
        )
        assertEquals(
            "sha256:2ed7bc3a0026c93d5b8a4544b352d9d00cd66ff0bac3ef6a20ac3d2cba4010d6",
            MorimilDeliberativeArtifactContractV02Candidate.ARTIFACT_SHA256
        )
        assertEquals(
            3_655_827_456L,
            MorimilDeliberativeArtifactContractV02Candidate.ARTIFACT_SIZE_BYTES
        )
        assertEquals(
            "0.14.0",
            MorimilDeliberativeArtifactContractV02Candidate.LITERT_LM_DEPENDENCY_VERSION
        )
        assertNull(MorimilDeliberativeArtifactContractV02Candidate.sourceModelRevision)
        assertFalse(MorimilDeliberativeArtifactContractV02Candidate.CERTIFIED)
        assertFalse(MorimilDeliberativeArtifactContractV02Candidate.SIGNED)
        assertFalse(MorimilDeliberativeArtifactContractV02Candidate.INSTALLED)
        assertFalse(MorimilDeliberativeArtifactContractV02Candidate.PROMOTION_ALLOWED)
    }

    @Test
    fun exactCandidateLoadsAndGeneratesOnPhysicalArm64Cpu() {
        runBlocking {
            runOptInHarness()
        }
    }

    private suspend fun runOptInHarness() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val enabled = InstrumentationRegistry.getArguments()
            .getString(ENABLE_ARGUMENT)
            ?.trim()
            ?.equals("true", ignoreCase = true) == true

        assumeTrue(
            "The 3.66 GB arm64 candidate harness is opt-in and never runs in normal CI.",
            enabled
        )

        val targetContext = instrumentation.targetContext
        val harnessRoot = File(targetContext.filesDir, HARNESS_ROOT).canonicalFile
        val inputRoot = File(harnessRoot, INPUT_DIRECTORY).canonicalFile
        val outputRoot = File(harnessRoot, OUTPUT_DIRECTORY).canonicalFile
        check(outputRoot.mkdirs() || outputRoot.isDirectory) { "arm64_harness_output_directory_unavailable" }
        val modelFile = File(
            inputRoot,
            MorimilDeliberativeArtifactContractV02Candidate.LOCAL_CANDIDATE_FILENAME
        ).canonicalFile
        val reportFile = File(outputRoot, REPORT_FILENAME).canonicalFile

        val startedAt = Instant.now().toString()
        val errors = mutableListOf<String>()
        var failure: Throwable? = null
        var status = "failed"
        var hashBeforeFirst: String? = null
        var hashBeforeSecond: String? = null
        var hashAfter: String? = null
        var loadMilliseconds: Long? = null
        var inferenceMilliseconds: Long? = null
        var responseText: String? = null
        var strictOutputPassed = false
        var engineInitialized = false
        var conversationClosed = false
        var engineClosed = false
        var conversation: Conversation? = null
        var engine: Engine? = null

        try {
            check(Build.SUPPORTED_ABIS.contains(REQUIRED_ABI)) {
                "arm64_harness_required_abi_missing:${Build.SUPPORTED_ABIS.joinToString()}"
            }
            check(Build.SUPPORTED_64_BIT_ABIS.contains(REQUIRED_ABI)) {
                "arm64_harness_required_64_bit_abi_missing"
            }
            check(Process.is64Bit()) { "arm64_harness_process_not_64_bit" }
            check(isInside(modelFile, inputRoot)) { "arm64_harness_model_path_escaped" }
            check(modelFile.isFile) { "arm64_harness_model_missing" }
            check(!Files.isSymbolicLink(modelFile.toPath())) { "arm64_harness_model_symlink_rejected" }
            check(modelFile.canRead()) { "arm64_harness_model_unreadable" }
            check(!modelFile.canWrite()) { "arm64_harness_model_must_be_read_only" }
            check(
                modelFile.name ==
                    MorimilDeliberativeArtifactContractV02Candidate.LOCAL_CANDIDATE_FILENAME
            ) { "arm64_harness_model_filename_mismatch" }
            check(
                modelFile.length() ==
                    MorimilDeliberativeArtifactContractV02Candidate.ARTIFACT_SIZE_BYTES
            ) { "arm64_harness_model_size_mismatch" }

            hashBeforeFirst = sha256(modelFile)
            hashBeforeSecond = sha256(modelFile)
            check(hashBeforeFirst == hashBeforeSecond) { "arm64_harness_hash_not_stable" }
            check(
                hashBeforeFirst == MorimilDeliberativeArtifactContractV02Candidate.ARTIFACT_SHA256
            ) { "arm64_harness_model_sha256_mismatch" }

            val loadStarted = SystemClock.elapsedRealtimeNanos()
            engine = Engine(
                EngineConfig(
                    modelPath = modelFile.path,
                    backend = Backend.CPU()
                )
            )
            engine.initialize()
            engineInitialized = true
            loadMilliseconds = elapsedMilliseconds(loadStarted)

            conversation = engine.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(
                        "Eres una prueba local limitada. Sigue exactamente el formato pedido."
                    )
                )
            )

            val inferenceStarted = SystemClock.elapsedRealtimeNanos()
            val response = conversation.sendMessage(
                "Devuelve exactamente FINAL:AZUL y nada mas."
            )
            inferenceMilliseconds = elapsedMilliseconds(inferenceStarted)
            responseText = response.contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString(separator = "") { content -> content.text }
                .trim()

            check(responseText.isNotEmpty()) { "arm64_harness_empty_text_response" }
            check(responseText.length <= MAX_RESPONSE_CHARS) {
                "arm64_harness_response_too_large:${responseText.length}"
            }
            strictOutputPassed = responseText == EXPECTED_STRICT_OUTPUT
            check(strictOutputPassed) {
                "arm64_harness_strict_output_mismatch:$responseText"
            }
            status = "passed"
        } catch (error: Throwable) {
            failure = error
            errors += errorSummary(error)
        } finally {
            conversation?.let { openedConversation ->
                runCatching { openedConversation.close() }
                    .onSuccess { conversationClosed = true }
                    .onFailure { closeError ->
                        errors += "conversation_close:${errorSummary(closeError)}"
                        if (failure == null) failure = closeError
                    }
            }
            engine?.let { openedEngine ->
                runCatching { openedEngine.close() }
                    .onSuccess { engineClosed = true }
                    .onFailure { closeError ->
                        errors += "engine_close:${errorSummary(closeError)}"
                        if (failure == null) failure = closeError
                    }
            }

            if (modelFile.isFile) {
                runCatching { sha256(modelFile) }
                    .onSuccess { digest -> hashAfter = digest }
                    .onFailure { hashError ->
                        errors += "post_inference_hash:${errorSummary(hashError)}"
                        if (failure == null) failure = hashError
                    }
            }
            if (hashBeforeFirst != null && hashAfter != null && hashBeforeFirst != hashAfter) {
                val integrityError = IllegalStateException("arm64_harness_model_changed_during_run")
                errors += errorSummary(integrityError)
                if (failure == null) failure = integrityError
            }
            if (modelFile.isFile && modelFile.canWrite()) {
                val writableError = IllegalStateException("arm64_harness_model_became_writable")
                errors += errorSummary(writableError)
                if (failure == null) failure = writableError
            }
            if (engineInitialized && !engineClosed) {
                val resourceError = IllegalStateException("arm64_harness_engine_not_closed")
                errors += errorSummary(resourceError)
                if (failure == null) failure = resourceError
            }
            if (conversation != null && !conversationClosed) {
                val resourceError = IllegalStateException("arm64_harness_conversation_not_closed")
                errors += errorSummary(resourceError)
                if (failure == null) failure = resourceError
            }

            if (failure != null) status = "failed"
            val reportError = runCatching {
                writeReportAtomically(
                    reportFile = reportFile,
                    report = buildReport(
                        status = status,
                        startedAt = startedAt,
                        modelFile = modelFile,
                        hashBeforeFirst = hashBeforeFirst,
                        hashBeforeSecond = hashBeforeSecond,
                        hashAfter = hashAfter,
                        loadMilliseconds = loadMilliseconds,
                        inferenceMilliseconds = inferenceMilliseconds,
                        responseText = responseText,
                        strictOutputPassed = strictOutputPassed,
                        engineInitialized = engineInitialized,
                        conversationClosed = conversationClosed,
                        engineClosed = engineClosed,
                        errors = errors
                    )
                )
            }.exceptionOrNull()
            if (reportError != null) {
                failure?.addSuppressed(reportError)
                if (failure == null) failure = reportError
            }
        }

        failure?.let { throw it }
    }

    private fun buildReport(
        status: String,
        startedAt: String,
        modelFile: File,
        hashBeforeFirst: String?,
        hashBeforeSecond: String?,
        hashAfter: String?,
        loadMilliseconds: Long?,
        inferenceMilliseconds: Long?,
        responseText: String?,
        strictOutputPassed: Boolean,
        engineInitialized: Boolean,
        conversationClosed: Boolean,
        engineClosed: Boolean,
        errors: List<String>
    ): JSONObject {
        return JSONObject()
            .put("schemaVersion", REPORT_SCHEMA)
            .put("status", status)
            .put("startedAt", startedAt)
            .put("completedAt", Instant.now().toString())
            .put("candidateProfileVersion", MorimilDeliberativeArtifactContractV02Candidate.PROFILE_VERSION)
            .put("artifactVersion", MorimilDeliberativeArtifactContractV02Candidate.ARTIFACT_VERSION)
            .put("artifactFilename", MorimilDeliberativeArtifactContractV02Candidate.LOCAL_CANDIDATE_FILENAME)
            .put("artifactPath", modelFile.path)
            .put("artifactSizeBytes", if (modelFile.isFile) modelFile.length() else 0L)
            .put("expectedArtifactSizeBytes", MorimilDeliberativeArtifactContractV02Candidate.ARTIFACT_SIZE_BYTES)
            .put("expectedArtifactSha256", MorimilDeliberativeArtifactContractV02Candidate.ARTIFACT_SHA256)
            .put("hashBeforeFirst", hashBeforeFirst ?: JSONObject.NULL)
            .put("hashBeforeSecond", hashBeforeSecond ?: JSONObject.NULL)
            .put("hashAfter", hashAfter ?: JSONObject.NULL)
            .put(
                "hashStable",
                hashBeforeFirst != null &&
                    hashBeforeFirst == hashBeforeSecond &&
                    hashBeforeFirst == hashAfter
            )
            .put("runtimeDependencyVersion", MorimilDeliberativeArtifactContractV02Candidate.LITERT_LM_DEPENDENCY_VERSION)
            .put("backend", "cpu")
            .put("requiredAbi", REQUIRED_ABI)
            .put("supportedAbis", JSONArray(Build.SUPPORTED_ABIS.toList()))
            .put("process64Bit", Process.is64Bit())
            .put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL)
            .put("device", Build.DEVICE)
            .put("sdkInt", Build.VERSION.SDK_INT)
            .put("buildFingerprint", Build.FINGERPRINT)
            .put("loadMilliseconds", loadMilliseconds ?: JSONObject.NULL)
            .put("inferenceMilliseconds", inferenceMilliseconds ?: JSONObject.NULL)
            .put("responseText", responseText ?: JSONObject.NULL)
            .put("expectedStrictOutput", EXPECTED_STRICT_OUTPUT)
            .put("strictOutputPassed", strictOutputPassed)
            .put("engineInitialized", engineInitialized)
            .put("conversationClosed", conversationClosed)
            .put("engineClosed", engineClosed)
            .put("sourceModelRevision", JSONObject.NULL)
            .put("certified", false)
            .put("signed", false)
            .put("installed", false)
            .put("promotionAllowed", false)
            .put("productionAuthorization", false)
            .put("newInferencePerformed", engineInitialized)
            .put("errors", JSONArray(errors))
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                check(count > 0) { "arm64_harness_hash_stream_stalled" }
                digest.update(buffer, 0, count)
            }
        }
        return "sha256:" + digest.digest().joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    private fun writeReportAtomically(reportFile: File, report: JSONObject) {
        val reportParent = checkNotNull(reportFile.parentFile).canonicalFile
        check(isInside(reportFile, reportParent)) { "arm64_harness_report_path_escaped" }
        check(reportParent.mkdirs() || reportParent.isDirectory) {
            "arm64_harness_report_directory_unavailable"
        }
        val temporary = File(reportParent, reportFile.name + ".partial")
        if (temporary.exists()) {
            check(temporary.delete()) { "arm64_harness_old_partial_report_not_removed" }
        }
        FileOutputStream(temporary).use { output ->
            output.write((report.toString(2) + "\n").toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        if (reportFile.exists()) {
            check(reportFile.delete()) { "arm64_harness_old_report_not_removed" }
        }
        check(temporary.renameTo(reportFile)) { "arm64_harness_report_atomic_rename_failed" }
    }

    private fun isInside(file: File, root: File): Boolean {
        val canonicalFile = file.canonicalFile
        val canonicalRoot = root.canonicalFile
        return canonicalFile == canonicalRoot ||
            canonicalFile.path.startsWith(canonicalRoot.path + File.separator)
    }

    private fun elapsedMilliseconds(startNanos: Long): Long {
        return (SystemClock.elapsedRealtimeNanos() - startNanos) / 1_000_000L
    }

    private fun errorSummary(error: Throwable): String {
        return buildString {
            append(error::class.java.name)
            error.message?.takeIf(String::isNotBlank)?.let { message ->
                append(':').append(message.take(1_000))
            }
        }
    }

    private companion object {
        const val ENABLE_ARGUMENT = "morimilArm64HarnessEnabled"
        const val REQUIRED_ABI = "arm64-v8a"
        const val HARNESS_ROOT = "morimil-arm64-harness"
        const val INPUT_DIRECTORY = "input"
        const val OUTPUT_DIRECTORY = "output"
        const val REPORT_FILENAME = "morimil-arm64-candidate-runtime-v0.json"
        const val REPORT_SCHEMA = "morimil.android-arm64-candidate-runtime.v0"
        const val EXPECTED_STRICT_OUTPUT = "FINAL:AZUL"
        const val MAX_RESPONSE_CHARS = 4_096
    }
}
