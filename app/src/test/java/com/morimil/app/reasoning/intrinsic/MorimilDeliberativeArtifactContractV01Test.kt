package com.morimil.app.reasoning.intrinsic

import com.morimil.app.data.genesis.ultra.GenesisUltraHashProfile
import com.morimil.app.reasoning.ReasoningMotorRole
import com.morimil.app.reasoning.growth.MorimilIntrinsicMotorBlueprints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MorimilDeliberativeArtifactContractV01Test {
    @Test
    fun exactProfilePinsTheFirstDeployableTarget() {
        assertEquals("morimil.deliberative.artifact.contract.v0.1",
            MorimilDeliberativeArtifactContractV01.CONTRACT_VERSION)
        assertEquals("morimil-deliberative-v0.1",
            MorimilDeliberativeArtifactContractV01.ARTIFACT_VERSION)
        assertEquals("google.gemma3.text.1b.it",
            MorimilDeliberativeArtifactContractV01.ARCHITECTURE_ID)
        assertEquals("google.gemma3.tokenizer",
            MorimilDeliberativeArtifactContractV01.TOKENIZER_ID)
        assertEquals(4_096,
            MorimilDeliberativeArtifactContractV01.CONTEXT_WINDOW_TOKENS)
        assertEquals("litertlm.int4.per-channel",
            MorimilDeliberativeArtifactContractV01.QUANTIZATION_PROFILE)
        assertEquals("text-only", MorimilDeliberativeArtifactContractV01.MODALITY)
        assertEquals("cpu",
            MorimilDeliberativeArtifactContractV01.EXECUTION_BACKEND)
        assertEquals("google/gemma-3-1b-it",
            MorimilDeliberativeArtifactContractV01.SOURCE_MODEL_ID)
    }

    @Test
    fun manifestDigestMatchesOfflineCertifierGoldenVector() {
        assertEquals(
            "sha256:9e0863f34ed35090d60a66a57f51dc7" +
                "22e4976ac34eaa730d16b23c4b6603747",
            DeliberativeArtifactHashProfile.manifestDigest(manifest())
        )
    }

    @Test
    fun everyIdentityAndProvenanceFieldIsCommittedByTheManifestDigest() {
        val base = manifest()
        val baseDigest = DeliberativeArtifactHashProfile.manifestDigest(base)
        val changed = listOf(
            base.copy(contractVersion = base.contractVersion + "-changed"),
            base.copy(architectureId = base.architectureId + "-changed"),
            base.copy(tokenizerId = base.tokenizerId + "-changed"),
            base.copy(tokenizerSha256 = hash('d')),
            base.copy(contextWindowTokens = base.contextWindowTokens + 1),
            base.copy(quantizationProfile = base.quantizationProfile + "-changed"),
            base.copy(modality = base.modality + "-changed"),
            base.copy(executionBackend = base.executionBackend + "-changed"),
            base.copy(deliberationProfile = base.deliberationProfile + "-changed"),
            base.copy(sourceModelId = base.sourceModelId + "-changed"),
            base.copy(sourceModelRevision = "1".repeat(40)),
            base.copy(sourceModelSnapshotSha256 = hash('e')),
            base.copy(conversionRecipeSha256 = hash('f')),
            base.copy(licenseId = base.licenseId + "-changed")
        )

        changed.forEach { manifest ->
            assertNotEquals(baseDigest, DeliberativeArtifactHashProfile.manifestDigest(manifest))
        }
    }

    @Test
    fun incompatibleArchitectureFailsClosedBeforeSignatureVerification() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            MorimilDeliberativeArtifactContractV01.validate(
                manifest().copy(architectureId = "other.architecture")
            )
        }

        assertEquals("artifact_contract_architecture_mismatch", error.message)
    }

    @Test
    fun provenanceRequiresCanonicalHashesAndPinnedSourceRevision() {
        val badTokenizer = assertThrows(IllegalArgumentException::class.java) {
            MorimilDeliberativeArtifactContractV01.validate(
                manifest().copy(tokenizerSha256 = "sha256:not-a-digest")
            )
        }
        val badRevision = assertThrows(IllegalArgumentException::class.java) {
            MorimilDeliberativeArtifactContractV01.validate(
                manifest().copy(sourceModelRevision = "main")
            )
        }

        assertEquals("artifact_contract_tokenizer_sha256_invalid", badTokenizer.message)
        assertEquals("artifact_contract_source_revision_invalid", badRevision.message)
    }

    private fun manifest(): DeliberativeArtifactManifest {
        val bytes = "contract-fixture".toByteArray()
        return DeliberativeArtifactManifest(
            schemaVersion = DeliberativeArtifactHashProfile.MANIFEST_SCHEMA,
            contractVersion = MorimilDeliberativeArtifactContractV01.CONTRACT_VERSION,
            artifactVersion = MorimilDeliberativeArtifactContractV01.ARTIFACT_VERSION,
            artifactSha256 = GenesisUltraHashProfile.sha256(bytes),
            artifactSizeBytes = bytes.size.toLong(),
            formatId = MorimilDeliberativeArtifactContractV01.FORMAT_ID,
            runtimeAbi = MorimilDeliberativeArtifactContractV01.RUNTIME_ABI,
            architectureId = MorimilDeliberativeArtifactContractV01.ARCHITECTURE_ID,
            tokenizerId = MorimilDeliberativeArtifactContractV01.TOKENIZER_ID,
            tokenizerSha256 = hash('a'),
            contextWindowTokens =
                MorimilDeliberativeArtifactContractV01.CONTEXT_WINDOW_TOKENS,
            quantizationProfile =
                MorimilDeliberativeArtifactContractV01.QUANTIZATION_PROFILE,
            modality = MorimilDeliberativeArtifactContractV01.MODALITY,
            executionBackend =
                MorimilDeliberativeArtifactContractV01.EXECUTION_BACKEND,
            deliberationProfile =
                MorimilDeliberativeArtifactContractV01.DELIBERATION_PROFILE,
            sourceModelId = MorimilDeliberativeArtifactContractV01.SOURCE_MODEL_ID,
            sourceModelRevision = "0".repeat(40),
            sourceModelSnapshotSha256 = hash('b'),
            conversionRecipeSha256 = hash('c'),
            licenseId = MorimilDeliberativeArtifactContractV01.LICENSE_ID,
            blueprintVersion = MorimilIntrinsicMotorBlueprints.VERSION,
            techniques = MorimilIntrinsicMotorBlueprints
                .requireBlueprint(ReasoningMotorRole.DELIBERATIVE)
                .requiredTechniques
        )
    }

    private fun hash(character: Char): String {
        return "sha256:" + character.toString().repeat(64)
    }
}
