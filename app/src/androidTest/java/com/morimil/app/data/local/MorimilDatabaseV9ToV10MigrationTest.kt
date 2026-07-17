package com.morimil.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MorimilDatabaseV9ToV10MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MorimilDatabase::class.java
    )

    @Test
    fun migrate9To10CreatesImmutableAtomicBirthBoundary() {
        helper.createDatabase(TEST_DATABASE, 9).close()

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            10,
            true,
            MorimilDatabase.MIGRATION_9_10
        ).use { database ->
            assertEquals(10, database.query("PRAGMA user_version").use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getInt(0)
            })
            assertTrue(
                database.tableNames().containsAll(
                    setOf(
                        "genesis_ultra_birth_commit",
                        "genesis_ultra_birth_artifacts",
                        "genesis_ultra_birth_journal"
                    )
                )
            )
            assertTrue(
                database.indexNames("genesis_ultra_birth_commit").containsAll(
                    setOf(
                        "index_genesis_ultra_birth_commit_birthId",
                        "index_genesis_ultra_birth_commit_instanceId",
                        "index_genesis_ultra_birth_commit_journalId"
                    )
                )
            )
            assertTrue(
                database.indexNames("genesis_ultra_birth_journal").containsAll(
                    setOf(
                        "index_genesis_ultra_birth_journal_journalId_sequence",
                        "index_genesis_ultra_birth_journal_journalDigest"
                    )
                )
            )
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.tableNames(): Set<String> {
        return query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.indexNames(tableName: String): Set<String> {
        return query("PRAGMA index_list(`$tableName`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
        }
    }

    private companion object {
        const val TEST_DATABASE = "morimil-v9-v10-migration-test"
    }
}
