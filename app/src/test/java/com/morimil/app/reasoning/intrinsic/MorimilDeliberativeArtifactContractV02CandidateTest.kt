package com.morimil.app.reasoning.intrinsic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class MorimilDeliberativeArtifactContractV02CandidateTest {
    @Test
    fun exactAcquisitionEvidenceRemainsResearchOnly() {
        MorimilDeliberativeArtifactContractV02Candidate.validateResearchEvidence(validEvidence())

        assertNull(MorimilDeliberativeArtifactContractV02Candidate.sourceModelRevision)
        assertFalse(MorimilDeliberativeArtifactContractV02Candidate.CERTIFIED)
        assertFalse(MorimilDeliberativeArtifactContractV02Candidate.SIGNED)
        assertFalse(MorimilDeliberativeArtifactContractV02Candidate.INSTALLED)
        assertFalse(MorimilDeliberativeArtifactContractV02Candidate.PROMOTION_ALLOWED)
    }

    @Test
    fun directUpstreamBinaryIsNotMisrepresentedAsMorimilConversion() {
        val candidate = MorimilDeliberativeArtifactContractV02Candidate

        assertEquals("DIRECT_UPSTREAM_BINARY_RENAME_ONLY", candidate.ACQUISITION_MODE)
        assertFalse(candidate.CONVERSION_PERFORMED_BY_MORIMIL)
        assertEquals(
            "ba9ca88da013b537b6ed38108be609b8db1c3a16",
            candidate.UPSTREAM_ARTIFACT_REVISION
        )
        assertEquals("google-ai-edge/gallery", candidate.OFFICIAL_ALLOWLIST_REPOSITORY)
        assertEquals(
            "126501c8849affcfb094d2c5b193aa5deb1434a6",
            candidate.OFFICIAL_ALLOWLIST_REVISION
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun artifactRepositoryRevisionCannotBeUsedAsSourceModelRevision() {
        MorimilDeliberativeArtifactContractV02Candidate.validateResearchEvidence(
            validEvidence().copy(
                sourceModelRevision =
                    MorimilDeliberativeArtifactContractV02Candidate.UPSTREAM_REPOSITORY_REVISION
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun candidateCannotClaimPromotion() {
        MorimilDeliberativeArtifactContractV02Candidate.validateResearchEvidence(
            validEvidence().copy(promotionAllowed = true)
        )
    }

    private fun validEvidence(): DeliberativeCandidateEvidenceV02 {
        return DeliberativeCandidateEvidenceV02(
            profileVersion = MorimilDeliberativeArtifactContractV02Candidate.PROFILE_VERSION,
            localFilename = MorimilDeliberativeArtifactContractV02Candidate.LOCAL_CANDIDATE_FILENAME,
            upstreamRepository = MorimilDeliberativeArtifactContractV02Candidate.UPSTREAM_REPOSITORY,
            upstreamRepositoryRevision =
                MorimilDeliberativeArtifactContractV02Candidate.UPSTREAM_REPOSITORY_REVISION,
            upstreamArtifactFilename =
                MorimilDeliberativeArtifactContractV02Candidate.UPSTREAM_ARTIFACT_FILENAME,
            artifactSha256 = MorimilDeliberativeArtifactContractV02Candidate.ARTIFACT_SHA256,
            artifactSizeBytes = MorimilDeliberativeArtifactContractV02Candidate.ARTIFACT_SIZE_BYTES,
            sourceModelRevision = null,
            certified = false,
            signed = false,
            installed = false,
            promotionAllowed = false
        )
    }
}
