package com.morimil.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.morimil.app.ai.ReasoningCredentialScopePolicy
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores named runtime secrets encrypted with an AndroidKeyStore-backed
 * AES-GCM key. The key material never leaves secure hardware; only ciphertext
 * and IV are persisted in app-private preferences.
 */
class SecretVault(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun hasSecret(name: String): Boolean {
        return preferences.contains(ciphertextKey(name)) && preferences.contains(ivKey(name))
    }

    fun saveSecret(name: String, value: String): Result<Unit> = runCatching {
        val cleanValue = value.trim()
        require(cleanValue.isNotEmpty()) { "Secret must not be empty." }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(cleanValue.toByteArray(Charsets.UTF_8))

        val committed = preferences.edit()
            .putString(ciphertextKey(name), encrypted.toBase64())
            .putString(ivKey(name), cipher.iv.toBase64())
            .commit()
        check(committed) { "Could not persist encrypted secret." }
    }

    fun clearSecret(name: String) {
        val committed = preferences.edit()
            .remove(ciphertextKey(name))
            .remove(ivKey(name))
            .commit()
        check(committed) { "Could not clear encrypted secret." }
    }

    fun readSecret(name: String): Result<String?> = runCatching {
        val encrypted = preferences.getString(ciphertextKey(name), null)?.fromBase64()
            ?: return@runCatching null
        val iv = preferences.getString(ivKey(name), null)?.fromBase64()
            ?: return@runCatching null

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateSecretKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        )
        String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    fun hasReasoningKey(slotId: Int = 1, endpoint: String): Boolean {
        val slot = normalizeSlot(slotId)
        val scope = runCatching {
            ReasoningCredentialScopePolicy.fromRemoteEndpoint(endpoint)
        }.getOrNull() ?: return false
        return preferences.getString(reasoningScopeIdKey(slot), null) == scope.storageId &&
            preferences.getString(reasoningScopeOriginKey(slot), null) == scope.canonicalOrigin &&
            hasSecret(scopedReasoningKey(slot, scope.storageId))
    }

    fun saveReasoningKey(
        slotId: Int = 1,
        endpoint: String,
        key: String
    ): Result<Unit> = runCatching {
        val slot = normalizeSlot(slotId)
        val scope = ReasoningCredentialScopePolicy.fromRemoteEndpoint(endpoint)
        val secretName = scopedReasoningKey(slot, scope.storageId)
        val previousStorageId = preferences.getString(reasoningScopeIdKey(slot), null)

        saveSecret(secretName, key).getOrThrow()
        val committed = preferences.edit()
            .putString(reasoningScopeIdKey(slot), scope.storageId)
            .putString(reasoningScopeOriginKey(slot), scope.canonicalOrigin)
            .commit()
        if (!committed) {
            clearSecret(secretName)
            error("Could not bind reasoning credential to its remote origin.")
        }

        if (previousStorageId != null && previousStorageId != scope.storageId) {
            clearSecret(scopedReasoningKey(slot, previousStorageId))
        }
        clearLegacyReasoningKeys(slot)
    }

    fun readReasoningKey(
        slotId: Int = 1,
        endpoint: String
    ): Result<String?> = runCatching {
        val slot = normalizeSlot(slotId)
        val scope = ReasoningCredentialScopePolicy.fromRemoteEndpoint(endpoint)
        if (preferences.getString(reasoningScopeIdKey(slot), null) != scope.storageId) {
            return@runCatching null
        }
        if (preferences.getString(reasoningScopeOriginKey(slot), null) != scope.canonicalOrigin) {
            return@runCatching null
        }
        readSecret(scopedReasoningKey(slot, scope.storageId)).getOrThrow()
    }

    fun clearReasoningKey(slotId: Int = 1, endpoint: String) {
        val slot = normalizeSlot(slotId)
        val scope = runCatching {
            ReasoningCredentialScopePolicy.fromRemoteEndpoint(endpoint)
        }.getOrNull()
        val storedStorageId = preferences.getString(reasoningScopeIdKey(slot), null)
        val storedOrigin = preferences.getString(reasoningScopeOriginKey(slot), null)
        if (
            scope != null &&
            storedStorageId == scope.storageId &&
            storedOrigin == scope.canonicalOrigin
        ) {
            clearSecret(scopedReasoningKey(slot, scope.storageId))
            clearReasoningScopeMetadata(slot)
        }
        clearLegacyReasoningKeys(slot)
    }

    fun clearAllReasoningKeys(slotId: Int = 1) {
        val slot = normalizeSlot(slotId)
        preferences.getString(reasoningScopeIdKey(slot), null)?.let { storageId ->
            clearSecret(scopedReasoningKey(slot, storageId))
        }
        clearReasoningScopeMetadata(slot)
        clearLegacyReasoningKeys(slot)
    }

    fun hasLegacyUnboundReasoningKey(slotId: Int = 1): Boolean {
        val slot = normalizeSlot(slotId)
        return hasSecret(legacyReasoningSlotKey(slot)) ||
            (slot == 1 && (hasSecret(REASONING_KEY) || hasSecret(LEGACY_MESSAGES_KEY)))
    }

    private fun clearReasoningScopeMetadata(slot: Int) {
        val committed = preferences.edit()
            .remove(reasoningScopeIdKey(slot))
            .remove(reasoningScopeOriginKey(slot))
            .commit()
        check(committed) { "Could not clear reasoning credential scope." }
    }

    private fun clearLegacyReasoningKeys(slot: Int) {
        clearSecretIfPresent(legacyReasoningSlotKey(slot))
        if (slot == 1) {
            clearSecretIfPresent(REASONING_KEY)
            clearSecretIfPresent(LEGACY_MESSAGES_KEY)
        }
    }

    private fun clearSecretIfPresent(name: String) {
        if (hasSecret(name)) clearSecret(name)
    }

    private fun normalizeSlot(slotId: Int): Int = slotId.coerceIn(1, 10)

    private fun ciphertextKey(name: String) = "${name}_ciphertext"
    private fun ivKey(name: String) = "${name}_iv"
    private fun legacyReasoningSlotKey(slot: Int) = "reasoning_runtime_key_slot_$slot"
    private fun scopedReasoningKey(slot: Int, storageId: String) =
        "reasoning_runtime_key_slot_${slot}_origin_$storageId"
    private fun reasoningScopeIdKey(slot: Int) = "reasoning_runtime_key_slot_${slot}_scope_id"
    private fun reasoningScopeOriginKey(slot: Int) = "reasoning_runtime_key_slot_${slot}_scope_origin"

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) return existingKey

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val keySpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(keySpec)
        return keyGenerator.generateKey()
    }

    private fun ByteArray.toBase64(): String {
        return Base64.encodeToString(this, Base64.NO_WRAP)
    }

    private fun String.fromBase64(): ByteArray {
        return Base64.decode(this, Base64.NO_WRAP)
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "morimil_secret_vault_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val PREFERENCES_NAME = "morimil_secret_vault"
        private const val LEGACY_MESSAGES_KEY = "anth" + "ropic_api_key"
        private const val REASONING_KEY = "reasoning_runtime_key"
    }
}
