package com.morimil.app.reasoning.growth

import com.morimil.app.ai.ReasoningProviderConfig
import com.morimil.app.reasoning.ReasoningMotorRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntrinsicMotorPromotionGateTest {
    @Test
    fun signedCandidateWithFullCoverageAndNoRegressionIsAccepted() {
        val decision = verifiedGate().evaluate(
            candidate = candidate(),
            report = improvingReport()
        )

        assertTrue(decision.reasons.toString(), decision.accepted)
    }

    @Test
    fun anyDomainRegressionBlocksPromotion() {
        val report = improvingReport().copy(
            scores = improvingReport().scores +
                (ReasoningBenchmarkDomain.SPANISH_DIALOGUE to BenchmarkDomainScore(800, 799))
        )

        val decision = verifiedGate().evaluate(candidate(), report)

        assertFalse(decision.accepted)
        assertTrue(decision.reasons.any { it.startsWith("domain_regression") })
    }

    @Test
    fun missingResearchTechniqueBlocksPromotion() {
        val candidate = candidate().copy(
            techniques = setOf(IntrinsicMotorTechnique.LOOPED_LATENT_DEPTH)
        )

        val decision = verifiedGate().evaluate(candidate, improvingReport())

        assertFalse(decision.accepted)
        assertTrue(decision.reasons.any { it.startsWith("required_techniques_missing") })
    }

    @Test
    fun missingBenchmarkDomainBlocksPromotion() {
        val incompleteScores = improvingReport().scores - ReasoningBenchmarkDomain.TOOL_USE

        val decision = verifiedGate().evaluate(
            candidate(),
            IntrinsicMotorBenchmarkReport(incompleteScores)
        )

        assertFalse(decision.accepted)
        assertTrue(decision.reasons.any { it.startsWith("benchmark_domains_missing") })
    }

    @Test
    fun unsignedOrUnverifiedCandidateBlocksPromotion() {
        val decision = IntrinsicMotorPromotionGate(
            evidenceVerifier = IntrinsicLearningEvidenceVerifier { false }
        ).evaluate(candidate(), improvingReport())

        assertFalse(decision.accepted)
        assertTrue(decision.reasons.contains("candidate_signature_unverified"))
    }

    @Test
    fun rollbackMustPointToCurrentMotorArtifact() {
        val decision = verifiedGate().evaluate(
            candidate = candidate().copy(rollbackArtifactSha256 = digest('f')),
            report = improvingReport()
        )

        assertFalse(decision.accepted)
        assertTrue(decision.reasons.contains("rollback_does_not_preserve_current_artifact"))
    }

    @Test
    fun unchangedMotorArtifactCannotPretendToBeLearning() {
        val base = candidate()
        val decision = verifiedGate().evaluate(
            candidate = base.copy(candidateArtifactSha256 = base.currentArtifactSha256),
            report = improvingReport()
        )

        assertFalse(decision.accepted)
        assertTrue(decision.reasons.contains("candidate_artifact_unchanged"))
    }

    @Test
    fun promotionGateExposesNoApiMemoryOrInstallationWriter() {
        val forbidden = listOf(
            ReasoningProviderConfig::class.java.name,
            "Repository",
            "Dao",
            "MemoryUseCase",
            "Installer",
            "Endpoint",
            "RuntimeAccess"
        )
        val exposedTypes = IntrinsicMotorPromotionGate::class.java.declaredConstructors
            .flatMap { constructor -> constructor.parameterTypes.toList() } +
            IntrinsicMotorPromotionGate::class.java.declaredMethods
                .flatMap { method -> method.parameterTypes.toList() + method.returnType }

        assertTrue(
            exposedTypes.none { type ->
                forbidden.any { token -> type.name.contains(token) }
            }
        )
    }

    private fun verifiedGate(): IntrinsicMotorPromotionGate {
        return IntrinsicMotorPromotionGate(
            evidenceVerifier = IntrinsicLearningEvidenceVerifier { true }
        )
    }

    private fun candidate(): IntrinsicMotorLearningCandidate {
        val role = ReasoningMotorRole.DELIBERATIVE
        return IntrinsicMotorLearningCandidate(
            role = role,
            fromVersion = "deliberative-v1",
            candidateVersion = "deliberative-v2",
            techniques = MorimilIntrinsicMotorBlueprints.requireBlueprint(role).requiredTechniques,
            currentArtifactSha256 = digest('a'),
            candidateArtifactSha256 = digest('b'),
            rollbackArtifactSha256 = digest('a'),
            trainingCorpusSha256 = digest('c'),
            benchmarkReportSha256 = digest('d'),
            signerKeyId = "morimil-learning-test-key",
            signatureAlgorithm = "ed25519",
            signature = "test-signature"
        )
    }

    private fun improvingReport(): IntrinsicMotorBenchmarkReport {
        return IntrinsicMotorBenchmarkReport(
            scores = ReasoningBenchmarkDomain.entries.associateWith { domain ->
                BenchmarkDomainScore(
                    baseline = 700,
                    candidate = if (domain == ReasoningBenchmarkDomain.PLANNING) 720 else 700
                )
            }
        )
    }

    private fun digest(character: Char): String {
        return "sha256:" + character.toString().repeat(64)
    }
}
