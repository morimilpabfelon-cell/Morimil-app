package com.morimil.app.data.genesis.ultra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenesisUltraHashProfileTest {
    @Test
    fun fieldFramesMatchGoldenVectors() {
        assertEquals("343a686f6c610a", GenesisUltraHashProfile.frame("hola").toHex())
        assertEquals("353a6e69c3b16f0a", GenesisUltraHashProfile.frame("niño").toHex())
        assertEquals("343af09fa7ac0a", GenesisUltraHashProfile.frame("🧬").toHex())
    }

    @Test
    fun seedRootMatchesGoldenVector() {
        val manifest = GenesisUltraSeedManifest(
            schemaVersion = "genesis.seed.manifest.v0.1",
            protocolVersion = "genesis.protocol.v0.1",
            hashProfile = "genesis.hash.fields.v0.1",
            seedId = "seed_01HNEUTRAL000000000000001",
            identityDigest = "sha256:" + "b".repeat(64),
            doctrineDigest = "sha256:" + "a".repeat(64),
            files = listOf(
                GenesisUltraSeedFileRecord(
                    path = "identity/companion.identity.json",
                    kind = "identity",
                    required = true,
                    digest = "sha256:" + "b".repeat(64)
                ),
                GenesisUltraSeedFileRecord(
                    path = "doctrine/doctrine.md",
                    kind = "doctrine",
                    required = true,
                    digest = "sha256:" + "a".repeat(64)
                )
            ),
            rootHash = "sha256:0acaca2f62840715cfec8f7149bd5ab5fffd039fc8f0105bf5752ad25f385568"
        )

        assertEquals(manifest.rootHash, GenesisUltraHashProfile.seedRoot(manifest))
    }

    @Test
    fun instanceIdentityMatchesGoldenVector() {
        val identity = GenesisUltraInstanceIdentity(
            schemaVersion = "genesis.instance.identity.v0.1",
            instanceId = "inst_01HNEUTRAL00000000000001",
            seedId = "seed_01HNEUTRAL000000000000001",
            seedRootHash = "sha256:0acaca2f62840715cfec8f7149bd5ab5fffd039fc8f0105bf5752ad25f385568",
            companionName = "Genesis Test 01",
            guardianId = "guardian_01HNEUTRAL000000000001",
            bornAt = "2026-07-12T00:00:00Z",
            identityDigest = "sha256:c165d16d96946d183a51edfda182ef3dcdaa82961a0dcbe715002d4d491ddebf"
        )

        assertEquals(identity.identityDigest, GenesisUltraHashProfile.instanceIdentityDigest(identity))
    }

    @Test
    fun bodyRegistryMatchesContinuityVector() {
        val registry = GenesisUltraBodyRegistry(
            schemaVersion = "genesis.body.registry.v0.1",
            instanceId = "inst_01HNEUTRAL00000000000001",
            registryEpoch = 1,
            bodies = listOf(
                GenesisUltraRegisteredBody(
                    bodyId = "body_01HNEUTRAL00000000000001",
                    status = "active_writer",
                    platformProfile = "android-app",
                    publicKeyFingerprint = "pkfp:aaaaaaaaaaaaaaaa",
                    createdAt = "2026-07-12T00:00:00Z",
                    lastSeenAt = "2026-07-12T00:05:00Z",
                    revocationRef = null
                ),
                GenesisUltraRegisteredBody(
                    bodyId = "body_01HNEUTRAL00000000000002",
                    status = "read_only",
                    platformProfile = "windows-app",
                    publicKeyFingerprint = "pkfp:bbbbbbbbbbbbbbbb",
                    createdAt = "2026-07-12T00:01:00Z",
                    lastSeenAt = "2026-07-12T00:04:00Z",
                    revocationRef = null
                )
            ),
            updatedAt = "2026-07-12T00:05:00Z",
            registryDigest = "sha256:8dffd4f6802167db9ea2eaf95d39b00fa8f518667c7df01963f50cf1ee1baa73"
        )

        assertEquals(registry.registryDigest, GenesisUltraHashProfile.bodyRegistryDigest(registry))
    }

    @Test
    fun keyEpochMatchesCryptoVector() {
        val epoch = GenesisUltraKeyEpoch(
            schemaVersion = "genesis.key.epoch.v0.1",
            keyEpochId = "epoch_01HNEUTRAL0000000000001",
            instanceId = "inst_01HNEUTRAL00000000000001",
            bodyId = "body_01HNEUTRAL00000000000001",
            epochNumber = 1,
            publicKeyFingerprint = "sha256:" + "b".repeat(64),
            createdAt = "2026-07-12T00:00:00Z",
            status = "active",
            previousEpochId = null,
            rotationAuthorizationRef = null,
            epochDigest = "sha256:87e9286776d2ed339bdea91c54f0078a57192d8f0d9c5bd85663b24625ebc7df",
            signature = null
        )

        assertEquals(epoch.epochDigest, GenesisUltraHashProfile.keyEpochDigest(epoch))
    }

    @Test
    fun ed25519VerifierAcceptsOfficialCryptoVectorAndRejectsMutation() {
        val publicKey = GenesisUltraHashProfile.decodeLowerHex(
            "d04ab232742bb4ab3a1368bd4615e4e6d0224ab71a016baf8520a332c9778737"
        )
        val envelope = GenesisUltraSignatureEnvelope(
            schemaVersion = "genesis.signature.envelope.v0.1",
            signatureProfile = "genesis.signature.ed25519.v0.1",
            signerType = "body",
            signerId = "body_01HCRYPTO000000000000001",
            keyEpochId = "epoch_01HCRYPTO00000000000001",
            signedDomain = "genesis.crypto.vector.signature.v0.1",
            signedDigest = "sha256:" + "a".repeat(64),
            signatureValue = "389deebe9b4ff2142a6fbb2cc28c4766b85db9eb58c2e1ba5673b3feb50e79d432317e68aca86648ff1ec011ff9c36552c590f67e9ef38911721d4f25edde500",
            createdAt = "2026-07-15T02:30:00Z",
            publicKeyRef = "sha256:10ba682c8ad13513971e8b56881aab8bd702bb807796eca81932c735a94d6e6d"
        )
        val verifier = GenesisUltraEd25519SignatureVerifier(mapOf(envelope.publicKeyRef to publicKey))

        assertTrue(verifier.verify(envelope, GenesisUltraHashProfile.signatureEnvelopePreimage(envelope)))

        val mutated = envelope.copy(createdAt = "2026-07-15T02:30:01Z")
        assertFalse(verifier.verify(mutated, GenesisUltraHashProfile.signatureEnvelopePreimage(mutated)))
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }
}
