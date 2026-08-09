package com.example.tabletennisscore

import android.os.SystemClock
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/**
 * Holds all game state and enforces table tennis scoring rules.
 *
 * Scoring rules applied:
 * - A set is won when a player reaches 11 points with at least a 2-point lead.
 * - Service alternates every 2 points, except from 10-10 (deuce) where it alternates every point.
 * - The player who did NOT serve first in the previous set serves first in the next set.
 * - The first server of the match is decided externally (defaults to player 1).
 */
class GameViewModel : ViewModel() {

    companion object {
        const val MAX_PLAYER_NAME_LENGTH = 12
    }

    data class GameState(
        val score1: Int = 0,
        val score2: Int = 0,
        val sets1: Int = 0,
        val sets2: Int = 0,
        val bestOfSets: Int = 5,
        val server: Int = 1,         // 1 or 2
        val player1Name: String = "Player 1",
        val player2Name: String = "Player 2",
        val isMatchRunning: Boolean = false,
        val hasMatchStarted: Boolean = false,
        val matchWinner: Int? = null, // 1 or 2 when match is finished
        val setResults: List<Pair<Int, Int>> = emptyList(), // score1 to score2 per completed set
    )

    private val _state = MutableLiveData(GameState())
    val state: LiveData<GameState> = _state

    // History stack for undo support (max 50 entries)
    private val history = ArrayDeque<GameState>(50)

    private val current get() = _state.value!!

    // First server for the match; current-set first server is derived from completed set count.
    private var matchFirstServer: Int = 1
    private var elapsedPlayedMs: Long = 0L
    private var runningSinceMs: Long? = null

    fun swapServer() {
        if (current.matchWinner != null) return
        pushHistory()
        matchFirstServer = otherPlayer(matchFirstServer)
        _state.value = current.copy(server = otherPlayer(current.server))
    }

    fun addPoint(player: Int) {
        if (!current.isMatchRunning || current.matchWinner != null) return
        pushHistory()
        val s = current
        var score1 = s.score1
        var score2 = s.score2
        var sets1 = s.sets1
        var sets2 = s.sets2
        var isMatchRunning = s.isMatchRunning
        var matchWinner = s.matchWinner
        val setResults = s.setResults.toMutableList()
        if (player == 1) score1++ else score2++

        val totalPoints = score1 + score2
        var server = nextServer(score1, score2, totalPoints, currentSetFirstServer(s.setResults.size))

        // Check if set is won
        if (isSetWon(score1, score2)) {
            setResults.add(Pair(score1, score2))
            if (player == 1) sets1++ else sets2++
            score1 = 0
            score2 = 0

            if (isMatchWon(sets1, sets2, s.bestOfSets)) {
                matchWinner = player
                isMatchRunning = false
                captureElapsedUntilNow()
            } else {
                server = currentSetFirstServer(setResults.size)
            }
        }

        _state.value = s.copy(
            score1 = score1,
            score2 = score2,
            sets1 = sets1,
            sets2 = sets2,
            server = server,
            isMatchRunning = isMatchRunning,
            matchWinner = matchWinner,
            setResults = setResults,
        )
    }

    fun undo() {
        if (history.isNotEmpty()) {
            _state.value = history.removeLast()
        }
    }

    fun resetMatch() {
        history.clear()
        matchFirstServer = 1
        elapsedPlayedMs = 0L
        runningSinceMs = null
        _state.value = GameState(
            bestOfSets = current.bestOfSets,
            player1Name = current.player1Name,
            player2Name = current.player2Name,
        )
    }

    fun startOrResumeMatch() {
        if (current.matchWinner != null) return
        if (!current.isMatchRunning) {
            runningSinceMs = SystemClock.elapsedRealtime()
        }
        _state.value = current.copy(
            isMatchRunning = true,
            hasMatchStarted = true,
        )
    }

    fun pauseMatch() {
        if (current.isMatchRunning) {
            captureElapsedUntilNow()
        }
        _state.value = current.copy(isMatchRunning = false)
    }

    fun getElapsedPlayedMs(nowMs: Long = SystemClock.elapsedRealtime()): Long {
        val startedAt = runningSinceMs
        return if (current.isMatchRunning && startedAt != null) {
            elapsedPlayedMs + (nowMs - startedAt)
        } else {
            elapsedPlayedMs
        }
    }

    fun updatePausedScore(score1: Int, score2: Int): Boolean {
        if (current.isMatchRunning || current.matchWinner != null || !current.hasMatchStarted) return false
        if (score1 < 0 || score2 < 0) return false
        if (isSetWon(score1, score2)) return false

        pushHistory()
        val totalPoints = score1 + score2
        val server = nextServer(score1, score2, totalPoints, currentSetFirstServer(current.setResults.size))
        _state.value = current.copy(
            score1 = score1,
            score2 = score2,
            server = server,
        )
        return true
    }

    fun updatePausedMatchScores(setResults: List<Pair<Int, Int>>, currentScore1: Int, currentScore2: Int): Boolean {
        if (current.isMatchRunning || current.matchWinner != null || !current.hasMatchStarted) return false
        if (currentScore1 < 0 || currentScore2 < 0) return false

        var sets1 = 0
        var sets2 = 0
        for ((set1, set2) in setResults) {
            if (set1 < 0 || set2 < 0) return false
            if (!isSetWon(set1, set2)) return false
            if (set1 > set2) sets1++ else sets2++
        }
        if (isMatchWon(sets1, sets2, current.bestOfSets)) return false

        pushHistory()

        if (isSetWon(currentScore1, currentScore2)) {
            // Current set score is a finished set — finalize it
            val setWinner = if (currentScore1 > currentScore2) 1 else 2
            val finalSets1 = sets1 + if (setWinner == 1) 1 else 0
            val finalSets2 = sets2 + if (setWinner == 2) 1 else 0
            val finalSetResults = setResults + listOf(currentScore1 to currentScore2)
            val matchWinner = if (isMatchWon(finalSets1, finalSets2, current.bestOfSets)) setWinner else null
            _state.value = current.copy(
                score1 = 0,
                score2 = 0,
                sets1 = finalSets1,
                sets2 = finalSets2,
                setResults = finalSetResults,
                server = currentSetFirstServer(finalSetResults.size),
                isMatchRunning = false,
                matchWinner = matchWinner,
            )
        } else {
            val server = nextServer(
                currentScore1,
                currentScore2,
                currentScore1 + currentScore2,
                currentSetFirstServer(setResults.size),
            )
            _state.value = current.copy(
                score1 = currentScore1,
                score2 = currentScore2,
                sets1 = sets1,
                sets2 = sets2,
                setResults = setResults,
                server = server,
            )
        }
        return true
    }

    fun setupMatch(player1Name: String, player2Name: String, firstServer: Int, bestOfSets: Int) {
        history.clear()
        matchFirstServer = if (firstServer == 2) 2 else 1
        elapsedPlayedMs = 0L
        runningSinceMs = null
        val validatedBestOf = if (bestOfSets in setOf(1, 3, 5, 7)) bestOfSets else 5
        _state.value = GameState(
            bestOfSets = validatedBestOf,
            server = matchFirstServer,
            player1Name = sanitizePlayerName(player1Name, "Player 1"),
            player2Name = sanitizePlayerName(player2Name, "Player 2"),
            isMatchRunning = false,
            hasMatchStarted = false,
        )
    }

    fun setPlayerName(player: Int, name: String) {
        val trimmed = sanitizePlayerName(name, if (player == 1) "Player 1" else "Player 2")
        _state.value = if (player == 1) current.copy(player1Name = trimmed)
                       else current.copy(player2Name = trimmed)
    }

    private fun sanitizePlayerName(name: String, fallback: String): String {
        return name
            .trim()
            .take(MAX_PLAYER_NAME_LENGTH)
            .ifEmpty { fallback }
    }

    private fun pushHistory() {
        if (history.size >= 50) history.removeFirst()
        history.addLast(current)
    }

    private fun currentSetFirstServer(completedSetCount: Int): Int {
        return if (completedSetCount % 2 == 0) matchFirstServer else otherPlayer(matchFirstServer)
    }

    private fun captureElapsedUntilNow() {
        val startedAt = runningSinceMs ?: return
        elapsedPlayedMs += SystemClock.elapsedRealtime() - startedAt
        runningSinceMs = null
    }

    private fun isSetWon(s1: Int, s2: Int): Boolean {
        return (s1 >= 11 || s2 >= 11) && Math.abs(s1 - s2) >= 2
    }

    private fun isMatchWon(sets1: Int, sets2: Int, bestOfSets: Int): Boolean {
        val setsToWin = (bestOfSets / 2) + 1
        return sets1 >= setsToWin || sets2 >= setsToWin
    }

    private fun nextServer(s1: Int, s2: Int, total: Int, firstServer: Int): Int {
        // At deuce (10-10 and beyond) service alternates every point
        return if (s1 >= 10 && s2 >= 10) {
            val pointsSinceDeuce = (s1 - 10) + (s2 - 10)
            if (pointsSinceDeuce % 2 == 0) firstServer else otherPlayer(firstServer)
        } else {
            // Before deuce: alternate every 2 points
            val block = total / 2
            if (block % 2 == 0) firstServer else otherPlayer(firstServer)
        }
    }

    private fun otherPlayer(p: Int) = if (p == 1) 2 else 1
}
