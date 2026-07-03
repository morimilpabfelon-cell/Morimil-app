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
        KnowledgeCapsuleEntity::class
    ],
    version = 3,
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

        fun getInstance(context: Context): MemoryOrganDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MemoryOrganDatabase::class.java,
                    "morimil_memory_organs.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
        }
    }
}