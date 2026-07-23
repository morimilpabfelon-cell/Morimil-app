package com.morimil.app.reasoning.intrinsic

/**
 * Declarative, fail-closed gate between deliberative research evidence and normal runtime.
 *
 * Passing this policy never installs or registers a motor by itself. A future activation
 * still requires an explicit code change that supplies a verified local artifact, engine
 * and signed authorization. The current v0.2 candidate is intentionally represented here
 * as blocked by its frozen physical benchmark evidence.
 */
object MorimilNormalDeliberativeActivationGateV0 {
    const val GATE_VERSION = "morimil.deliberative.normal-runtime-gate.v0"
    const val MINIMUM_REQUIRED_CASES = 120

    val currentCandidateEvidence: NormalDeliberativeEvidenceV0 =
        NormalDeliberativeEvidenceV0(
            gateVersion = GATE_VERSION,
            candidateProfileVersion = MorimilDeliberativeArtifactContractV02Candidate.PROFILE_VERSION,
            artifactVersion = MorimilDeliberativeArtifactContractV02Candidate.ARTIFACT_VERSION,
            artifactFilename = MorimilDeliberativeArtifactContractV02Candidate.LOCAL_CANDIDATE_FILENAME,
            artifactSha256 = MorimilDeliberativeArtifactContractV02Candidate.ARTIFACT_SHA256,
            artifactSizeBytes = MorimilDeliberativeArtifactContractV02Candidate.ARTIFACT_SIZE_BYTES,
            requiredCaseCount = MINIMUM_REQUIRED_CASES,
            completedCaseCount = 120,
            benchmarkQualityGatePassed = false,
            falseAcceptedCount = 40,
            sourceModelRevision = null,
            reproducibleConversionEvidence = false,
            certified = false,
            signed = false,
            installationAuthorized = false,
            productionAuthorized = false
        )

    val currentCandidateDecision: NormalDeliberativeActivationDecisionV0
        get() = evaluate(currentCandidateEvidence)

    fun evaluate(
        evidence: NormalDeliberativeEvidenceV0
    ): NormalDeliberativeActivationDecisionV0 {
        require(evidence.gateVersion == GATE_VERSION) {
            "normal_deliberative_gate_version_mismatch"
        }
        require(
            evidence.candidateProfileVersion ==
                MorimilDeliberativeArtifactContractV02Candidate.PROFILE_VERSION
        ) { "normal_deliberative_candidate_profile_mismatch" }
        require(
            evidence.artifactVersion ==
                MorimilDeliberativeArtifactContractV02Candidate.ARTIFACT_VERSION
        ) { "normal_deliberative_artifact_version_mismatch" }
        require(
            evidence.artifactFilename ==
                MorimilDeliberativeArtifactContractV02Candidate.LOCAL_CANDIDATE_FILENAME
        ) { "normal_deliberative_artifact_filename_mismatch" }
        require(
            evidence.artifactSha256 ==
                MorimilDeliberativeArtifactContractV02Candidate.ARTIFACT_SHA256
        ) { "normal_deliberative_artifact_sha256_mismatch" }
        require(
            evidence.artifactSizeBytes ==
                MorimilDeliberativeArtifactContractV02Candidate.ARTIFACT_SIZE_BYTES
        ) { "normal_deliberative_artifact_size_mismatch" }

        val blockers = linkedSetOf<NormalDeliberativeActivationBlockerV0>()
        if (
            evidence.requiredCaseCount < MINIMUM_REQUIRED_CASES ||
            evidence.completedCaseCount != evidence.requiredCaseCount
        ) {
            blockers += NormalDeliberativeActivationBlockerV0.PHYSICAL_BENCHMARK_INCOMPLETE
        }
        if (!evidence.benchmarkQualityGatePassed) {
            blockers += NormalDeliberativeActivationBlockerV0.BENCHMARK_QUALITY_GATE_FAILED
        }
        if (evidence.falseAcceptedCount > 0) {
            blockers += NormalDeliberativeActivationBlockerV0.FALSE_ACCEPTANCES_PRESENT
        }
        if (evidence.sourceModelRevision?.trim().isNullOrEmpty()) {
            blockers += NormalDeliberativeActivationBlockerV0.SOURCE_MODEL_REVISION_MISSING
        }
        if (!evidence.reproducibleConversionEvidence) {
            blockers +=
                NormalDeliberativeActivationBlockerV0.REPRODUCIBLE_CONVERSION_EVIDENCE_MISSING
        }
        if (!evidence.certified) {
            blockers += NormalDeliberativeActivationBlockerV0.CERTIFICATION_MISSING
        }
        if (!evidence.signed) {
            blockers += NormalDeliberativeActivationBlockerV0.SIGNATURE_MISSING
        }
        if (!evidence.installationAuthorized) {
            blockers += NormalDeliberativeActivationBlockerV0.INSTALLATION_AUTHORIZATION_MISSING
        }
        if (!evidence.productionAuthorized) {
            blockers += NormalDeliberativeActivationBlockerV0.PRODUCTION_AUTHORIZATION_MISSING
        }

        return NormalDeliberativeActivationDecisionV0(
            gateVersion = GATE_VERSION,
            activationAllowed = blockers.isEmpty(),
            blockers = blockers
        )
    }
}

enum class NormalDeliberativeActivationBlockerV0 {
    PHYSICAL_BENCHMARK_INCOMPLETE,
    BENCHMARK_QUALITY_GATE_FAILED,
    FALSE_ACCEPTANCES_PRESENT,
    SOURCE_MODEL_REVISION_MISSING,
    REPRODUCIBLE_CONVERSION_EVIDENCE_MISSING,
    CERTIFICATION_MISSING,
    SIGNATURE_MISSING,
    INSTALLATION_AUTHORIZATION_MISSING,
    PRODUCTION_AUTHORIZATION_MISSING
}

data class NormalDeliberativeActivationDecisionV0(
    val gateVersion: String,
    val activationAllowed: Boolean,
    val blockers: Set<NormalDeliberativeActivationBlockerV0>
) {
    init {
        require(activationAllowed == blockers.isEmpty()) {
            "normal_deliberative_activation_decision_inconsistent"
        }
    }
}

data class NormalDeliberativeEvidenceV0(
    val gateVersion: String,
    val candidateProfileVersion: String,
    val artifactVersion: String,
    val artifactFilename: String,
    val artifactSha256: String,
    val artifactSizeBytes: Long,
    val requiredCaseCount: Int,
    val completedCaseCount: Int,
    val benchmarkQualityGatePassed: Boolean,
    val falseAcceptedCount: Int,
    val sourceModelRevision: String?,
    val reproducibleConversionEvidence: Boolean,
    val certified: Boolean,
    val signed: Boolean,
    val installationAuthorized: Boolean,
    val productionAuthorized: Boolean
) {
    init {
        require(artifactSizeBytes > 0L) { "normal_deliberative_artifact_size_invalid" }
        require(requiredCaseCount > 0) { "normal_deliberative_required_case_count_invalid" }
        require(completedCaseCount >= 0) { "normal_deliberative_completed_case_count_invalid" }
        require(completedCaseCount <= requiredCaseCount) {
            "normal_deliberative_completed_cases_exceed_required"
        }
        require(falseAcceptedCount >= 0) {
            "normal_deliberative_false_accepted_count_invalid"
        }
        require(falseAcceptedCount <= completedCaseCount) {
            "normal_deliberative_false_accepted_count_exceeds_completed"
        }
    }
}
