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
class DatabaseMigrationTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun cleanUp() {
        context.deleteDatabase(MORIMIL_DB)
        context.deleteDatabase(MEMORY_ORGAN_DB)
    }

    @Test
    fun morimilDatabaseMigratesFrom7To8WithMemoryEventDefaults() {
        createMorimilDatabaseAtVersion7()

        val database = Room.databaseBuilder(context, MorimilDatabase::class.java, MORIMIL_DB)
            .addMigrations(MorimilDatabase.MIGRATION_7_8)
            .build()

        val migrated = database.openHelper.writableDatabase
        val columns = migrated.columnNames("memory_events")
        assertTrue(columns.containsAll(listOf("memoryKind", "tagsJson", "evidenceJson", "confidence", "userConfirmed")))

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

        assertTrue(migrated.indexNames("memory_events").containsAll(MORIMIL_MEMORY_EVENT_INDEXES))
        database.close()
    }

    @Test
    fun memoryOrganDatabaseMigratesFrom4To5WithGraphAndMigrationTables() {
        createMemoryOrganDatabaseAtVersion4()

        val database = Room.databaseBuilder(context, MemoryOrganDatabase::class.java, MEMORY_ORGAN_DB)
            .addMigrations(MemoryOrganDatabase.MIGRATION_4_5)
            .build()

        val migrated = database.openHelper.writableDatabase
        assertTrue(
            migrated.columnNames("memory_links").containsAll(
                listOf("linkId", "sourceId", "sourceType", "targetId", "targetType", "relation", "strength")
            )
        )
        assertTrue(
            migrated.columnNames("migration_records").containsAll(
                listOf("migrationId", "migrationType", "fromVersion", "toVersion", "status", "rollbackStrategy")
            )
        )
        assertTrue(migrated.indexNames("memory_links").containsAll(MEMORY_LINK_INDEXES))
        assertTrue(migrated.indexNames("migration_records").containsAll(MIGRATION_RECORD_INDEXES))
        database.close()
    }

    private fun createMorimilDatabaseAtVersion7() {
        context.deleteDatabase(MORIMIL_DB)
        val file = context.getDatabasePath(MORIMIL_DB)
        file.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
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
            db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_events_eventHash ON memory_events(eventHash)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_events_createdAtMillis ON memory_events(createdAtMillis)")
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
            db.setVersion(7)
        } finally {
            db.close()
        }
    }

    private fun createMemoryOrganDatabaseAtVersion4() {
        context.deleteDatabase(MEMORY_ORGAN_DB)
        val file = context.getDatabasePath(MEMORY_ORGAN_DB)
        file.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
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
                    updatedAtMillis INTEGER NOT NULL,
                    capsuleType TEXT NOT NULL DEFAULT 'knowledge_capsule',
                    source TEXT NOT NULL DEFAULT 'user_approved_notes',
                    privacyVisibility TEXT NOT NULL DEFAULT 'private_local',
                    claimsJson TEXT NOT NULL DEFAULT '[]',
                    evidenceJson TEXT NOT NULL DEFAULT '{}',
                    previousCapsuleHash TEXT,
                    capsuleHash TEXT NOT NULL DEFAULT 'sha256:legacy-unverified',
                    hashAlgorithm TEXT NOT NULL DEFAULT 'sha256',
                    canonicalization TEXT NOT NULL DEFAULT 'morimil.knowledge_capsule_hash.v1',
                    capsuleVersion INTEGER NOT NULL DEFAULT 1,
                    capsuleCategory TEXT NOT NULL DEFAULT 'general_knowledge',
                    status TEXT NOT NULL DEFAULT 'active'
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_knowledge_capsules_capsuleHash ON knowledge_capsules(capsuleHash)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_knowledge_capsules_capsuleType ON knowledge_capsules(capsuleType)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_knowledge_capsules_updatedAtMillis ON knowledge_capsules(updatedAtMillis)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_knowledge_capsules_status ON knowledge_capsules(status)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_knowledge_capsules_category ON knowledge_capsules(capsuleCategory)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_knowledge_capsules_title ON knowledge_capsules(title)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS recall_schedules (
                    recallId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    genesisCoreId TEXT NOT NULL,
                    targetEventHash TEXT NOT NULL,
                    targetMemoryKind TEXT NOT NULL,
                    prompt TEXT NOT NULL,
                    reason TEXT NOT NULL,
                    priority INTEGER NOT NULL,
                    intervalDays INTEGER NOT NULL,
                    dueAtMillis INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    lastAction TEXT NOT NULL,
                    source TEXT NOT NULL,
                    createdAtMillis INTEGER NOT NULL,
                    updatedAtMillis INTEGER NOT NULL,
                    lastReviewedAtMillis INTEGER
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_recall_schedules_targetEventHash ON recall_schedules(targetEventHash)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_recall_schedules_status ON recall_schedules(status)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_recall_schedules_dueAtMillis ON recall_schedules(dueAtMillis)")
            db.setVersion(4)
        } finally {
            db.close()
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

    companion object {
        private const val MORIMIL_DB = "migration-test-morimil.db"
        private const val MEMORY_ORGAN_DB = "migration-test-memory-organ.db"

        private val MORIMIL_MEMORY_EVENT_INDEXES = setOf(
            "index_memory_events_eventHash",
            "index_memory_events_createdAtMillis",
            "index_memory_events_memoryKind",
            "index_memory_events_importance"
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
