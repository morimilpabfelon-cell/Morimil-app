package com.morimil.app.reasoning.intrinsic

/**
 * Research-only identity profile for the acquired Gemma 3n E2B LiteRT-LM candidate.
 *
 * This is deliberately not a signed artifact contract and cannot authorize loading,
 * installation or promotion. The exact base-model source revision and reproducible
 * conversion evidence are still missing.
 */
object MorimilDeliberativeArtifactContractV02Candidate {
    const val PROFILE_VERSION = "morimil.deliberative.artifact.contract.v0.2-candidate"
    const val ARTIFACT_VERSION = "morimil-deliberative-v0.2"
    const val LOCAL_CANDIDATE_FILENAME = "morimil-deliberative-v0.2.candidate.litertlm"
    const val LITERT_LM_DEPENDENCY_VERSION = "0.14.0"
    const val FORMAT_ID = "litertlm.v1"
    const val ARCHITECTURE_ID = "google.gemma3n.text.e2b.it"
    const val SOURCE_MODEL_ID = "google/gemma-3n-E2B-it"
    const val UPSTREAM_REPOSITORY = "google/gemma-3n-E2B-it-litert-lm"
    const val UPSTREAM_REPOSITORY_REVISION = "c03b6f60b8da6c5400b6838a2cf26420f80c0a01"
    const val UPSTREAM_ARTIFACT_FILENAME = "gemma-3n-E2B-it-int4.litertlm"
    const val ARTIFACT_SHA256 =
        "sha256:2ed7bc3a0026c93d5b8a4544b352d9d00cd66ff0bac3ef6a20ac3d2cba4010d6"
    const val ARTIFACT_SIZE_BYTES = 3_655_827_456L
    const val CONTEXT_WINDOW_TOKENS = 4_096
    const val QUANTIZATION_PROFILE = "litertlm.int4.per-channel"
    const val LICENSE_ID = "gemma"
    const val AUTHORITY_PROFILE = "morimil.hybrid-authority-router.v0"

    const val CERTIFIED = false
    const val SIGNED = false
    const val INSTALLED = false
    const val PROMOTION_ALLOWED = false

    val sourceModelRevision: String?
        get() = null

    fun validateResearchEvidence(evidence: DeliberativeCandidateEvidenceV02) {
        require(evidence.profileVersion == PROFILE_VERSION) { "candidate_profile_version_mismatch" }
        require(evidence.localFilename == LOCAL_CANDIDATE_FILENAME) { "candidate_filename_mismatch" }
        require(evidence.upstreamRepository == UPSTREAM_REPOSITORY) { "candidate_repository_mismatch" }
        require(evidence.upstreamRepositoryRevision == UPSTREAM_REPOSITORY_REVISION) {
            "candidate_repository_revision_mismatch"
        }
        require(evidence.upstreamArtifactFilename == UPSTREAM_ARTIFACT_FILENAME) {
            "candidate_upstream_artifact_mismatch"
        }
        require(evidence.artifactSha256 == ARTIFACT_SHA256) { "candidate_artifact_sha256_mismatch" }
        require(evidence.artifactSizeBytes == ARTIFACT_SIZE_BYTES) { "candidate_artifact_size_mismatch" }
        require(evidence.sourceModelRevision == null) { "candidate_source_revision_must_remain_unassigned" }
        require(!evidence.certified) { "candidate_must_not_be_certified" }
        require(!evidence.signed) { "candidate_must_not_be_signed" }
        require(!evidence.installed) { "candidate_must_not_be_installed" }
        require(!evidence.promotionAllowed) { "candidate_must_not_be_promotable" }
    }
}

data class DeliberativeCandidateEvidenceV02(
    val profileVersion: String,
    val localFilename: String,
    val upstreamRepository: String,
    val upstreamRepositoryRevision: String,
    val upstreamArtifactFilename: String,
    val artifactSha256: String,
    val artifactSizeBytes: Long,
    val sourceModelRevision: String?,
    val certified: Boolean,
    val signed: Boolean,
    val installed: Boolean,
    val promotionAllowed: Boolean
)
