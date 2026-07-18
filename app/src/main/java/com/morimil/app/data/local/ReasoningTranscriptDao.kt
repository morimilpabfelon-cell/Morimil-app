package com.morimil.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReasoningTranscriptDao {
    @Query("SELECT * FROM reasoning_turns ORDER BY createdAtMillis ASC, id ASC")
    fun observeTurns(): Flow<List<ReasoningTurnEntity>>

    @Query("SELECT COUNT(*) FROM reasoning_turns")
    suspend fun countTurns(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTurn(turn: ReasoningTurnEntity)
}
