package com.example.tabletennisscore.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a completed match stored in the Room database.
 *
 * [setResultsJson] is a comma-separated list of "score1-score2" per completed set,
 * e.g. "11-7,9-11,11-5".
 */
@Entity(tableName = "match_results")
data class MatchResult(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tournamentName: String,
    val player1Name: String,
    val player2Name: String,
    val sets1: Int,
    val sets2: Int,
    val winner: Int,         // 1 or 2
    val bestOfSets: Int,
    val durationMs: Long,
    val setResultsJson: String,
    val playedAt: Long = System.currentTimeMillis(),
)

