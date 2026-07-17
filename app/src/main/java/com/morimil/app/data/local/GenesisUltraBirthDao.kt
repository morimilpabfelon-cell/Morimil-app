package com.morimil.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface GenesisUltraBirthDao {
    @Query("SELECT COUNT(*) FROM genesis_ultra_birth_commit")
    suspend fun countBirthCommits(): Int

    @Query("SELECT COUNT(*) FROM genesis_ultra_birth_artifacts")
    suspend fun countBirthArtifacts(): Int

    @Query("SELECT COUNT(*) FROM genesis_ultra_birth_journal")
    suspend fun countBirthJournalEntries(): Int

    @Query("SELECT * FROM genesis_ultra_birth_commit WHERE slotId = :slotId LIMIT 1")
    suspend fun loadBirthCommit(slotId: String): GenesisUltraBirthCommitEntity?

    @Query("SELECT * FROM genesis_ultra_birth_artifacts WHERE slotId = :slotId ORDER BY relativePath ASC")
    suspend fun loadBirthArtifacts(slotId: String): List<GenesisUltraBirthArtifactEntity>

    @Query("SELECT * FROM genesis_ultra_birth_journal WHERE slotId = :slotId ORDER BY sequence ASC")
    suspend fun loadBirthJournal(slotId: String): List<GenesisUltraBirthJournalEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBirthArtifacts(artifacts: List<GenesisUltraBirthArtifactEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBirthJournal(entries: List<GenesisUltraBirthJournalEntity>)

    /** Inserted last inside the Room transaction: this row is the commit marker. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBirthCommit(commit: GenesisUltraBirthCommitEntity)
}
