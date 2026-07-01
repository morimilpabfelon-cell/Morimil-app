package com.morimil.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_workspace")
data class UserWorkspaceEntity(
    @PrimaryKey
    val workspaceId: String,
    val displayName: String,
    val genesisSource: String,
    val localPrimary: Boolean,
    val optionalRepoOwner: String?,
    val optionalRepoName: String?,
    val optionalRepoPrivate: Boolean,
    val repoProposalApproved: Boolean,
    val updatedAtMillis: Long
)
