package com.morimil.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memory_messages")
data class MemoryMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val author: String,
    val body: String,
    val createdAtMillis: Long
)
