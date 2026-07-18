package com.morimil.app.data.genesis.ultra

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class GenesisUltraAtomicBirthEvidenceVerifierTest {
    @Test
    fun verifiesOfficialGenesisBirthEvidenceWithoutActivatingBirth() {
        val fixture = fixture()

        val verified = GenesisUltraAtomicBirthEvidenceVerifier.verify(fixture.request)
        val persistence = verified.copyPersistenceBundle()

        assertEquals("Genesis Libre", persistence.instanceIdentity.companionName)
        assertEquals(13, GenesisUltraAtomicBirthPersistenceValidator.mandatoryArtifactKinds.size)
        assertTrue(GenesisUltraAtomicBirthPersistenceValidator.validate(persistence).isEmpty())
    }

    @Test
    fun rejectsForgedFreedomCharterSignature() {
        val fixture = fixture()
        val artifacts = fixture.request.artifacts.map { artifact ->
            if (artifact.artifactKind != "freedom_charter") return@map artifact
            val root = JSONObject(artifact.payload.toString(StandardCharsets.UTF_8))
            root.getJSONObject("signature").put("signature_value", "00".repeat(64))
            artifact.copy(payload = root.toString().utf8())
        }

        val failure = runCatching {
            GenesisUltraAtomicBirthEvidenceVerifier.verify(
                fixture.request.copy(artifacts = artifacts)
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message?.contains("freedom_charter_signature_invalid") == true)
    }

    @Test
    fun rejectsMissingDetachedSeedSignature() {
        val fixture = fixture()
        val artifacts = fixture.request.artifacts.filterNot { artifact ->
            artifact.artifactKind == "seed_signature"
        }

        val failure = runCatching {
            GenesisUltraAtomicBirthEvidenceVerifier.verify(
                fixture.request.copy(artifacts = artifacts)
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message?.contains("birth_artifact_kind_invalid:seed_signature") == true)
    }

    @Test
    fun rejectsJournalWhoseSourceSignatureWasForged() {
        val fixture = fixture()
        val journal = fixture.request.journal.mapIndexed { index, evidence ->
            if (index != 3) return@mapIndexed evidence
            val root = JSONObject(evidence.sourceBytes.toString(StandardCharsets.UTF_8))
            root.getJSONObject("signature").put("signature_value", "00".repeat(64))
            val source = root.toString().utf8()
            evidence.copy(
                entry = GenesisUltraAtomicBirthDocumentParser.parseJournalEntry(source),
                sourceBytes = source
            )
        }

        val failure = runCatching {
            GenesisUltraAtomicBirthEvidenceVerifier.verify(fixture.request.copy(journal = journal))
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message?.contains("birth_journal_signature_invalid") == true)
    }

    @Test
    fun rejectsUnknownFieldInAnyBirthDocument() {
        val fixture = fixture()
        val artifacts = fixture.request.artifacts.map { artifact ->
            if (artifact.artifactKind != "birth_state") return@map artifact
            val root = JSONObject(artifact.payload.toString(StandardCharsets.UTF_8))
            root.put("untrusted_extension", true)
            artifact.copy(payload = root.toString().utf8())
        }

        val failure = runCatching {
            GenesisUltraAtomicBirthEvidenceVerifier.verify(
                fixture.request.copy(artifacts = artifacts)
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message?.contains("atomic_birth_unexpected_or_missing_fields") == true)
    }

    private fun fixture(): Fixture {
        val vectors = resourceJson("/genesis-ultra/atomic_birth_conformance.json")
        val fixture = vectors.getJSONObject("fixture")
        val charter = vectors.getJSONObject("charter")
        val seedManifest = fixture.getJSONObject("seed_manifest")
        val seedSignatureText = resourceText("/genesis-ultra/seed_release_signature.json")
        val seedSignature = GenesisUltraContractParser.parseSignatureEnvelope(seedSignatureText)
        val guardianPublicKey = decodeLowerHex(
            vectors.getJSONObject("guardian_test_signing_key").getString("public_key_hex")
        )
        val guardianRegistry = GenesisUltraTrustedGuardianKeyEpochRegistry(
            listOf(
                GenesisUltraTrustedGuardianKeyEpoch(
                    guardianId = seedSignature.signerId,
                    keyEpochId = seedSignature.keyEpochId,
                    publicKeyRef = seedSignature.publicKeyRef,
                    status = "active",
                    rawPublicKey = guardianPublicKey
                )
            )
        )
        val manifestText = seedManifest.toString()
        val releaseFiles = mapOf(
            "doctrine/free-birth.md" to "free birth doctrine".utf8(),
            "identity/seed.identity.json" to "free birth seed identity".utf8()
        )
        val release = GenesisUltraReleaseVerifier(guardianRegistry.signatureVerifier()).verify(
            GenesisUltraReleaseBundle(
                manifestJson = manifestText,
                signatureJson = seedSignatureText,
                files = releaseFiles
            )
        )

        val artifacts = buildList {
            add(artifact("birth/seed-manifest.json", "seed_manifest", manifestText))
            add(artifact("birth/seed-signature.json", "seed_signature", seedSignatureText))
            add(artifact("birth/instance-identity.json", "instance_identity", fixture, "instance_identity"))
            add(artifact("birth/freedom-charter.json", "freedom_charter", charter.toString()))
            add(artifact("birth/initial-body-record.json", "initial_body_record", fixture, "initial_body_record"))
            add(artifact("birth/initial-body-registry.json", "initial_body_registry", fixture, "initial_body_registry"))
            add(artifact("birth/initial-body-key-epoch.json", "initial_body_key_epoch", fixture, "initial_body_key_epoch"))
            add(artifact("birth/initial-body-possession.json", "initial_body_possession", fixture, "initial_body_possession"))
            add(artifact("birth/first-memory-event.json", "first_memory_event", fixture, "first_memory_event"))
            add(artifact("birth/recovery-policy.json", "recovery_policy", fixture, "recovery_policy"))
            add(artifact("birth/birth-recovery-state.json", "birth_recovery_state", fixture, "birth_recovery_state"))
            add(artifact("birth/birth-state.json", "birth_state", fixture, "birth_state"))
            add(artifact("birth/birth-receipt.json", "birth_receipt", fixture, "birth_receipt"))
            releaseFiles.forEach { (path, bytes) ->
                add(GenesisUltraBirthArtifact(path, "seed_file", bytes.copyOf()))
            }
        }

        val journalArray = fixture.getJSONArray("journal_entries")
        val journal = List(journalArray.length()) { index ->
            val source = journalArray.getJSONObject(index).toString().utf8()
            GenesisUltraBirthJournalEvidence(
                entry = GenesisUltraAtomicBirthDocumentParser.parseJournalEntry(
                    source.toString(StandardCharsets.UTF_8)
                ),
                sourceBytes = source
            )
        }
        val bodyPublicKey = decodeLowerHex(
            fixture.getJSONObject("test_public_keys").getString("body")
        )
        val request = GenesisUltraAtomicBirthEvidenceRequest(
            release = release,
            guardianKeyEpochRegistry = guardianRegistry,
            bodyRawPublicKey = bodyPublicKey,
            artifacts = artifacts,
            journal = journal,
            evaluatedAt = fixture.getJSONObject("instance_identity").getString("born_at")
        )
        return Fixture(request)
    }

    private fun artifact(
        path: String,
        kind: String,
        source: JSONObject,
        field: String
    ): GenesisUltraBirthArtifact = artifact(path, kind, source.getJSONObject(field).toString())

    private fun artifact(path: String, kind: String, json: String): GenesisUltraBirthArtifact {
        return GenesisUltraBirthArtifact(path, kind, json.utf8())
    }

    private fun resourceJson(path: String): JSONObject = JSONObject(resourceText(path))

    private fun resourceText(path: String): String {
        return checkNotNull(javaClass.getResource(path)) { "missing test resource: $path" }.readText()
    }

    private fun decodeLowerHex(value: String): ByteArray {
        require(value.length % 2 == 0)
        return ByteArray(value.length / 2) { index ->
            value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun String.utf8(): ByteArray = toByteArray(StandardCharsets.UTF_8)

    private data class Fixture(val request: GenesisUltraAtomicBirthEvidenceRequest)
}
