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
        LocalInstanceIdentityEntity::class
    ],
    version = 3,
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

        fun getInstance(context: Context): MorimilDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MorimilDatabase::class.java,
                    "morimil_memory.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
