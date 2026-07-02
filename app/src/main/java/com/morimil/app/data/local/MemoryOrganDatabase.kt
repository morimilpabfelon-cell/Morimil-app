package com.morimil.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AutobiographicalSnapshotEntity::class,
        KnowledgeCapsuleEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class MemoryOrganDatabase : RoomDatabase() {
    abstract fun memoryOrganDao(): MemoryOrganDao

    companion object {
        @Volatile
        private var instance: MemoryOrganDatabase? = null

        fun getInstance(context: Context): MemoryOrganDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MemoryOrganDatabase::class.java,
                    "morimil_memory_organs.db"
                )
                    .build()
                    .also { instance = it }
            }
        }
    }
}
