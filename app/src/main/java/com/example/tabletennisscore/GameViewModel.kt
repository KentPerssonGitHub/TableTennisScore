package com.example.tabletennisscore

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

    data class GameState(
        val score1: Int = 0,
        val score2: Int = 0,
        val sets1: Int = 0,
        val sets2: Int = 0,
        val server: Int = 1,         // 1 or 2
        val player1Name: String = "Player 1",
        val player2Name: String = "Player 2",
    )

    private val _state = MutableLiveData(GameState())
    val state: LiveData<GameState> = _state

    // History stack for undo support (max 50 entries)
    private val history = ArrayDeque<GameState>(50)

    private val current get() = _state.value!!

    // Who served first in the current set (to know who serves first in the next)
    private var setFirstServer: Int = 1

    fun addPoint(player: Int) {
        pushHistory()
        val s = current
        var score1 = s.score1
        var score2 = s.score2
        var sets1 = s.sets1
        var sets2 = s.sets2
        if (player == 1) score1++ else score2++

        val totalPoints = score1 + score2
        var server = nextServer(score1, score2, totalPoints, setFirstServer)

        // Check if set is won
        if (isSetWon(score1, score2)) {
            if (player == 1) sets1++ else sets2++
            // Next set: the player who did NOT serve first in this set serves first
            setFirstServer = if (setFirstServer == 1) 2 else 1
            score1 = 0
            score2 = 0
            server = setFirstServer
        }

        _state.value = s.copy(
            score1 = score1,
            score2 = score2,
            sets1 = sets1,
            sets2 = sets2,
            server = server,
        )
    }

    fun undo() {
        if (history.isNotEmpty()) {
            _state.value = history.removeLast()
        }
    }

    fun resetMatch() {
        history.clear()
        setFirstServer = 1
        _state.value = GameState(
            player1Name = current.player1Name,
            player2Name = current.player2Name,
        )
    }

    fun setPlayerName(player: Int, name: String) {
        val trimmed = name.trim().ifEmpty { if (player == 1) "Player 1" else "Player 2" }
        _state.value = if (player == 1) current.copy(player1Name = trimmed)
                       else current.copy(player2Name = trimmed)
    }

    private fun pushHistory() {
        if (history.size >= 50) history.removeFirst()
        history.addLast(current)
    }

    private fun isSetWon(s1: Int, s2: Int): Boolean {
        return (s1 >= 11 || s2 >= 11) && Math.abs(s1 - s2) >= 2
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
