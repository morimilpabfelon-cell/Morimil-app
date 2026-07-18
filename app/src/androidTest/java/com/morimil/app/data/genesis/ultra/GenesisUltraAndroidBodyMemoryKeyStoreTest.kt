package com.morimil.app.data.genesis.ultra

import android.content.Context
import android.util.Base64
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.TinkProtoKeysetFormat
import com.google.crypto.tink.integration.android.AndroidKeystore
import com.google.crypto.tink.subtle.Ed25519Verify
import com.morimil.app.data.local.MorimilDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.charset.StandardCharsets
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class GenesisUltraAndroidBodyMemoryKeyStoreTest {
    private lateinit var database: MorimilDatabase
    private lateinit var preferencesName: String
    private lateinit var masterKeyAlias: String

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val binding = GenesisUltraBodyMemoryKeyBinding(
        instanceId = "inst_01HSECUREBODYKEY00000001",
        bodyId = "body_01HSECUREBODYKEY00000001",
        keyEpochId = "epoch_01HSECUREBODYKEY0000001"
    )

    @Before
    fun setUp() {
        val suffix = UUID.randomUUID().toString()
        preferencesName = "genesis-ultra-key-test-$suffix"
        masterKeyAlias = "com.morimil.app.test.genesis.ultra.$suffix"
        database = Room.inMemoryDatabaseBuilder(context, MorimilDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE).edit().clear().commit()
        runCatching {
            if (AndroidKeystore.hasKey(masterKeyAlias)) {
                AndroidKeystore.deleteKey(masterKeyAlias)
            }
        }
    }

    @Test
    fun provisionSignAndReloadKeepsTheExactRawEd25519Identity() = runBlocking {
        val original = keyStore().provisionBeforeBirth(binding)
        val message = "genesis-secure-restart".toByteArray(StandardCharsets.UTF_8)
        val signature = original.sign(message)

        val restarted = keyStore().loadExisting(binding)

        assertArrayEquals(original.key.copyRawPublicKey(), restarted.key.copyRawPublicKey())
        assertEquals(original.key.publicKeyRef, restarted.key.publicKeyRef)
        assertEquals(64, signature.size)
        Ed25519Verify(restarted.key.copyRawPublicKey()).verify(signature, message)
    }

    @Test
    fun durableRecordContainsOnlyAnEncryptedKeyset() = runBlocking {
        val signer = keyStore().provisionBeforeBirth(binding)
        val encodedRecord = requireNotNull(preferences().getString(RECORD_KEY, null))
        val root = JSONObject(encodedRecord)
        val encrypted = Base64.decode(root.getString("encrypted_keyset"), Base64.NO_WRAP)
        val rawPublicKeyBase64 = Base64.encodeToString(
            signer.key.copyRawPublicKey(),
            Base64.NO_WRAP
        )

        assertFalse(encodedRecord.contains(rawPublicKeyBase64))
        assertTrue(
            runCatching {
                TinkProtoKeysetFormat.parseKeyset(
                    encrypted,
                    InsecureSecretKeyAccess.get(),
                    RegistryConfiguration.get()
                )
            }.isFailure
        )
    }

    @Test
    fun ciphertextTamperFailsClosedWithoutReplacingTheRecord() = runBlocking {
        keyStore().provisionBeforeBirth(binding)
        val root = JSONObject(requireNotNull(preferences().getString(RECORD_KEY, null)))
        val encrypted = Base64.decode(root.getString("encrypted_keyset"), Base64.NO_WRAP)
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 0x01).toByte()
        val tamperedRecord = root
            .put("encrypted_keyset", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .toString()
        assertTrue(preferences().edit().putString(RECORD_KEY, tamperedRecord).commit())

        val loadFailure = runCatching { keyStore().loadExisting(binding) }.exceptionOrNull()
        val reprovisionFailure = runCatching {
            keyStore().provisionBeforeBirth(binding)
        }.exceptionOrNull()

        assertNotNull(loadFailure)
        assertNotNull(reprovisionFailure)
        assertEquals(tamperedRecord, preferences().getString(RECORD_KEY, null))
    }

    @Test
    fun missingRecordNeverCreatesAReplacementDuringLoad() = runBlocking {
        val failure = runCatching { keyStore().loadExisting(binding) }.exceptionOrNull()

        assertNotNull(failure)
        assertFalse(preferences().contains(RECORD_KEY))
        assertFalse(AndroidKeystore.hasKey(masterKeyAlias))
    }

    @Test
    fun lostAndroidKeystoreKeyDoesNotRegenerateTheEd25519Identity() = runBlocking {
        val original = keyStore().provisionBeforeBirth(binding)
        val record = preferences().getString(RECORD_KEY, null)
        AndroidKeystore.deleteKey(masterKeyAlias)

        val loadFailure = runCatching { keyStore().loadExisting(binding) }.exceptionOrNull()
        val reprovisionFailure = runCatching {
            keyStore().provisionBeforeBirth(binding)
        }.exceptionOrNull()

        assertNotNull(original.key.publicKeyRef)
        assertNotNull(loadFailure)
        assertNotNull(reprovisionFailure)
        assertEquals(record, preferences().getString(RECORD_KEY, null))
        assertFalse(AndroidKeystore.hasKey(masterKeyAlias))
    }

    private fun keyStore(): GenesisUltraAndroidBodyMemoryKeyStore {
        return GenesisUltraAndroidBodyMemoryKeyStore(
            context = context,
            database = database,
            preferencesName = preferencesName,
            masterKeyAlias = masterKeyAlias
        )
    }

    private fun preferences() = context.getSharedPreferences(
        preferencesName,
        Context.MODE_PRIVATE
    )

    private companion object {
        const val RECORD_KEY = GenesisUltraAndroidBodyMemoryKeyStore.RECORD_KEY
    }
}
