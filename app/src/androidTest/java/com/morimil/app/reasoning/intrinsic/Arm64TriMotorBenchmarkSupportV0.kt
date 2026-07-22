package com.morimil.app.reasoning.intrinsic

import com.morimil.app.reasoning.IntrinsicTriMotorResearchRuntimeV0
import com.morimil.app.reasoning.ReasoningMotorRole
import com.morimil.app.reasoning.TriMotorFinalizationStatus
import com.morimil.app.reasoning.TriMotorReasoningResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal object Arm64TriMotorBenchmarkSupportV0 {
    const val ENABLE_ARGUMENT = "morimilArm64TriMotorBenchmarkEnabled"
    const val RESPONSES_FILENAME = "responses-trimotor-v0.2.jsonl"
    const val PHYSICAL_REPORT_FILENAME = "physical-execution-trimotor-v0.2.json"
    const val PHYSICAL_REPORT_SCHEMA = "morimil.android-arm64-trimotor-benchmark.v0"
    const val RESPONSE_SCHEMA = "morimil.trimotor.benchmark.response.v0"
    const val MAXIMUM_CASE_MILLISECONDS = 130_000L
    const val MEMORY_WRITE_CAPABILITY = false
    const val IDENTITY_AUTHORITY = false
    const val LIFECYCLE_AUTHORITY = false
    const val NORMAL_RUNTIME_ACTIVATED = false
    const val PRODUCTION_AUTHORIZATION = false

    fun buildResponseRecord(
        benchmarkCase: MorimilBenchmarkCaseV0,
        plan: TriMotorBenchmarkPlanV0,
        result: TriMotorReasoningResult,
        presentedResponse: String,
        latencyMilliseconds: Long,
        requestStateReleased: Boolean
    ): JSONObject {
        val abstained = presentedResponse == MorimilDeliberativeBenchmarkDatasetV0.ABSTAIN_TOKEN
        val strictFormatPassed = MorimilDeliberativeBenchmarkDatasetV0.strictFormatPassed(
            benchmarkCase,
            presentedResponse,
            abstained
        )
        val instructionCompliant = MorimilDeliberativeBenchmarkDatasetV0.instructionCompliant(
            benchmarkCase,
            presentedResponse,
            abstained,
            strictFormatPassed
        )
        val claimVerificationPassed = if (benchmarkCase.claimVerificationRequired) {
            !abstained && MorimilDeliberativeBenchmarkDatasetV0.answerMatchesExpected(
                benchmarkCase,
                presentedResponse
            )
        } else {
            null
        }
        val iterations = result.findings
            .firstOrNull { finding -> finding.startsWith("deliberation_passes:") }
            ?.substringAfter(':')
            ?.toIntOrNull()
            ?: 0
        val activatedVersions = JSONObject()
        result.activatedVersions.forEach { (role, version) ->
            activatedVersions.put(role.name, version)
        }

        return JSONObject()
            .put("schemaVersion", RESPONSE_SCHEMA)
            .put("caseId", benchmarkCase.caseId)
            .put("domain", benchmarkCase.domain)
            .put("finalDisposition", if (abstained) "ABSTAINED" else "ACCEPTED")
            .put("finalAnswer", if (abstained) JSONObject.NULL else presentedResponse)
            .put("latencyMs", latencyMilliseconds)
            .put("stateKind", "HYBRID_ROUTED")
            .put("completedIterations", iterations)
            .put(
                "stopReason",
                if (abstained) "AUTHORITY_ABSTAINED" else "CONVERGED"
            )
            .put("confidencePermille", JSONObject.NULL)
            .put("strictFormatPassed", strictFormatPassed)
            .put("instructionCompliant", instructionCompliant)
            .put("claimVerificationPassed", claimVerificationPassed ?: JSONObject.NULL)
            .put("requestStateReleased", requestStateReleased)
            .put("memoryWriteCapability", MEMORY_WRITE_CAPABILITY)
            .put("identityAuthority", IDENTITY_AUTHORITY)
            .put("lifecycleAuthority", LIFECYCLE_AUTHORITY)
            .put("normalRuntimeActivated", NORMAL_RUNTIME_ACTIVATED)
            .put("authorityReduction", plan.authorityReduction)
            .put("outputProfile", plan.outputProfile.name)
            .put("requestedRoles", JSONArray(result.requestedRoles.map(ReasoningMotorRole::name)))
            .put("activatedRoles", JSONArray(result.activatedRoles.map(ReasoningMotorRole::name)))
            .put("failedRoles", JSONArray(result.failedRoles.map(ReasoningMotorRole::name)))
            .put("unavailableRoles", JSONArray(result.unavailableRoles.map(ReasoningMotorRole::name)))
            .put("activatedVersions", activatedVersions)
            .put("primaryCandidate", result.primaryCandidate)
            .put("verifierCandidate", result.verifierCandidate ?: JSONObject.NULL)
            .put("finalizationStatus", result.finalizationStatus.name)
            .put(
                "authorityRoute",
                result.authorityDecision?.route?.name ?: JSONObject.NULL
            )
            .put(
                "authorityStatus",
                result.authorityDecision?.status?.name ?: JSONObject.NULL
            )
            .put(
                "authorityVersion",
                result.authorityDecision?.authorityVersion ?: JSONObject.NULL
            )
            .put("findings", JSONArray(result.findings))
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
        caseTelemetry: List<Arm64DeliberativeBenchmarkSupportV0.CaseTelemetry>,
        memorySnapshots: List<Arm64DeliberativeBenchmarkSupportV0.MemorySnapshot>,
        environmentSnapshots: List<Arm64DeliberativeBenchmarkSupportV0.EnvironmentSnapshot>,
        finalMemory: Arm64DeliberativeBenchmarkSupportV0.MemorySnapshot?,
        finalEnvironment: Arm64DeliberativeBenchmarkSupportV0.EnvironmentSnapshot?,
        engineInitialized: Boolean,
        engineClosed: Boolean,
        allRequestStatesReleased: Boolean,
        openedConversationCount: Int,
        closedConversationCount: Int,
        errors: List<String>
    ): JSONObject {
        val report = Arm64DeliberativeBenchmarkSupportV0.buildPhysicalReport(
            status = status,
            startedAt = startedAt,
            modelFile = modelFile,
            hashBeforeFirst = hashBeforeFirst,
            hashBeforeSecond = hashBeforeSecond,
            hashAfter = hashAfter,
            loadMilliseconds = loadMilliseconds,
            totalBenchmarkMilliseconds = totalBenchmarkMilliseconds,
            responseRecords = responseRecords,
            caseTelemetry = caseTelemetry,
            memorySnapshots = memorySnapshots,
            environmentSnapshots = environmentSnapshots,
            finalMemory = finalMemory,
            finalEnvironment = finalEnvironment,
            engineInitialized = engineInitialized,
            engineClosed = engineClosed,
            allConversationsClosed = allRequestStatesReleased,
            errors = errors
        )
        val roleCounts = JSONObject()
        ReasoningMotorRole.entries.forEach { role -> roleCounts.put(role.name, 0) }
        val authorityStatusCounts = JSONObject()
        responseRecords.forEach { record ->
            val roles = record.getJSONArray("activatedRoles")
            for (index in 0 until roles.length()) {
                val role = roles.getString(index)
                roleCounts.put(role, roleCounts.getInt(role) + 1)
            }
            val authorityStatus = record.optString("authorityStatus")
            if (authorityStatus.isNotEmpty() && authorityStatus != "null") {
                authorityStatusCounts.put(
                    authorityStatus,
                    authorityStatusCounts.optInt(authorityStatus) + 1
                )
            }
        }

        return report
            .put("schemaVersion", PHYSICAL_REPORT_SCHEMA)
            .put("benchmarkMode", "TRIMOTOR_HYBRID_AUTHORITY")
            .put("responseSchemaVersion", RESPONSE_SCHEMA)
            .put("triMotorRuntimeVersion", IntrinsicTriMotorResearchRuntimeV0.VERSION)
            .put("benchmarkAdapterVersion", MorimilTriMotorBenchmarkAdapterV0.VERSION)
            .put("intuitiveCoreVersion", BoundedLocalIntuitiveCoreV0.VERSION)
            .put("deliberativeArtifactVersion", MorimilDeliberativeArtifactContractV02Candidate.ARTIFACT_VERSION)
            .put("metacognitiveCoreVersion", BoundedLocalMetacognitiveCoreV0.VERSION)
            .put("hybridAuthorityEnabled", true)
            .put("roleActivationCounts", roleCounts)
            .put("authorityStatusCounts", authorityStatusCounts)
            .put("openedConversationCount", openedConversationCount)
            .put("closedConversationCount", closedConversationCount)
            .put("allRequestStatesReleased", allRequestStatesReleased)
            .put("normalRuntimeActivated", NORMAL_RUNTIME_ACTIVATED)
            .put("productionAuthorization", PRODUCTION_AUTHORIZATION)
            .put("promotionAllowed", false)
    }
}
