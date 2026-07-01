package com.morimil.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MemoryMessageEntity::class,
        DecisionLogEntity::class,
        ProjectStateEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class MorimilDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao

    companion object {
        @Volatile
        private var instance: MorimilDatabase? = null

        fun getInstance(context: Context): MorimilDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MorimilDatabase::class.java,
                    "morimil_memory.db"
                )
                    .build()
                    .also { instance = it }
            }
        }
    }
}
