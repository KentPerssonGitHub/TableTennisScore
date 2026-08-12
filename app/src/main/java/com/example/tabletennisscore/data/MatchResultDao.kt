package com.example.tabletennisscore.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MatchResultDao {

    @Insert
    suspend fun insert(result: MatchResult)

    /** Returns all results newest-first as a live reactive stream. */
    @Query("SELECT * FROM match_results ORDER BY playedAt DESC")
    fun getAll(): Flow<List<MatchResult>>

    @Query("DELETE FROM match_results WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM match_results")
    suspend fun deleteAll()
}

