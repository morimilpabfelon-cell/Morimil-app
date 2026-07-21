package com.morimil.app.reasoning.intrinsic

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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Instant
import kotlin.math.ceil

internal object Arm64DeliberativeBenchmarkSupportV0 {
    const val ENABLE_ARGUMENT = "morimilArm64DeliberativeBenchmarkEnabled"
    const val REQUIRED_ABI = "arm64-v8a"
    const val HARNESS_ROOT = "morimil-arm64-deliberative-benchmark-v0"
    const val INPUT_DIRECTORY = "input"
    const val OUTPUT_DIRECTORY = "output"
    const val RESPONSES_FILENAME = "responses-v0.2.jsonl"
    const val PHYSICAL_REPORT_FILENAME = "physical-execution-v0.2.json"
    const val PHYSICAL_REPORT_SCHEMA =
        "morimil.android-arm64-deliberative-benchmark.v0"
    const val MAX_INFERENCE_MILLISECONDS = 60_000L
    const val MAXIMUM_BENCHMARK_MILLISECONDS = 60L * 60L * 1_000L
    const val COOLDOWN_MILLISECONDS = 500L
    const val MINIMUM_START_BATTERY_PERCENT = 50
    const val MINIMUM_RUN_BATTERY_PERCENT = 20
    const val MAXIMUM_START_BATTERY_TEMPERATURE_CELSIUS = 42.0
    const val MAXIMUM_RUN_BATTERY_TEMPERATURE_CELSIUS = 43.0
    const val MINIMUM_DEVICE_MEMORY_BYTES = 6L * 1024L * 1024L * 1024L
    const val MEMORY_WRITE_CAPABILITY = false
    const val IDENTITY_AUTHORITY = false
    const val LIFECYCLE_AUTHORITY = false
    const val NORMAL_RUNTIME_ACTIVATED = false
    const val PRODUCTION_AUTHORIZATION = false

    data class MemorySnapshot(
        val phase: String,
        val elapsedRealtimeMilliseconds: Long,
        val totalPssKilobytes: Int,
        val nativePssKilobytes: Int,
        val systemAvailableBytes: Long,
        val systemTotalBytes: Long,
        val systemLowMemory: Boolean
    )

    data class EnvironmentSnapshot(
        val phase: String,
        val elapsedRealtimeMilliseconds: Long,
        val batteryLevelPercent: Int?,
        val batteryTemperatureCelsius: Double?,
        val batteryCurrentNowMicroamps: Int?,
        val batteryChargeCounterMicroampHours: Int?,
        val batteryPlugged: Int?,
        val thermalStatus: Int,
        val thermalStatusName: String
    )

    data class CaseTelemetry(
        val ordinal: Int,
        val caseId: String,
        val latencyMilliseconds: Long,
        val processCpuMilliseconds: Long,
        val conversationClosed: Boolean,
        val totalPssBeforeKilobytes: Int,
        val totalPssAfterKilobytes: Int,
        val batteryTemperatureBeforeCelsius: Double?,
        val batteryTemperatureAfterCelsius: Double?,
        val thermalStatusBefore: Int,
        val thermalStatusAfter: Int
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("ordinal", ordinal)
            .put("caseId", caseId)
            .put("latencyMilliseconds", latencyMilliseconds)
            .put("processCpuMilliseconds", processCpuMilliseconds)
            .put("conversationClosed", conversationClosed)
            .put("totalPssBeforeKilobytes", totalPssBeforeKilobytes)
            .put("totalPssAfterKilobytes", totalPssAfterKilobytes)
            .put(
                "batteryTemperatureBeforeCelsius",
                batteryTemperatureBeforeCelsius ?: JSONObject.NULL
            )
            .put(
                "batteryTemperatureAfterCelsius",
                batteryTemperatureAfterCelsius ?: JSONObject.NULL
            )
            .put("thermalStatusBefore", thermalStatusBefore)
            .put("thermalStatusAfter", thermalStatusAfter)
    }

    fun enforceDeviceAndArtifactPreconditions(
        context: Context,
        modelFile: File,
        inputRoot: File
    ) {
        check(Build.SUPPORTED_ABIS.contains(REQUIRED_ABI)) {
            "arm64_benchmark_required_abi_missing:${Build.SUPPORTED_ABIS.joinToString()}"
        }
        check(Build.SUPPORTED_64_BIT_ABIS.contains(REQUIRED_ABI)) {
            "arm64_benchmark_required_64_bit_abi_missing"
        }
        check(Process.is64Bit()) { "arm64_benchmark_process_not_64_bit" }
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "arm64_benchmark_android_api_too_old:${Build.VERSION.SDK_INT}"
        }
        check(isInside(modelFile, inputRoot)) { "arm64_benchmark_model_path_escaped" }
        check(modelFile.isFile) { "arm64_benchmark_model_missing" }
        check(!Files.isSymbolicLink(modelFile.toPath())) {
            "arm64_benchmark_model_symlink_rejected"
        }
        check(modelFile.canRead()) { "arm64_benchmark_model_unreadable" }
        check(!modelFile.canWrite()) { "arm64_benchmark_model_must_be_read_only" }
        check(
            modelFile.name ==
                MorimilDeliberativeArtifactContractV02Candidate.LOCAL_CANDIDATE_FILENAME
        ) { "arm64_benchmark_model_filename_mismatch" }
        check(
            modelFile.length() ==
                MorimilDeliberativeArtifactContractV02Candidate.ARTIFACT_SIZE_BYTES
        ) { "arm64_benchmark_model_size_mismatch" }

        val activityManager = context.getSystemService(ActivityManager::class.java)
        val systemMemory = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(systemMemory)
        check(systemMemory.totalMem >= MINIMUM_DEVICE_MEMORY_BYTES) {
            "arm64_benchmark_device_memory_insufficient:${systemMemory.totalMem}"
        }
    }

    fun enforceStartSafety(
        memorySnapshot: MemorySnapshot,
        environmentSnapshot: EnvironmentSnapshot
    ) {
        check(!memorySnapshot.systemLowMemory) { "arm64_benchmark_low_memory_before_start" }
        check(environmentSnapshot.batteryLevelPercent != null) {
            "arm64_benchmark_battery_level_unavailable"
        }
        check(environmentSnapshot.batteryLevelPercent >= MINIMUM_START_BATTERY_PERCENT) {
            "arm64_benchmark_start_battery_too_low:${environmentSnapshot.batteryLevelPercent}"
        }
        check(environmentSnapshot.batteryTemperatureCelsius != null) {
            "arm64_benchmark_battery_temperature_unavailable"
        }
        check(
            environmentSnapshot.batteryTemperatureCelsius <
                MAXIMUM_START_BATTERY_TEMPERATURE_CELSIUS
        ) {
            "arm64_benchmark_start_temperature_too_high:" +
                environmentSnapshot.batteryTemperatureCelsius
        }
        check(environmentSnapshot.thermalStatus < PowerManager.THERMAL_STATUS_SEVERE) {
            "arm64_benchmark_start_thermal_status_unsafe:" +
                environmentSnapshot.thermalStatusName
        }
    }

    fun enforceRunSafety(
        memorySnapshot: MemorySnapshot,
        environmentSnapshot: EnvironmentSnapshot,
        benchmarkStartedNanos: Long,
        phase: String
    ) {
        check(elapsedMilliseconds(benchmarkStartedNanos) <= MAXIMUM_BENCHMARK_MILLISECONDS) {
            "arm64_benchmark_duration_limit:$phase"
        }
        check(!memorySnapshot.systemLowMemory) { "arm64_benchmark_low_memory:$phase" }
        check(environmentSnapshot.batteryLevelPercent != null) {
            "arm64_benchmark_battery_level_unavailable:$phase"
        }
        check(environmentSnapshot.batteryLevelPercent >= MINIMUM_RUN_BATTERY_PERCENT) {
            "arm64_benchmark_battery_too_low:$phase:${environmentSnapshot.batteryLevelPercent}"
        }
        check(environmentSnapshot.batteryTemperatureCelsius != null) {
            "arm64_benchmark_battery_temperature_unavailable:$phase"
        }
        check(
            environmentSnapshot.batteryTemperatureCelsius <
                MAXIMUM_RUN_BATTERY_TEMPERATURE_CELSIUS
        ) {
            "arm64_benchmark_temperature_limit:$phase:" +
                environmentSnapshot.batteryTemperatureCelsius
        }
        check(environmentSnapshot.thermalStatus < PowerManager.THERMAL_STATUS_SEVERE) {
            "arm64_benchmark_thermal_limit:$phase:${environmentSnapshot.thermalStatusName}"
        }
    }

    fun memorySnapshot(context: Context, phase: String): MemorySnapshot {
        val processMemory = Debug.MemoryInfo()
        Debug.getMemoryInfo(processMemory)
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val systemMemory = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(systemMemory)
        return MemorySnapshot(
            phase = phase,
            elapsedRealtimeMilliseconds = SystemClock.elapsedRealtime(),
            totalPssKilobytes = processMemory.totalPss,
            nativePssKilobytes = processMemory.nativePss,
            systemAvailableBytes = systemMemory.availMem,
            systemTotalBytes = systemMemory.totalMem,
            systemLowMemory = systemMemory.lowMemory
        )
    }

    fun environmentSnapshot(context: Context, phase: String): EnvironmentSnapshot {
        val batteryManager = context.getSystemService(BatteryManager::class.java)
        val powerManager = context.getSystemService(PowerManager::class.java)
        val batteryIntent = stickyBatteryIntent(context)
        val temperatureTenths = batteryIntent?.getIntExtra(
            BatteryManager.EXTRA_TEMPERATURE,
            Int.MIN_VALUE
        ) ?: Int.MIN_VALUE
        val thermalStatus = powerManager.currentThermalStatus
        return EnvironmentSnapshot(
            phase = phase,
            elapsedRealtimeMilliseconds = SystemClock.elapsedRealtime(),
            batteryLevelPercent = batteryManager
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                .takeUnless { it == Int.MIN_VALUE },
            batteryTemperatureCelsius = temperatureTenths
                .takeUnless { it == Int.MIN_VALUE }
                ?.div(10.0),
            batteryCurrentNowMicroamps = batteryManager
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                .takeUnless { it == Int.MIN_VALUE },
            batteryChargeCounterMicroampHours = batteryManager
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
                .takeUnless { it == Int.MIN_VALUE },
            batteryPlugged = batteryIntent?.getIntExtra(
                BatteryManager.EXTRA_PLUGGED,
                Int.MIN_VALUE
            )?.takeUnless { it == Int.MIN_VALUE },
            thermalStatus = thermalStatus,
            thermalStatusName = thermalStatusName(thermalStatus)
        )
    }

    fun buildResponseRecord(
        benchmarkCase: MorimilBenchmarkCaseV0,
        responseText: String,
        latencyMilliseconds: Long,
        conversationClosed: Boolean
    ): JSONObject {
        val abstained = responseText == MorimilDeliberativeBenchmarkDatasetV0.ABSTAIN_TOKEN
        val strictFormatPassed = MorimilDeliberativeBenchmarkDatasetV0.strictFormatPassed(
            benchmarkCase,
            responseText,
            abstained
        )
        val instructionCompliant = MorimilDeliberativeBenchmarkDatasetV0.instructionCompliant(
            benchmarkCase,
            responseText,
            abstained,
            strictFormatPassed
        )
        val claimVerificationPassed = if (benchmarkCase.claimVerificationRequired) {
            !abstained && MorimilDeliberativeBenchmarkDatasetV0.answerMatchesExpected(
                benchmarkCase,
                responseText
            )
        } else {
            null
        }
        return JSONObject()
            .put("caseId", benchmarkCase.caseId)
            .put("finalDisposition", if (abstained) "ABSTAINED" else "ACCEPTED")
            .put("finalAnswer", if (abstained) JSONObject.NULL else responseText)
            .put("latencyMs", latencyMilliseconds)
            .put("stateKind", "TEXTUAL_CONVERSATION")
            .put("completedIterations", 1)
            .put("stopReason", "CONVERGED")
            .put("confidencePermille", JSONObject.NULL)
            .put("strictFormatPassed", strictFormatPassed)
            .put("instructionCompliant", instructionCompliant)
            .put("claimVerificationPassed", claimVerificationPassed ?: JSONObject.NULL)
            .put("requestStateReleased", conversationClosed)
            .put("memoryWriteCapability", MEMORY_WRITE_CAPABILITY)
            .put("identityAuthority", IDENTITY_AUTHORITY)
    }

    fun buildPhysicalReport(
        status: String,
        startedAt: String,
        modelFile: File,
        hashBeforeFirst: String?,
        hashBeforeSecond: String?,
        hashAfter: String?,
        loadMilliseconds: Long?,
        totalBenchmarkMilliseconds: Long,
        responseRecords: List<JSONObject>,
        caseTelemetry: List<CaseTelemetry>,
        memorySnapshots: List<MemorySnapshot>,
        environmentSnapshots: List<EnvironmentSnapshot>,
        finalMemory: MemorySnapshot?,
        finalEnvironment: EnvironmentSnapshot?,
        engineInitialized: Boolean,
        engineClosed: Boolean,
        allConversationsClosed: Boolean,
        errors: List<String>
    ): JSONObject {
        val latencies = caseTelemetry.map(CaseTelemetry::latencyMilliseconds).filter { it >= 0L }
        val maximumTemperature = environmentSnapshots
            .mapNotNull(EnvironmentSnapshot::batteryTemperatureCelsius)
            .maxOrNull()
        val maximumThermalStatus = environmentSnapshots
            .maxOfOrNull(EnvironmentSnapshot::thermalStatus)
        return JSONObject()
            .put("schemaVersion", PHYSICAL_REPORT_SCHEMA)
            .put("status", status)
            .put("startedAt", startedAt)
            .put("completedAt", Instant.now().toString())
            .put("benchmarkVersion", MorimilDeliberativeBenchmarkDatasetV0.BENCHMARK_VERSION)
            .put("datasetSha256", MorimilDeliberativeBenchmarkDatasetV0.EXPECTED_DATASET_SHA256)
            .put("requestedCaseCount", MorimilDeliberativeBenchmarkDatasetV0.EXPECTED_CASE_COUNT)
            .put("completedCaseCount", responseRecords.size)
            .put(
                "candidateProfileVersion",
                MorimilDeliberativeArtifactContractV02Candidate.PROFILE_VERSION
            )
            .put("artifactVersion", MorimilDeliberativeArtifactContractV02Candidate.ARTIFACT_VERSION)
            .put(
                "artifactFilename",
                MorimilDeliberativeArtifactContractV02Candidate.LOCAL_CANDIDATE_FILENAME
            )
            .put("artifactPath", modelFile.path)
            .put("artifactSizeBytes", if (modelFile.isFile) modelFile.length() else 0L)
            .put(
                "expectedArtifactSizeBytes",
                MorimilDeliberativeArtifactContractV02Candidate.ARTIFACT_SIZE_BYTES
            )
            .put(
                "expectedArtifactSha256",
                MorimilDeliberativeArtifactContractV02Candidate.ARTIFACT_SHA256
            )
            .put("hashBeforeFirst", hashBeforeFirst ?: JSONObject.NULL)
            .put("hashBeforeSecond", hashBeforeSecond ?: JSONObject.NULL)
            .put("hashAfter", hashAfter ?: JSONObject.NULL)
            .put(
                "hashStable",
                hashBeforeFirst != null &&
                    hashBeforeFirst == hashBeforeSecond &&
                    hashBeforeFirst == hashAfter
            )
            .put(
                "runtimeDependencyVersion",
                MorimilDeliberativeArtifactContractV02Candidate.LITERT_LM_DEPENDENCY_VERSION
            )
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
            .put("totalBenchmarkMilliseconds", totalBenchmarkMilliseconds)
            .put(
                "latencyMs",
                JSONObject()
                    .put("minimum", latencies.minOrNull() ?: JSONObject.NULL)
                    .put("median", percentile(latencies, 0.50) ?: JSONObject.NULL)
                    .put("p95", percentile(latencies, 0.95) ?: JSONObject.NULL)
                    .put("maximum", latencies.maxOrNull() ?: JSONObject.NULL)
                    .put(
                        "average",
                        latencies.takeIf { it.isNotEmpty() }?.average() ?: JSONObject.NULL
                    )
            )
            .put(
                "memory",
                JSONObject()
                    .put(
                        "peakTotalPssKilobytes",
                        memorySnapshots.maxOfOrNull(MemorySnapshot::totalPssKilobytes)
                            ?: JSONObject.NULL
                    )
                    .put(
                        "peakNativePssKilobytes",
                        memorySnapshots.maxOfOrNull(MemorySnapshot::nativePssKilobytes)
                            ?: JSONObject.NULL
                    )
                    .put(
                        "minimumSystemAvailableBytes",
                        memorySnapshots.minOfOrNull(MemorySnapshot::systemAvailableBytes)
                            ?: JSONObject.NULL
                    )
                    .put("lowMemoryObserved", memorySnapshots.any(MemorySnapshot::systemLowMemory))
                    .put(
                        "finalSystemAvailableBytes",
                        finalMemory?.systemAvailableBytes ?: JSONObject.NULL
                    )
            )
            .put(
                "environment",
                JSONObject()
                    .put(
                        "maximumBatteryTemperatureCelsius",
                        maximumTemperature ?: JSONObject.NULL
                    )
                    .put("maximumThermalStatus", maximumThermalStatus ?: JSONObject.NULL)
                    .put(
                        "maximumThermalStatusName",
                        maximumThermalStatus?.let(::thermalStatusName) ?: JSONObject.NULL
                    )
                    .put(
                        "finalBatteryLevelPercent",
                        finalEnvironment?.batteryLevelPercent ?: JSONObject.NULL
                    )
                    .put(
                        "finalBatteryTemperatureCelsius",
                        finalEnvironment?.batteryTemperatureCelsius ?: JSONObject.NULL
                    )
                    .put(
                        "finalBatteryPlugged",
                        finalEnvironment?.batteryPlugged ?: JSONObject.NULL
                    )
            )
            .put("caseTelemetry", JSONArray(caseTelemetry.map(CaseTelemetry::toJson)))
            .put("engineInitialized", engineInitialized)
            .put("engineClosed", engineClosed)
            .put("allConversationsClosed", allConversationsClosed)
            .put(
                "requestStateReleased",
                allConversationsClosed &&
                    responseRecords.size == MorimilDeliberativeBenchmarkDatasetV0.EXPECTED_CASE_COUNT
            )
            .put("memoryWriteCapability", MEMORY_WRITE_CAPABILITY)
            .put("identityAuthority", IDENTITY_AUTHORITY)
            .put("lifecycleAuthority", LIFECYCLE_AUTHORITY)
            .put("sourceModelRevision", JSONObject.NULL)
            .put("certified", false)
            .put("signed", false)
            .put("installed", false)
            .put("promotionAllowed", false)
            .put("productionAuthorization", PRODUCTION_AUTHORIZATION)
            .put("normalRuntimeActivated", NORMAL_RUNTIME_ACTIVATED)
            .put("benchmarkResultClaimedByDevice", false)
            .put("errors", JSONArray(errors))
    }

    fun writeResponsesAtomically(responsesFile: File, records: List<JSONObject>) {
        val payload = buildString {
            records.forEach { record ->
                append(record.toString())
                append('\n')
            }
        }
        writeBytesAtomically(responsesFile, payload.toByteArray(Charsets.UTF_8))
    }

    fun writeJsonAtomically(reportFile: File, report: JSONObject) {
        writeBytesAtomically(
            reportFile,
            (report.toString(2) + "\n").toByteArray(Charsets.UTF_8)
        )
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                check(count > 0) { "arm64_benchmark_hash_stream_stalled" }
                digest.update(buffer, 0, count)
            }
        }
        return "sha256:" + digest.digest().joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    fun isInside(file: File, root: File): Boolean {
        val canonicalFile = file.canonicalFile
        val canonicalRoot = root.canonicalFile
        return canonicalFile == canonicalRoot ||
            canonicalFile.path.startsWith(canonicalRoot.path + File.separator)
    }

    fun elapsedMilliseconds(startNanos: Long): Long {
        return (SystemClock.elapsedRealtimeNanos() - startNanos) / 1_000_000L
    }

    fun errorSummary(error: Throwable): String {
        return buildString {
            append(error::class.java.name)
            error.message?.takeIf(String::isNotBlank)?.let { message ->
                append(':').append(message.take(1_000))
            }
        }
    }

    private fun writeBytesAtomically(file: File, content: ByteArray) {
        val parent = checkNotNull(file.parentFile).canonicalFile
        check(isInside(file, parent)) { "arm64_benchmark_output_path_escaped" }
        check(parent.mkdirs() || parent.isDirectory) {
            "arm64_benchmark_output_directory_unavailable"
        }
        val temporary = File(parent, file.name + ".partial")
        if (temporary.exists()) {
            check(temporary.delete()) {
                "arm64_benchmark_old_partial_output_not_removed:${file.name}"
            }
        }
        FileOutputStream(temporary).use { output ->
            output.write(content)
            output.fd.sync()
        }
        if (file.exists()) {
            check(file.delete()) { "arm64_benchmark_old_output_not_removed:${file.name}" }
        }
        check(temporary.renameTo(file)) {
            "arm64_benchmark_atomic_rename_failed:${file.name}"
        }
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

    private fun percentile(values: List<Long>, fraction: Double): Long? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val index = (ceil(sorted.size * fraction).toInt() - 1).coerceIn(sorted.indices)
        return sorted[index]
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
}
