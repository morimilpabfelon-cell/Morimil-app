package com.morimil.app.reasoning.intrinsic

/**
 * Signed model profile for the first Morimil deliberative artifact.
 *
 * This contract identifies the local inference artifact. It does not grant the
 * model ownership of Morimil identity, memory, continuity, goals or lifecycle.
 */
object MorimilDeliberativeArtifactContractV01 {
    const val CONTRACT_VERSION = "morimil.deliberative.artifact.contract.v0.1"
    const val ARTIFACT_VERSION = "morimil-deliberative-v0.1"
    const val LITERT_LM_DEPENDENCY_VERSION = "0.14.0"
    const val FORMAT_ID = "litertlm.v1"
    const val RUNTIME_ABI = "litertlm.kotlin.android.v0.14.0"

    const val ARCHITECTURE_ID = "google.gemma3.text.1b.it"
    const val TOKENIZER_ID = "google.gemma3.tokenizer"
    const val CONTEXT_WINDOW_TOKENS = 4_096
    const val QUANTIZATION_PROFILE = "litertlm.int4.per-channel"
    const val MODALITY = "text-only"
    const val EXECUTION_BACKEND = "cpu"
    const val DELIBERATION_PROFILE = "morimil.request-scoped.textual-recurrence.v0"
    const val SOURCE_MODEL_ID = "google/gemma-3-1b-it"
    const val LICENSE_ID = "gemma"

    internal fun validate(manifest: DeliberativeArtifactManifest) {
        require(manifest.contractVersion == CONTRACT_VERSION) {
            "artifact_contract_version_unsupported"
        }
        require(manifest.artifactVersion == ARTIFACT_VERSION) {
            "artifact_contract_artifact_version_mismatch"
        }
        require(manifest.formatId == FORMAT_ID) {
            "artifact_contract_format_mismatch"
        }
        require(manifest.runtimeAbi == RUNTIME_ABI) {
            "artifact_contract_runtime_abi_mismatch"
        }
        require(manifest.architectureId == ARCHITECTURE_ID) {
            "artifact_contract_architecture_mismatch"
        }
        require(manifest.tokenizerId == TOKENIZER_ID) {
            "artifact_contract_tokenizer_mismatch"
        }
        require(SHA256_PATTERN.matches(manifest.tokenizerSha256)) {
            "artifact_contract_tokenizer_sha256_invalid"
        }
        require(manifest.contextWindowTokens == CONTEXT_WINDOW_TOKENS) {
            "artifact_contract_context_window_mismatch"
        }
        require(manifest.quantizationProfile == QUANTIZATION_PROFILE) {
            "artifact_contract_quantization_mismatch"
        }
        require(manifest.modality == MODALITY) {
            "artifact_contract_modality_mismatch"
        }
        require(manifest.executionBackend == EXECUTION_BACKEND) {
            "artifact_contract_backend_mismatch"
        }
        require(manifest.deliberationProfile == DELIBERATION_PROFILE) {
            "artifact_contract_deliberation_profile_mismatch"
        }
        require(manifest.sourceModelId == SOURCE_MODEL_ID) {
            "artifact_contract_source_model_mismatch"
        }
        require(SOURCE_REVISION_PATTERN.matches(manifest.sourceModelRevision)) {
            "artifact_contract_source_revision_invalid"
        }
        require(SHA256_PATTERN.matches(manifest.sourceModelSnapshotSha256)) {
            "artifact_contract_source_snapshot_sha256_invalid"
        }
        require(SHA256_PATTERN.matches(manifest.conversionRecipeSha256)) {
            "artifact_contract_conversion_recipe_sha256_invalid"
        }
        require(manifest.licenseId == LICENSE_ID) {
            "artifact_contract_license_mismatch"
        }
    }

    private val SHA256_PATTERN = Regex("sha256:[0-9a-f]{64}")
    private val SOURCE_REVISION_PATTERN = Regex("[0-9a-f]{40}")
}