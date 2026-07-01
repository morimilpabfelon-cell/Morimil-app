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

class SecretVault(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun hasGitHubToken(): Boolean {
        return preferences.contains(GITHUB_TOKEN_CIPHERTEXT) &&
            preferences.contains(GITHUB_TOKEN_IV)
    }

    fun saveGitHubToken(token: String): Result<Unit> = runCatching {
        val cleanToken = token.trim()
        require(cleanToken.isNotEmpty()) { "Token must not be empty." }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())

        val encrypted = cipher.doFinal(cleanToken.toByteArray(Charsets.UTF_8))

        preferences.edit()
            .putString(GITHUB_TOKEN_CIPHERTEXT, encrypted.toBase64())
            .putString(GITHUB_TOKEN_IV, cipher.iv.toBase64())
            .apply()
    }

    fun clearGitHubToken() {
        preferences.edit()
            .remove(GITHUB_TOKEN_CIPHERTEXT)
            .remove(GITHUB_TOKEN_IV)
            .apply()
    }

    fun readGitHubTokenForApprovedSync(): Result<String?> = runCatching {
        val encrypted = preferences.getString(GITHUB_TOKEN_CIPHERTEXT, null)?.fromBase64()
            ?: return@runCatching null
        val iv = preferences.getString(GITHUB_TOKEN_IV, null)?.fromBase64()
            ?: return@runCatching null

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

        String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

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
        private const val KEY_ALIAS = "morimil_github_sync_gate_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val PREFERENCES_NAME = "morimil_secret_vault"
        private const val GITHUB_TOKEN_CIPHERTEXT = "github_token_ciphertext"
        private const val GITHUB_TOKEN_IV = "github_token_iv"
    }
}
