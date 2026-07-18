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
class MorimilDatabaseMigrationTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun cleanUp() {
        context.deleteDatabase(MORIMIL_DB)
    }

    @Test
    fun migratesLegacyV1ToCurrentV11WithoutDroppingCoreMemory() {
        createVersion1Database()

        val database = openMigratedDatabase()
        val migrated = database.openHelper.writableDatabase

        assertEquals(11, migrated.userVersion())
        assertTrue(
            migrated.tableNames().containsAll(
                listOf(
                    "memory_messages",
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
        assertEquals("seed message survives migration", migrated.singleString("SELECT body FROM memory_messages WHERE id = 1"))
        assertEquals("Keep local memory", migrated.singleString("SELECT title FROM decision_log WHERE id = 1"))
        assertEquals("Morimil App", migrated.singleString("SELECT title FROM project_state WHERE projectId = 'morimil-app'"))
        assertTrue(migrated.columnNames("memory_events").containsAll(MEMORY_EVENT_COLUMNS))
        assertTrue(migrated.indexNames("memory_events").containsAll(MEMORY_EVENT_INDEXES))
        database.close()
    }

    @Test
    fun migratesV7ToV11WithMemoryEventDefaultsAndIndexes() {
        createVersion7DatabaseWithMemoryEvent()

        val database = Room.databaseBuilder(context, MorimilDatabase::class.java, MORIMIL_DB)
            .addMigrations(
                MorimilDatabase.MIGRATION_7_8,
                MorimilDatabase.MIGRATION_8_9,
                MorimilDatabase.MIGRATION_9_10,
                MorimilDatabase.MIGRATION_10_11
            )
            .build()
        val migrated = database.openHelper.writableDatabase

        assertEquals(11, migrated.userVersion())
        assertTrue(migrated.columnNames("memory_events").containsAll(MEMORY_EVENT_V8_COLUMNS))
        migrated.query(
            "SELECT memoryKind, tagsJson, evidenceJson, confidence, userConfirmed FROM memory_events WHERE id = 1"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("observation", cursor.getString(0))
            assertEquals("[]", cursor.getString(1))
            assertEquals("{}", cursor.getString(2))
            assertEquals(70, cursor.getInt(3))
            assertEquals(0, cursor.getInt(4))
        }
        assertTrue(migrated.indexNames("memory_events").containsAll(MEMORY_EVENT_INDEXES))
        database.close()
    }

    @Test
    fun migratesIdentityForkFieldsFromV4ToV5() {
        createVersion4DatabaseWithLegacyIdentity()

        val database = Room.databaseBuilder(context, MorimilDatabase::class.java, MORIMIL_DB)
            .addMigrations(
                MorimilDatabase.MIGRATION_4_5,
                MorimilDatabase.MIGRATION_5_6,
                MorimilDatabase.MIGRATION_6_7,
                MorimilDatabase.MIGRATION_7_8,
                MorimilDatabase.MIGRATION_8_9,
                MorimilDatabase.MIGRATION_9_10,
                MorimilDatabase.MIGRATION_10_11
            )
            .build()
        val migrated = database.openHelper.writableDatabase

        assertEquals(11, migrated.userVersion())
        assertTrue(
            migrated.columnNames("local_instance_identity").containsAll(
                listOf("localMemoryOwner", "localMemoryName", "localMemoryUri")
            )
        )
        migrated.query(
            """
            SELECT localMemoryOwner, localMemoryName, localMemoryUri
            FROM local_instance_identity
            WHERE instanceId = 'instance-1'
            """.trimIndent()
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("morimilpabfelon-cell", cursor.getString(0))
            assertEquals("Morimil-app", cursor.getString(1))
            assertEquals("https://github.com/morimilpabfelon-cell/Morimil-app", cursor.getString(2))
        }
        database.close()
    }

    private fun openMigratedDatabase(): MorimilDatabase {
        return Room.databaseBuilder(context, MorimilDatabase::class.java, MORIMIL_DB)
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
                MorimilDatabase.MIGRATION_10_11
            )
            .build()
    }

    private fun createVersion1Database() {
        context.deleteDatabase(MORIMIL_DB)
        val db = openWritableDatabase()
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

    private fun createVersion4DatabaseWithLegacyIdentity() {
        context.deleteDatabase(MORIMIL_DB)
        val db = openWritableDatabase()
        try {
            createBaseTablesThroughVersion4(db)
            db.execSQL(
                """
                INSERT INTO local_instance_identity (
                    instanceId,
                    alias,
                    bornAtMillis,
                    genesisAgentId,
                    genesisRole,
                    genesisRiskTier,
                    genesisSchemaVersion,
                    forkOwner,
                    forkRepo,
                    forkHtmlUrl
                ) VALUES (
                    'instance-1',
                    'Morimil',
                    1000,
                    'morimil',
                    'companion',
                    'local_only',
                    '1',
                    'morimilpabfelon-cell',
                    'Morimil-app',
                    'https://github.com/morimilpabfelon-cell/Morimil-app'
                )
                """.trimIndent()
            )
            db.setVersion(4)
        } finally {
            db.close()
        }
    }

    private fun createBaseTablesThroughVersion4(db: SQLiteDatabase) {
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
            CREATE TABLE IF NOT EXISTS user_workspace (
                workspaceId TEXT NOT NULL PRIMARY KEY,
                displayName TEXT NOT NULL,
                genesisSource TEXT NOT NULL,
                localPrimary INTEGER NOT NULL,
                optionalRepoOwner TEXT,
                optionalRepoName TEXT,
                optionalRepoPrivate INTEGER NOT NULL,
                repoProposalApproved INTEGER NOT NULL,
                updatedAtMillis INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS local_instance_identity (
                instanceId TEXT NOT NULL PRIMARY KEY,
                alias TEXT NOT NULL,
                bornAtMillis INTEGER NOT NULL,
                genesisAgentId TEXT NOT NULL,
                genesisRole TEXT NOT NULL,
                genesisRiskTier TEXT NOT NULL,
                genesisSchemaVersion TEXT NOT NULL,
                forkOwner TEXT NOT NULL,
                forkRepo TEXT NOT NULL,
                forkHtmlUrl TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS genesis_core (
                coreId TEXT NOT NULL PRIMARY KEY,
                instanceId TEXT NOT NULL,
                aliasAtBirth TEXT NOT NULL,
                copiedAtMillis INTEGER NOT NULL,
                sourceOrigin TEXT NOT NULL,
                schemaVersion TEXT NOT NULL,
                agentId TEXT NOT NULL,
                role TEXT NOT NULL,
                owner TEXT NOT NULL,
                riskTier TEXT NOT NULL,
                doctrineRef TEXT NOT NULL,
                policyRef TEXT NOT NULL,
                allowedActionsJson TEXT NOT NULL,
                disallowedActionsJson TEXT NOT NULL,
                doctrineText TEXT,
                policyText TEXT,
                contentSha256 TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS memory_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                genesisCoreId TEXT NOT NULL,
                eventType TEXT NOT NULL,
                actor TEXT NOT NULL,
                body TEXT NOT NULL,
                importance INTEGER NOT NULL,
                createdAtMillis INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS memory_snapshots (
                snapshotId TEXT NOT NULL PRIMARY KEY,
                genesisCoreId TEXT NOT NULL,
                summary TEXT NOT NULL,
                eventCount INTEGER NOT NULL,
                messageCount INTEGER NOT NULL,
                updatedAtMillis INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun createBaseTablesThroughVersion7(db: SQLiteDatabase) {
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
            CREATE TABLE IF NOT EXISTS user_workspace (
                workspaceId TEXT NOT NULL PRIMARY KEY,
                displayName TEXT NOT NULL,
                genesisSource TEXT NOT NULL,
                localPrimary INTEGER NOT NULL,
                optionalRepoOwner TEXT,
                optionalRepoName TEXT,
                optionalRepoPrivate INTEGER NOT NULL,
                repoProposalApproved INTEGER NOT NULL,
                updatedAtMillis INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS local_instance_identity (
                instanceId TEXT NOT NULL PRIMARY KEY,
                alias TEXT NOT NULL,
                bornAtMillis INTEGER NOT NULL,
                genesisAgentId TEXT NOT NULL,
                genesisRole TEXT NOT NULL,
                genesisRiskTier TEXT NOT NULL,
                genesisSchemaVersion TEXT NOT NULL,
                localMemoryOwner TEXT NOT NULL,
                localMemoryName TEXT NOT NULL,
                localMemoryUri TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS genesis_core (
                coreId TEXT NOT NULL PRIMARY KEY,
                instanceId TEXT NOT NULL,
                aliasAtBirth TEXT NOT NULL,
                copiedAtMillis INTEGER NOT NULL,
                sourceOrigin TEXT NOT NULL,
                schemaVersion TEXT NOT NULL,
                agentId TEXT NOT NULL,
                role TEXT NOT NULL,
                owner TEXT NOT NULL,
                riskTier TEXT NOT NULL,
                doctrineRef TEXT NOT NULL,
                policyRef TEXT NOT NULL,
                allowedActionsJson TEXT NOT NULL,
                disallowedActionsJson TEXT NOT NULL,
                doctrineText TEXT,
                policyText TEXT,
                contentSha256 TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS memory_snapshots (
                snapshotId TEXT NOT NULL PRIMARY KEY,
                genesisCoreId TEXT NOT NULL,
                summary TEXT NOT NULL,
                eventCount INTEGER NOT NULL,
                messageCount INTEGER NOT NULL,
                updatedAtMillis INTEGER NOT NULL
            )
            """.trimIndent()
        )
        createCurrentTablesThroughVersion7(db)
    }

    private fun createCurrentTablesThroughVersion7(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS memory_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                genesisCoreId TEXT NOT NULL,
                eventType TEXT NOT NULL,
                actor TEXT NOT NULL,
                body TEXT NOT NULL,
                importance INTEGER NOT NULL,
                createdAtMillis INTEGER NOT NULL,
                previousEventHash TEXT,
                genesisCoreHash TEXT NOT NULL DEFAULT 'sha256:legacy-unverified',
                eventHash TEXT NOT NULL DEFAULT 'sha256:legacy-unverified',
                hashAlgorithm TEXT NOT NULL DEFAULT 'sha256',
                canonicalization TEXT NOT NULL DEFAULT 'morimil.memory_event_hash.v1',
                signatureAlgorithm TEXT,
                eventSignature TEXT,
                source TEXT NOT NULL DEFAULT 'system',
                contextTag TEXT NOT NULL DEFAULT 'local_runtime',
                privacyVisibility TEXT NOT NULL DEFAULT 'private_local'
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_events_eventHash ON memory_events(eventHash)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_events_createdAtMillis ON memory_events(createdAtMillis)")
    }

    private fun createVersion7DatabaseWithMemoryEvent() {
        context.deleteDatabase(MORIMIL_DB)
        val db = openWritableDatabase()
        try {
            createBaseTablesThroughVersion7(db)
            db.execSQL(
                """
                INSERT INTO memory_events (
                    id,
                    genesisCoreId,
                    eventType,
                    actor,
                    body,
                    importance,
                    createdAtMillis,
                    genesisCoreHash,
                    eventHash,
                    hashAlgorithm,
                    canonicalization,
                    source,
                    contextTag,
                    privacyVisibility
                ) VALUES (
                    1,
                    'primary_genesis',
                    'test.event',
                    'system',
                    'old memory event',
                    77,
                    1000,
                    'sha256:legacy-unverified',
                    'sha256:legacy-unverified',
                    'sha256',
                    'morimil.memory_event_hash.v1',
                    'system',
                    'local_runtime',
                    'private_local'
                )
                """.trimIndent()
            )
            db.setVersion(7)
        } finally {
            db.close()
        }
    }

    private fun openWritableDatabase(): SQLiteDatabase {
        val file = context.getDatabasePath(MORIMIL_DB)
        file.parentFile?.mkdirs()
        return SQLiteDatabase.openOrCreateDatabase(file, null)
    }

    private fun SupportSQLiteDatabase.userVersion(): Int {
        return singleInt("PRAGMA user_version")
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
        private const val MORIMIL_DB = "morimil-database-migration-test.db"

        private val MEMORY_EVENT_V8_COLUMNS = setOf(
            "memoryKind",
            "tagsJson",
            "evidenceJson",
            "confidence",
            "userConfirmed"
        )
        private val MEMORY_EVENT_COLUMNS = setOf(
            "previousEventHash",
            "genesisCoreHash",
            "eventHash",
            "hashAlgorithm",
            "canonicalization",
            "signatureAlgorithm",
            "eventSignature",
            "source",
            "contextTag",
            "privacyVisibility"
        ) + MEMORY_EVENT_V8_COLUMNS
        private val MEMORY_EVENT_INDEXES = setOf(
            "index_memory_events_eventHash",
            "index_memory_events_createdAtMillis",
            "index_memory_events_memoryKind",
            "index_memory_events_importance"
        )
    }
}
