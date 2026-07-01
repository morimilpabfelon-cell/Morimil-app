package com.morimil.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory_messages ORDER BY createdAtMillis ASC, id ASC")
    fun observeMessages(): Flow<List<MemoryMessageEntity>>

    @Query("SELECT COUNT(*) FROM memory_messages")
    suspend fun countMessages(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMessage(message: MemoryMessageEntity)

    @Query("SELECT * FROM decision_log ORDER BY createdAtMillis DESC, id DESC")
    fun observeDecisions(): Flow<List<DecisionLogEntity>>

    @Query("SELECT COUNT(*) FROM decision_log")
    suspend fun countDecisions(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDecision(decision: DecisionLogEntity)

    @Query("SELECT * FROM project_state ORDER BY updatedAtMillis DESC")
    fun observeProjects(): Flow<List<ProjectStateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(project: ProjectStateEntity)

    @Query("SELECT * FROM user_workspace ORDER BY updatedAtMillis DESC LIMIT 1")
    fun observeActiveWorkspace(): Flow<UserWorkspaceEntity?>

    @Query("SELECT COUNT(*) FROM user_workspace")
    suspend fun countWorkspaces(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkspace(workspace: UserWorkspaceEntity)

    @Query("UPDATE user_workspace SET displayName = :displayName, updatedAtMillis = :updatedAtMillis WHERE workspaceId = 'local_primary'")
    suspend fun renameWorkspace(displayName: String, updatedAtMillis: Long): Int

    @Query("SELECT * FROM local_instance_identity LIMIT 1")
    fun observeLocalIdentity(): Flow<LocalInstanceIdentityEntity?>

    @Query("SELECT COUNT(*) FROM local_instance_identity")
    suspend fun countLocalIdentity(): Int

    /**
     * ABORT, not REPLACE: an instance is born once. A future attempt to
     * insert a second identity fails loudly rather than silently renaming
     * the instance underneath the user.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLocalIdentity(identity: LocalInstanceIdentityEntity)
}
