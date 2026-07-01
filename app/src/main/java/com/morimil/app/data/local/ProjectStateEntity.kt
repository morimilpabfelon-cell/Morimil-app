package com.morimil.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "project_state")
data class ProjectStateEntity(
    @PrimaryKey
    val projectId: String,
    val title: String,
    val status: String,
    val updatedAtMillis: Long
)
