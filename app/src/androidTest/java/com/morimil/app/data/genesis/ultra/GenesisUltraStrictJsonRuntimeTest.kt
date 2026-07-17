package com.morimil.app.data.genesis.ultra

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GenesisUltraStrictJsonRuntimeTest {
    @Test
    fun duplicateKeysAreRejectedOnAndroid() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            GenesisUltraStrictJson.parseObject("{\"value\":1,\"value\":2}")
        }

        assertEquals("invalid_strict_json", error.message)
    }

    @Test
    fun stringCannotMasqueradeAsBooleanOnAndroid() {
        val json = """
            {
              "schema_version": "genesis.seed.manifest.v0.1",
              "protocol_version": "genesis.protocol.v0.1",
              "hash_profile": "genesis.hash.fields.v0.1",
              "seed_id": "seed_01HANDROID000000000000001",
              "identity_digest": "sha256:${"a".repeat(64)}",
              "doctrine_digest": "sha256:${"b".repeat(64)}",
              "files": [
                {
                  "path": "identity/identity.json",
                  "kind": "identity",
                  "required": "true",
                  "digest": "sha256:${"a".repeat(64)}"
                },
                {
                  "path": "doctrine/doctrine.md",
                  "kind": "doctrine",
                  "required": true,
                  "digest": "sha256:${"b".repeat(64)}"
                }
              ],
              "root_hash": "sha256:${"c".repeat(64)}"
            }
        """.trimIndent()

        val error = assertThrows(IllegalArgumentException::class.java) {
            GenesisUltraContractParser.parseSeedManifest(json)
        }

        assertEquals("invalid_required", error.message)
    }

    @Test
    fun stringCannotMasqueradeAsIntegerOnAndroid() {
        val json = """
            {
              "schema_version": "genesis.body.registry.v0.1",
              "instance_id": "inst_01HANDROID000000000000001",
              "registry_epoch": "1",
              "bodies": [
                {
                  "body_id": "body_01HANDROID000000000000001",
                  "status": "active_writer",
                  "platform_profile": "android-kotlin",
                  "public_key_fingerprint": "sha256:${"d".repeat(64)}",
                  "created_at": "2026-07-17T00:00:00Z"
                }
              ],
              "updated_at": "2026-07-17T00:00:00Z",
              "registry_digest": "sha256:${"e".repeat(64)}"
            }
        """.trimIndent()

        val error = assertThrows(IllegalArgumentException::class.java) {
            GenesisUltraContractParser.parseBodyRegistry(json)
        }

        assertEquals("invalid_registry_epoch", error.message)
    }
}
