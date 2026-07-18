package com.morimil.app.data.genesis.ultra

import android.content.Context
import android.util.Base64
import com.google.crypto.tink.AccessesPartialKey
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.PublicKeySign
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.TinkProtoKeysetFormat
import com.google.crypto.tink.integration.android.AndroidKeystore
import com.google.crypto.tink.signature.Ed25519Parameters
import com.google.crypto.tink.signature.Ed25519PrivateKey
import com.google.crypto.tink.signature.SignatureConfig
import com.morimil.app.data.local.MorimilDatabase
import com.morimil.app.data.repository.MemoryAppendGate
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/** Stable protocol identifiers bound to the one Body signing key. */
internal data class GenesisUltraBodyMemoryKeyBinding(
    val instanceId: String,
    val bodyId: String,
    val keyEpochId: String
) {
    init {
        listOf(instanceId, bodyId, keyEpochId).forEach { value ->
            GenesisUltraHashProfile.requireNfc(value)
            require(value.length in 16..128) { "body_memory_key_binding_invalid" }
        }
    }
}

/**
 * Stores a RAW Ed25519 Tink keyset encrypted by an AES-GCM key that never
 * leaves Android Keystore. There is intentionally no cleartext or unsigned
 * fallback, and [loadExisting] never creates or replaces key material.
 */
internal class GenesisUltraAndroidBodyMemoryKeyStore(
    context: Context,
    private val database: MorimilDatabase,
    private val preferencesName: String = DEFAULT_PREFERENCES_NAME,
    private val masterKeyAlias: String = DEFAULT_MASTER_KEY_ALIAS
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        preferencesName,
        Context.MODE_PRIVATE
    )

    /**
     * Provisioning is legal only while durable birth state is exactly ABSENT.
     * A key left by a rolled-back attempt is reused instead of duplicated.
     */
    suspend fun provisionBeforeBirth(
        binding: GenesisUltraBodyMemoryKeyBinding
    ): GenesisUltraBodyMemorySigner {
        return MemoryAppendGate.withAppendLock {
            require(
                GenesisUltraAtomicBirthStore(database).readState() ==
                    GenesisUltraPersistedBirthState.ABSENT
            ) { "body_memory_key_provision_requires_absent_birth" }
            synchronized(STORAGE_LOCK) {
                val existing = preferences.getString(RECORD_KEY, null)
                if (existing != null) {
                    loadRecord(binding, existing)
                } else {
                    createRecord(binding)
                }
            }
        }
    }

    /** Loads the exact existing key and fails closed if it is absent, changed or unavailable. */
    suspend fun loadExisting(
        binding: GenesisUltraBodyMemoryKeyBinding
    ): GenesisUltraBodyMemorySigner {
        return MemoryAppendGate.withAppendLock {
            require(
                GenesisUltraAtomicBirthStore(database).readState() !=
                    GenesisUltraPersistedBirthState.INCONSISTENT
            ) { "body_memory_key_load_denied_for_inconsistent_birth" }
            synchronized(STORAGE_LOCK) {
                val encoded = requireNotNull(preferences.getString(RECORD_KEY, null)) {
                    "body_memory_key_record_missing"
                }
                loadRecord(binding, encoded)
            }
        }
    }

    private fun createRecord(binding: GenesisUltraBodyMemoryKeyBinding): GenesisUltraBodyMemorySigner {
        return try {
            SignatureConfig.register()
            if (!AndroidKeystore.hasKey(masterKeyAlias)) {
                AndroidKeystore.generateNewAes256GcmKey(masterKeyAlias)
            }
            val handle = KeysetHandle.generateNew(Ed25519Parameters.create())
            val signer = signerFromHandle(binding, handle, expectedPublicKeyRef = null)
            val ciphertext = TinkProtoKeysetFormat.serializeEncryptedKeyset(
                handle,
                AndroidKeystore.getAead(masterKeyAlias),
                associatedData(binding),
                RegistryConfiguration.get()
            )
            val record = JSONObject()
                .put("schema_version", RECORD_SCHEMA)
                .put("protection_profile", PROTECTION_PROFILE)
                .put("instance_id", binding.instanceId)
                .put("body_id", binding.bodyId)
                .put("key_epoch_id", binding.keyEpochId)
                .put("public_key_ref", signer.key.publicKeyRef)
                .put("encrypted_keyset", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                .toString()
            check(preferences.edit().putString(RECORD_KEY, record).commit()) {
                "body_memory_key_record_commit_failed"
            }

            // Never return a key that cannot be reloaded from its durable encrypted record.
            loadRecord(binding, requireNotNull(preferences.getString(RECORD_KEY, null)))
        } catch (failure: Exception) {
            throw IllegalStateException("body_memory_key_provision_failed", failure)
        }
    }

    private fun loadRecord(
        binding: GenesisUltraBodyMemoryKeyBinding,
        encodedRecord: String
    ): GenesisUltraBodyMemorySigner {
        return try {
            SignatureConfig.register()
            val root = JSONObject(encodedRecord)
            require(root.keys().asSequence().toSet() == RECORD_FIELDS) {
                "body_memory_key_record_fields_invalid"
            }
            require(
                root.getString("schema_version") == RECORD_SCHEMA &&
                    root.getString("protection_profile") == PROTECTION_PROFILE &&
                    root.getString("instance_id") == binding.instanceId &&
                    root.getString("body_id") == binding.bodyId &&
                    root.getString("key_epoch_id") == binding.keyEpochId
            ) { "body_memory_key_record_binding_mismatch" }

            val publicKeyRef = root.getString("public_key_ref")
            val ciphertext = Base64.decode(root.getString("encrypted_keyset"), Base64.NO_WRAP)
            val handle = TinkProtoKeysetFormat.parseEncryptedKeyset(
                ciphertext,
                AndroidKeystore.getAead(masterKeyAlias),
                associatedData(binding),
                RegistryConfiguration.get()
            )
            signerFromHandle(binding, handle, expectedPublicKeyRef = publicKeyRef)
        } catch (failure: Exception) {
            throw IllegalStateException("body_memory_key_load_failed", failure)
        }
    }

    @AccessesPartialKey
    private fun signerFromHandle(
        binding: GenesisUltraBodyMemoryKeyBinding,
        handle: KeysetHandle,
        expectedPublicKeyRef: String?
    ): GenesisUltraBodyMemorySigner {
        require(handle.size() == 1) { "body_memory_keyset_size_invalid" }
        val privateKey = handle.getPrimary().getKey() as? Ed25519PrivateKey
            ?: throw IllegalArgumentException("body_memory_key_type_invalid")
        require(privateKey.parameters == Ed25519Parameters.create()) {
            "body_memory_key_variant_invalid"
        }
        val rawPublicKey = privateKey.publicKey.publicKeyBytes.toByteArray()
        val key = GenesisUltraBodyMemoryKey(
            instanceId = binding.instanceId,
            bodyId = binding.bodyId,
            keyEpochId = binding.keyEpochId,
            publicKeyRef = GenesisUltraHashProfile.sha256(rawPublicKey),
            rawPublicKey = rawPublicKey
        )
        require(expectedPublicKeyRef == null || expectedPublicKeyRef == key.publicKeyRef) {
            "body_memory_public_key_ref_changed"
        }
        val primitive = handle.getPrimitive(
            RegistryConfiguration.get(),
            PublicKeySign::class.java
        )
        return GenesisUltraTinkBodyMemorySigner(key, primitive)
    }

    private fun associatedData(binding: GenesisUltraBodyMemoryKeyBinding): ByteArray {
        return ByteArrayOutputStream().use { output ->
            output.write(GenesisUltraHashProfile.frame(KEYSET_AAD_DOMAIN))
            output.write(GenesisUltraHashProfile.frame(binding.instanceId))
            output.write(GenesisUltraHashProfile.frame(binding.bodyId))
            output.write(GenesisUltraHashProfile.frame(binding.keyEpochId))
            output.toByteArray()
        }
    }

    internal companion object {
        const val DEFAULT_PREFERENCES_NAME = "genesis_ultra_body_memory_key_v1"
        const val DEFAULT_MASTER_KEY_ALIAS = "com.morimil.app.genesis.ultra.body.memory.kek.v1"
        const val RECORD_KEY = "active_body_memory_key"

        private const val RECORD_SCHEMA = "genesis.body.memory.key.record.v0.1"
        private const val PROTECTION_PROFILE = "tink.ed25519.raw+android-keystore.aes256-gcm.v0.1"
        private const val KEYSET_AAD_DOMAIN = "genesis.body.memory.keyset.aad.v0.1"
        private val RECORD_FIELDS = setOf(
            "schema_version",
            "protection_profile",
            "instance_id",
            "body_id",
            "key_epoch_id",
            "public_key_ref",
            "encrypted_keyset"
        )
        private val STORAGE_LOCK = Any()
    }
}
