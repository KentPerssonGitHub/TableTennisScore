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
import java.util.Locale

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
        private const val PREFS_NAME = "table_tennis_prefs"
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

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
        _state.value = applyPoint(current, player)
        saveMatchInProgress()
    }

    /** The state after [player] wins a point in state [s], including a possible end of set or match. */
    private fun applyPoint(s: GameState, player: Int): GameState {
        var next = recordPoint(s, player)
        next = swapSidesAtFiveInDecidingSet(s, next)
        next = updateServerAfterPoint(s, next)
        if (isSetWon(next.score1, next.score2)) {
            next = finishSet(s, next, player)
        }
        return next
    }

    private fun recordPoint(s: GameState, player: Int): GameState {
        val pointHistory = s.pointHistory.ifEmpty { listOf(emptyList()) }
        return s.copy(
            score1 = if (player == 1) s.score1 + 1 else s.score1,
            score2 = if (player == 1) s.score2 else s.score2 + 1,
            pointHistory = pointHistory.dropLast(1) + listOf(pointHistory.last() + player),
        )
    }

    /** In a deciding set the players switch ends once, when the first player reaches 5 points. */
    private fun swapSidesAtFiveInDecidingSet(s: GameState, next: GameState): GameState {
        val shouldSwap = isDecidingSet(next.sets1, next.sets2, s.bestOfSets) &&
            !next.decidingSetFiveSwapDone &&
            (next.score1 >= 5 || next.score2 >= 5)
        if (!shouldSwap) return next

        captureElapsedUntilNow()
        return next.copy(
            sidesSwapped = !next.sidesSwapped,
            decidingSetFiveSwapDone = true,
            decidingSetSwapNoticeVersion = next.decidingSetSwapNoticeVersion + 1,
            isMatchRunning = false,
            awaitingDecidingSetSwapConfirmation = true,
            resumeAfterDecidingSetSwapConfirmation = true,
        )
    }

    /** Sets the next server from the new score and, in doubles, rotates a team whose service turn ended. */
    private fun updateServerAfterPoint(s: GameState, next: GameState): GameState {
        val totalPoints = next.score1 + next.score2
        val server = nextServer(next.score1, next.score2, totalPoints, currentSetFirstServer(s.setResults.size))
        val updated = next.copy(server = server)
        if (s.matchMode != MATCH_MODE_DOUBLES || server == s.server) return updated

        return if (s.server == 1) {
            updated.copy(
                team1PlayerA = updated.team1PlayerB,
                team1PlayerB = updated.team1PlayerA,
                player1Name = composeDoublesTeamName(updated.team1PlayerB, updated.team1PlayerA),
            )
        } else {
            updated.copy(
                team2PlayerA = updated.team2PlayerB,
                team2PlayerB = updated.team2PlayerA,
                player2Name = composeDoublesTeamName(updated.team2PlayerB, updated.team2PlayerA),
            )
        }
    }

    /** Records the finished set. Ends the match, or switches ends and starts the next set. */
    private fun finishSet(s: GameState, next: GameState, player: Int): GameState {
        val setResults = next.setResults + (next.score1 to next.score2)
        val sets1 = next.sets1 + if (player == 1) 1 else 0
        val sets2 = next.sets2 + if (player == 1) 0 else 1
        val afterSet = next.copy(
            score1 = 0,
            score2 = 0,
            sets1 = sets1,
            sets2 = sets2,
            setResults = setResults,
            decidingSetFiveSwapDone = false,
            awaitingDecidingSetSwapConfirmation = false,
            resumeAfterDecidingSetSwapConfirmation = false,
        )

        if (!isMatchWon(sets1, sets2, s.bestOfSets)) {
            return afterSet.copy(
                sidesSwapped = !afterSet.sidesSwapped, // players switch ends after each set
                server = currentSetFirstServer(setResults.size),
                pointHistory = afterSet.pointHistory + listOf(emptyList()),
            )
        }

        // Match over: do NOT swap sides.
        captureElapsedUntilNow()
        saveMatchResult(
            tournamentName = s.tournamentName,
            player1Name = afterSet.player1Name,
            player2Name = afterSet.player2Name,
            matchMode = sanitizeMatchMode(s.matchMode),
            sets1 = sets1,
            sets2 = sets2,
            winner = player,
            bestOfSets = s.bestOfSets,
            setResults = setResults,
            pointHistory = afterSet.pointHistory,
            matchFirstServer = matchFirstServer,
            matchRound = s.matchRound,
        )
        return afterSet.copy(matchWinner = player, isMatchRunning = false)
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
        if (!canEditPausedScores()) return false
        if (score1 < 0 || score2 < 0) return false
        if (isSetWon(score1, score2)) return false

        pushHistory()
        val server = nextServer(score1, score2, score1 + score2, currentSetFirstServer(current.setResults.size))
        val editedDoubles = recalculateDoublesOrderForCurrentSetEdit(
            state = current,
            editedScore1 = score1,
            editedScore2 = score2,
            editedCompletedSetCount = current.setResults.size,
            matchFirstServer = matchFirstServer,
        )
        _state.value = current.withDoublesOrder(editedDoubles).copy(
            score1 = score1,
            score2 = score2,
            server = server,
        )
        return true
    }

    fun updatePausedMatchScores(setResults: List<Pair<Int, Int>>, currentScore1: Int, currentScore2: Int): Boolean {
        if (!canEditPausedScores()) return false
        if (currentScore1 < 0 || currentScore2 < 0) return false

        val (sets1, sets2) = countSetWins(setResults) ?: return false
        if (isMatchWon(sets1, sets2, current.bestOfSets)) return false

        pushHistory()
        val editedDoubles = recalculateDoublesOrderForCurrentSetEdit(
            state = current,
            editedScore1 = currentScore1,
            editedScore2 = currentScore2,
            editedCompletedSetCount = setResults.size,
            matchFirstServer = matchFirstServer,
        )
        val base = current.withDoublesOrder(editedDoubles)
        _state.value = if (isSetWon(currentScore1, currentScore2)) {
            withEditedSetFinished(base, setResults, sets1, sets2, currentScore1, currentScore2)
        } else {
            withEditedSetInProgress(base, setResults, sets1, sets2, currentScore1, currentScore2)
        }
        return true
    }

    private fun canEditPausedScores(): Boolean {
        return !current.isMatchRunning && current.matchWinner == null && current.hasMatchStarted
    }

    /** Number of sets won by each player, or null when any entered set is not a valid finished set. */
    private fun countSetWins(setResults: List<Pair<Int, Int>>): Pair<Int, Int>? {
        var sets1 = 0
        var sets2 = 0
        for ((set1, set2) in setResults) {
            if (set1 < 0 || set2 < 0) return null
            if (!isSetWon(set1, set2)) return null
            if (set1 > set2) sets1++ else sets2++
        }
        return sets1 to sets2
    }

    private fun GameState.withDoublesOrder(order: DoublesOrder?): GameState {
        if (order == null) return this
        return copy(
            player1Name = order.player1Name,
            player2Name = order.player2Name,
            team1PlayerA = order.team1PlayerA,
            team1PlayerB = order.team1PlayerB,
            team2PlayerA = order.team2PlayerA,
            team2PlayerB = order.team2PlayerB,
        )
    }

    /** The edited current-set score is itself a finished set: finalize it (and the match, if that decides it). */
    private fun withEditedSetFinished(
        base: GameState,
        setResults: List<Pair<Int, Int>>,
        sets1: Int,
        sets2: Int,
        currentScore1: Int,
        currentScore2: Int,
    ): GameState {
        val setWinner = if (currentScore1 > currentScore2) 1 else 2
        val finalSets1 = sets1 + if (setWinner == 1) 1 else 0
        val finalSets2 = sets2 + if (setWinner == 2) 1 else 0
        val finalSetResults = setResults + listOf(currentScore1 to currentScore2)
        val matchWinner = if (isMatchWon(finalSets1, finalSets2, base.bestOfSets)) setWinner else null

        // For manual score updates, we can't reliably reconstruct point history,
        // so it is represented as empty for the edited sets.
        val finalPointHistory = finalSetResults.map { emptyList<Int>() }

        if (matchWinner != null) {
            saveMatchResult(
                tournamentName = base.tournamentName,
                player1Name = base.player1Name,
                player2Name = base.player2Name,
                matchMode = sanitizeMatchMode(base.matchMode),
                sets1 = finalSets1,
                sets2 = finalSets2,
                winner = matchWinner,
                bestOfSets = base.bestOfSets,
                setResults = finalSetResults,
                pointHistory = finalPointHistory,
                matchFirstServer = matchFirstServer,
                matchRound = base.matchRound,
            )
        }
        return base.copy(
            score1 = 0,
            score2 = 0,
            sets1 = finalSets1,
            sets2 = finalSets2,
            setResults = finalSetResults,
            pointHistory = if (matchWinner == null) finalPointHistory + listOf(emptyList()) else finalPointHistory,
            server = currentSetFirstServer(finalSetResults.size),
            isMatchRunning = false,
            matchWinner = matchWinner,
            // Only swap sides if the match is not over
            sidesSwapped = if (matchWinner != null) base.sidesSwapped else !base.sidesSwapped,
            decidingSetFiveSwapDone = false,
            awaitingDecidingSetSwapConfirmation = false,
            resumeAfterDecidingSetSwapConfirmation = false,
        )
    }

    /** The edited current-set score is still an unfinished set. */
    private fun withEditedSetInProgress(
        base: GameState,
        setResults: List<Pair<Int, Int>>,
        sets1: Int,
        sets2: Int,
        currentScore1: Int,
        currentScore2: Int,
    ): GameState {
        val server = nextServer(
            currentScore1,
            currentScore2,
            currentScore1 + currentScore2,
            currentSetFirstServer(setResults.size),
        )
        val reachedFiveInDecidingSet = isDecidingSet(sets1, sets2, base.bestOfSets) &&
            (currentScore1 >= 5 || currentScore2 >= 5)
        val shouldSwapAtFive = reachedFiveInDecidingSet && !base.decidingSetFiveSwapDone

        // Manual edit: clear point history for reconstructed sets
        val manualPointHistory = setResults.map { emptyList<Int>() } + listOf(emptyList())

        return base.copy(
            score1 = currentScore1,
            score2 = currentScore2,
            sets1 = sets1,
            sets2 = sets2,
            setResults = setResults,
            pointHistory = manualPointHistory,
            server = server,
            sidesSwapped = if (shouldSwapAtFive) !base.sidesSwapped else base.sidesSwapped,
            decidingSetFiveSwapDone = reachedFiveInDecidingSet,
            decidingSetSwapNoticeVersion = base.decidingSetSwapNoticeVersion + if (shouldSwapAtFive) 1 else 0,
            awaitingDecidingSetSwapConfirmation = shouldSwapAtFive,
            resumeAfterDecidingSetSwapConfirmation = false,
        )
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

    private fun sanitizeMatchMode(value: String?): String {
        return when (value?.trim()?.uppercase(Locale.ROOT)) {
            MATCH_MODE_DOUBLES -> MATCH_MODE_DOUBLES
            else -> MATCH_MODE_SINGLES
        }
    }

    /** Persists the finished match to the database. Call after [captureElapsedUntilNow]. */
    private fun saveMatchResult(
        tournamentName: String,
        player1Name: String,
        player2Name: String,
        matchMode: String,
        sets1: Int,
        sets2: Int,
        winner: Int,
        bestOfSets: Int,
        setResults: List<Pair<Int, Int>>,
        pointHistory: List<List<Int>>,
        matchFirstServer: Int,
        matchRound: String,
    ) {
        val durationMs = elapsedPlayedMs
        val setResultsJson = setResults.joinToString(",") { "${it.first}-${it.second}" }
        val pointHistoryJson = pointHistory.joinToString(",") { setPoints ->
            setPoints.joinToString("")
        }
        viewModelScope.launch {
            dao.insert(
                MatchResult(
                    tournamentName = sanitizeTournamentName(tournamentName),
                    player1Name = player1Name,
                    player2Name = player2Name,
                    matchMode = sanitizeMatchMode(matchMode),
                    sets1 = sets1,
                    sets2 = sets2,
                    winner = winner,
                    bestOfSets = bestOfSets,
                    durationMs = durationMs,
                    setResultsJson = setResultsJson,
                    pointHistoryJson = pointHistoryJson,
                    matchFirstServer = matchFirstServer,
                    matchRound = matchRound,
                )
            )
        }
    }

    private fun pushHistory() {
        if (history.size >= 50) history.removeFirst()
        history.addLast(current)
    }

    private fun currentSetFirstServer(completedSetCount: Int): Int {
        return firstServerOfSet(matchFirstServer, completedSetCount)
    }

    private fun captureElapsedUntilNow() {
        val startedAt = runningSinceMs ?: return
        elapsedPlayedMs += SystemClock.elapsedRealtime() - startedAt
        runningSinceMs = null
    }
}
