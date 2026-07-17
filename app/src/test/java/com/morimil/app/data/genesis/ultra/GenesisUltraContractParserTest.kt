package com.morimil.app.data.genesis.ultra

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GenesisUltraContractParserTest {
    @Test
    fun parsesInstanceIdentityGoldenVectorAndRecomputesDigest() {
        val json = JSONObject()
            .put("schema_version", "genesis.instance.identity.v0.1")
            .put("instance_id", "inst_01HNEUTRAL00000000000001")
            .put("seed_id", "seed_01HNEUTRAL000000000000001")
            .put("seed_root_hash", "sha256:0acaca2f62840715cfec8f7149bd5ab5fffd039fc8f0105bf5752ad25f385568")
            .put("companion_name", "Genesis Test 01")
            .put("guardian_id", "guardian_01HNEUTRAL000000000001")
            .put("born_at", "2026-07-12T00:00:00Z")
            .put("identity_digest", "sha256:c165d16d96946d183a51edfda182ef3dcdaa82961a0dcbe715002d4d491ddebf")

        val identity = GenesisUltraContractParser.parseInstanceIdentity(json.toString())

        assertEquals("Genesis Test 01", identity.companionName)
    }

    @Test
    fun parsesBodyRegistryContinuityVectorAndRecomputesDigest() {
        val json = bodyRegistryJson(
            firstStatus = "active_writer",
            secondStatus = "read_only",
            digest = "sha256:8dffd4f6802167db9ea2eaf95d39b00fa8f518667c7df01963f50cf1ee1baa73"
        )

        val registry = GenesisUltraContractParser.parseBodyRegistry(json.toString())

        assertEquals(1L, registry.registryEpoch)
        assertEquals(1, registry.bodies.count { body -> body.status == "active_writer" })
    }

    @Test
    fun parsesKeyEpochCryptoVectorAndRecomputesDigest() {
        val json = JSONObject()
            .put("schema_version", "genesis.key.epoch.v0.1")
            .put("key_epoch_id", "epoch_01HNEUTRAL0000000000001")
            .put("instance_id", "inst_01HNEUTRAL00000000000001")
            .put("body_id", "body_01HNEUTRAL00000000000001")
            .put("epoch_number", 1)
            .put("public_key_fingerprint", "sha256:" + "b".repeat(64))
            .put("created_at", "2026-07-12T00:00:00Z")
            .put("status", "active")
            .put("previous_epoch_id", JSONObject.NULL)
            .put("rotation_authorization_ref", JSONObject.NULL)
            .put("epoch_digest", "sha256:d8bb77c80131b4faebec7a385b00e29a3ce2d70c0e414bff340e21c325155053")
            .put("signature", JSONObject.NULL)

        val epoch = GenesisUltraContractParser.parseKeyEpoch(json.toString())

        assertEquals("active", epoch.status)
        assertEquals(1L, epoch.epochNumber)
    }

    @Test
    fun parsesRotatedKeyEpochAndBindsRotationFields() {
        val json = JSONObject()
            .put("schema_version", "genesis.key.epoch.v0.1")
            .put("key_epoch_id", "epoch_01HNEUTRAL0000000000002")
            .put("instance_id", "inst_01HNEUTRAL00000000000001")
            .put("body_id", "body_01HNEUTRAL00000000000001")
            .put("epoch_number", 2)
            .put("public_key_fingerprint", "sha256:" + "c".repeat(64))
            .put("created_at", "2026-07-13T00:00:00Z")
            .put("status", "active")
            .put("previous_epoch_id", "epoch_01HNEUTRAL0000000000001")
            .put("rotation_authorization_ref", "grant_01HNEUTRAL_ROTATE0000001")
            .put("epoch_digest", "sha256:c004cc8c49659b0f7ae913a2bdf644bf611bad0211bb5bc22ac6f07d2ae755a2")
            .put("signature", JSONObject.NULL)

        val epoch = GenesisUltraContractParser.parseKeyEpoch(json.toString())

        assertEquals("epoch_01HNEUTRAL0000000000001", epoch.previousEpochId)
        assertEquals("grant_01HNEUTRAL_ROTATE0000001", epoch.rotationAuthorizationRef)
    }

    @Test
    fun rejectsNonNfcCanonicalNameEvenWhenOtherFieldsAreValid() {
        val json = JSONObject()
            .put("schema_version", "genesis.instance.identity.v0.1")
            .put("instance_id", "inst_01HNEUTRAL00000000000001")
            .put("seed_id", "seed_01HNEUTRAL000000000000001")
            .put("seed_root_hash", "sha256:0acaca2f62840715cfec8f7149bd5ab5fffd039fc8f0105bf5752ad25f385568")
            .put("companion_name", "Ge\u0301nesis Test 01")
            .put("guardian_id", "guardian_01HNEUTRAL000000000001")
            .put("born_at", "2026-07-12T00:00:00Z")
            .put("identity_digest", "sha256:c165d16d96946d183a51edfda182ef3dcdaa82961a0dcbe715002d4d491ddebf")

        val error = assertThrows(IllegalArgumentException::class.java) {
            GenesisUltraContractParser.parseInstanceIdentity(json.toString())
        }

        assertEquals("text_not_nfc", error.message)
    }

    @Test
    fun rejectsMultipleActiveWritersBeforeDigestAcceptance() {
        val json = bodyRegistryJson(
            firstStatus = "active_writer",
            secondStatus = "active_writer",
            digest = "sha256:" + "0".repeat(64)
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            GenesisUltraContractParser.parseBodyRegistry(json.toString())
        }

        assertEquals("multiple_active_writers", error.message)
    }

    @Test
    fun rejectsSeedPathThatEscapesBundle() {
        val identityDigest = "sha256:" + "a".repeat(64)
        val doctrineDigest = "sha256:" + "b".repeat(64)
        val files = JSONArray()
            .put(
                JSONObject()
                    .put("path", "../identity.json")
                    .put("kind", "identity")
                    .put("required", true)
                    .put("digest", identityDigest)
            )
            .put(
                JSONObject()
                    .put("path", "doctrine/doctrine.md")
                    .put("kind", "doctrine")
                    .put("required", true)
                    .put("digest", doctrineDigest)
            )
        val json = JSONObject()
            .put("schema_version", "genesis.seed.manifest.v0.1")
            .put("protocol_version", "genesis.protocol.v0.1")
            .put("hash_profile", "genesis.hash.fields.v0.1")
            .put("seed_id", "seed_01HNEUTRAL000000000000001")
            .put("identity_digest", identityDigest)
            .put("doctrine_digest", doctrineDigest)
            .put("files", files)
            .put("root_hash", "sha256:" + "c".repeat(64))

        val error = assertThrows(IllegalArgumentException::class.java) {
            GenesisUltraContractParser.parseSeedManifest(json.toString())
        }

        assertEquals("invalid_relative_path", error.message)
    }

    private fun bodyRegistryJson(firstStatus: String, secondStatus: String, digest: String): JSONObject {
        return JSONObject()
            .put("schema_version", "genesis.body.registry.v0.1")
            .put("instance_id", "inst_01HNEUTRAL00000000000001")
            .put("registry_epoch", 1)
            .put(
                "bodies",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("body_id", "body_01HNEUTRAL00000000000001")
                            .put("status", firstStatus)
                            .put("platform_profile", "android-app")
                            .put("public_key_fingerprint", "pkfp:aaaaaaaaaaaaaaaa")
                            .put("created_at", "2026-07-12T00:00:00Z")
                            .put("last_seen_at", "2026-07-12T00:05:00Z")
                            .put("revocation_ref", JSONObject.NULL)
                    )
                    .put(
                        JSONObject()
                            .put("body_id", "body_01HNEUTRAL00000000000002")
                            .put("status", secondStatus)
                            .put("platform_profile", "windows-app")
                            .put("public_key_fingerprint", "pkfp:bbbbbbbbbbbbbbbb")
                            .put("created_at", "2026-07-12T00:01:00Z")
                            .put("last_seen_at", "2026-07-12T00:04:00Z")
                            .put("revocation_ref", JSONObject.NULL)
                    )
            )
            .put("updated_at", "2026-07-12T00:05:00Z")
            .put("registry_digest", digest)
    }
}
