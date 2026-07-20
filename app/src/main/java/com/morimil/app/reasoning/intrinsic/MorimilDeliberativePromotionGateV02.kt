package com.morimil.app.reasoning.intrinsic

/**
 * Fail-closed promotion gate for the exact Gemma 3n E2B LiteRT-LM research candidate.
 *
 * Physical runtime evidence may validate continued research. It cannot replace exact
 * source-model provenance, reproducible conversion evidence, certification, signing,
 * installation authorization or production authorization.
 */
object MorimilDeliberativePromotionGateV02 {
    const val GATE_VERSION = "morimil.deliberative.promotion-gate.v0.2"
    const val REQUIRED_RUNTIME_VERSION = "0.14.0"
    const val REQUIRED_BACKEND = "cpu"
    const val REQUIRED_ABI = "arm64-v8a"
    const val MINIMUM_SUSTAINED_PASSES = 6
    const val MINIMUM_UNPLUGGED_PASSES = 6
    const val MINIMUM_STRICT_INFERENCES = 36
    const val MAXIMUM_P95_INFERENCE_MILLISECONDS = 20_000L
    const val MAXIMUM_TOTAL_PSS_KILOBYTES = 8L * 1024L * 1024L
    const val MAXIMUM_BATTERY_TEMPERATURE_CELSIUS_EXCLUSIVE = 43.0
    const val SEVERE_THERMAL_STATUS = 3

    fun evaluate(evidence: DeliberativeCandidatePromotionEvidenceV02): DeliberativePromotionDecisionV02 {
        val candidate = MorimilDeliberativeArtifactContractV02Candidate

        require(evidence.gateVersion == GATE_VERSION) { "promotion_gate_version_mismatch" }
        require(evidence.candidateProfileVersion == candidate.PROFILE_VERSION) { "candidate_profile_version_mismatch" }
        require(evidence.artifactVersion == candidate.ARTIFACT_VERSION) { "candidate_artifact_version_mismatch" }
        require(evidence.artifactFilename == candidate.LOCAL_CANDIDATE_FILENAME) { "candidate_artifact_filename_mismatch" }
        require(evidence.artifactSha256 == candidate.ARTIFACT_SHA256) { "candidate_artifact_sha256_mismatch" }
        require(evidence.artifactSizeBytes == candidate.ARTIFACT_SIZE_BYTES) { "candidate_artifact_size_mismatch" }
        require(evidence.runtimeVersion == REQUIRED_RUNTIME_VERSION) { "candidate_runtime_version_mismatch" }
        require(evidence.backend == REQUIRED_BACKEND) { "candidate_backend_mismatch" }
        require(evidence.requiredAbi == REQUIRED_ABI) { "candidate_abi_mismatch" }
        require(evidence.process64Bit) { "candidate_process_must_be_64_bit" }

        require(evidence.physicalArm64HarnessPassed) { "physical_arm64_harness_required" }
        require(evidence.sustainedProfilePasses >= MINIMUM_SUSTAINED_PASSES) { "sustained_profile_passes_insufficient" }
        require(evidence.unpluggedProfilePasses >= MINIMUM_UNPLUGGED_PASSES) { "unplugged_profile_passes_insufficient" }
        require(evidence.strictInferenceCount >= MINIMUM_STRICT_INFERENCES) { "strict_inference_count_insufficient" }
        require(evidence.allStrictOutputsPassed) { "strict_outputs_must_all_pass" }
        require(evidence.hashStable) { "candidate_hash_must_remain_stable" }
        require(evidence.allConversationsClosed) { "candidate_conversations_must_close" }
        require(evidence.allEnginesClosed) { "candidate_engines_must_close" }
        require(evidence.allSamplesUnplugged) { "unplugged_samples_required" }
        require(evidence.observedChargeDecreaseMicroampHours > 0L) { "positive_energy_observation_required" }
        require(evidence.p95InferenceMilliseconds <= MAXIMUM_P95_INFERENCE_MILLISECONDS) { "candidate_p95_latency_exceeded" }
        require(evidence.peakTotalPssKilobytes <= MAXIMUM_TOTAL_PSS_KILOBYTES) { "candidate_pss_limit_exceeded" }
        require(evidence.maximumBatteryTemperatureCelsius < MAXIMUM_BATTERY_TEMPERATURE_CELSIUS_EXCLUSIVE) {
            "candidate_battery_temperature_exceeded"
        }
        require(evidence.maximumThermalStatus < SEVERE_THERMAL_STATUS) { "candidate_severe_thermal_status" }
        require(!evidence.lowMemoryObserved) { "candidate_low_memory_observed" }
        require(evidence.errors.isEmpty()) { "candidate_physical_evidence_contains_errors" }

        require(evidence.sourceModelRevision == null) { "candidate_source_revision_must_remain_unassigned" }
        require(!evidence.reproducibleConversionEvidence) { "candidate_conversion_evidence_must_not_be_claimed" }
        require(!evidence.certified) { "candidate_must_not_be_certified" }
        require(!evidence.signed) { "candidate_must_not_be_signed" }
        require(!evidence.installed) { "candidate_must_not_be_installed" }
        require(!evidence.productionAuthorization) { "candidate_must_not_have_production_authorization" }
        require(!evidence.normalRuntimeActivated) { "candidate_normal_runtime_must_remain_disabled" }
        require(!evidence.promotionAllowed) { "candidate_must_not_be_promotable" }

        return DeliberativePromotionDecisionV02(
            gateVersion = GATE_VERSION,
            researchEvidenceAccepted = true,
            productionPromotionAllowed = false,
            blockers = linkedSetOf(
                DeliberativePromotionBlockerV02.SOURCE_MODEL_REVISION_MISSING,
                DeliberativePromotionBlockerV02.REPRODUCIBLE_CONVERSION_EVIDENCE_MISSING,
                DeliberativePromotionBlockerV02.CERTIFICATION_MISSING,
                DeliberativePromotionBlockerV02.SIGNATURE_MISSING,
                DeliberativePromotionBlockerV02.INSTALLATION_AUTHORIZATION_MISSING,
                DeliberativePromotionBlockerV02.PRODUCTION_AUTHORIZATION_MISSING
            )
        )
    }
}

enum class DeliberativePromotionBlockerV02 {
    SOURCE_MODEL_REVISION_MISSING,
    REPRODUCIBLE_CONVERSION_EVIDENCE_MISSING,
    CERTIFICATION_MISSING,
    SIGNATURE_MISSING,
    INSTALLATION_AUTHORIZATION_MISSING,
    PRODUCTION_AUTHORIZATION_MISSING
}

data class DeliberativePromotionDecisionV02(
    val gateVersion: String,
    val researchEvidenceAccepted: Boolean,
    val productionPromotionAllowed: Boolean,
    val blockers: Set<DeliberativePromotionBlockerV02>
)

data class DeliberativeCandidatePromotionEvidenceV02(
    val gateVersion: String,
    val candidateProfileVersion: String,
    val artifactVersion: String,
    val artifactFilename: String,
    val artifactSha256: String,
    val artifactSizeBytes: Long,
    val runtimeVersion: String,
    val backend: String,
    val requiredAbi: String,
    val process64Bit: Boolean,
    val physicalArm64HarnessPassed: Boolean,
    val sustainedProfilePasses: Int,
    val unpluggedProfilePasses: Int,
    val strictInferenceCount: Int,
    val allStrictOutputsPassed: Boolean,
    val hashStable: Boolean,
    val allConversationsClosed: Boolean,
    val allEnginesClosed: Boolean,
    val allSamplesUnplugged: Boolean,
    val observedChargeDecreaseMicroampHours: Long,
    val p95InferenceMilliseconds: Long,
    val peakTotalPssKilobytes: Long,
    val maximumBatteryTemperatureCelsius: Double,
    val maximumThermalStatus: Int,
    val lowMemoryObserved: Boolean,
    val errors: List<String>,
    val sourceModelRevision: String?,
    val reproducibleConversionEvidence: Boolean,
    val certified: Boolean,
    val signed: Boolean,
    val installed: Boolean,
    val productionAuthorization: Boolean,
    val normalRuntimeActivated: Boolean,
    val promotionAllowed: Boolean
)
