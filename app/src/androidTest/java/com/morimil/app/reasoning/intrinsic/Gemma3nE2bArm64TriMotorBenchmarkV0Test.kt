package com.morimil.app.reasoning.intrinsic

import android.annotation.SuppressLint
import android.os.Process
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.morimil.app.reasoning.ReasoningMotorRole
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

/** Physical research-only execution of the frozen dataset through Morimil's tri-motor runtime. */
@SuppressLint("NewApi")
@RunWith(AndroidJUnit4::class)
class Gemma3nE2bArm64TriMotorBenchmarkV0Test {
    @Test
    fun trimotorBenchmarkContractRemainsFrozenAndFailClosed() {
        val dataset = MorimilDeliberativeBenchmarkDatasetV0.build()
        val plans = dataset.map(MorimilTriMotorBenchmarkAdapterV0::plan)

        assertEquals(MorimilDeliberativeBenchmarkDatasetV0.EXPECTED_CASE_COUNT, plans.size)
        assertEquals(
            MorimilDeliberativeBenchmarkDatasetV0.EXPECTED_DATASET_SHA256,
            MorimilDeliberativeBenchmarkDatasetV0.digest(dataset)
        )
        assertEquals(
            MorimilTriMotorBenchmarkAdapterV0.BOUNDED_CASE_COUNT,
            plans.count { plan ->
                plan.outputProfile != TriMotorBenchmarkOutputProfileV0.GENERATIVE_FAIL_CLOSED
            }
        )
        assertEquals(
            MorimilTriMotorBenchmarkAdapterV0.GENERATIVE_CASE_COUNT,
            plans.count { plan ->
                plan.outputProfile == TriMotorBenchmarkOutputProfileV0.GENERATIVE_FAIL_CLOSED
            }
        )
        assertTrue(plans.all { plan -> plan.request.authorityPrompt?.isNotBlank() == true })
        assertFalse(Arm64TriMotorBenchmarkSupportV0.MEMORY_WRITE_CAPABILITY)
        assertFalse(Arm64TriMotorBenchmarkSupportV0.IDENTITY_AUTHORITY)
        assertFalse(Arm64TriMotorBenchmarkSupportV0.LIFECYCLE_AUTHORITY)
        assertFalse(Arm64TriMotorBenchmarkSupportV0.NORMAL_RUNTIME_ACTIVATED)
        assertFalse(Arm64TriMotorBenchmarkSupportV0.PRODUCTION_AUTHORIZATION)
    }

    @Test
    fun exactCandidateExecutesFrozenTriMotorBenchmarkOnPhysicalArm64Cpu() {
        runBlocking { runOptInBenchmark() }
    }

    private suspend fun runOptInBenchmark() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val enabled = InstrumentationRegistry.getArguments()
            .getString(Arm64TriMotorBenchmarkSupportV0.ENABLE_ARGUMENT)
            ?.trim()
            ?.equals("true", ignoreCase = true) == true
        assumeTrue(
            "The exact 3.66 GB tri-motor physical benchmark is opt-in and never runs in CI.",
            enabled
        )

        val targetContext = instrumentation.targetContext
        val dataset = MorimilDeliberativeBenchmarkDatasetV0.build()
        check(
            MorimilDeliberativeBenchmarkDatasetV0.digest(dataset) ==
                MorimilDeliberativeBenchmarkDatasetV0.EXPECTED_DATASET_SHA256
        ) { "arm64_trimotor_dataset_sha256_mismatch" }

        val physicalSupport = Arm64DeliberativeBenchmarkSupportV0
        val harnessRoot = File(targetContext.filesDir, physicalSupport.HARNESS_ROOT).canonicalFile
        val inputRoot = File(harnessRoot, physicalSupport.INPUT_DIRECTORY).canonicalFile
        val outputRoot = File(harnessRoot, physicalSupport.OUTPUT_DIRECTORY).canonicalFile
        check(outputRoot.mkdirs() || outputRoot.isDirectory) {
            "arm64_trimotor_output_directory_unavailable"
        }
        val modelFile = File(
            inputRoot,
            MorimilDeliberativeArtifactContractV02Candidate.LOCAL_CANDIDATE_FILENAME
        ).canonicalFile
        val responsesFile = File(
            outputRoot,
            Arm64TriMotorBenchmarkSupportV0.RESPONSES_FILENAME
        ).canonicalFile
        val physicalReportFile = File(
            outputRoot,
            Arm64TriMotorBenchmarkSupportV0.PHYSICAL_REPORT_FILENAME
        ).canonicalFile

        val startedAt = Instant.now().toString()
        val benchmarkStartedNanos = SystemClock.elapsedRealtimeNanos()
        val errors = mutableListOf<String>()
        val responseRecords = mutableListOf<JSONObject>()
        val caseTelemetry = mutableListOf<Arm64DeliberativeBenchmarkSupportV0.CaseTelemetry>()
        val memorySnapshots =
            mutableListOf<Arm64DeliberativeBenchmarkSupportV0.MemorySnapshot>()
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
        var deliberativeCore: Arm64TriMotorDeliberativeCoreV0? = null

        try {
            physicalSupport.enforceDeviceAndArtifactPreconditions(
                targetContext,
                modelFile,
                inputRoot
            )
            val initialMemory = physicalSupport.memorySnapshot(targetContext, "before_load")
            val initialEnvironment = physicalSupport.environmentSnapshot(targetContext, "before_load")
            memorySnapshots += initialMemory
            environmentSnapshots += initialEnvironment
            physicalSupport.enforceStartSafety(initialMemory, initialEnvironment)

            hashBeforeFirst = physicalSupport.sha256(modelFile)
            hashBeforeSecond = physicalSupport.sha256(modelFile)
            check(hashBeforeFirst == hashBeforeSecond) {
                "arm64_trimotor_artifact_hash_not_stable"
            }
            check(
                hashBeforeFirst == MorimilDeliberativeArtifactContractV02Candidate.ARTIFACT_SHA256
            ) { "arm64_trimotor_artifact_sha256_mismatch" }

            val loadStarted = SystemClock.elapsedRealtimeNanos()
            val openedEngine = Engine(
                EngineConfig(modelPath = modelFile.path, backend = Backend.CPU())
            )
            engine = openedEngine
            openedEngine.initialize()
            engineInitialized = true
            loadMilliseconds = physicalSupport.elapsedMilliseconds(loadStarted)

            val core = Arm64TriMotorDeliberativeCoreV0(openedEngine)
            deliberativeCore = core
            val runtime = BoundedLocalTriMotorResearchRuntimeFactoryV0.create(
                DeliberativeMotorV0(core)
            )
            check(runtime.availableRoles() == ReasoningMotorRole.entries.toSet()) {
                "arm64_trimotor_roles_incomplete:${runtime.availableRoles()}"
            }

            val afterLoadMemory = physicalSupport.memorySnapshot(targetContext, "after_load")
            val afterLoadEnvironment = physicalSupport.environmentSnapshot(
                targetContext,
                "after_load"
            )
            memorySnapshots += afterLoadMemory
            environmentSnapshots += afterLoadEnvironment
            physicalSupport.enforceRunSafety(
                afterLoadMemory,
                afterLoadEnvironment,
                benchmarkStartedNanos,
                "after_load"
            )

            dataset.forEachIndexed { index, benchmarkCase ->
                val ordinal = index + 1
                val phase = "case_${ordinal}_${benchmarkCase.caseId}"
                val memoryBefore = physicalSupport.memorySnapshot(
                    targetContext,
                    "${phase}_before"
                )
                val environmentBefore = physicalSupport.environmentSnapshot(
                    targetContext,
                    "${phase}_before"
                )
                memorySnapshots += memoryBefore
                environmentSnapshots += environmentBefore
                physicalSupport.enforceRunSafety(
                    memoryBefore,
                    environmentBefore,
                    benchmarkStartedNanos,
                    "${phase}_before"
                )

                val plan = MorimilTriMotorBenchmarkAdapterV0.plan(benchmarkCase)
                val openedBefore = core.openedConversationCount
                val closedBefore = core.closedConversationCount
                val cpuStarted = Process.getElapsedCpuTime()
                val inferenceStarted = SystemClock.elapsedRealtimeNanos()
                val result = withTimeout(
                    Arm64TriMotorBenchmarkSupportV0.MAXIMUM_CASE_MILLISECONDS
                ) {
                    runtime.reason(plan.request).getOrThrow()
                }
                val latencyMilliseconds = physicalSupport.elapsedMilliseconds(inferenceStarted)
                val cpuMilliseconds = Process.getElapsedCpuTime() - cpuStarted
                val presented = MorimilTriMotorBenchmarkAdapterV0.present(plan, result)
                val openedDelta = core.openedConversationCount - openedBefore
                val closedDelta = core.closedConversationCount - closedBefore
                val requestStateReleased = openedDelta == closedDelta &&
                    core.openedConversationCount == core.closedConversationCount
                check(requestStateReleased) {
                    "arm64_trimotor_request_state_not_released:${benchmarkCase.caseId}:" +
                        "$openedDelta/$closedDelta"
                }

                val memoryAfter = physicalSupport.memorySnapshot(
                    targetContext,
                    "${phase}_after"
                )
                val environmentAfter = physicalSupport.environmentSnapshot(
                    targetContext,
                    "${phase}_after"
                )
                memorySnapshots += memoryAfter
                environmentSnapshots += environmentAfter
                physicalSupport.enforceRunSafety(
                    memoryAfter,
                    environmentAfter,
                    benchmarkStartedNanos,
                    "${phase}_after"
                )

                responseRecords += Arm64TriMotorBenchmarkSupportV0.buildResponseRecord(
                    benchmarkCase = benchmarkCase,
                    plan = plan,
                    result = result,
                    presentedResponse = presented,
                    latencyMilliseconds = latencyMilliseconds,
                    requestStateReleased = requestStateReleased
                )
                caseTelemetry += Arm64DeliberativeBenchmarkSupportV0.CaseTelemetry(
                    ordinal = ordinal,
                    caseId = benchmarkCase.caseId,
                    latencyMilliseconds = latencyMilliseconds,
                    processCpuMilliseconds = cpuMilliseconds,
                    conversationClosed = requestStateReleased,
                    totalPssBeforeKilobytes = memoryBefore.totalPssKilobytes,
                    totalPssAfterKilobytes = memoryAfter.totalPssKilobytes,
                    batteryTemperatureBeforeCelsius =
                        environmentBefore.batteryTemperatureCelsius,
                    batteryTemperatureAfterCelsius =
                        environmentAfter.batteryTemperatureCelsius,
                    thermalStatusBefore = environmentBefore.thermalStatus,
                    thermalStatusAfter = environmentAfter.thermalStatus
                )

                if (ordinal < dataset.size) {
                    SystemClock.sleep(physicalSupport.COOLDOWN_MILLISECONDS)
                }
            }

            check(responseRecords.size == MorimilDeliberativeBenchmarkDatasetV0.EXPECTED_CASE_COUNT) {
                "arm64_trimotor_incomplete_response_set:${responseRecords.size}"
            }
            val activatedRoleUnion = responseRecords.flatMap { record ->
                val roles = record.getJSONArray("activatedRoles")
                (0 until roles.length()).map(roles::getString)
            }.toSet()
            check(activatedRoleUnion == ReasoningMotorRole.entries.map { it.name }.toSet()) {
                "arm64_trimotor_role_coverage_incomplete:$activatedRoleUnion"
            }
            check(core.openedConversationCount == core.closedConversationCount) {
                "arm64_trimotor_conversation_leak:${core.openedConversationCount}/" +
                    core.closedConversationCount
            }
            status = "passed"
        } catch (error: Throwable) {
            failure = error
            errors += physicalSupport.errorSummary(error)
        } finally {
            engine?.let { openedEngine ->
                runCatching { openedEngine.close() }
                    .onSuccess { engineClosed = true }
                    .onFailure { closeError ->
                        errors += "engine_close:${physicalSupport.errorSummary(closeError)}"
                        if (failure == null) failure = closeError
                    }
            }
            if (modelFile.isFile) {
                runCatching { physicalSupport.sha256(modelFile) }
                    .onSuccess { digest -> hashAfter = digest }
                    .onFailure { hashError ->
                        errors += "post_benchmark_hash:${physicalSupport.errorSummary(hashError)}"
                        if (failure == null) failure = hashError
                    }
            }
            if (hashBeforeFirst != null && hashAfter != null && hashBeforeFirst != hashAfter) {
                val integrityError = IllegalStateException(
                    "arm64_trimotor_artifact_changed_during_run"
                )
                errors += physicalSupport.errorSummary(integrityError)
                if (failure == null) failure = integrityError
            }
            if (modelFile.isFile && modelFile.canWrite()) {
                val writableError = IllegalStateException(
                    "arm64_trimotor_artifact_became_writable"
                )
                errors += physicalSupport.errorSummary(writableError)
                if (failure == null) failure = writableError
            }
            if (engineInitialized && !engineClosed) {
                val lifecycleError = IllegalStateException("arm64_trimotor_engine_not_closed")
                errors += physicalSupport.errorSummary(lifecycleError)
                if (failure == null) failure = lifecycleError
            }

            val core = deliberativeCore
            val allRequestStatesReleased = responseRecords.size ==
                MorimilDeliberativeBenchmarkDatasetV0.EXPECTED_CASE_COUNT &&
                responseRecords.all { record -> record.getBoolean("requestStateReleased") } &&
                (core == null || core.openedConversationCount == core.closedConversationCount)
            if (responseRecords.isNotEmpty() && !allRequestStatesReleased) {
                val lifecycleError = IllegalStateException(
                    "arm64_trimotor_all_request_states_not_released"
                )
                errors += physicalSupport.errorSummary(lifecycleError)
                if (failure == null) failure = lifecycleError
            }
            if (failure != null) status = "failed"

            val finalMemory = runCatching {
                physicalSupport.memorySnapshot(targetContext, "after_close")
            }.onSuccess { snapshot -> memorySnapshots += snapshot }.getOrNull()
            val finalEnvironment = runCatching {
                physicalSupport.environmentSnapshot(targetContext, "after_close")
            }.onSuccess { snapshot -> environmentSnapshots += snapshot }.getOrNull()

            val outputError = runCatching {
                physicalSupport.writeResponsesAtomically(responsesFile, responseRecords)
                physicalSupport.writeJsonAtomically(
                    physicalReportFile,
                    Arm64TriMotorBenchmarkSupportV0.buildPhysicalReport(
                        status = status,
                        startedAt = startedAt,
                        modelFile = modelFile,
                        hashBeforeFirst = hashBeforeFirst,
                        hashBeforeSecond = hashBeforeSecond,
                        hashAfter = hashAfter,
                        loadMilliseconds = loadMilliseconds,
                        totalBenchmarkMilliseconds =
                            physicalSupport.elapsedMilliseconds(benchmarkStartedNanos),
                        responseRecords = responseRecords,
                        caseTelemetry = caseTelemetry,
                        memorySnapshots = memorySnapshots,
                        environmentSnapshots = environmentSnapshots,
                        finalMemory = finalMemory,
                        finalEnvironment = finalEnvironment,
                        engineInitialized = engineInitialized,
                        engineClosed = engineClosed,
                        allRequestStatesReleased = allRequestStatesReleased,
                        openedConversationCount = core?.openedConversationCount ?: 0,
                        closedConversationCount = core?.closedConversationCount ?: 0,
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
