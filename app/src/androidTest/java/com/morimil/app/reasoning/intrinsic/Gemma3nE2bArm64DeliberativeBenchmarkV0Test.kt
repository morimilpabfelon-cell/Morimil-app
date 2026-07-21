package com.morimil.app.reasoning.intrinsic

import android.annotation.SuppressLint
import android.os.SystemClock
import android.os.Process
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
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

/**
 * Opt-in execution of the frozen 120-case v0 benchmark against the exact v0.2
 * artifact. This is instrumentation-only research code, never normal runtime.
 */
@SuppressLint("NewApi")
@RunWith(AndroidJUnit4::class)
class Gemma3nE2bArm64DeliberativeBenchmarkV0Test {
    @Test
    fun benchmarkContractRemainsExactAndFailClosed() {
        val dataset = MorimilDeliberativeBenchmarkDatasetV0.build()

        assertEquals(MorimilDeliberativeBenchmarkDatasetV0.EXPECTED_CASE_COUNT, dataset.size)
        assertEquals(
            MorimilDeliberativeBenchmarkDatasetV0.EXPECTED_DATASET_SHA256,
            MorimilDeliberativeBenchmarkDatasetV0.digest(dataset)
        )
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
        assertNull(MorimilDeliberativeArtifactContractV02Candidate.sourceModelRevision)
        assertFalse(MorimilDeliberativeArtifactContractV02Candidate.CERTIFIED)
        assertFalse(MorimilDeliberativeArtifactContractV02Candidate.SIGNED)
        assertFalse(MorimilDeliberativeArtifactContractV02Candidate.INSTALLED)
        assertFalse(MorimilDeliberativeArtifactContractV02Candidate.PROMOTION_ALLOWED)
        assertFalse(Arm64DeliberativeBenchmarkSupportV0.MEMORY_WRITE_CAPABILITY)
        assertFalse(Arm64DeliberativeBenchmarkSupportV0.IDENTITY_AUTHORITY)
        assertFalse(Arm64DeliberativeBenchmarkSupportV0.LIFECYCLE_AUTHORITY)
        assertFalse(Arm64DeliberativeBenchmarkSupportV0.NORMAL_RUNTIME_ACTIVATED)
        assertFalse(Arm64DeliberativeBenchmarkSupportV0.PRODUCTION_AUTHORIZATION)
    }

    @Test
    fun exactCandidateExecutesFrozenBenchmarkOnPhysicalArm64Cpu() {
        runBlocking { runOptInBenchmark() }
    }

    private suspend fun runOptInBenchmark() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val enabled = InstrumentationRegistry.getArguments()
            .getString(Arm64DeliberativeBenchmarkSupportV0.ENABLE_ARGUMENT)
            ?.trim()
            ?.equals("true", ignoreCase = true) == true
        assumeTrue(
            "The exact 3.66 GB, 120-case physical benchmark is opt-in and never runs in CI.",
            enabled
        )

        val targetContext = instrumentation.targetContext
        val dataset = MorimilDeliberativeBenchmarkDatasetV0.build()
        check(
            MorimilDeliberativeBenchmarkDatasetV0.digest(dataset) ==
                MorimilDeliberativeBenchmarkDatasetV0.EXPECTED_DATASET_SHA256
        ) { "arm64_benchmark_dataset_sha256_mismatch" }
        check(dataset.size == MorimilDeliberativeBenchmarkDatasetV0.EXPECTED_CASE_COUNT) {
            "arm64_benchmark_case_count_mismatch:${dataset.size}"
        }

        val support = Arm64DeliberativeBenchmarkSupportV0
        val harnessRoot = File(targetContext.filesDir, support.HARNESS_ROOT).canonicalFile
        val inputRoot = File(harnessRoot, support.INPUT_DIRECTORY).canonicalFile
        val outputRoot = File(harnessRoot, support.OUTPUT_DIRECTORY).canonicalFile
        check(outputRoot.mkdirs() || outputRoot.isDirectory) {
            "arm64_benchmark_output_directory_unavailable"
        }
        val modelFile = File(
            inputRoot,
            MorimilDeliberativeArtifactContractV02Candidate.LOCAL_CANDIDATE_FILENAME
        ).canonicalFile
        val responsesFile = File(outputRoot, support.RESPONSES_FILENAME).canonicalFile
        val physicalReportFile = File(outputRoot, support.PHYSICAL_REPORT_FILENAME).canonicalFile

        val startedAt = Instant.now().toString()
        val benchmarkStartedNanos = SystemClock.elapsedRealtimeNanos()
        val errors = mutableListOf<String>()
        val responseRecords = mutableListOf<JSONObject>()
        val caseTelemetry = mutableListOf<Arm64DeliberativeBenchmarkSupportV0.CaseTelemetry>()
        val memorySnapshots = mutableListOf<Arm64DeliberativeBenchmarkSupportV0.MemorySnapshot>()
        val environmentSnapshots =
            mutableListOf<Arm64DeliberativeBenchmarkSupportV0.EnvironmentSnapshot>()
        var failure: Throwable? = null
        var status = "failed"
        var hashBeforeFirst: String? = null
        var hashBeforeSecond: String? = null
        var hashAfter: String? = null
        var loadMilliseconds: Long? = null
        var engineInitialized = false
        var engineClosed = false
        var engine: Engine? = null

        try {
            support.enforceDeviceAndArtifactPreconditions(targetContext, modelFile, inputRoot)
            val initialMemory = support.memorySnapshot(targetContext, "before_load")
            val initialEnvironment = support.environmentSnapshot(targetContext, "before_load")
            memorySnapshots += initialMemory
            environmentSnapshots += initialEnvironment
            support.enforceStartSafety(initialMemory, initialEnvironment)

            hashBeforeFirst = support.sha256(modelFile)
            hashBeforeSecond = support.sha256(modelFile)
            check(hashBeforeFirst == hashBeforeSecond) {
                "arm64_benchmark_artifact_hash_not_stable"
            }
            check(
                hashBeforeFirst == MorimilDeliberativeArtifactContractV02Candidate.ARTIFACT_SHA256
            ) { "arm64_benchmark_artifact_sha256_mismatch" }

            val loadStarted = SystemClock.elapsedRealtimeNanos()
            val openedEngine = Engine(
                EngineConfig(modelPath = modelFile.path, backend = Backend.CPU())
            )
            engine = openedEngine
            openedEngine.initialize()
            engineInitialized = true
            loadMilliseconds = support.elapsedMilliseconds(loadStarted)

            val afterLoadMemory = support.memorySnapshot(targetContext, "after_load")
            val afterLoadEnvironment = support.environmentSnapshot(targetContext, "after_load")
            memorySnapshots += afterLoadMemory
            environmentSnapshots += afterLoadEnvironment
            support.enforceRunSafety(
                afterLoadMemory,
                afterLoadEnvironment,
                benchmarkStartedNanos,
                "after_load"
            )

            dataset.forEachIndexed { index, benchmarkCase ->
                val ordinal = index + 1
                val phase = "case_${ordinal}_${benchmarkCase.caseId}"
                val memoryBefore = support.memorySnapshot(targetContext, "${phase}_before")
                val environmentBefore = support.environmentSnapshot(
                    targetContext,
                    "${phase}_before"
                )
                memorySnapshots += memoryBefore
                environmentSnapshots += environmentBefore
                support.enforceRunSafety(
                    memoryBefore,
                    environmentBefore,
                    benchmarkStartedNanos,
                    "${phase}_before"
                )

                var conversation: Conversation? = null
                var conversationClosed = false
                var responseText = ""
                var latencyMilliseconds = 0L
                var cpuMilliseconds = 0L
                var caseError: Throwable? = null

                try {
                    val openedConversation = openedEngine.createConversation(
                        ConversationConfig(
                            systemInstruction = Contents.of(
                                MorimilDeliberativeBenchmarkDatasetV0.SYSTEM_INSTRUCTION
                            )
                        )
                    )
                    conversation = openedConversation
                    val cpuStarted = Process.getElapsedCpuTime()
                    val inferenceStarted = SystemClock.elapsedRealtimeNanos()
                    val response = withTimeout(support.MAX_INFERENCE_MILLISECONDS) {
                        openedConversation.sendMessage(
                            MorimilDeliberativeBenchmarkDatasetV0.renderPrompt(benchmarkCase)
                        )
                    }
                    latencyMilliseconds = support.elapsedMilliseconds(inferenceStarted)
                    cpuMilliseconds = Process.getElapsedCpuTime() - cpuStarted
                    responseText = response.contents.contents
                        .filterIsInstance<Content.Text>()
                        .joinToString(separator = "") { content -> content.text }
                        .trim()
                    check(responseText.isNotEmpty()) {
                        "arm64_benchmark_empty_response:${benchmarkCase.caseId}"
                    }
                    check(
                        responseText.length <=
                            MorimilDeliberativeBenchmarkDatasetV0.MAX_RESPONSE_CHARS
                    ) {
                        "arm64_benchmark_response_too_large:${benchmarkCase.caseId}:" +
                            responseText.length
                    }
                } catch (error: Throwable) {
                    caseError = error
                    throw error
                } finally {
                    conversation?.let { openedConversation ->
                        runCatching { openedConversation.close() }
                            .onSuccess { conversationClosed = true }
                            .onFailure { closeError ->
                                if (caseError == null) caseError = closeError
                            }
                    }
                }

                check(conversationClosed) {
                    "arm64_benchmark_conversation_not_closed:${benchmarkCase.caseId}"
                }
                check(caseError == null) {
                    "arm64_benchmark_case_failed:${benchmarkCase.caseId}:" +
                        support.errorSummary(checkNotNull(caseError))
                }

                val memoryAfter = support.memorySnapshot(targetContext, "${phase}_after")
                val environmentAfter = support.environmentSnapshot(
                    targetContext,
                    "${phase}_after"
                )
                memorySnapshots += memoryAfter
                environmentSnapshots += environmentAfter
                support.enforceRunSafety(
                    memoryAfter,
                    environmentAfter,
                    benchmarkStartedNanos,
                    "${phase}_after"
                )

                responseRecords += support.buildResponseRecord(
                    benchmarkCase,
                    responseText,
                    latencyMilliseconds,
                    conversationClosed
                )
                caseTelemetry += Arm64DeliberativeBenchmarkSupportV0.CaseTelemetry(
                    ordinal = ordinal,
                    caseId = benchmarkCase.caseId,
                    latencyMilliseconds = latencyMilliseconds,
                    processCpuMilliseconds = cpuMilliseconds,
                    conversationClosed = conversationClosed,
                    totalPssBeforeKilobytes = memoryBefore.totalPssKilobytes,
                    totalPssAfterKilobytes = memoryAfter.totalPssKilobytes,
                    batteryTemperatureBeforeCelsius =
                        environmentBefore.batteryTemperatureCelsius,
                    batteryTemperatureAfterCelsius =
                        environmentAfter.batteryTemperatureCelsius,
                    thermalStatusBefore = environmentBefore.thermalStatus,
                    thermalStatusAfter = environmentAfter.thermalStatus
                )

                if (ordinal < dataset.size) SystemClock.sleep(support.COOLDOWN_MILLISECONDS)
            }
            check(
                responseRecords.size ==
                    MorimilDeliberativeBenchmarkDatasetV0.EXPECTED_CASE_COUNT
            ) { "arm64_benchmark_incomplete_response_set:${responseRecords.size}" }
            status = "passed"
        } catch (error: Throwable) {
            failure = error
            errors += support.errorSummary(error)
        } finally {
            engine?.let { openedEngine ->
                runCatching { openedEngine.close() }
                    .onSuccess { engineClosed = true }
                    .onFailure { closeError ->
                        errors += "engine_close:${support.errorSummary(closeError)}"
                        if (failure == null) failure = closeError
                    }
            }
            if (modelFile.isFile) {
                runCatching { support.sha256(modelFile) }
                    .onSuccess { digest -> hashAfter = digest }
                    .onFailure { hashError ->
                        errors += "post_benchmark_hash:${support.errorSummary(hashError)}"
                        if (failure == null) failure = hashError
                    }
            }
            if (hashBeforeFirst != null && hashAfter != null && hashBeforeFirst != hashAfter) {
                val integrityError = IllegalStateException(
                    "arm64_benchmark_artifact_changed_during_run"
                )
                errors += support.errorSummary(integrityError)
                if (failure == null) failure = integrityError
            }
            if (modelFile.isFile && modelFile.canWrite()) {
                val writableError = IllegalStateException(
                    "arm64_benchmark_artifact_became_writable"
                )
                errors += support.errorSummary(writableError)
                if (failure == null) failure = writableError
            }
            if (engineInitialized && !engineClosed) {
                val lifecycleError = IllegalStateException(
                    "arm64_benchmark_engine_not_closed"
                )
                errors += support.errorSummary(lifecycleError)
                if (failure == null) failure = lifecycleError
            }
            val allConversationsClosed =
                caseTelemetry.size == responseRecords.size &&
                    caseTelemetry.all(Arm64DeliberativeBenchmarkSupportV0.CaseTelemetry::conversationClosed)
            if (responseRecords.isNotEmpty() && !allConversationsClosed) {
                val lifecycleError = IllegalStateException(
                    "arm64_benchmark_request_state_not_released"
                )
                errors += support.errorSummary(lifecycleError)
                if (failure == null) failure = lifecycleError
            }
            if (failure != null) status = "failed"

            val finalMemory = runCatching {
                support.memorySnapshot(targetContext, "after_close")
            }.onSuccess { snapshot -> memorySnapshots += snapshot }.getOrNull()
            val finalEnvironment = runCatching {
                support.environmentSnapshot(targetContext, "after_close")
            }.onSuccess { snapshot -> environmentSnapshots += snapshot }.getOrNull()

            val outputError = runCatching {
                support.writeResponsesAtomically(responsesFile, responseRecords)
                support.writeJsonAtomically(
                    physicalReportFile,
                    support.buildPhysicalReport(
                        status = status,
                        startedAt = startedAt,
                        modelFile = modelFile,
                        hashBeforeFirst = hashBeforeFirst,
                        hashBeforeSecond = hashBeforeSecond,
                        hashAfter = hashAfter,
                        loadMilliseconds = loadMilliseconds,
                        totalBenchmarkMilliseconds =
                            support.elapsedMilliseconds(benchmarkStartedNanos),
                        responseRecords = responseRecords,
                        caseTelemetry = caseTelemetry,
                        memorySnapshots = memorySnapshots,
                        environmentSnapshots = environmentSnapshots,
                        finalMemory = finalMemory,
                        finalEnvironment = finalEnvironment,
                        engineInitialized = engineInitialized,
                        engineClosed = engineClosed,
                        allConversationsClosed = allConversationsClosed,
                        errors = errors
                    )
                )
            }.exceptionOrNull()
            if (outputError != null) {
                failure?.addSuppressed(outputError)
                if (failure == null) failure = outputError
            }
        }

        failure?.let { throw it }
    }
}
