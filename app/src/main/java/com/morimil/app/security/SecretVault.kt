package com.morimil.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
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

        preferences.edit()
            .putString(ciphertextKey(name), encrypted.toBase64())
            .putString(ivKey(name), cipher.iv.toBase64())
            .apply()
    }

    fun clearSecret(name: String) {
        preferences.edit()
            .remove(ciphertextKey(name))
            .remove(ivKey(name))
            .apply()
    }

    fun readSecret(name: String): Result<String?> = runCatching {
        val encrypted = preferences.getString(ciphertextKey(name), null)?.fromBase64()
            ?: return@runCatching null
        val iv = preferences.getString(ivKey(name), null)?.fromBase64()
            ?: return@runCatching null

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

        String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    fun hasReasoningKey(): Boolean = hasSecret(REASONING_KEY) || hasSecret(ANTHROPIC_KEY)
    fun saveReasoningKey(key: String): Result<Unit> = saveSecret(REASONING_KEY, key)
    fun readReasoningKey(): Result<String?> = runCatching {
        readSecret(REASONING_KEY).getOrThrow() ?: readSecret(ANTHROPIC_KEY).getOrThrow()
    }
    fun clearReasoningKey() {
        clearSecret(REASONING_KEY)
        clearSecret(ANTHROPIC_KEY)
    }

    fun hasAnthropicKey(): Boolean = hasReasoningKey()
    fun saveAnthropicKey(key: String): Result<Unit> = saveReasoningKey(key)
    fun readAnthropicKey(): Result<String?> = readReasoningKey()

    private fun ciphertextKey(name: String) = "${name}_ciphertext"
    private fun ivKey(name: String) = "${name}_iv"

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) return existingKey

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
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
        private const val ANTHROPIC_KEY = "anthropic_api_key"
        private const val REASONING_KEY = "reasoning_runtime_key"
    }
}
