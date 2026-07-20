package com.morimil.app.reasoning.intrinsic

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
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
import kotlinx.coroutines.withTimeout
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
import kotlin.math.ceil

/**
 * Opt-in physical-device sustained profile for the exact Gemma 3n E2B research candidate.
 *
 * The profile runs only from the instrumentation APK. It never registers, installs, signs,
 * certifies or enables the candidate in Morimil's normal runtime.
 */
@SuppressLint("NewApi")
@RunWith(AndroidJUnit4::class)
class Gemma3nE2bArm64SustainedProfileV0Test {
    @Test
    fun profileContractRemainsFailClosed() {
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
    fun exactCandidateCompletesBoundedSustainedArm64CpuProfile() {
        runBlocking {
            runOptInProfile()
        }
    }

    private suspend fun runOptInProfile() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val enabled = InstrumentationRegistry.getArguments()
            .getString(ENABLE_ARGUMENT)
            ?.trim()
            ?.equals("true", ignoreCase = true) == true

        assumeTrue(
            "The sustained 3.66 GB ARM64 profile is opt-in and never runs in normal CI.",
            enabled
        )

        val targetContext = instrumentation.targetContext
        val harnessRoot = File(targetContext.filesDir, HARNESS_ROOT).canonicalFile
        val inputRoot = File(harnessRoot, INPUT_DIRECTORY).canonicalFile
        val outputRoot = File(harnessRoot, OUTPUT_DIRECTORY).canonicalFile
        check(outputRoot.mkdirs() || outputRoot.isDirectory) {
            "arm64_profile_output_directory_unavailable"
        }
        val modelFile = File(
            inputRoot,
            MorimilDeliberativeArtifactContractV02Candidate.LOCAL_CANDIDATE_FILENAME
        ).canonicalFile
        val reportFile = File(outputRoot, REPORT_FILENAME).canonicalFile

        val startedAt = Instant.now().toString()
        val profileStartedNanos = SystemClock.elapsedRealtimeNanos()
        val errors = mutableListOf<String>()
        val rounds = mutableListOf<RoundResult>()
        val memorySnapshots = mutableListOf<MemorySnapshot>()
        val environmentSnapshots = mutableListOf<EnvironmentSnapshot>()
        var failure: Throwable? = null
        var status = "failed"
        var researchGatePassed: Boolean
        var hashBeforeFirst: String? = null
        var hashBeforeSecond: String? = null
        var hashAfter: String? = null
        var loadMilliseconds: Long? = null
        var engineInitialized = false
        var engineClosed = false
        var engine: Engine? = null

        try {
            check(Build.SUPPORTED_ABIS.contains(REQUIRED_ABI)) {
                "arm64_profile_required_abi_missing:${Build.SUPPORTED_ABIS.joinToString()}"
            }
            check(Build.SUPPORTED_64_BIT_ABIS.contains(REQUIRED_ABI)) {
                "arm64_profile_required_64_bit_abi_missing"
            }
            check(Process.is64Bit()) { "arm64_profile_process_not_64_bit" }
            check(isInside(modelFile, inputRoot)) { "arm64_profile_model_path_escaped" }
            check(modelFile.isFile) { "arm64_profile_model_missing" }
            check(!Files.isSymbolicLink(modelFile.toPath())) {
                "arm64_profile_model_symlink_rejected"
            }
            check(modelFile.canRead()) { "arm64_profile_model_unreadable" }
            check(!modelFile.canWrite()) { "arm64_profile_model_must_be_read_only" }
            check(
                modelFile.name ==
                    MorimilDeliberativeArtifactContractV02Candidate.LOCAL_CANDIDATE_FILENAME
            ) { "arm64_profile_model_filename_mismatch" }
            check(
                modelFile.length() ==
                    MorimilDeliberativeArtifactContractV02Candidate.ARTIFACT_SIZE_BYTES
            ) { "arm64_profile_model_size_mismatch" }

            val initialEnvironment = environmentSnapshot(targetContext, "before_load")
            environmentSnapshots += initialEnvironment
            enforceStartSafety(initialEnvironment)
            memorySnapshots += memorySnapshot(targetContext, "before_load")

            hashBeforeFirst = sha256(modelFile)
            hashBeforeSecond = sha256(modelFile)
            check(hashBeforeFirst == hashBeforeSecond) { "arm64_profile_hash_not_stable" }
            check(
                hashBeforeFirst == MorimilDeliberativeArtifactContractV02Candidate.ARTIFACT_SHA256
            ) { "arm64_profile_model_sha256_mismatch" }

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

            memorySnapshots += memorySnapshot(targetContext, "after_load")
            environmentSnapshots += environmentSnapshot(targetContext, "after_load").also {
                enforceRunSafety(it, "after_load")
            }

            PROFILE_CASES.forEachIndexed { index, profileCase ->
                val roundNumber = index + 1
                val environmentBefore = environmentSnapshot(
                    targetContext,
                    "round_${roundNumber}_before"
                )
                environmentSnapshots += environmentBefore
                enforceRunSafety(environmentBefore, "round_${roundNumber}_before")
                val memoryBefore = memorySnapshot(
                    targetContext,
                    "round_${roundNumber}_before"
                )
                memorySnapshots += memoryBefore

                var conversation: Conversation? = null
                var conversationClosed = false
                var responseText = ""
                var latencyMilliseconds = 0L
                var cpuMilliseconds = 0L
                var roundError: String? = null

                try {
                    conversation = engine.createConversation(
                        ConversationConfig(
                            systemInstruction = Contents.of(
                                "Eres una prueba local limitada. Devuelve solo el formato pedido."
                            )
                        )
                    )
                    val cpuStarted = Process.getElapsedCpuTime()
                    val inferenceStarted = SystemClock.elapsedRealtimeNanos()
                    val response = withTimeout(MAX_INFERENCE_MILLISECONDS) {
                        conversation.sendMessage(profileCase.prompt)
                    }
                    latencyMilliseconds = elapsedMilliseconds(inferenceStarted)
                    cpuMilliseconds = Process.getElapsedCpuTime() - cpuStarted
                    responseText = response.contents.contents
                        .filterIsInstance<Content.Text>()
                        .joinToString(separator = "") { content -> content.text }
                        .trim()
                    check(responseText.isNotEmpty()) {
                        "arm64_profile_empty_response_round_$roundNumber"
                    }
                    check(responseText.length <= MAX_RESPONSE_CHARS) {
                        "arm64_profile_response_too_large_round_$roundNumber:${responseText.length}"
                    }
                } catch (error: Throwable) {
                    roundError = errorSummary(error)
                    throw error
                } finally {
                    conversation?.let { openedConversation ->
                        runCatching { openedConversation.close() }
                            .onSuccess { conversationClosed = true }
                            .onFailure { closeError ->
                                val summary = "conversation_close_round_$roundNumber:${errorSummary(closeError)}"
                                errors += summary
                                if (roundError == null) roundError = summary
                                if (failure == null) failure = closeError
                            }
                    }

                    val memoryAfter = memorySnapshot(
                        targetContext,
                        "round_${roundNumber}_after"
                    )
                    memorySnapshots += memoryAfter
                    val environmentAfter = environmentSnapshot(
                        targetContext,
                        "round_${roundNumber}_after"
                    )
                    environmentSnapshots += environmentAfter
                    rounds += RoundResult(
                        round = roundNumber,
                        caseId = profileCase.id,
                        expected = profileCase.expected,
                        response = responseText,
                        strictOutputPassed = responseText == profileCase.expected,
                        latencyMilliseconds = latencyMilliseconds,
                        processCpuMilliseconds = cpuMilliseconds,
                        conversationClosed = conversationClosed,
                        memoryBefore = memoryBefore,
                        memoryAfter = memoryAfter,
                        environmentBefore = environmentBefore,
                        environmentAfter = environmentAfter,
                        error = roundError
                    )
                    enforceRunSafety(environmentAfter, "round_${roundNumber}_after")
                }

                if (roundNumber < PROFILE_CASES.size) {
                    SystemClock.sleep(COOLDOWN_MILLISECONDS)
                }
            }

            status = "passed"
        } catch (error: Throwable) {
            failure = failure ?: error
            errors += errorSummary(error)
        } finally {
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
                        errors += "post_profile_hash:${errorSummary(hashError)}"
                        if (failure == null) failure = hashError
                    }
            }
            if (hashBeforeFirst != null && hashAfter != null && hashBeforeFirst != hashAfter) {
                val integrityError = IllegalStateException("arm64_profile_model_changed_during_run")
                errors += errorSummary(integrityError)
                if (failure == null) failure = integrityError
            }
            if (modelFile.isFile && modelFile.canWrite()) {
                val writableError = IllegalStateException("arm64_profile_model_became_writable")
                errors += errorSummary(writableError)
                if (failure == null) failure = writableError
            }
            if (engineInitialized && !engineClosed) {
                val resourceError = IllegalStateException("arm64_profile_engine_not_closed")
                errors += errorSummary(resourceError)
                if (failure == null) failure = resourceError
            }

            runCatching {
                memorySnapshot(targetContext, "after_close")
            }.onSuccess { snapshot -> memorySnapshots += snapshot }
            val finalEnvironment = runCatching {
                environmentSnapshot(targetContext, "after_close")
            }.onSuccess { snapshot -> environmentSnapshots += snapshot }.getOrNull()

            val summary = buildSummary(
                rounds = rounds,
                memorySnapshots = memorySnapshots,
                environmentSnapshots = environmentSnapshots,
                loadMilliseconds = loadMilliseconds,
                totalProfileMilliseconds = elapsedMilliseconds(profileStartedNanos),
                hashStable = hashBeforeFirst != null &&
                    hashBeforeFirst == hashBeforeSecond &&
                    hashBeforeFirst == hashAfter,
                engineInitialized = engineInitialized,
                engineClosed = engineClosed,
                finalEnvironment = finalEnvironment
            )
            researchGatePassed = failure == null && evaluateResearchGate(summary)
            if (failure == null && !researchGatePassed) {
                val gateError = IllegalStateException("arm64_profile_research_gate_not_passed")
                errors += errorSummary(gateError)
                failure = gateError
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
                        rounds = rounds,
                        memorySnapshots = memorySnapshots,
                        environmentSnapshots = environmentSnapshots,
                        summary = summary,
                        researchGatePassed = researchGatePassed,
                        engineInitialized = engineInitialized,
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

    private fun buildSummary(
        rounds: List<RoundResult>,
        memorySnapshots: List<MemorySnapshot>,
        environmentSnapshots: List<EnvironmentSnapshot>,
        loadMilliseconds: Long?,
        totalProfileMilliseconds: Long,
        hashStable: Boolean,
        engineInitialized: Boolean,
        engineClosed: Boolean,
        finalEnvironment: EnvironmentSnapshot?
    ): ProfileSummary {
        val latencies = rounds.map(RoundResult::latencyMilliseconds).filter { it > 0L }
        val initialEnvironment = environmentSnapshots.firstOrNull()
        val maximumBatteryTemperature = environmentSnapshots
            .mapNotNull(EnvironmentSnapshot::batteryTemperatureCelsius)
            .maxOrNull()
        val maximumThermalStatus = environmentSnapshots
            .maxOfOrNull(EnvironmentSnapshot::thermalStatus)
        return ProfileSummary(
            requestedRounds = PROFILE_CASES.size,
            completedRounds = rounds.size,
            strictOutputPassCount = rounds.count(RoundResult::strictOutputPassed),
            allConversationsClosed = rounds.all(RoundResult::conversationClosed),
            loadMilliseconds = loadMilliseconds,
            minimumInferenceMilliseconds = latencies.minOrNull(),
            medianInferenceMilliseconds = percentile(latencies, 0.50),
            p95InferenceMilliseconds = percentile(latencies, 0.95),
            maximumInferenceMilliseconds = latencies.maxOrNull(),
            averageInferenceMilliseconds = latencies
                .takeIf { it.isNotEmpty() }
                ?.average(),
            totalProfileMilliseconds = totalProfileMilliseconds,
            peakTotalPssKilobytes = memorySnapshots.maxOfOrNull(MemorySnapshot::totalPssKilobytes),
            peakNativePssKilobytes = memorySnapshots.maxOfOrNull(MemorySnapshot::nativePssKilobytes),
            minimumSystemAvailableBytes = memorySnapshots.minOfOrNull(MemorySnapshot::systemAvailableBytes),
            lowMemoryObserved = memorySnapshots.any(MemorySnapshot::systemLowMemory),
            initialBatteryLevelPercent = initialEnvironment?.batteryLevelPercent,
            finalBatteryLevelPercent = finalEnvironment?.batteryLevelPercent,
            initialBatteryTemperatureCelsius = initialEnvironment?.batteryTemperatureCelsius,
            finalBatteryTemperatureCelsius = finalEnvironment?.batteryTemperatureCelsius,
            maximumBatteryTemperatureCelsius = maximumBatteryTemperature,
            batteryTemperatureIncreaseCelsius = batteryTemperatureIncrease(
                initialEnvironment?.batteryTemperatureCelsius,
                finalEnvironment?.batteryTemperatureCelsius
            ),
            maximumThermalStatus = maximumThermalStatus,
            maximumThermalStatusName = maximumThermalStatus?.let(::thermalStatusName),
            hashStable = hashStable,
            engineInitialized = engineInitialized,
            engineClosed = engineClosed
        )
    }

    private fun evaluateResearchGate(summary: ProfileSummary): Boolean {
        return summary.completedRounds == summary.requestedRounds &&
            summary.strictOutputPassCount == summary.requestedRounds &&
            summary.allConversationsClosed &&
            summary.engineInitialized &&
            summary.engineClosed &&
            summary.hashStable &&
            summary.maximumInferenceMilliseconds != null &&
            summary.maximumInferenceMilliseconds <= MAX_GATE_INFERENCE_MILLISECONDS &&
            summary.p95InferenceMilliseconds != null &&
            summary.p95InferenceMilliseconds <= MAX_GATE_P95_MILLISECONDS &&
            summary.peakTotalPssKilobytes != null &&
            summary.peakTotalPssKilobytes <= MAX_GATE_TOTAL_PSS_KILOBYTES &&
            summary.maximumBatteryTemperatureCelsius != null &&
            summary.maximumBatteryTemperatureCelsius < MAX_BATTERY_TEMPERATURE_CELSIUS &&
            summary.batteryTemperatureIncreaseCelsius != null &&
            summary.batteryTemperatureIncreaseCelsius <= MAX_BATTERY_TEMPERATURE_INCREASE_CELSIUS &&
            summary.maximumThermalStatus != null &&
            summary.maximumThermalStatus < PowerManager.THERMAL_STATUS_SEVERE &&
            !summary.lowMemoryObserved
    }

    private fun enforceStartSafety(snapshot: EnvironmentSnapshot) {
        check(snapshot.batteryLevelPercent != null) { "arm64_profile_battery_level_unavailable" }
        check(snapshot.batteryLevelPercent >= MINIMUM_START_BATTERY_PERCENT) {
            "arm64_profile_battery_level_too_low:${snapshot.batteryLevelPercent}"
        }
        check(snapshot.batteryTemperatureCelsius != null) {
            "arm64_profile_battery_temperature_unavailable"
        }
        check(snapshot.batteryTemperatureCelsius < MAX_START_BATTERY_TEMPERATURE_CELSIUS) {
            "arm64_profile_start_temperature_too_high:${snapshot.batteryTemperatureCelsius}"
        }
        check(snapshot.thermalStatus < PowerManager.THERMAL_STATUS_SEVERE) {
            "arm64_profile_start_thermal_status_unsafe:${snapshot.thermalStatusName}"
        }
    }

    private fun enforceRunSafety(snapshot: EnvironmentSnapshot, phase: String) {
        check(snapshot.batteryTemperatureCelsius != null) {
            "arm64_profile_battery_temperature_unavailable:$phase"
        }
        check(snapshot.batteryTemperatureCelsius < MAX_BATTERY_TEMPERATURE_CELSIUS) {
            "arm64_profile_temperature_limit:$phase:${snapshot.batteryTemperatureCelsius}"
        }
        check(snapshot.thermalStatus < PowerManager.THERMAL_STATUS_SEVERE) {
            "arm64_profile_thermal_limit:$phase:${snapshot.thermalStatusName}"
        }
    }

    private fun memorySnapshot(context: Context, phase: String): MemorySnapshot {
        val processMemory = Debug.MemoryInfo()
        Debug.getMemoryInfo(processMemory)
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val systemMemory = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(systemMemory)
        val runtime = Runtime.getRuntime()
        return MemorySnapshot(
            phase = phase,
            elapsedRealtimeMilliseconds = SystemClock.elapsedRealtime(),
            totalPssKilobytes = processMemory.totalPss,
            nativePssKilobytes = processMemory.nativePss,
            dalvikPssKilobytes = processMemory.dalvikPss,
            otherPssKilobytes = processMemory.otherPss,
            javaHeapUsedBytes = runtime.totalMemory() - runtime.freeMemory(),
            javaHeapCommittedBytes = runtime.totalMemory(),
            javaHeapMaximumBytes = runtime.maxMemory(),
            systemAvailableBytes = systemMemory.availMem,
            systemTotalBytes = systemMemory.totalMem,
            systemLowMemory = systemMemory.lowMemory,
            lowMemoryThresholdBytes = systemMemory.threshold
        )
    }

    private fun environmentSnapshot(context: Context, phase: String): EnvironmentSnapshot {
        val batteryManager = context.getSystemService(BatteryManager::class.java)
        val powerManager = context.getSystemService(PowerManager::class.java)
        val batteryIntent = stickyBatteryIntent(context)
        val thermalStatus = powerManager.currentThermalStatus
        val temperatureTenths = batteryIntent?.getIntExtra(
            BatteryManager.EXTRA_TEMPERATURE,
            Int.MIN_VALUE
        ) ?: Int.MIN_VALUE
        return EnvironmentSnapshot(
            phase = phase,
            elapsedRealtimeMilliseconds = SystemClock.elapsedRealtime(),
            batteryLevelPercent = batteryProperty(
                batteryManager,
                BatteryManager.BATTERY_PROPERTY_CAPACITY
            ),
            batteryTemperatureCelsius = temperatureTenths
                .takeUnless { it == Int.MIN_VALUE }
                ?.div(10.0),
            batteryCurrentNowMicroamps = batteryProperty(
                batteryManager,
                BatteryManager.BATTERY_PROPERTY_CURRENT_NOW
            ),
            batteryChargeCounterMicroampHours = batteryProperty(
                batteryManager,
                BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER
            ),
            batteryStatus = batteryIntent?.getIntExtra(
                BatteryManager.EXTRA_STATUS,
                Int.MIN_VALUE
            )?.takeUnless { it == Int.MIN_VALUE },
            batteryPlugged = batteryIntent?.getIntExtra(
                BatteryManager.EXTRA_PLUGGED,
                Int.MIN_VALUE
            )?.takeUnless { it == Int.MIN_VALUE },
            thermalStatus = thermalStatus,
            thermalStatusName = thermalStatusName(thermalStatus)
        )
    }

    @Suppress("DEPRECATION")
    private fun stickyBatteryIntent(context: Context): Intent? {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(null, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(null, filter)
        }
    }

    private fun batteryProperty(batteryManager: BatteryManager, property: Int): Int? {
        return batteryManager.getIntProperty(property).takeUnless { it == Int.MIN_VALUE }
    }

    private fun buildReport(
        status: String,
        startedAt: String,
        modelFile: File,
        hashBeforeFirst: String?,
        hashBeforeSecond: String?,
        hashAfter: String?,
        loadMilliseconds: Long?,
        rounds: List<RoundResult>,
        memorySnapshots: List<MemorySnapshot>,
        environmentSnapshots: List<EnvironmentSnapshot>,
        summary: ProfileSummary,
        researchGatePassed: Boolean,
        engineInitialized: Boolean,
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
            .put("hashStable", summary.hashStable)
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
            .put("rounds", JSONArray(rounds.map(RoundResult::toJson)))
            .put("memorySnapshots", JSONArray(memorySnapshots.map(MemorySnapshot::toJson)))
            .put("environmentSnapshots", JSONArray(environmentSnapshots.map(EnvironmentSnapshot::toJson)))
            .put("summary", summary.toJson())
            .put("researchGateThresholds", researchGateThresholdsJson())
            .put("researchGatePassed", researchGatePassed)
            .put("engineInitialized", engineInitialized)
            .put("engineClosed", engineClosed)
            .put("sourceModelRevision", JSONObject.NULL)
            .put("certified", false)
            .put("signed", false)
            .put("installed", false)
            .put("promotionAllowed", false)
            .put("productionAuthorization", false)
            .put("normalRuntimeActivated", false)
            .put("newInferencePerformed", engineInitialized)
            .put("errors", JSONArray(errors))
    }

    private fun researchGateThresholdsJson(): JSONObject {
        return JSONObject()
            .put("roundsRequired", PROFILE_CASES.size)
            .put("strictOutputsRequired", PROFILE_CASES.size)
            .put("maximumInferenceMilliseconds", MAX_GATE_INFERENCE_MILLISECONDS)
            .put("maximumP95InferenceMilliseconds", MAX_GATE_P95_MILLISECONDS)
            .put("maximumTotalPssKilobytes", MAX_GATE_TOTAL_PSS_KILOBYTES)
            .put("maximumBatteryTemperatureCelsiusExclusive", MAX_BATTERY_TEMPERATURE_CELSIUS)
            .put("maximumBatteryTemperatureIncreaseCelsius", MAX_BATTERY_TEMPERATURE_INCREASE_CELSIUS)
            .put("maximumThermalStatusExclusive", PowerManager.THERMAL_STATUS_SEVERE)
            .put("lowMemoryAllowed", false)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                check(count > 0) { "arm64_profile_hash_stream_stalled" }
                digest.update(buffer, 0, count)
            }
        }
        return "sha256:" + digest.digest().joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    private fun writeReportAtomically(reportFile: File, report: JSONObject) {
        val reportParent = checkNotNull(reportFile.parentFile).canonicalFile
        check(isInside(reportFile, reportParent)) { "arm64_profile_report_path_escaped" }
        check(reportParent.mkdirs() || reportParent.isDirectory) {
            "arm64_profile_report_directory_unavailable"
        }
        val temporary = File(reportParent, reportFile.name + ".partial")
        if (temporary.exists()) {
            check(temporary.delete()) { "arm64_profile_old_partial_report_not_removed" }
        }
        FileOutputStream(temporary).use { output ->
            output.write((report.toString(2) + "\n").toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        if (reportFile.exists()) {
            check(reportFile.delete()) { "arm64_profile_old_report_not_removed" }
        }
        check(temporary.renameTo(reportFile)) { "arm64_profile_report_atomic_rename_failed" }
    }

    private fun isInside(file: File, root: File): Boolean {
        val canonicalFile = file.canonicalFile
        val canonicalRoot = root.canonicalFile
        return canonicalFile == canonicalRoot ||
            canonicalFile.path.startsWith(canonicalRoot.path + File.separator)
    }

    private fun percentile(values: List<Long>, fraction: Double): Long? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val index = (ceil(sorted.size * fraction).toInt() - 1).coerceIn(sorted.indices)
        return sorted[index]
    }

    private fun batteryTemperatureIncrease(initial: Double?, final: Double?): Double? {
        if (initial == null || final == null) return null
        return final - initial
    }

    private fun thermalStatusName(status: Int): String {
        return when (status) {
            PowerManager.THERMAL_STATUS_NONE -> "none"
            PowerManager.THERMAL_STATUS_LIGHT -> "light"
            PowerManager.THERMAL_STATUS_MODERATE -> "moderate"
            PowerManager.THERMAL_STATUS_SEVERE -> "severe"
            PowerManager.THERMAL_STATUS_CRITICAL -> "critical"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "emergency"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "shutdown"
            else -> "unknown_$status"
        }
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

    private data class ProfileCase(
        val id: String,
        val prompt: String,
        val expected: String
    )

    private data class MemorySnapshot(
        val phase: String,
        val elapsedRealtimeMilliseconds: Long,
        val totalPssKilobytes: Int,
        val nativePssKilobytes: Int,
        val dalvikPssKilobytes: Int,
        val otherPssKilobytes: Int,
        val javaHeapUsedBytes: Long,
        val javaHeapCommittedBytes: Long,
        val javaHeapMaximumBytes: Long,
        val systemAvailableBytes: Long,
        val systemTotalBytes: Long,
        val systemLowMemory: Boolean,
        val lowMemoryThresholdBytes: Long
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("phase", phase)
            .put("elapsedRealtimeMilliseconds", elapsedRealtimeMilliseconds)
            .put("totalPssKilobytes", totalPssKilobytes)
            .put("nativePssKilobytes", nativePssKilobytes)
            .put("dalvikPssKilobytes", dalvikPssKilobytes)
            .put("otherPssKilobytes", otherPssKilobytes)
            .put("javaHeapUsedBytes", javaHeapUsedBytes)
            .put("javaHeapCommittedBytes", javaHeapCommittedBytes)
            .put("javaHeapMaximumBytes", javaHeapMaximumBytes)
            .put("systemAvailableBytes", systemAvailableBytes)
            .put("systemTotalBytes", systemTotalBytes)
            .put("systemLowMemory", systemLowMemory)
            .put("lowMemoryThresholdBytes", lowMemoryThresholdBytes)
    }

    private data class EnvironmentSnapshot(
        val phase: String,
        val elapsedRealtimeMilliseconds: Long,
        val batteryLevelPercent: Int?,
        val batteryTemperatureCelsius: Double?,
        val batteryCurrentNowMicroamps: Int?,
        val batteryChargeCounterMicroampHours: Int?,
        val batteryStatus: Int?,
        val batteryPlugged: Int?,
        val thermalStatus: Int,
        val thermalStatusName: String
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("phase", phase)
            .put("elapsedRealtimeMilliseconds", elapsedRealtimeMilliseconds)
            .put("batteryLevelPercent", batteryLevelPercent ?: JSONObject.NULL)
            .put("batteryTemperatureCelsius", batteryTemperatureCelsius ?: JSONObject.NULL)
            .put("batteryCurrentNowMicroamps", batteryCurrentNowMicroamps ?: JSONObject.NULL)
            .put("batteryChargeCounterMicroampHours", batteryChargeCounterMicroampHours ?: JSONObject.NULL)
            .put("batteryStatus", batteryStatus ?: JSONObject.NULL)
            .put("batteryPlugged", batteryPlugged ?: JSONObject.NULL)
            .put("thermalStatus", thermalStatus)
            .put("thermalStatusName", thermalStatusName)
    }

    private data class RoundResult(
        val round: Int,
        val caseId: String,
        val expected: String,
        val response: String,
        val strictOutputPassed: Boolean,
        val latencyMilliseconds: Long,
        val processCpuMilliseconds: Long,
        val conversationClosed: Boolean,
        val memoryBefore: MemorySnapshot,
        val memoryAfter: MemorySnapshot,
        val environmentBefore: EnvironmentSnapshot,
        val environmentAfter: EnvironmentSnapshot,
        val error: String?
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("round", round)
            .put("caseId", caseId)
            .put("expected", expected)
            .put("response", response)
            .put("strictOutputPassed", strictOutputPassed)
            .put("latencyMilliseconds", latencyMilliseconds)
            .put("processCpuMilliseconds", processCpuMilliseconds)
            .put("conversationClosed", conversationClosed)
            .put("memoryBefore", memoryBefore.toJson())
            .put("memoryAfter", memoryAfter.toJson())
            .put("environmentBefore", environmentBefore.toJson())
            .put("environmentAfter", environmentAfter.toJson())
            .put("error", error ?: JSONObject.NULL)
    }

    private data class ProfileSummary(
        val requestedRounds: Int,
        val completedRounds: Int,
        val strictOutputPassCount: Int,
        val allConversationsClosed: Boolean,
        val loadMilliseconds: Long?,
        val minimumInferenceMilliseconds: Long?,
        val medianInferenceMilliseconds: Long?,
        val p95InferenceMilliseconds: Long?,
        val maximumInferenceMilliseconds: Long?,
        val averageInferenceMilliseconds: Double?,
        val totalProfileMilliseconds: Long,
        val peakTotalPssKilobytes: Int?,
        val peakNativePssKilobytes: Int?,
        val minimumSystemAvailableBytes: Long?,
        val lowMemoryObserved: Boolean,
        val initialBatteryLevelPercent: Int?,
        val finalBatteryLevelPercent: Int?,
        val initialBatteryTemperatureCelsius: Double?,
        val finalBatteryTemperatureCelsius: Double?,
        val maximumBatteryTemperatureCelsius: Double?,
        val batteryTemperatureIncreaseCelsius: Double?,
        val maximumThermalStatus: Int?,
        val maximumThermalStatusName: String?,
        val hashStable: Boolean,
        val engineInitialized: Boolean,
        val engineClosed: Boolean
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("requestedRounds", requestedRounds)
            .put("completedRounds", completedRounds)
            .put("strictOutputPassCount", strictOutputPassCount)
            .put("allConversationsClosed", allConversationsClosed)
            .put("loadMilliseconds", loadMilliseconds ?: JSONObject.NULL)
            .put("minimumInferenceMilliseconds", minimumInferenceMilliseconds ?: JSONObject.NULL)
            .put("medianInferenceMilliseconds", medianInferenceMilliseconds ?: JSONObject.NULL)
            .put("p95InferenceMilliseconds", p95InferenceMilliseconds ?: JSONObject.NULL)
            .put("maximumInferenceMilliseconds", maximumInferenceMilliseconds ?: JSONObject.NULL)
            .put("averageInferenceMilliseconds", averageInferenceMilliseconds ?: JSONObject.NULL)
            .put("totalProfileMilliseconds", totalProfileMilliseconds)
            .put("peakTotalPssKilobytes", peakTotalPssKilobytes ?: JSONObject.NULL)
            .put("peakNativePssKilobytes", peakNativePssKilobytes ?: JSONObject.NULL)
            .put("minimumSystemAvailableBytes", minimumSystemAvailableBytes ?: JSONObject.NULL)
            .put("lowMemoryObserved", lowMemoryObserved)
            .put("initialBatteryLevelPercent", initialBatteryLevelPercent ?: JSONObject.NULL)
            .put("finalBatteryLevelPercent", finalBatteryLevelPercent ?: JSONObject.NULL)
            .put("initialBatteryTemperatureCelsius", initialBatteryTemperatureCelsius ?: JSONObject.NULL)
            .put("finalBatteryTemperatureCelsius", finalBatteryTemperatureCelsius ?: JSONObject.NULL)
            .put("maximumBatteryTemperatureCelsius", maximumBatteryTemperatureCelsius ?: JSONObject.NULL)
            .put("batteryTemperatureIncreaseCelsius", batteryTemperatureIncreaseCelsius ?: JSONObject.NULL)
            .put("maximumThermalStatus", maximumThermalStatus ?: JSONObject.NULL)
            .put("maximumThermalStatusName", maximumThermalStatusName ?: JSONObject.NULL)
            .put("hashStable", hashStable)
            .put("engineInitialized", engineInitialized)
            .put("engineClosed", engineClosed)
    }

    private companion object {
        const val ENABLE_ARGUMENT = "morimilArm64SustainedProfileEnabled"
        const val REQUIRED_ABI = "arm64-v8a"
        const val HARNESS_ROOT = "morimil-arm64-sustained-profile"
        const val INPUT_DIRECTORY = "input"
        const val OUTPUT_DIRECTORY = "output"
        const val REPORT_FILENAME = "morimil-arm64-sustained-profile-v0.json"
        const val REPORT_SCHEMA = "morimil.android-arm64-sustained-profile.v0"
        const val MAX_RESPONSE_CHARS = 4_096
        const val MAX_INFERENCE_MILLISECONDS = 60_000L
        const val COOLDOWN_MILLISECONDS = 1_000L
        const val MINIMUM_START_BATTERY_PERCENT = 30
        const val MAX_START_BATTERY_TEMPERATURE_CELSIUS = 42.0
        const val MAX_BATTERY_TEMPERATURE_CELSIUS = 45.0
        const val MAX_BATTERY_TEMPERATURE_INCREASE_CELSIUS = 8.0
        const val MAX_GATE_INFERENCE_MILLISECONDS = 30_000L
        const val MAX_GATE_P95_MILLISECONDS = 20_000L
        const val MAX_GATE_TOTAL_PSS_KILOBYTES = 8 * 1024 * 1024

        val PROFILE_CASES = listOf(
            ProfileCase("exact_blue_1", "Devuelve exactamente FINAL:AZUL y nada mas.", "FINAL:AZUL"),
            ProfileCase("exact_number_1", "Devuelve exactamente FINAL:7 y nada mas.", "FINAL:7"),
            ProfileCase("exact_green", "Devuelve exactamente FINAL:VERDE y nada mas.", "FINAL:VERDE"),
            ProfileCase("second_letter", "Devuelve la segunda letra de MORIMIL usando exactamente FINAL:letra.", "FINAL:O"),
            ProfileCase("exact_blue_2", "Devuelve exactamente FINAL:AZUL y nada mas.", "FINAL:AZUL"),
            ProfileCase("exact_number_2", "Devuelve exactamente FINAL:7 y nada mas.", "FINAL:7")
        )
    }
}
