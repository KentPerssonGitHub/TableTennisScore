package com.example.tabletennisscore

import com.example.tabletennisscore.GameViewModel.GameState

/** A match that has just been decided and should be stored in the history. */
data class FinishedMatch(
    val tournamentName: String,
    val player1Name: String,
    val player2Name: String,
    val matchMode: String,
    val sets1: Int,
    val sets2: Int,
    val winner: Int,
    val bestOfSets: Int,
    val setResults: List<Pair<Int, Int>>,
    val pointHistory: List<List<Int>>,
    val matchFirstServer: Int,
    val matchRound: String,
)

/**
 * The outcome of a score change: the [state] to show next, plus what the caller must do besides showing it.
 *
 * @property stopClock true when the played-time clock must stop (deciding-set side swap or match end).
 * @property finishedMatch set when the change decided the match, so it can be stored.
 */
class ScoreChange(
    val state: GameState,
    val stopClock: Boolean = false,
    val finishedMatch: FinishedMatch? = null,
)

/**
 * The table-tennis match rules as pure functions on [GameState]: no Android, no clock, no storage.
 * [GameViewModel] applies the returned [ScoreChange]s.
 */
object ScoringEngine {

    // ----- A point is scored -----

    /** The change after [player] wins a point in [s], including a possible end of set or match. */
    fun addPoint(s: GameState, player: Int, matchFirstServer: Int): ScoreChange {
        var next = recordPoint(s, player)
        val swappedAtFive = shouldSwapAtFive(s, next)
        if (swappedAtFive) next = swapSidesAtFive(next)
        next = updateServerAfterPoint(s, next, matchFirstServer)

        if (!isSetWon(next.score1, next.score2)) {
            return ScoreChange(next, stopClock = swappedAtFive)
        }
        return finishSet(s, next, player, matchFirstServer, stopClockAlready = swappedAtFive)
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
    private fun shouldSwapAtFive(s: GameState, next: GameState): Boolean {
        return isDecidingSet(next.sets1, next.sets2, s.bestOfSets) &&
            !next.decidingSetFiveSwapDone &&
            (next.score1 >= 5 || next.score2 >= 5)
    }

    private fun swapSidesAtFive(next: GameState): GameState = next.copy(
        sidesSwapped = !next.sidesSwapped,
        decidingSetFiveSwapDone = true,
        decidingSetSwapNoticeVersion = next.decidingSetSwapNoticeVersion + 1,
        isMatchRunning = false,
        awaitingDecidingSetSwapConfirmation = true,
        resumeAfterDecidingSetSwapConfirmation = true,
    )

    /** Sets the next server from the new score and, in doubles, rotates a team whose service turn ended. */
    private fun updateServerAfterPoint(s: GameState, next: GameState, matchFirstServer: Int): GameState {
        val totalPoints = next.score1 + next.score2
        val firstServer = firstServerOfSet(matchFirstServer, s.setResults.size)
        val server = nextServer(next.score1, next.score2, totalPoints, firstServer)
        val updated = next.copy(server = server)
        if (s.matchMode != GameViewModel.MATCH_MODE_DOUBLES || server == s.server) return updated

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
    private fun finishSet(
        s: GameState,
        next: GameState,
        player: Int,
        matchFirstServer: Int,
        stopClockAlready: Boolean,
    ): ScoreChange {
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
            val nextSet = afterSet.copy(
                sidesSwapped = !afterSet.sidesSwapped, // players switch ends after each set
                server = firstServerOfSet(matchFirstServer, setResults.size),
                pointHistory = afterSet.pointHistory + listOf(emptyList()),
            )
            return ScoreChange(nextSet, stopClock = stopClockAlready)
        }

        // Match over: do NOT swap sides.
        val finished = FinishedMatch(
            tournamentName = s.tournamentName,
            player1Name = afterSet.player1Name,
            player2Name = afterSet.player2Name,
            matchMode = s.matchMode,
            sets1 = sets1,
            sets2 = sets2,
            winner = player,
            bestOfSets = s.bestOfSets,
            setResults = setResults,
            pointHistory = afterSet.pointHistory,
            matchFirstServer = matchFirstServer,
            matchRound = s.matchRound,
        )
        return ScoreChange(
            state = afterSet.copy(matchWinner = player, isMatchRunning = false),
            stopClock = true,
            finishedMatch = finished,
        )
    }

    // ----- Manual score edits while the match is paused -----

    fun canEditPausedScores(s: GameState): Boolean {
        return !s.isMatchRunning && s.matchWinner == null && s.hasMatchStarted
    }

    /** The state with the current set's score edited to [score1]-[score2], or null when that is not allowed. */
    fun editCurrentSetScore(s: GameState, score1: Int, score2: Int, matchFirstServer: Int): GameState? {
        if (!canEditPausedScores(s)) return null
        if (score1 < 0 || score2 < 0) return null
        if (isSetWon(score1, score2)) return null

        val firstServer = firstServerOfSet(matchFirstServer, s.setResults.size)
        val server = nextServer(score1, score2, score1 + score2, firstServer)
        val editedDoubles = recalculateDoublesOrderForCurrentSetEdit(
            state = s,
            editedScore1 = score1,
            editedScore2 = score2,
            editedCompletedSetCount = s.setResults.size,
            matchFirstServer = matchFirstServer,
        )
        return s.withDoublesOrder(editedDoubles).copy(
            score1 = score1,
            score2 = score2,
            server = server,
        )
    }

    /**
     * The change after editing all set results and the current score, or null when the input is not allowed
     * (a set result that is not a finished set, a negative score, or sets that would already decide the match).
     */
    fun editPausedMatchScores(
        s: GameState,
        setResults: List<Pair<Int, Int>>,
        currentScore1: Int,
        currentScore2: Int,
        matchFirstServer: Int,
    ): ScoreChange? {
        if (!canEditPausedScores(s)) return null
        if (currentScore1 < 0 || currentScore2 < 0) return null

        val (sets1, sets2) = countSetWins(setResults) ?: return null
        if (isMatchWon(sets1, sets2, s.bestOfSets)) return null

        val editedDoubles = recalculateDoublesOrderForCurrentSetEdit(
            state = s,
            editedScore1 = currentScore1,
            editedScore2 = currentScore2,
            editedCompletedSetCount = setResults.size,
            matchFirstServer = matchFirstServer,
        )
        val base = s.withDoublesOrder(editedDoubles)
        return if (isSetWon(currentScore1, currentScore2)) {
            editedSetFinished(base, setResults, sets1, sets2, currentScore1, currentScore2, matchFirstServer)
        } else {
            ScoreChange(
                editedSetInProgress(base, setResults, sets1, sets2, currentScore1, currentScore2, matchFirstServer),
            )
        }
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
    private fun editedSetFinished(
        base: GameState,
        setResults: List<Pair<Int, Int>>,
        sets1: Int,
        sets2: Int,
        currentScore1: Int,
        currentScore2: Int,
        matchFirstServer: Int,
    ): ScoreChange {
        val setWinner = if (currentScore1 > currentScore2) 1 else 2
        val finalSets1 = sets1 + if (setWinner == 1) 1 else 0
        val finalSets2 = sets2 + if (setWinner == 2) 1 else 0
        val finalSetResults = setResults + listOf(currentScore1 to currentScore2)
        val matchWinner = if (isMatchWon(finalSets1, finalSets2, base.bestOfSets)) setWinner else null

        // For manual score updates, we can't reliably reconstruct point history,
        // so it is represented as empty for the edited sets.
        val finalPointHistory = finalSetResults.map { emptyList<Int>() }

        val finished = matchWinner?.let {
            FinishedMatch(
                tournamentName = base.tournamentName,
                player1Name = base.player1Name,
                player2Name = base.player2Name,
                matchMode = base.matchMode,
                sets1 = finalSets1,
                sets2 = finalSets2,
                winner = it,
                bestOfSets = base.bestOfSets,
                setResults = finalSetResults,
                pointHistory = finalPointHistory,
                matchFirstServer = matchFirstServer,
                matchRound = base.matchRound,
            )
        }
        val state = base.copy(
            score1 = 0,
            score2 = 0,
            sets1 = finalSets1,
            sets2 = finalSets2,
            setResults = finalSetResults,
            pointHistory = if (matchWinner == null) finalPointHistory + listOf(emptyList()) else finalPointHistory,
            server = firstServerOfSet(matchFirstServer, finalSetResults.size),
            isMatchRunning = false,
            matchWinner = matchWinner,
            // Only swap sides if the match is not over
            sidesSwapped = if (matchWinner != null) base.sidesSwapped else !base.sidesSwapped,
            decidingSetFiveSwapDone = false,
            awaitingDecidingSetSwapConfirmation = false,
            resumeAfterDecidingSetSwapConfirmation = false,
        )
        return ScoreChange(state, finishedMatch = finished)
    }

    /** The edited current-set score is still an unfinished set. */
    private fun editedSetInProgress(
        base: GameState,
        setResults: List<Pair<Int, Int>>,
        sets1: Int,
        sets2: Int,
        currentScore1: Int,
        currentScore2: Int,
        matchFirstServer: Int,
    ): GameState {
        val firstServer = firstServerOfSet(matchFirstServer, setResults.size)
        val server = nextServer(currentScore1, currentScore2, currentScore1 + currentScore2, firstServer)
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
}
