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
class MemoryOrganDatabaseMigrationTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun cleanUp() {
        context.deleteDatabase(MEMORY_ORGAN_DB)
    }

    @Test
    fun migratesLegacyV1ToCurrentV5WithoutDroppingCapsules() {
        createVersion1Database()

        val database = openMigratedDatabase()
        val migrated = database.openHelper.writableDatabase

        assertEquals(5, migrated.userVersion())
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
        migrated.query(
            """
            SELECT capsuleType, source, privacyVisibility, claimsJson, evidenceJson,
                   capsuleVersion, capsuleCategory, status, confidence
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
            assertEquals(88, cursor.getInt(8))
        }
        assertTrue(migrated.indexNames("knowledge_capsules").containsAll(KNOWLEDGE_CAPSULE_INDEXES))
        assertTrue(migrated.indexNames("recall_schedules").containsAll(RECALL_SCHEDULE_INDEXES))
        assertTrue(migrated.indexNames("memory_links").containsAll(MEMORY_LINK_INDEXES))
        assertTrue(migrated.indexNames("migration_records").containsAll(MIGRATION_RECORD_INDEXES))
        database.close()
    }

    @Test
    fun migratesV4ToV5AndAcceptsGraphAndMigrationRows() {
        createVersion4Database()

        val database = Room.databaseBuilder(context, MemoryOrganDatabase::class.java, MEMORY_ORGAN_DB)
            .addMigrations(MemoryOrganDatabase.MIGRATION_4_5)
            .build()
        val migrated = database.openHelper.writableDatabase

        assertEquals(5, migrated.userVersion())
        assertTrue(migrated.columnNames("memory_links").containsAll(MEMORY_LINK_COLUMNS))
        assertTrue(migrated.columnNames("migration_records").containsAll(MIGRATION_RECORD_COLUMNS))
        assertTrue(migrated.indexNames("memory_links").containsAll(MEMORY_LINK_INDEXES))
        assertTrue(migrated.indexNames("migration_records").containsAll(MIGRATION_RECORD_INDEXES))

        migrated.execSQL(
            """
            INSERT INTO memory_links (
                linkId,
                instanceId,
                genesisCoreHash,
                sourceId,
                sourceType,
                targetId,
                targetType,
                relation,
                strength,
                reason,
                createdBy,
                privacyVisibility,
                cloudSyncAllowed,
                exportAllowed,
                verificationState,
                createdAtMillis
            ) VALUES (
                'link-1',
                'local',
                'sha256:genesis',
                'sha256:event-a',
                'memory_event',
                'sha256:event-b',
                'memory_event',
                'mentions',
                0.8,
                'migration insert check',
                'test',
                'private_local',
                0,
                0,
                'valid',
                1000
            )
            """.trimIndent()
        )
        migrated.execSQL(
            """
            INSERT INTO migration_records (
                migrationId,
                instanceId,
                genesisCoreHash,
                proposalId,
                migrationType,
                fromVersion,
                toVersion,
                affectedArtifactsJson,
                preSnapshotId,
                chainVerified,
                backupRequired,
                stepsJson,
                expectedEffect,
                riskLevel,
                approvalRequired,
                approvedByUser,
                approvalId,
                status,
                postSnapshotId,
                errorsJson,
                rollbackAvailable,
                rollbackStrategy,
                createdBy,
                createdAtMillis,
                updatedAtMillis
            ) VALUES (
                'migration-1',
                'local',
                'sha256:genesis',
                NULL,
                'test',
                'v4',
                'v5',
                '[]',
                'pre',
                1,
                0,
                '[]',
                'verify migrated table accepts runtime rows',
                'low',
                0,
                0,
                NULL,
                'completed',
                'post',
                '[]',
                1,
                'append_only',
                'test',
                1000,
                1000
            )
            """.trimIndent()
        )
        assertEquals(1, migrated.singleInt("SELECT COUNT(*) FROM memory_links"))
        assertEquals(1, migrated.singleInt("SELECT COUNT(*) FROM migration_records"))
        database.close()
    }

    @Test
    fun migratesV3ToCurrentV5WithRecallScheduleRuntimeTable() {
        createVersion3Database()

        val database = Room.databaseBuilder(context, MemoryOrganDatabase::class.java, MEMORY_ORGAN_DB)
            .addMigrations(
                MemoryOrganDatabase.MIGRATION_3_4,
                MemoryOrganDatabase.MIGRATION_4_5
            )
            .build()
        val migrated = database.openHelper.writableDatabase

        assertEquals(5, migrated.userVersion())
        assertTrue(migrated.columnNames("recall_schedules").containsAll(RECALL_SCHEDULE_COLUMNS))
        assertTrue(migrated.indexNames("recall_schedules").containsAll(RECALL_SCHEDULE_INDEXES))
        migrated.execSQL(
            """
            INSERT INTO recall_schedules (
                genesisCoreId,
                targetEventHash,
                targetMemoryKind,
                prompt,
                reason,
                priority,
                intervalDays,
                dueAtMillis,
                status,
                lastAction,
                source,
                createdAtMillis,
                updatedAtMillis,
                lastReviewedAtMillis
            ) VALUES (
                'primary_genesis',
                'sha256:event',
                'decision',
                'remember this',
                'test migration',
                80,
                3,
                2000,
                'scheduled',
                'created',
                'test',
                1000,
                1000,
                NULL
            )
            """.trimIndent()
        )
        assertEquals(1, migrated.singleInt("SELECT COUNT(*) FROM recall_schedules"))
        database.close()
    }

    private fun openMigratedDatabase(): MemoryOrganDatabase {
        return Room.databaseBuilder(context, MemoryOrganDatabase::class.java, MEMORY_ORGAN_DB)
            .addMigrations(
                MemoryOrganDatabase.MIGRATION_1_2,
                MemoryOrganDatabase.MIGRATION_2_3,
                MemoryOrganDatabase.MIGRATION_3_4,
                MemoryOrganDatabase.MIGRATION_4_5
            )
            .build()
    }

    private fun createVersion1Database() {
        context.deleteDatabase(MEMORY_ORGAN_DB)
        val db = openWritableDatabase()
        try {
            createVersion1Tables(db)
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

    private fun createVersion3Database() {
        context.deleteDatabase(MEMORY_ORGAN_DB)
        val db = openWritableDatabase()
        try {
            createVersion3Tables(db)
            db.setVersion(3)
        } finally {
            db.close()
        }
    }

    private fun createVersion4Database() {
        context.deleteDatabase(MEMORY_ORGAN_DB)
        val db = openWritableDatabase()
        try {
            createVersion3Tables(db)
            createRecallScheduleTable(db)
            db.setVersion(4)
        } finally {
            db.close()
        }
    }

    private fun createVersion1Tables(db: SQLiteDatabase) {
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
    }

    private fun createVersion3Tables(db: SQLiteDatabase) {
        createVersion1Tables(db)
        db.execSQL("ALTER TABLE knowledge_capsules ADD COLUMN capsuleType TEXT NOT NULL DEFAULT 'knowledge_capsule'")
        db.execSQL("ALTER TABLE knowledge_capsules ADD COLUMN source TEXT NOT NULL DEFAULT 'user_approved_notes'")
        db.execSQL("ALTER TABLE knowledge_capsules ADD COLUMN privacyVisibility TEXT NOT NULL DEFAULT 'private_local'")
        db.execSQL("ALTER TABLE knowledge_capsules ADD COLUMN claimsJson TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE knowledge_capsules ADD COLUMN evidenceJson TEXT NOT NULL DEFAULT '{}'")
        db.execSQL("ALTER TABLE knowledge_capsules ADD COLUMN previousCapsuleHash TEXT")
        db.execSQL("ALTER TABLE knowledge_capsules ADD COLUMN capsuleHash TEXT NOT NULL DEFAULT 'sha256:legacy-unverified'")
        db.execSQL("ALTER TABLE knowledge_capsules ADD COLUMN hashAlgorithm TEXT NOT NULL DEFAULT 'sha256'")
        db.execSQL("ALTER TABLE knowledge_capsules ADD COLUMN canonicalization TEXT NOT NULL DEFAULT 'morimil.knowledge_capsule_hash.v1'")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_knowledge_capsules_capsuleHash ON knowledge_capsules(capsuleHash)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_knowledge_capsules_capsuleType ON knowledge_capsules(capsuleType)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_knowledge_capsules_updatedAtMillis ON knowledge_capsules(updatedAtMillis)")
        db.execSQL("ALTER TABLE knowledge_capsules ADD COLUMN capsuleVersion INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE knowledge_capsules ADD COLUMN capsuleCategory TEXT NOT NULL DEFAULT 'general_knowledge'")
        db.execSQL("ALTER TABLE knowledge_capsules ADD COLUMN status TEXT NOT NULL DEFAULT 'active'")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_knowledge_capsules_status ON knowledge_capsules(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_knowledge_capsules_category ON knowledge_capsules(capsuleCategory)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_knowledge_capsules_title ON knowledge_capsules(title)")
    }

    private fun createRecallScheduleTable(db: SQLiteDatabase) {
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
    }

    private fun openWritableDatabase(): SQLiteDatabase {
        val file = context.getDatabasePath(MEMORY_ORGAN_DB)
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

    companion object {
        private const val MEMORY_ORGAN_DB = "memory-organ-database-migration-test.db"

        private val KNOWLEDGE_CAPSULE_INDEXES = setOf(
            "index_knowledge_capsules_capsuleHash",
            "index_knowledge_capsules_capsuleType",
            "index_knowledge_capsules_updatedAtMillis",
            "index_knowledge_capsules_status",
            "index_knowledge_capsules_category",
            "index_knowledge_capsules_title"
        )
        private val RECALL_SCHEDULE_COLUMNS = setOf(
            "recallId",
            "targetEventHash",
            "targetMemoryKind",
            "priority",
            "intervalDays",
            "dueAtMillis",
            "status",
            "lastReviewedAtMillis"
        )
        private val RECALL_SCHEDULE_INDEXES = setOf(
            "index_recall_schedules_targetEventHash",
            "index_recall_schedules_status",
            "index_recall_schedules_dueAtMillis"
        )
        private val MEMORY_LINK_COLUMNS = setOf(
            "linkId",
            "instanceId",
            "genesisCoreHash",
            "sourceId",
            "sourceType",
            "targetId",
            "targetType",
            "relation",
            "strength",
            "reason",
            "verificationState"
        )
        private val MEMORY_LINK_INDEXES = setOf(
            "index_memory_links_sourceId_sourceType",
            "index_memory_links_targetId_targetType",
            "index_memory_links_relation",
            "index_memory_links_verificationState",
            "index_memory_links_sourceId_sourceType_targetId_targetType_relation"
        )
        private val MIGRATION_RECORD_COLUMNS = setOf(
            "migrationId",
            "migrationType",
            "fromVersion",
            "toVersion",
            "status",
            "rollbackAvailable",
            "rollbackStrategy"
        )
        private val MIGRATION_RECORD_INDEXES = setOf(
            "index_migration_records_migrationType",
            "index_migration_records_status",
            "index_migration_records_approvedByUser",
            "index_migration_records_createdAtMillis"
        )
    }
}
