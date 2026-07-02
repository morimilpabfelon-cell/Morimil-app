package com.morimil.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MemoryMessageEntity::class,
        DecisionLogEntity::class,
        ProjectStateEntity::class,
        UserWorkspaceEntity::class,
        LocalInstanceIdentityEntity::class,
        GenesisCoreEntity::class,
        MemoryEventEntity::class,
        MemorySnapshotEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class MorimilDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao

    companion object {
        @Volatile
        private var instance: MorimilDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
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
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
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
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
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
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS local_instance_identity_new (
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
                    INSERT INTO local_instance_identity_new (
                        instanceId,
                        alias,
                        bornAtMillis,
                        genesisAgentId,
                        genesisRole,
                        genesisRiskTier,
                        genesisSchemaVersion,
                        localMemoryOwner,
                        localMemoryName,
                        localMemoryUri
                    )
                    SELECT
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
                    FROM local_instance_identity
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE local_instance_identity")
                db.execSQL("ALTER TABLE local_instance_identity_new RENAME TO local_instance_identity")
            }
        }

        fun getInstance(context: Context): MorimilDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MorimilDatabase::class.java,
                    "morimil_memory.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
