package com.morimil.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AutobiographicalSnapshotEntity::class,
        KnowledgeCapsuleEntity::class,
        RecallScheduleEntity::class,
        MemoryLinkEntity::class,
        MigrationRecordEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class MemoryOrganDatabase : RoomDatabase() {
    abstract fun memoryOrganDao(): MemoryOrganDao

    companion object {
        @Volatile
        private var instance: MemoryOrganDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
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
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE knowledge_capsules ADD COLUMN capsuleVersion INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE knowledge_capsules ADD COLUMN capsuleCategory TEXT NOT NULL DEFAULT 'general_knowledge'")
                db.execSQL("ALTER TABLE knowledge_capsules ADD COLUMN status TEXT NOT NULL DEFAULT 'active'")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_knowledge_capsules_status ON knowledge_capsules(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_knowledge_capsules_category ON knowledge_capsules(capsuleCategory)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_knowledge_capsules_title ON knowledge_capsules(title)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
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
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS memory_links (
                        linkId TEXT NOT NULL PRIMARY KEY,
                        instanceId TEXT NOT NULL,
                        genesisCoreHash TEXT NOT NULL,
                        sourceId TEXT NOT NULL,
                        sourceType TEXT NOT NULL,
                        targetId TEXT NOT NULL,
                        targetType TEXT NOT NULL,
                        relation TEXT NOT NULL,
                        strength REAL NOT NULL,
                        reason TEXT NOT NULL,
                        createdBy TEXT NOT NULL,
                        privacyVisibility TEXT NOT NULL,
                        cloudSyncAllowed INTEGER NOT NULL,
                        exportAllowed INTEGER NOT NULL,
                        verificationState TEXT NOT NULL,
                        createdAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_links_sourceId_sourceType ON memory_links(sourceId, sourceType)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_links_targetId_targetType ON memory_links(targetId, targetType)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_links_relation ON memory_links(relation)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_links_verificationState ON memory_links(verificationState)")
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS index_memory_links_sourceId_sourceType_targetId_targetType_relation
                    ON memory_links(sourceId, sourceType, targetId, targetType, relation)
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS migration_records (
                        migrationId TEXT NOT NULL PRIMARY KEY,
                        instanceId TEXT NOT NULL,
                        genesisCoreHash TEXT NOT NULL,
                        proposalId TEXT,
                        migrationType TEXT NOT NULL,
                        fromVersion TEXT NOT NULL,
                        toVersion TEXT NOT NULL,
                        affectedArtifactsJson TEXT NOT NULL,
                        preSnapshotId TEXT NOT NULL,
                        chainVerified INTEGER NOT NULL,
                        backupRequired INTEGER NOT NULL,
                        stepsJson TEXT NOT NULL,
                        expectedEffect TEXT NOT NULL,
                        riskLevel TEXT NOT NULL,
                        approvalRequired INTEGER NOT NULL,
                        approvedByUser INTEGER NOT NULL,
                        approvalId TEXT,
                        status TEXT NOT NULL,
                        postSnapshotId TEXT,
                        errorsJson TEXT NOT NULL,
                        rollbackAvailable INTEGER NOT NULL,
                        rollbackStrategy TEXT NOT NULL,
                        createdBy TEXT NOT NULL,
                        createdAtMillis INTEGER NOT NULL,
                        updatedAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_migration_records_migrationType ON migration_records(migrationType)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_migration_records_status ON migration_records(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_migration_records_approvedByUser ON migration_records(approvedByUser)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_migration_records_createdAtMillis ON migration_records(createdAtMillis)")
            }
        }

        fun getInstance(context: Context): MemoryOrganDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MemoryOrganDatabase::class.java,
                    "morimil_memory_organs.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
