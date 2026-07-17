package com.morimil.app.data.genesis.ultra

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature

class GenesisUltraReleaseVerifierTest {
    @Test
    fun verifiesExactFileSetRootBindingAndGuardianSignature() {
        val fixture = signedFixture()

        val verified = fixture.verifier.verify(fixture.bundle)

        assertEquals(fixture.rootHash, verified.verifiedRootHash)
        assertEquals(2, verified.verifiedFileCount)
        assertEquals("guardian_01HRELEASE000000000001", verified.signature.signerId)
    }

    @Test
    fun verifiedReleaseRetainsDefensiveByteSnapshots() {
        val fixture = signedFixture()
        val verified = fixture.verifier.verify(fixture.bundle)
        val expected = verified.copyVerifiedFiles().getValue(IDENTITY_PATH)

        fixture.bundle.files.getValue(IDENTITY_PATH)[0] = 'X'.code.toByte()
        val firstCopy = verified.copyVerifiedFiles()
        assertEquals(expected.toList(), firstCopy.getValue(IDENTITY_PATH).toList())

        firstCopy.getValue(IDENTITY_PATH)[0] = 'Y'.code.toByte()
        assertEquals(
            expected.toList(),
            verified.copyVerifiedFiles().getValue(IDENTITY_PATH).toList()
        )
    }

    @Test
    fun rejectsTamperedPayloadBeforeSignatureAcceptance() {
        val fixture = signedFixture()
        val tamperedFiles = fixture.bundle.files.toMutableMap().apply {
            this[IDENTITY_PATH] = "tampered".toByteArray()
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            fixture.verifier.verify(fixture.bundle.copy(files = tamperedFiles))
        }

        assertEquals("release_file_digest_mismatch:$IDENTITY_PATH", error.message)
    }

    @Test
    fun rejectsUnexpectedPayloadFile() {
        val fixture = signedFixture()
        val unexpectedFiles = fixture.bundle.files.toMutableMap().apply {
            this["unexpected.txt"] = byteArrayOf(1)
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            fixture.verifier.verify(fixture.bundle.copy(files = unexpectedFiles))
        }

        assertTrue(error.message.orEmpty().startsWith("release_file_set_mismatch:"))
    }

    @Test
    fun rejectsEnvelopeThatDoesNotBindSeedRoot() {
        val fixture = signedFixture()
        val envelope = JSONObject(fixture.bundle.signatureJson)
            .put("signed_digest", "sha256:" + "f".repeat(64))

        val error = assertThrows(IllegalArgumentException::class.java) {
            fixture.verifier.verify(fixture.bundle.copy(signatureJson = envelope.toString()))
        }

        assertEquals("seed_release_signed_digest_mismatch", error.message)
    }

    @Test
    fun rejectsCryptographicallyValidKeyWhenSignerIdentityIsNotTrusted() {
        val fixture = signedFixture()
        val wrongSignerKey = GenesisUltraTrustedEd25519Key(
            signerType = "guardian",
            signerId = "guardian_01HOTHER000000000000001",
            keyEpochId = fixture.keyEpochId,
            publicKeyRef = fixture.publicKeyRef,
            rawPublicKey = fixture.rawPublicKey
        )
        val untrustedVerifier = GenesisUltraReleaseVerifier(
            GenesisUltraEd25519SignatureVerifier(listOf(wrongSignerKey))
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            untrustedVerifier.verify(fixture.bundle)
        }

        assertEquals("seed_release_signature_invalid_or_untrusted", error.message)
    }

    @Test
    fun rejectsManifestAdditionalField() {
        val fixture = signedFixture()
        val manifest = JSONObject(fixture.bundle.manifestJson).put("display_name", "not allowed")

        val error = assertThrows(IllegalArgumentException::class.java) {
            fixture.verifier.verify(fixture.bundle.copy(manifestJson = manifest.toString()))
        }

        assertTrue(error.message.orEmpty().startsWith("unexpected_or_missing_fields:"))
    }

    private fun signedFixture(): Fixture {
        val identityBytes = "{\"schema_version\":\"test.identity.v0.1\"}".toByteArray()
        val doctrineBytes = "Genesis Ultra test doctrine\n".toByteArray()
        val identityDigest = GenesisUltraHashProfile.sha256(identityBytes)
        val doctrineDigest = GenesisUltraHashProfile.sha256(doctrineBytes)
        val seedManifestWithoutRoot = GenesisUltraSeedManifest(
            schemaVersion = "genesis.seed.manifest.v0.1",
            protocolVersion = "genesis.protocol.v0.1",
            hashProfile = "genesis.hash.fields.v0.1",
            seedId = "seed_01HRELEASE000000000000001",
            identityDigest = identityDigest,
            doctrineDigest = doctrineDigest,
            files = listOf(
                GenesisUltraSeedFileRecord(
                    path = IDENTITY_PATH,
                    kind = "identity",
                    required = true,
                    digest = identityDigest
                ),
                GenesisUltraSeedFileRecord(
                    path = DOCTRINE_PATH,
                    kind = "doctrine",
                    required = true,
                    digest = doctrineDigest
                )
            ),
            rootHash = "sha256:" + "0".repeat(64)
        )
        val rootHash = GenesisUltraHashProfile.seedRoot(seedManifestWithoutRoot)
        val manifest = JSONObject()
            .put("schema_version", seedManifestWithoutRoot.schemaVersion)
            .put("protocol_version", seedManifestWithoutRoot.protocolVersion)
            .put("hash_profile", seedManifestWithoutRoot.hashProfile)
            .put("seed_id", seedManifestWithoutRoot.seedId)
            .put("identity_digest", identityDigest)
            .put("doctrine_digest", doctrineDigest)
            .put(
                "files",
                JSONArray().apply {
                    seedManifestWithoutRoot.files.forEach { file ->
                        put(
                            JSONObject()
                                .put("path", file.path)
                                .put("kind", file.kind)
                                .put("required", file.required)
                                .put("digest", file.digest)
                        )
                    }
                }
            )
            .put("root_hash", rootHash)

        val keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val rawPublicKey = keyPair.public.encoded.takeLast(32).toByteArray()
        val publicKeyRef = GenesisUltraHashProfile.sha256(rawPublicKey)
        val keyEpochId = "guardian_epoch_01HRELEASE00001"
        val unsignedEnvelope = GenesisUltraSignatureEnvelope(
            schemaVersion = "genesis.signature.envelope.v0.1",
            signatureProfile = "genesis.signature.ed25519.v0.1",
            signerType = "guardian",
            signerId = "guardian_01HRELEASE000000000001",
            keyEpochId = keyEpochId,
            signedDomain = GenesisUltraHashProfile.SEED_ROOT_DOMAIN,
            signedDigest = rootHash,
            signatureValue = "0".repeat(128),
            createdAt = "2026-07-17T00:00:00Z",
            publicKeyRef = publicKeyRef
        )
        val signer = Signature.getInstance("Ed25519")
        signer.initSign(keyPair.private)
        signer.update(GenesisUltraHashProfile.signatureEnvelopePreimage(unsignedEnvelope))
        val signatureValue = signer.sign().toHex()
        val envelope = JSONObject()
            .put("schema_version", unsignedEnvelope.schemaVersion)
            .put("signature_profile", unsignedEnvelope.signatureProfile)
            .put("signer_type", unsignedEnvelope.signerType)
            .put("signer_id", unsignedEnvelope.signerId)
            .put("key_epoch_id", unsignedEnvelope.keyEpochId)
            .put("signed_domain", unsignedEnvelope.signedDomain)
            .put("signed_digest", unsignedEnvelope.signedDigest)
            .put("signature_value", signatureValue)
            .put("created_at", unsignedEnvelope.createdAt)
            .put("public_key_ref", unsignedEnvelope.publicKeyRef)

        val bundle = GenesisUltraReleaseBundle(
            manifestJson = manifest.toString(),
            signatureJson = envelope.toString(),
            files = mapOf(
                IDENTITY_PATH to identityBytes,
                DOCTRINE_PATH to doctrineBytes
            )
        )
        val trustedKey = GenesisUltraTrustedEd25519Key(
            signerType = unsignedEnvelope.signerType,
            signerId = unsignedEnvelope.signerId,
            keyEpochId = unsignedEnvelope.keyEpochId,
            publicKeyRef = publicKeyRef,
            rawPublicKey = rawPublicKey
        )
        val verifier = GenesisUltraReleaseVerifier(
            GenesisUltraEd25519SignatureVerifier(listOf(trustedKey))
        )
        return Fixture(
            bundle = bundle,
            verifier = verifier,
            rootHash = rootHash,
            rawPublicKey = rawPublicKey,
            publicKeyRef = publicKeyRef,
            keyEpochId = keyEpochId
        )
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }

    private data class Fixture(
        val bundle: GenesisUltraReleaseBundle,
        val verifier: GenesisUltraReleaseVerifier,
        val rootHash: String,
        val rawPublicKey: ByteArray,
        val publicKeyRef: String,
        val keyEpochId: String
    )

    private companion object {
        const val IDENTITY_PATH = "identity/companion.identity.json"
        const val DOCTRINE_PATH = "doctrine/doctrine.md"
    }
}
