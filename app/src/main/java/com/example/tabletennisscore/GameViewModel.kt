package com.example.tabletennisscore

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.tabletennisscore.data.MatchDatabase
import com.example.tabletennisscore.data.MatchResult
import androidx.core.content.edit
import com.google.gson.Gson
import kotlinx.coroutines.launch

/**
 * Holds all game state and enforces table tennis scoring rules.
 *
 * Scoring rules applied:
 * - A set is won when a player reaches 11 points with at least a 2-point lead.
 * - Service alternates every 2 points, except from 10-10 (deuce) where it alternates every point.
 * - The player who did NOT serve first in the previous set serves first in the next set.
 * - The first server of the match is decided externally (defaults to player 1).
 */
class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = MatchDatabase.getInstance(application).matchResultDao()

    companion object {
        const val MAX_PLAYER_NAME_LENGTH = 30
        const val MAX_TOURNAMENT_NAME_LENGTH = 40
        const val MATCH_MODE_SINGLES = "SINGLES"
        const val MATCH_MODE_DOUBLES = "DOUBLES"
        private const val KEY_SINGLES_PLAYER1_NAME = "singles_p1_name"
        private const val KEY_SINGLES_PLAYER2_NAME = "singles_p2_name"
        private const val KEY_DOUBLES_TEAM1_PLAYER_A = "doubles_t1_a"
        private const val KEY_DOUBLES_TEAM1_PLAYER_B = "doubles_t1_b"
        private const val KEY_DOUBLES_TEAM2_PLAYER_A = "doubles_t2_a"
        private const val KEY_DOUBLES_TEAM2_PLAYER_B = "doubles_t2_b"
        private const val KEY_PLAYER1_NAME = "p1_name"
        private const val KEY_PLAYER2_NAME = "p2_name"
        private const val KEY_TOURNAMENT_NAME = "tournament_name"
        private const val KEY_MATCH_IN_PROGRESS = "match_in_progress"
        private const val KEY_MATCH_FIRST_SERVER = "match_first_server"
        private const val KEY_ELAPSED_PLAYED_MS = "elapsed_played_ms"
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
        val pointHistory: List<List<Int>> = listOf(emptyList()), // winner (1 or 2) per point, per set
        val sidesSwapped: Boolean = false, // true when players have physically swapped ends
        val decidingSetFiveSwapDone: Boolean = false,
        val decidingSetSwapNoticeVersion: Int = 0,
        val awaitingDecidingSetSwapConfirmation: Boolean = false,
        val resumeAfterDecidingSetSwapConfirmation: Boolean = false,
        val tournamentName: String = "",
        val matchRound: String = "",
        val matchMode: String = MATCH_MODE_SINGLES,
        val team1PlayerA: String = "",
        val team1PlayerB: String = "",
        val team2PlayerA: String = "",
        val team2PlayerB: String = "",
    )

    private val prefs = application.getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE)
    private val nameStore = PlayerNameStore(prefs)

    private val _state = MutableLiveData(
        GameState(
            player1Name = prefs.getString(
                KEY_SINGLES_PLAYER1_NAME,
                prefs.getString(KEY_PLAYER1_NAME, "Player 1") ?: "Player 1",
            ) ?: "Player 1",
            player2Name = prefs.getString(
                KEY_SINGLES_PLAYER2_NAME,
                prefs.getString(KEY_PLAYER2_NAME, "Player 2") ?: "Player 2",
            ) ?: "Player 2",
            tournamentName = prefs.getString(KEY_TOURNAMENT_NAME, "") ?: "",
            team1PlayerA = prefs.getString(KEY_DOUBLES_TEAM1_PLAYER_A, "") ?: "",
            team1PlayerB = prefs.getString(KEY_DOUBLES_TEAM1_PLAYER_B, "") ?: "",
            team2PlayerA = prefs.getString(KEY_DOUBLES_TEAM2_PLAYER_A, "") ?: "",
            team2PlayerB = prefs.getString(KEY_DOUBLES_TEAM2_PLAYER_B, "") ?: "",
        )
    )
    val state: LiveData<GameState> = _state

    private val _ongoingMatchExists = MutableLiveData<Boolean>(false)
    val ongoingMatchExists: LiveData<Boolean> = _ongoingMatchExists

    var hasRespondedToOngoingMatch = false
        private set

    init {
        if (prefs.contains(KEY_MATCH_IN_PROGRESS)) {
            _ongoingMatchExists.value = true
        }
    }

    fun resumeMatch() {
        val json = prefs.getString(KEY_MATCH_IN_PROGRESS, null) ?: return
        try {
            val savedState = Gson().fromJson(json, GameState::class.java)
            matchFirstServer = prefs.getInt(KEY_MATCH_FIRST_SERVER, 1)
            elapsedPlayedMs = prefs.getLong(KEY_ELAPSED_PLAYED_MS, 0L)

            // When resuming, we default to paused state
            _state.value = savedState.copy(isMatchRunning = false)
            _ongoingMatchExists.value = false
            hasRespondedToOngoingMatch = true
        } catch (e: Exception) {
            clearSavedMatch()
            _ongoingMatchExists.value = false
            hasRespondedToOngoingMatch = true
        }
    }

    fun discardMatch() {
        clearSavedMatch()
        _ongoingMatchExists.value = false
        hasRespondedToOngoingMatch = true
    }

    fun clearSavedMatch() {
        prefs.edit {
            remove(KEY_MATCH_IN_PROGRESS)
            remove(KEY_MATCH_FIRST_SERVER)
            remove(KEY_ELAPSED_PLAYED_MS)
        }
    }

    fun saveMatchInProgress() {
        val s = current
        if (s.hasMatchStarted && s.matchWinner == null) {
            val json = Gson().toJson(s)
            prefs.edit {
                putString(KEY_MATCH_IN_PROGRESS, json)
                putInt(KEY_MATCH_FIRST_SERVER, matchFirstServer)
                putLong(KEY_ELAPSED_PLAYED_MS, getElapsedPlayedMs())
            }
        } else {
            clearSavedMatch()
        }
    }

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

    fun swapSides() {
        if (current.matchWinner != null) return
        pushHistory()
        _state.value = current.copy(sidesSwapped = !current.sidesSwapped)
    }

    fun confirmDecidingSetSideSwapDone() {
        if (!current.awaitingDecidingSetSwapConfirmation) return
        val shouldResume = current.resumeAfterDecidingSetSwapConfirmation && current.matchWinner == null
        if (shouldResume) {
            runningSinceMs = SystemClock.elapsedRealtime()
        }
        _state.value = current.copy(
            awaitingDecidingSetSwapConfirmation = false,
            resumeAfterDecidingSetSwapConfirmation = false,
            isMatchRunning = shouldResume,
        )
    }

    fun addPoint(player: Int) {
        if (!current.isMatchRunning || current.matchWinner != null) return
        pushHistory()
        applyScoreChange(ScoringEngine.addPoint(current, player, matchFirstServer))
        saveMatchInProgress()
    }

    /** Applies what the scoring rules decided: stop the clock, store a finished match, show the new state. */
    private fun applyScoreChange(change: ScoreChange) {
        if (change.stopClock) captureElapsedUntilNow()
        change.finishedMatch?.let { saveMatchResult(it) }
        _state.value = change.state
    }

    fun undo() {
        if (history.isNotEmpty()) {
            _state.value = history.removeLast()
            saveMatchInProgress()
        }
    }

    fun resetMatch() {
        history.clear()
        matchFirstServer = 1
        elapsedPlayedMs = 0L
        runningSinceMs = null
        clearSavedMatch()
        _state.value = GameState(
            bestOfSets = current.bestOfSets,
            player1Name = current.player1Name,
            player2Name = current.player2Name,
            tournamentName = current.tournamentName,
            matchMode = sanitizeMatchMode(current.matchMode),
            team1PlayerA = current.team1PlayerA,
            team1PlayerB = current.team1PlayerB,
            team2PlayerA = current.team2PlayerA,
            team2PlayerB = current.team2PlayerB,
        )
    }

    fun startOrResumeMatch() {
        if (current.matchWinner != null) return
        if (current.awaitingDecidingSetSwapConfirmation) return
        if (!current.isMatchRunning) {
            runningSinceMs = SystemClock.elapsedRealtime()
        }
        _state.value = current.copy(
            isMatchRunning = true,
            hasMatchStarted = true,
        )
        saveMatchInProgress()
    }

    fun pauseMatch() {
        if (current.awaitingDecidingSetSwapConfirmation) return
        if (current.isMatchRunning) {
            captureElapsedUntilNow()
        }
        _state.value = current.copy(isMatchRunning = false)
        saveMatchInProgress()
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
        val edited = ScoringEngine.editCurrentSetScore(current, score1, score2, matchFirstServer) ?: return false
        pushHistory()
        _state.value = edited
        return true
    }

    fun updatePausedMatchScores(setResults: List<Pair<Int, Int>>, currentScore1: Int, currentScore2: Int): Boolean {
        val change = ScoringEngine.editPausedMatchScores(
            current, setResults, currentScore1, currentScore2, matchFirstServer,
        ) ?: return false
        pushHistory()
        applyScoreChange(change)
        return true
    }

    fun setupMatch(
        player1Name: String,
        player2Name: String,
        firstServer: Int,
        bestOfSets: Int,
        matchMode: String = MATCH_MODE_SINGLES,
        team1PlayerA: String = "",
        team1PlayerB: String = "",
        team2PlayerA: String = "",
        team2PlayerB: String = "",
    ) {
        history.clear()
        matchFirstServer = if (firstServer == 2) 2 else 1
        elapsedPlayedMs = 0L
        runningSinceMs = null
        val validatedBestOf = if (bestOfSets in setOf(1, 3, 5, 7)) bestOfSets else 5
        
        val sanitizedMode = sanitizeMatchMode(matchMode)
        val sP1Name: String
        val sP2Name: String
        val sTeam1A: String
        val sTeam1B: String
        val sTeam2A: String
        val sTeam2B: String

        if (sanitizedMode == MATCH_MODE_DOUBLES) {
            sTeam1A = sanitizePlayerName(team1PlayerA, "Player 1A")
            sTeam1B = sanitizePlayerName(team1PlayerB, "Player 1B")
            sTeam2A = sanitizePlayerName(team2PlayerA, "Player 2A")
            sTeam2B = sanitizePlayerName(team2PlayerB, "Player 2B")
            sP1Name = composeDoublesTeamName(sTeam1A, sTeam1B)
            sP2Name = composeDoublesTeamName(sTeam2A, sTeam2B)
        } else {
            sP1Name = sanitizePlayerName(player1Name, "Player 1")
            sP2Name = sanitizePlayerName(player2Name, "Player 2")
            sTeam1A = ""
            sTeam1B = ""
            sTeam2A = ""
            sTeam2B = ""
        }

        prefs.edit {
            if (sanitizedMode == MATCH_MODE_DOUBLES) {
                putString(KEY_DOUBLES_TEAM1_PLAYER_A, sTeam1A)
                putString(KEY_DOUBLES_TEAM1_PLAYER_B, sTeam1B)
                putString(KEY_DOUBLES_TEAM2_PLAYER_A, sTeam2A)
                putString(KEY_DOUBLES_TEAM2_PLAYER_B, sTeam2B)
            } else {
                putString(KEY_SINGLES_PLAYER1_NAME, sP1Name)
                putString(KEY_SINGLES_PLAYER2_NAME, sP2Name)
                // Keep legacy keys updated for backward compatibility with older builds.
                putString(KEY_PLAYER1_NAME, sP1Name)
                putString(KEY_PLAYER2_NAME, sP2Name)
            }
        }
        addPlayerNameToGroup(sP1Name)
        addPlayerNameToGroup(sP2Name)
        if (sanitizedMode == MATCH_MODE_DOUBLES) {
            addPlayerNameToGroup(sTeam1A)
            addPlayerNameToGroup(sTeam1B)
            addPlayerNameToGroup(sTeam2A)
            addPlayerNameToGroup(sTeam2B)
        }

        _state.value = GameState(
            bestOfSets = validatedBestOf,
            server = matchFirstServer,
            player1Name = sP1Name,
            player2Name = sP2Name,
            tournamentName = current.tournamentName,
            matchMode = sanitizedMode,
            team1PlayerA = sTeam1A,
            team1PlayerB = sTeam1B,
            team2PlayerA = sTeam2A,
            team2PlayerB = sTeam2B,
            isMatchRunning = false,
            hasMatchStarted = false,
        )
    }

    fun setPlayerName(player: Int, name: String) {
        val trimmed = sanitizePlayerName(name, if (player == 1) "Player 1" else "Player 2")
        addPlayerNameToGroup(trimmed)
        _state.value = if (player == 1) {
            prefs.edit {
                if (current.matchMode == MATCH_MODE_SINGLES) {
                    putString(KEY_SINGLES_PLAYER1_NAME, trimmed)
                    putString(KEY_PLAYER1_NAME, trimmed)
                }
            }
            current.copy(player1Name = trimmed)
        } else {
            prefs.edit {
                if (current.matchMode == MATCH_MODE_SINGLES) {
                    putString(KEY_SINGLES_PLAYER2_NAME, trimmed)
                    putString(KEY_PLAYER2_NAME, trimmed)
                }
            }
            current.copy(player2Name = trimmed)
        }
    }

    fun setDoublesTeamNames(player: Int, playerA: String, playerB: String) {
        if (current.matchMode != MATCH_MODE_DOUBLES) return
        val (fallbackA, fallbackB) = if (player == 1) {
            "Player 1A" to "Player 1B"
        } else {
            "Player 2A" to "Player 2B"
        }
        val sanitizedA = sanitizePlayerName(playerA, fallbackA)
        val sanitizedB = sanitizePlayerName(playerB, fallbackB)
        val composedTeamName = composeDoublesTeamName(sanitizedA, sanitizedB)

        addPlayerNameToGroup(sanitizedA)
        addPlayerNameToGroup(sanitizedB)
        addPlayerNameToGroup(composedTeamName)

        _state.value = if (player == 1) {
            prefs.edit {
                putString(KEY_DOUBLES_TEAM1_PLAYER_A, sanitizedA)
                putString(KEY_DOUBLES_TEAM1_PLAYER_B, sanitizedB)
            }
            current.copy(
                player1Name = composedTeamName,
                team1PlayerA = sanitizedA,
                team1PlayerB = sanitizedB,
            )
        } else {
            prefs.edit {
                putString(KEY_DOUBLES_TEAM2_PLAYER_A, sanitizedA)
                putString(KEY_DOUBLES_TEAM2_PLAYER_B, sanitizedB)
            }
            current.copy(
                player2Name = composedTeamName,
                team2PlayerA = sanitizedA,
                team2PlayerB = sanitizedB,
            )
        }
    }

    fun getPlayerNameGroup(): List<String> {
        return nameStore.load(currentPlayerNames())
    }

    fun setPlayerNameGroup(names: List<String>) {
        nameStore.replaceAll(names)
    }

    fun addPlayerNameToGroup(name: String) {
        nameStore.add(name, currentPlayerNames())
    }

    private fun currentPlayerNames() = listOf(current.player1Name, current.player2Name)

    fun setTournamentName(name: String) {
        val sanitized = sanitizeTournamentName(name)
        prefs.edit { putString(KEY_TOURNAMENT_NAME, sanitized) }
        _state.value = current.copy(tournamentName = sanitized)
    }

    fun setMatchRound(round: String) {
        _state.value = current.copy(matchRound = round)
    }

    /** Persists the finished match to the database. The played time must already be captured. */
    private fun saveMatchResult(match: FinishedMatch) {
        val durationMs = elapsedPlayedMs
        val setResultsJson = match.setResults.joinToString(",") { "${it.first}-${it.second}" }
        val pointHistoryJson = match.pointHistory.joinToString(",") { setPoints ->
            setPoints.joinToString("")
        }
        viewModelScope.launch {
            dao.insert(
                MatchResult(
                    tournamentName = sanitizeTournamentName(match.tournamentName),
                    player1Name = match.player1Name,
                    player2Name = match.player2Name,
                    matchMode = sanitizeMatchMode(match.matchMode),
                    sets1 = match.sets1,
                    sets2 = match.sets2,
                    winner = match.winner,
                    bestOfSets = match.bestOfSets,
                    durationMs = durationMs,
                    setResultsJson = setResultsJson,
                    pointHistoryJson = pointHistoryJson,
                    matchFirstServer = match.matchFirstServer,
                    matchRound = match.matchRound,
                )
            )
        }
    }

    private fun pushHistory() {
        if (history.size >= 50) history.removeFirst()
        history.addLast(current)
    }


    private fun captureElapsedUntilNow() {
        val startedAt = runningSinceMs ?: return
        elapsedPlayedMs += SystemClock.elapsedRealtime() - startedAt
        runningSinceMs = null
    }
}
