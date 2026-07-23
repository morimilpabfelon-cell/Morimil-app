package com.morimil.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ReasoningTurnEntity::class,
        DecisionLogEntity::class,
        ProjectStateEntity::class,
        UserWorkspaceEntity::class,
        LocalInstanceIdentityEntity::class,
        GenesisCoreEntity::class,
        MemoryEventEntity::class,
        MemorySnapshotEntity::class,
        ImprovementDecisionHistoryEntity::class,
        GenesisUltraBirthCommitEntity::class,
        GenesisUltraBirthArtifactEntity::class,
        GenesisUltraBirthJournalEntity::class,
        GenesisUltraMemoryEventEntity::class
    ],
    version = 12,
    exportSchema = true
)
abstract class MorimilDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun reasoningTranscriptDao(): ReasoningTranscriptDao
    abstract fun genesisUltraBirthDao(): GenesisUltraBirthDao
    abstract fun genesisUltraMemoryDao(): GenesisUltraMemoryDao

    companion object {
        @Volatile
        private var instance: MorimilDatabase? = null

        val MIGRATION_1_2 = MorimilDatabaseMigrations.MIGRATION_1_2
        val MIGRATION_2_3 = MorimilDatabaseMigrations.MIGRATION_2_3
        val MIGRATION_3_4 = MorimilDatabaseMigrations.MIGRATION_3_4
        val MIGRATION_4_5 = MorimilDatabaseMigrations.MIGRATION_4_5
        val MIGRATION_5_6 = MorimilDatabaseMigrations.MIGRATION_5_6
        val MIGRATION_6_7 = MorimilDatabaseMigrations.MIGRATION_6_7
        val MIGRATION_7_8 = MorimilDatabaseMigrations.MIGRATION_7_8
        val MIGRATION_8_9 = MorimilDatabaseMigrations.MIGRATION_8_9
        val MIGRATION_9_10 = MorimilDatabaseMigrations.MIGRATION_9_10
        val MIGRATION_10_11 = MorimilDatabaseMigrations.MIGRATION_10_11
        val MIGRATION_11_12 = MorimilDatabaseMigrations.MIGRATION_11_12

        fun getInstance(context: Context): MorimilDatabase {
            return instance ?: synchronized(this) {
                instance ?: MorimilDatabaseEncryption.open(context)
                    .also { instance = it }
            }
        }
    }
}