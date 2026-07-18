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
class MorimilDatabaseV10ToV11MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MorimilDatabase::class.java
    )

    @Test
    fun migrate10To11CreatesAppendOnlyCanonicalMemoryBoundary() {
        helper.createDatabase(TEST_DATABASE, 10).close()

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            11,
            true,
            MorimilDatabase.MIGRATION_10_11
        ).use { database ->
            assertEquals(11, database.query("PRAGMA user_version").use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getInt(0)
            })
            assertTrue(database.tableNames().contains("genesis_ultra_memory_events"))
            assertTrue(
                database.indexNames("genesis_ultra_memory_events").containsAll(
                    setOf(
                        "index_genesis_ultra_memory_events_eventId",
                        "index_genesis_ultra_memory_events_eventHash"
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
        const val TEST_DATABASE = "morimil-v10-v11-migration-test"
    }
}
