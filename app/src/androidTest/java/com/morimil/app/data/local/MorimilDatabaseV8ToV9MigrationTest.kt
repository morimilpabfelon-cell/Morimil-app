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
class MorimilDatabaseV8ToV9MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MorimilDatabase::class.java
    )

    @Test
    fun migrate8To9_createsImprovementDecisionHistory() {
        helper.createDatabase(TEST_DATABASE, 8).close()

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            9,
            true,
            MorimilDatabase.MIGRATION_8_9
        ).use { database ->
            assertEquals(9, database.query("PRAGMA user_version").use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getInt(0)
            })
            database.query(
                "SELECT name FROM sqlite_master " +
                    "WHERE type = 'table' AND name = 'improvement_decision_history'"
            ).use { cursor ->
                assertTrue("Migration 8->9 must create improvement_decision_history", cursor.moveToFirst())
            }
        }
    }

    private companion object {
        const val TEST_DATABASE = "morimil-v8-v9-migration-test"
    }
}
