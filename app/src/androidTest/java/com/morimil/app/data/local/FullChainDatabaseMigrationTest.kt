package com.morimil.app.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FullChainDatabaseMigrationTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun cleanUp() {
        context.deleteDatabase(MORIMIL_DB)
        context.deleteDatabase(MEMORY_ORGAN_DB)
    }

    @Test
    fun morimilDatabaseMigratesFrom1To12ThroughFullChain() {
        createMorimilDatabaseAtVersion1()

        val database = Room.databaseBuilder(context, MorimilDatabase::class.java, MORIMIL_DB)
            .addMigrations(
                MorimilDatabase.MIGRATION_1_2,
                MorimilDatabase.MIGRATION_2_3,
                MorimilDatabase.MIGRATION_3_4,
                MorimilDatabase.MIGRATION_4_5,
                MorimilDatabase.MIGRATION_5_6,
                MorimilDatabase.MIGRATION_6_7,
                MorimilDatabase.MIGRATION_7_8,
                MorimilDatabase.MIGRATION_8_9,
                MorimilDatabase.MIGRATION_9_10,
                MorimilDatabase.MIGRATION_10_11,
                MorimilDatabase.MIGRATION_11_12
            )
            .build()

        val migrated = database.openHelper.writableDatabase
        assertTrue(
            migrated.tableNames().containsAll(
                listOf(
                    "reasoning_turns",
                    "decision_log",
                    "project_state",
                    "user_workspace",
                    "local_instance_identity",
                    "genesis_core",
                    "memory_events",
                    "memory_snapshots",
                    "genesis_ultra_birth_commit",
                    "genesis_ultra_birth_artifacts",
                    "genesis_ultra_birth_journal",
                    "genesis_ultra_memory_events"
                )
            )
        )
        assertEquals(1, migrated.singleInt("SELECT COUNT(*) FROM reasoning_turns"))
        assertEquals(
            "seed message survives migration",
            migrated.singleString("SELECT body FROM reasoning_turns WHERE id = 1")
        )
        assertTrue(!migrated.tableNames().contains("memory_messages"))
        assertEquals(1, migrated.singleInt("SELECT COUNT(*) FROM decision_log"))
        assertEquals(1, migrated.singleInt("SELECT COUNT(*) FROM project_state"))
        assertTrue(
            migrated.columnNames("memory_events").containsAll(
                listOf(
                    "previousEventHash",
                    "genesisCoreHash",
                    "eventHash",
                    "source",
                    "contextTag",
                    "privacyVisibility",
                    "memoryKind",
                    "tagsJson",
                    "evidenceJson",
                    "confidence",
                    "userConfirmed"
                )
            )
        )
        assertTrue(migrated.indexNames("memory_events").containsAll(MORIMIL_MEMORY_EVENT_INDEXES))
        database.close()
    }

    @Test
    fun memoryOrganDatabaseMigratesFrom1To7ThroughFullChain() {
        createMemoryOrganDatabaseAtVersion1()

        val database = Room.databaseBuilder(context, MemoryOrganDatabase::class.java, MEMORY_ORGAN_DB)
            .addMigrations(
                MemoryOrganDatabase.MIGRATION_1_2,
                MemoryOrganDatabase.MIGRATION_2_3,
                MemoryOrganDatabase.MIGRATION_3_4,
                MemoryOrganDatabase.MIGRATION_4_5,
                MemoryOrganDatabase.MIGRATION_5_6,
                MemoryOrganDatabase.MIGRATION_6_7
            )
            .build()

        val migrated = database.openHelper.writableDatabase
        assertTrue(
            migrated.tableNames().containsAll(
                listOf(
                    "autobiographical_snapshots",
                    "knowledge_capsules",
                    "recall_schedules",
                    "memory_links",
                    "migration_records"
                )
            )
        )
        assertEquals(1, migrated.singleInt("SELECT COUNT(*) FROM knowledge_capsules"))
        migrated.query(
            """
            SELECT capsuleType, source, privacyVisibility, claimsJson, evidenceJson,
                   capsuleVersion, capsuleCategory, status
            FROM knowledge_capsules
            WHERE capsuleId = 'capsule-1'
            """.trimIndent()
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("knowledge_capsule", cursor.getString(0))
            assertEquals("user_approved_notes", cursor.getString(1))
            assertEquals("private_local", cursor.getString(2))
            assertEquals("[]", cursor.getString(3))
            assertEquals("{}", cursor.getString(4))
            assertEquals(1, cursor.getInt(5))
            assertEquals("general_knowledge", cursor.getString(6))
            assertEquals("active", cursor.getString(7))
        }
        assertTrue(migrated.indexNames("knowledge_capsules").containsAll(KNOWLEDGE_CAPSULE_INDEXES))
        assertTrue(migrated.indexNames("recall_schedules").containsAll(RECALL_SCHEDULE_INDEXES))
        assertTrue(migrated.indexNames("memory_links").containsAll(MEMORY_LINK_INDEXES))
        assertTrue(migrated.indexNames("migration_records").containsAll(MIGRATION_RECORD_INDEXES))
        database.close()
    }

    private fun createMorimilDatabaseAtVersion1() {
        context.deleteDatabase(MORIMIL_DB)
        val db = openWritableDatabase(MORIMIL_DB)
        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS memory_messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    author TEXT NOT NULL,
                    body TEXT NOT NULL,
                    createdAtMillis INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO memory_messages (id, author, body, createdAtMillis)
                VALUES (1, 'user', 'seed message survives migration', 1000)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS decision_log (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    status TEXT NOT NULL,
                    createdAtMillis INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO decision_log (id, title, status, createdAtMillis)
                VALUES (1, 'Keep local memory', 'accepted', 1000)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS project_state (
                    projectId TEXT NOT NULL PRIMARY KEY,
                    title TEXT NOT NULL,
                    status TEXT NOT NULL,
                    updatedAtMillis INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO project_state (projectId, title, status, updatedAtMillis)
                VALUES ('morimil-app', 'Morimil App', 'active', 1000)
                """.trimIndent()
            )
            db.setVersion(1)
        } finally {
            db.close()
        }
    }

    private fun createMemoryOrganDatabaseAtVersion1() {
        context.deleteDatabase(MEMORY_ORGAN_DB)
        val db = openWritableDatabase(MEMORY_ORGAN_DB)
        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS autobiographical_snapshots (
                    snapshotId TEXT NOT NULL PRIMARY KEY,
                    genesisCoreId TEXT NOT NULL,
                    alias TEXT NOT NULL,
                    selfSummary TEXT NOT NULL,
                    stableTraits TEXT NOT NULL,
                    activeGoals TEXT NOT NULL,
                    importantConstraints TEXT NOT NULL,
                    sourceEventHash TEXT,
                    updatedAtMillis INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS knowledge_capsules (
                    capsuleId TEXT NOT NULL PRIMARY KEY,
                    genesisCoreId TEXT NOT NULL,
                    title TEXT NOT NULL,
                    summary TEXT NOT NULL,
                    tags TEXT NOT NULL,
                    confidence INTEGER NOT NULL,
                    sourceEventHash TEXT,
                    createdAtMillis INTEGER NOT NULL,
                    updatedAtMillis INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO knowledge_capsules (
                    capsuleId,
                    genesisCoreId,
                    title,
                    summary,
                    tags,
                    confidence,
                    sourceEventHash,
                    createdAtMillis,
                    updatedAtMillis
                ) VALUES (
                    'capsule-1',
                    'primary_genesis',
                    'Seed capsule',
                    'A capsule that must survive migration.',
                    '[]',
                    88,
                    NULL,
                    1000,
                    1000
                )
                """.trimIndent()
            )
            db.setVersion(1)
        } finally {
            db.close()
        }
    }

    private fun openWritableDatabase(name: String): SQLiteDatabase {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        return SQLiteDatabase.openOrCreateDatabase(file, null)
    }

    private fun SupportSQLiteDatabase.tableNames(): Set<String> {
        return query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
    }

    private fun SupportSQLiteDatabase.columnNames(tableName: String): Set<String> {
        return query("PRAGMA table_info($tableName)").use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
        }
    }

    private fun SupportSQLiteDatabase.indexNames(tableName: String): Set<String> {
        return query("PRAGMA index_list($tableName)").use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
        }
    }

    private fun SupportSQLiteDatabase.singleInt(sql: String): Int {
        return query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }
    }

    private fun SupportSQLiteDatabase.singleString(sql: String): String {
        return query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getString(0)
        }
    }

    companion object {
        private const val MORIMIL_DB = "full-chain-migration-test-morimil.db"
        private const val MEMORY_ORGAN_DB = "full-chain-migration-test-memory-organ.db"

        private val MORIMIL_MEMORY_EVENT_INDEXES = setOf(
            "index_memory_events_eventHash",
            "index_memory_events_createdAtMillis",
            "index_memory_events_memoryKind",
            "index_memory_events_importance"
        )
        private val KNOWLEDGE_CAPSULE_INDEXES = setOf(
            "index_knowledge_capsules_capsuleHash",
            "index_knowledge_capsules_capsuleType",
            "index_knowledge_capsules_updatedAtMillis",
            "index_knowledge_capsules_status",
            "index_knowledge_capsules_category",
            "index_knowledge_capsules_title"
        )
        private val RECALL_SCHEDULE_INDEXES = setOf(
            "index_recall_schedules_targetEventHash",
            "index_recall_schedules_status",
            "index_recall_schedules_dueAtMillis"
        )
        private val MEMORY_LINK_INDEXES = setOf(
            "index_memory_links_sourceId_sourceType",
            "index_memory_links_targetId_targetType",
            "index_memory_links_relation",
            "index_memory_links_verificationState",
            "index_memory_links_sourceId_sourceType_targetId_targetType_relation"
        )
        private val MIGRATION_RECORD_INDEXES = setOf(
            "index_migration_records_migrationType",
            "index_migration_records_status",
            "index_migration_records_approvedByUser",
            "index_migration_records_createdAtMillis"
        )
    }
}
