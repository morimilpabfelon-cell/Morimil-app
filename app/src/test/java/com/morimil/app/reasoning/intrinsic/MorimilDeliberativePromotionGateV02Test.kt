package com.morimil.app.reasoning.intrinsic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MorimilDeliberativePromotionGateV02Test {
    @Test
    fun physicalEvidenceCanPassResearchWhileProductionRemainsBlocked() {
        val decision = MorimilDeliberativePromotionGateV02.evaluate(validEvidence())

        assertTrue(decision.researchEvidenceAccepted)
        assertFalse(decision.productionPromotionAllowed)
        assertEquals(6, decision.blockers.size)
        assertTrue(
            decision.blockers.contains(
                DeliberativePromotionBlockerV02.SOURCE_MODEL_REVISION_MISSING
            )
        )
        assertTrue(
            decision.blockers.contains(
                DeliberativePromotionBlockerV02.PRODUCTION_AUTHORIZATION_MISSING
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun artifactRepositoryRevisionCannotBecomeSourceModelRevision() {
        MorimilDeliberativePromotionGateV02.evaluate(
            validEvidence().copy(
                sourceModelRevision =
                    MorimilDeliberativeArtifactContractV02Candidate.UPSTREAM_REPOSITORY_REVISION
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun mismatchedArtifactHashIsRejected() {
        MorimilDeliberativePromotionGateV02.evaluate(
            validEvidence().copy(artifactSha256 = "sha256:" + "0".repeat(64))
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingUnpluggedEnergyObservationIsRejected() {
        MorimilDeliberativePromotionGateV02.evaluate(
            validEvidence().copy(observedChargeDecreaseMicroampHours = 0L)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun insufficientStrictInferenceEvidenceIsRejected() {
        MorimilDeliberativePromotionGateV02.evaluate(
            validEvidence().copy(strictInferenceCount = 35)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun forgedProductionAuthorizationIsRejected() {
        MorimilDeliberativePromotionGateV02.evaluate(
            validEvidence().copy(productionAuthorization = true)
        )
    }

    private fun validEvidence(): DeliberativeCandidatePromotionEvidenceV02 {
        val candidate = MorimilDeliberativeArtifactContractV02Candidate
        return DeliberativeCandidatePromotionEvidenceV02(
            gateVersion = MorimilDeliberativePromotionGateV02.GATE_VERSION,
            candidateProfileVersion = candidate.PROFILE_VERSION,
            artifactVersion = candidate.ARTIFACT_VERSION,
            artifactFilename = candidate.LOCAL_CANDIDATE_FILENAME,
            artifactSha256 = candidate.ARTIFACT_SHA256,
            artifactSizeBytes = candidate.ARTIFACT_SIZE_BYTES,
            runtimeVersion = MorimilDeliberativePromotionGateV02.REQUIRED_RUNTIME_VERSION,
            backend = MorimilDeliberativePromotionGateV02.REQUIRED_BACKEND,
            requiredAbi = MorimilDeliberativePromotionGateV02.REQUIRED_ABI,
            process64Bit = true,
            physicalArm64HarnessPassed = true,
            sustainedProfilePasses = 6,
            unpluggedProfilePasses = 6,
            strictInferenceCount = 36,
            allStrictOutputsPassed = true,
            hashStable = true,
            allConversationsClosed = true,
            allEnginesClosed = true,
            allSamplesUnplugged = true,
            observedChargeDecreaseMicroampHours = 29_000L,
            p95InferenceMilliseconds = 8_953L,
            peakTotalPssKilobytes = 2_649_395L,
            maximumBatteryTemperatureCelsius = 35.0,
            maximumThermalStatus = 0,
            lowMemoryObserved = false,
            errors = emptyList(),
            sourceModelRevision = null,
            reproducibleConversionEvidence = false,
            certified = false,
            signed = false,
            installed = false,
            productionAuthorization = false,
            normalRuntimeActivated = false,
            promotionAllowed = false
        )
    }
}
