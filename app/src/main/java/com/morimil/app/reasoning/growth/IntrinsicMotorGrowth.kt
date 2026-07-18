package com.morimil.app.reasoning.growth

import com.morimil.app.reasoning.ReasoningMotorRole

enum class ReasoningBenchmarkDomain {
    SPANISH_DIALOGUE,
    PLANNING,
    CODE,
    TOOL_USE,
    UNCERTAINTY,
    MEMORY_CONTEXT_USE,
    MATH
}

data class BenchmarkDomainScore(
    val baseline: Int,
    val candidate: Int
) {
    init {
        require(baseline in SCORE_RANGE) { "Baseline score must be between 0 and 1000." }
        require(candidate in SCORE_RANGE) { "Candidate score must be between 0 and 1000." }
    }

    val gain: Int get() = candidate - baseline

    private companion object {
        val SCORE_RANGE = 0..1_000
    }
}

data class IntrinsicMotorBenchmarkReport(
    val scores: Map<ReasoningBenchmarkDomain, BenchmarkDomainScore>
) {
    val totalGain: Int get() = scores.values.sumOf { it.gain }
}

data class IntrinsicMotorLearningCandidate(
    val role: ReasoningMotorRole,
    val fromVersion: String,
    val candidateVersion: String,
    val techniques: Set<IntrinsicMotorTechnique>,
    val currentArtifactSha256: String,
    val candidateArtifactSha256: String,
    val rollbackArtifactSha256: String,
    val trainingCorpusSha256: String,
    val benchmarkReportSha256: String,
    val signerKeyId: String,
    val signatureAlgorithm: String,
    val signature: String
)

fun interface IntrinsicLearningEvidenceVerifier {
    fun verify(candidate: IntrinsicMotorLearningCandidate): Boolean
}

data class IntrinsicMotorPromotionPolicy(
    val minimumTotalGain: Int = 10,
    val allowDomainRegression: Boolean = false
) {
    init {
        require(minimumTotalGain > 0) { "Minimum total gain must be positive." }
    }
}

data class IntrinsicMotorPromotionDecision(
    val accepted: Boolean,
    val reasons: List<String>
)

/** Read-only decision gate. It evaluates candidates but cannot install them. */
class IntrinsicMotorPromotionGate(
    private val evidenceVerifier: IntrinsicLearningEvidenceVerifier,
    private val policy: IntrinsicMotorPromotionPolicy = IntrinsicMotorPromotionPolicy()
) {
    fun evaluate(
        candidate: IntrinsicMotorLearningCandidate,
        report: IntrinsicMotorBenchmarkReport
    ): IntrinsicMotorPromotionDecision {
        val reasons = mutableListOf<String>()
        val blueprint = MorimilIntrinsicMotorBlueprints.requireBlueprint(candidate.role)

        if (candidate.fromVersion.isBlank() || candidate.candidateVersion.isBlank()) {
            reasons += "motor_version_missing"
        } else if (candidate.fromVersion == candidate.candidateVersion) {
            reasons += "candidate_version_not_advanced"
        }

        val missingTechniques = blueprint.requiredTechniques - candidate.techniques
        if (missingTechniques.isNotEmpty()) {
            reasons += "required_techniques_missing:${missingTechniques.sortedBy { it.name }}"
        }

        val requiredDomains = ReasoningBenchmarkDomain.entries.toSet()
        val missingDomains = requiredDomains - report.scores.keys
        if (missingDomains.isNotEmpty()) {
            reasons += "benchmark_domains_missing:${missingDomains.sortedBy { it.name }}"
        }

        if (!policy.allowDomainRegression) {
            val regressions = report.scores
                .filterValues { score -> score.gain < 0 }
                .keys
            if (regressions.isNotEmpty()) {
                reasons += "domain_regression:${regressions.sortedBy { it.name }}"
            }
        }

        if (report.totalGain < policy.minimumTotalGain) {
            reasons += "insufficient_total_gain:${report.totalGain}"
        }

        if (!validSha256(candidate.currentArtifactSha256) ||
            !validSha256(candidate.candidateArtifactSha256) ||
            !validSha256(candidate.rollbackArtifactSha256) ||
            !validSha256(candidate.trainingCorpusSha256) ||
            !validSha256(candidate.benchmarkReportSha256)
        ) {
            reasons += "invalid_evidence_digest"
        }

        if (candidate.candidateArtifactSha256 == candidate.currentArtifactSha256) {
            reasons += "candidate_artifact_unchanged"
        }

        if (candidate.rollbackArtifactSha256 != candidate.currentArtifactSha256) {
            reasons += "rollback_does_not_preserve_current_artifact"
        }

        if (candidate.signerKeyId.isBlank() ||
            candidate.signatureAlgorithm != "ed25519" ||
            candidate.signature.isBlank()
        ) {
            reasons += "signature_envelope_invalid"
        } else if (!evidenceVerifier.verify(candidate)) {
            reasons += "candidate_signature_unverified"
        }

        return IntrinsicMotorPromotionDecision(
            accepted = reasons.isEmpty(),
            reasons = reasons.toList()
        )
    }

    private fun validSha256(value: String): Boolean {
        return SHA256_PATTERN.matches(value)
    }

    private companion object {
        val SHA256_PATTERN = Regex("sha256:[0-9a-f]{64}")
    }
}
