package com.morimil.app.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase as FrameworkDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import net.zetetic.database.sqlcipher.SQLiteDatabase as CipherDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MorimilDatabaseEncryptionTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val databaseFile: File
        get() = context.getDatabasePath(TEST_DATABASE_NAME)

    @Before
    fun setUp() {
        System.loadLibrary("sqlcipher")
        deleteTestFiles()
    }

    @After
    fun cleanUp() {
        deleteTestFiles()
    }

    @Test
    fun convertsPlaintextDatabaseWithoutLosingRowsOrVersion() {
        FrameworkDatabase.openOrCreateDatabase(databaseFile, null).use { database ->
            database.execSQL(
                "CREATE TABLE continuity (id INTEGER PRIMARY KEY NOT NULL, value TEXT NOT NULL)"
            )
            database.execSQL(
                "INSERT INTO continuity (id, value) VALUES (1, 'memory-survives')"
            )
            database.version = 12
        }
        assertTrue(hasPlaintextHeader(databaseFile))

        val prepared = MorimilDatabaseEncryption.prepareDatabaseFile(
            context = context,
            databaseName = TEST_DATABASE_NAME,
            passphrase = PASSPHRASE.copyOf()
        )

        assertEquals(databaseFile.absolutePath, prepared.absolutePath)
        assertFalse(hasPlaintextHeader(prepared))
        assertFalse(canOpenWithoutPassphrase(prepared))
        assertNoMigrationArtifacts()

        var encrypted: CipherDatabase? = null
        try {
            encrypted = CipherDatabase.openDatabase(
                prepared.absolutePath,
                PASSPHRASE,
                null,
                CipherDatabase.OPEN_READWRITE,
                null
            )
            assertEquals(12, encrypted.version)
            encrypted.rawQuery(
                "SELECT value FROM continuity WHERE id = 1",
                emptyArray<String>()
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("memory-survives", cursor.getString(0))
            }
        } finally {
            encrypted?.close()
        }

        val preparedAgain = MorimilDatabaseEncryption.prepareDatabaseFile(
            context = context,
            databaseName = TEST_DATABASE_NAME,
            passphrase = PASSPHRASE.copyOf()
        )
        assertEquals(prepared.absolutePath, preparedAgain.absolutePath)
        assertFalse(hasPlaintextHeader(preparedAgain))
        assertFalse(canOpenWithoutPassphrase(preparedAgain))
        assertNoMigrationArtifacts()
    }

    private fun assertNoMigrationArtifacts() {
        migrationArtifacts().forEach { artifact ->
            assertFalse("Migration artifact remained: ${artifact.name}", artifact.exists())
        }
    }

    private fun deleteTestFiles() {
        CipherDatabase.deleteDatabase(databaseFile)
        migrationArtifacts().forEach { artifact ->
            CipherDatabase.deleteDatabase(artifact)
        }
    }

    private fun migrationArtifacts(): List<File> {
        val parent = requireNotNull(databaseFile.parentFile)
        return listOf(
            File(parent, "${databaseFile.name}.encrypted.tmp"),
            File(parent, "${databaseFile.name}.plaintext.backup")
        )
    }

    private fun hasPlaintextHeader(file: File): Boolean {
        if (!file.isFile || file.length() < SQLITE_HEADER.size) return false
        val header = ByteArray(SQLITE_HEADER.size)
        file.inputStream().use { input ->
            assertEquals(header.size, input.read(header))
        }
        return header.contentEquals(SQLITE_HEADER)
    }

    private fun canOpenWithoutPassphrase(file: File): Boolean {
        var database: CipherDatabase? = null
        return try {
            database = CipherDatabase.openDatabase(
                file.absolutePath,
                ByteArray(0),
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

    private companion object {
        const val TEST_DATABASE_NAME = "morimil_memory_encryption_test.db"
        val PASSPHRASE = "instrumented-morimil-database-key-v1".toByteArray(Charsets.UTF_8)
        val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    }
}
