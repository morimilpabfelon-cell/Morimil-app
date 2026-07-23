package com.morimil.app.data.local

import android.content.Context
import android.database.DatabaseUtils
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.room.Room
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import net.zetetic.database.sqlcipher.SQLiteConnection
import net.zetetic.database.sqlcipher.SQLiteDatabase as CipherDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Opens Morimil's Room database through SQLCipher. Existing plaintext databases
 * are exported into an encrypted replacement before Room is allowed to open.
 * Any unreadable or ambiguous state fails closed; memory is never deleted to
 * make startup succeed.
 */
internal object MorimilDatabaseEncryption {
    const val DATABASE_NAME = "morimil_memory.db"

    fun open(context: Context): MorimilDatabase {
        val appContext = context.applicationContext
        loadSqlCipher()
        val passphrase = MorimilDatabaseKeyStore(appContext).getOrCreatePassphrase()
        return try {
            val databaseFile = prepareDatabaseFile(appContext, DATABASE_NAME, passphrase)
            val factoryPassphrase = passphrase.copyOf()
            Room.databaseBuilder(
                appContext,
                MorimilDatabase::class.java,
                databaseFile.absolutePath
            )
                .openHelperFactory(SupportOpenHelperFactory(factoryPassphrase))
                .addMigrations(*MorimilDatabaseMigrations.ALL)
                .build()
        } finally {
            passphrase.fill(0)
        }
    }

    internal fun prepareDatabaseFile(
        context: Context,
        databaseName: String,
        passphrase: ByteArray
    ): File {
        require(databaseName.isNotBlank()) { "Database name must not be blank." }
        require(passphrase.isNotEmpty()) { "Database passphrase must not be empty." }
        loadSqlCipher()

        val databaseFile = context.getDatabasePath(databaseName)
        databaseFile.parentFile?.mkdirs()
        val encryptedTemp = File(databaseFile.parentFile, "${databaseFile.name}.encrypted.tmp")
        val plaintextBackup = File(databaseFile.parentFile, "${databaseFile.name}.plaintext.backup")

        recoverInterruptedMigration(
            databaseFile = databaseFile,
            encryptedTemp = encryptedTemp,
            plaintextBackup = plaintextBackup,
            passphrase = passphrase
        )

        if (!databaseFile.exists()) return databaseFile
        if (canOpen(databaseFile, passphrase)) return databaseFile
        check(canOpen(databaseFile, EMPTY_PASSPHRASE)) {
            "Morimil memory database is unreadable. Refusing destructive recovery."
        }

        migratePlaintextDatabase(
            databaseFile = databaseFile,
            encryptedTemp = encryptedTemp,
            plaintextBackup = plaintextBackup,
            passphrase = passphrase
        )
        return databaseFile
    }

    private fun migratePlaintextDatabase(
        databaseFile: File,
        encryptedTemp: File,
        plaintextBackup: File,
        passphrase: ByteArray
    ) {
        deleteDatabaseFiles(encryptedTemp)
        deleteDatabaseFiles(plaintextBackup)
        check(databaseFile.isFile && databaseFile.length() > 0L) {
            "Plaintext memory database disappeared before encryption migration."
        }

        val passphraseText = String(passphrase, Charsets.UTF_8)
        var sourceVersion = -1
        var sourceSchemaObjectCount = -1L
        val exportHook = object : SQLiteDatabaseHook {
            override fun preKey(connection: SQLiteConnection) = Unit

            override fun postKey(connection: SQLiteConnection) {
                val checkpointBusy = connection.executeForLong(
                    "PRAGMA wal_checkpoint(TRUNCATE)",
                    null,
                    null
                )
                check(checkpointBusy == 0L) {
                    "Plaintext database WAL is busy; refusing a partial encryption migration."
                }

                sourceVersion = connection.executeForLong(
                    "PRAGMA user_version",
                    null,
                    null
                ).toInt()
                sourceSchemaObjectCount = connection.executeForLong(
                    USER_SCHEMA_OBJECT_COUNT_SQL,
                    null,
                    null
                )
                check(sourceSchemaObjectCount > 0L) {
                    "Plaintext memory database has no application schema; refusing replacement."
                }

                val escapedPath = DatabaseUtils.sqlEscapeString(encryptedTemp.absolutePath)
                val escapedPassphrase = DatabaseUtils.sqlEscapeString(passphraseText)
                var encryptedAttached = false
                var exportVerified = false
                try {
                    connection.execute(
                        "ATTACH DATABASE $escapedPath AS encrypted KEY $escapedPassphrase",
                        null,
                        null
                    )
                    encryptedAttached = true
                    connection.executeForString(
                        "SELECT sqlcipher_export('encrypted')",
                        null,
                        null
                    )
                    connection.execute(
                        "PRAGMA encrypted.user_version = $sourceVersion",
                        null,
                        null
                    )
                    val exportedSchemaObjectCount = connection.executeForLong(
                        ENCRYPTED_SCHEMA_OBJECT_COUNT_SQL,
                        null,
                        null
                    )
                    check(exportedSchemaObjectCount == sourceSchemaObjectCount) {
                        "Encrypted export schema count differs from the plaintext source."
                    }
                    exportVerified = true
                } finally {
                    if (encryptedAttached) {
                        val detachResult = runCatching {
                            connection.execute(
                                "DETACH DATABASE encrypted",
                                null,
                                null
                            )
                        }
                        if (exportVerified) {
                            detachResult.getOrThrow()
                        }
                    }
                }
            }
        }

        var source: CipherDatabase? = null
        try {
            source = CipherDatabase.openDatabase(
                databaseFile.absolutePath,
                EMPTY_PASSPHRASE,
                null,
                CipherDatabase.OPEN_READWRITE or CipherDatabase.CREATE_IF_NECESSARY,
                exportHook
            )
        } finally {
            source?.close()
        }

        check(sourceVersion >= 0 && sourceSchemaObjectCount > 0L) {
            "Plaintext memory export did not complete."
        }
        verifyEncryptedDatabase(encryptedTemp, passphrase, sourceVersion)
        deleteSidecars(databaseFile)

        check(databaseFile.renameTo(plaintextBackup)) {
            "Could not preserve the plaintext database before encrypted replacement."
        }

        try {
            check(encryptedTemp.renameTo(databaseFile)) {
                "Could not install the encrypted database replacement."
            }
            verifyEncryptedDatabase(databaseFile, passphrase, sourceVersion)
            check(deleteDatabaseFiles(plaintextBackup)) {
                "Encrypted database verified, but plaintext backup could not be removed."
            }
        } catch (error: Throwable) {
            deleteDatabaseFiles(databaseFile)
            check(plaintextBackup.renameTo(databaseFile)) {
                "Encrypted migration failed and plaintext rollback could not be restored."
            }
            throw error
        } finally {
            deleteDatabaseFiles(encryptedTemp)
        }
    }

    private fun recoverInterruptedMigration(
        databaseFile: File,
        encryptedTemp: File,
        plaintextBackup: File,
        passphrase: ByteArray
    ) {
        if (plaintextBackup.exists()) {
            when {
                databaseFile.exists() && canOpen(databaseFile, passphrase) -> {
                    check(deleteDatabaseFiles(plaintextBackup)) {
                        "Could not remove plaintext backup from completed migration."
                    }
                }

                !databaseFile.exists() -> {
                    check(plaintextBackup.renameTo(databaseFile)) {
                        "Could not restore plaintext database after interrupted migration."
                    }
                }

                canOpen(plaintextBackup, EMPTY_PASSPHRASE) -> {
                    deleteDatabaseFiles(databaseFile)
                    check(plaintextBackup.renameTo(databaseFile)) {
                        "Could not restore plaintext database after failed encrypted replacement."
                    }
                }

                else -> error("Interrupted database migration is not recoverable automatically.")
            }
        }
        deleteDatabaseFiles(encryptedTemp)
    }

    private fun verifyEncryptedDatabase(
        databaseFile: File,
        passphrase: ByteArray,
        expectedVersion: Int
    ) {
        var database: CipherDatabase? = null
        try {
            database = CipherDatabase.openDatabase(
                databaseFile.absolutePath,
                passphrase,
                null,
                CipherDatabase.OPEN_READWRITE,
                null
            )
            database.rawQuery("PRAGMA integrity_check", emptyArray<String>()).use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)) {
                    "Encrypted database integrity check failed."
                }
            }
            check(database.version == expectedVersion) {
                "Encrypted database version changed during migration."
            }
        } finally {
            database?.close()
        }
        check(!canOpen(databaseFile, EMPTY_PASSPHRASE)) {
            "Database replacement remained readable without its encryption key."
        }
    }

    private fun canOpen(databaseFile: File, passphrase: ByteArray): Boolean {
        if (!databaseFile.isFile) return false
        var database: CipherDatabase? = null
        return try {
            database = CipherDatabase.openDatabase(
                databaseFile.absolutePath,
                passphrase,
                null,
                CipherDatabase.OPEN_READWRITE,
                null
            )
            database.rawQuery(
                "SELECT count(*) FROM sqlite_schema",
                emptyArray<String>()
            ).use { cursor -> cursor.moveToFirst() }
        } catch (_: Throwable) {
            false
        } finally {
            database?.close()
        }
    }

    private fun deleteDatabaseFiles(databaseFile: File): Boolean {
        if (!databaseFile.exists() && !hasSidecars(databaseFile)) return true
        return CipherDatabase.deleteDatabase(databaseFile) &&
            !databaseFile.exists() &&
            !hasSidecars(databaseFile)
    }

    private fun deleteSidecars(databaseFile: File) {
        listOf("-journal", "-shm", "-wal").forEach { suffix ->
            val sidecar = File(databaseFile.path + suffix)
            check(!sidecar.exists() || sidecar.delete()) {
                "Could not remove database sidecar ${sidecar.name}."
            }
        }
    }

    private fun hasSidecars(databaseFile: File): Boolean {
        return listOf("-journal", "-shm", "-wal")
            .any { suffix -> File(databaseFile.path + suffix).exists() }
    }

    private fun loadSqlCipher() {
        System.loadLibrary("sqlcipher")
    }

    private const val USER_SCHEMA_OBJECT_COUNT_SQL =
        "SELECT count(*) FROM main.sqlite_schema " +
            "WHERE type IN ('table','index','trigger','view') AND name NOT LIKE 'sqlite_%'"

    private const val ENCRYPTED_SCHEMA_OBJECT_COUNT_SQL =
        "SELECT count(*) FROM encrypted.sqlite_schema " +
            "WHERE type IN ('table','index','trigger','view') AND name NOT LIKE 'sqlite_%'"

    private val EMPTY_PASSPHRASE = ByteArray(0)
}

/**
 * Persists a random SQLCipher passphrase wrapped by an Android Keystore-backed
 * AES-GCM key. Existing wrapped material is never replaced after a decrypt
 * failure, preventing silent loss of access to Morimil's memory.
 */
private class MorimilDatabaseKeyStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun getOrCreatePassphrase(): ByteArray {
        val encodedCiphertext = preferences.getString(CIPHERTEXT_KEY, null)
        val encodedIv = preferences.getString(IV_KEY, null)
        check((encodedCiphertext == null) == (encodedIv == null)) {
            "Database key metadata is incomplete."
        }

        if (encodedCiphertext != null && encodedIv != null) {
            return decrypt(
                ciphertext = Base64.decode(encodedCiphertext, Base64.NO_WRAP),
                iv = Base64.decode(encodedIv, Base64.NO_WRAP)
            )
        }

        val randomKey = ByteArray(DATABASE_KEY_BYTES).also(SecureRandom()::nextBytes)
        val passphrase = Base64.encode(randomKey, Base64.NO_WRAP)
        randomKey.fill(0)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateWrappingKey(requireExisting = false))
        cipher.updateAAD(AAD)
        val ciphertext = cipher.doFinal(passphrase)
        val committed = preferences.edit()
            .putString(CIPHERTEXT_KEY, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .putString(IV_KEY, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .commit()
        if (!committed) {
            passphrase.fill(0)
            error("Could not persist the encrypted database key.")
        }
        return passphrase
    }

    private fun decrypt(ciphertext: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateWrappingKey(requireExisting = true),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        )
        cipher.updateAAD(AAD)
        return cipher.doFinal(ciphertext)
    }

    private fun getOrCreateWrappingKey(requireExisting: Boolean): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        check(!requireExisting) {
            "Android Keystore database wrapping key is missing. Refusing key regeneration."
        }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "morimil_memory_database_wrap_v1"
        const val PREFERENCES_NAME = "morimil_memory_database_security"
        const val CIPHERTEXT_KEY = "database_key_ciphertext"
        const val IV_KEY = "database_key_iv"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
        const val DATABASE_KEY_BYTES = 32
        val AAD: ByteArray = "morimil.memory.database.key.v1".toByteArray(Charsets.UTF_8)
    }
}
