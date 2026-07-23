package com.morimil.app.reasoning.intrinsic

import com.morimil.app.reasoning.ReasoningMotorRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MorimilNormalDeliberativeActivationGateV0Test {
    @Test
    fun currentPhysicalCandidateRemainsBlockedByFrozenEvidence() {
        val evidence = MorimilNormalDeliberativeActivationGateV0.currentCandidateEvidence
        val decision = MorimilNormalDeliberativeActivationGateV0.currentCandidateDecision

        assertEquals(120, evidence.completedCaseCount)
        assertEquals(40, evidence.falseAcceptedCount)
        assertFalse(evidence.benchmarkQualityGatePassed)
        assertFalse(decision.activationAllowed)
        assertEquals(
            setOf(
                NormalDeliberativeActivationBlockerV0.BENCHMARK_QUALITY_GATE_FAILED,
                NormalDeliberativeActivationBlockerV0.FALSE_ACCEPTANCES_PRESENT,
                NormalDeliberativeActivationBlockerV0.SOURCE_MODEL_REVISION_MISSING,
                NormalDeliberativeActivationBlockerV0.REPRODUCIBLE_CONVERSION_EVIDENCE_MISSING,
                NormalDeliberativeActivationBlockerV0.CERTIFICATION_MISSING,
                NormalDeliberativeActivationBlockerV0.SIGNATURE_MISSING,
                NormalDeliberativeActivationBlockerV0.INSTALLATION_AUTHORIZATION_MISSING,
                NormalDeliberativeActivationBlockerV0.PRODUCTION_AUTHORIZATION_MISSING
            ),
            decision.blockers
        )
    }

    @Test
    fun completeFutureEvidenceCanPassPolicyButDoesNotAutoRegisterMotor() {
        val futureEvidence = MorimilNormalDeliberativeActivationGateV0
            .currentCandidateEvidence
            .copy(
                benchmarkQualityGatePassed = true,
                falseAcceptedCount = 0,
                sourceModelRevision = "exact-source-model-revision",
                reproducibleConversionEvidence = true,
                certified = true,
                signed = true,
                installationAuthorized = true,
                productionAuthorized = true
            )

        val decision = MorimilNormalDeliberativeActivationGateV0.evaluate(futureEvidence)

        assertTrue(decision.activationAllowed)
        assertTrue(decision.blockers.isEmpty())
        assertEquals(
            setOf(ReasoningMotorRole.INTUITIVE),
            MorimilNormalIntrinsicRuntimeV0.registeredRoles
        )
    }

    @Test
    fun incompleteBenchmarkCannotPassEvenWhenOtherFlagsArePositive() {
        val incompleteEvidence = MorimilNormalDeliberativeActivationGateV0
            .currentCandidateEvidence
            .copy(
                completedCaseCount = 119,
                benchmarkQualityGatePassed = true,
                falseAcceptedCount = 0,
                sourceModelRevision = "exact-source-model-revision",
                reproducibleConversionEvidence = true,
                certified = true,
                signed = true,
                installationAuthorized = true,
                productionAuthorized = true
            )

        val decision = MorimilNormalDeliberativeActivationGateV0.evaluate(incompleteEvidence)

        assertFalse(decision.activationAllowed)
        assertEquals(
            setOf(NormalDeliberativeActivationBlockerV0.PHYSICAL_BENCHMARK_INCOMPLETE),
            decision.blockers
        )
    }

    @Test
    fun candidateIdentityMismatchFailsClosed() {
        val result = runCatching {
            MorimilNormalDeliberativeActivationGateV0.evaluate(
                MorimilNormalDeliberativeActivationGateV0.currentCandidateEvidence.copy(
                    artifactSha256 = "sha256:${"0".repeat(64)}"
                )
            )
        }

        assertTrue(result.isFailure)
        assertEquals(
            "normal_deliberative_artifact_sha256_mismatch",
            result.exceptionOrNull()?.message
        )
    }

    @Test
    fun normalRuntimeExposesBlockersAndRegistersNoDeliberativeRole() {
        val coordinator = MorimilNormalIntrinsicRuntimeV0.createCoordinator()
        val decision = MorimilNormalIntrinsicRuntimeV0.deliberativeActivationDecision

        assertFalse(decision.activationAllowed)
        assertTrue(decision.blockers.isNotEmpty())
        assertEquals(setOf(ReasoningMotorRole.INTUITIVE), coordinator.availableRoles())
        assertFalse(ReasoningMotorRole.DELIBERATIVE in coordinator.availableRoles())
        assertFalse(ReasoningMotorRole.METACOGNITIVE in coordinator.availableRoles())
    }
}
