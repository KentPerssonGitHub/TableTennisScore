package com.example.tabletennisscore.history

import com.example.tabletennisscore.GameViewModel
import com.example.tabletennisscore.GameViewModel.GameState
import com.example.tabletennisscore.ScoringEngine
import com.example.tabletennisscore.data.MatchResult
import com.example.tabletennisscore.isSetWon

private const val TEAM_SEPARATOR = " / "

/** One point of a replayed match: the scoreboard before it, who won it, and the scoreboard after it. */
data class ReplayPoint(val before: GameState, val winner: Int, val after: GameState) {
    val server: Int get() = before.server
}

/**
 * Whether [result] has a complete point-by-point history: one entry per played set, each with exactly the
 * points of that set's score. Cheap enough to call while binding a history row.
 */
fun canReplay(result: MatchResult): Boolean {
    val setResults = parseSetResults(result.setResultsJson)
    if (setResults.isEmpty()) return false
    // Not filtered: an empty entry is a set without points and must keep the sets lined up.
    val setsPoints = result.pointHistoryJson.split(",")
    if (setsPoints.size != setResults.size) return false
    return setResults.zip(setsPoints).all { (score, points) ->
        isSetWon(score.first, score.second) &&
            points.length == score.first + score.second &&
            points.count { it == '1' } == score.first &&
            points.count { it == '2' } == score.second
    }
}

/**
 * Plays [result]'s recorded points through the real scoring rules, so serve order and side switches are
 * exactly as in the match. Nothing is stored. Null when the match can't be replayed (see [canReplay]) or
 * the replay doesn't end in the stored result.
 */
fun replayPoints(result: MatchResult): List<ReplayPoint>? {
    if (!canReplay(result)) return null

    var state = startState(result)
    val points = mutableListOf<ReplayPoint>()
    for (winner in result.pointHistoryJson.filter { it == '1' || it == '2' }.map { it - '0' }) {
        if (state.matchWinner != null) return null
        var after = ScoringEngine.addPoint(state, winner, result.matchFirstServer).state
        // The deciding-set side switch at 5 needs no confirmation in a replay: play just goes on.
        if (after.awaitingDecidingSetSwapConfirmation) {
            after = after.copy(
                awaitingDecidingSetSwapConfirmation = false,
                resumeAfterDecidingSetSwapConfirmation = false,
                isMatchRunning = after.matchWinner == null,
            )
        }
        points += ReplayPoint(state, winner, after)
        state = after
    }

    val matchesStored = state.matchWinner == result.winner &&
        state.sets1 == result.sets1 &&
        state.sets2 == result.sets2 &&
        state.setResults == parseSetResults(result.setResultsJson)
    return points.takeIf { matchesStored }
}

private fun startState(result: MatchResult): GameState {
    val isDoubles = result.matchMode == GameViewModel.MATCH_MODE_DOUBLES
    val (team1A, team1B) = splitTeam(result.player1Name, isDoubles)
    val (team2A, team2B) = splitTeam(result.player2Name, isDoubles)
    return GameState(
        bestOfSets = result.bestOfSets,
        server = result.matchFirstServer,
        player1Name = result.player1Name,
        player2Name = result.player2Name,
        isMatchRunning = true,
        hasMatchStarted = true,
        tournamentName = result.tournamentName,
        matchRound = result.matchRound,
        matchMode = if (isDoubles) GameViewModel.MATCH_MODE_DOUBLES else GameViewModel.MATCH_MODE_SINGLES,
        team1PlayerA = team1A,
        team1PlayerB = team1B,
        team2PlayerA = team2A,
        team2PlayerB = team2B,
    )
}

/** A doubles team name "A / B" as its two players; empty for singles. */
private fun splitTeam(teamName: String, isDoubles: Boolean): Pair<String, String> {
    if (!isDoubles) return "" to ""
    val parts = teamName.split(TEAM_SEPARATOR, limit = 2)
    return parts[0] to (parts.getOrNull(1) ?: "")
}

/** "11-7,9-11" as a list of set scores; tokens that aren't a score are skipped. */
internal fun parseSetResults(setResultsJson: String): List<Pair<Int, Int>> {
    if (setResultsJson.isBlank()) return emptyList()
    return setResultsJson.split(",").mapNotNull { token ->
        val parts = token.trim().split("-")
        val first = parts.getOrNull(0)?.trim()?.toIntOrNull()
        val second = parts.getOrNull(1)?.trim()?.toIntOrNull()
        if (first != null && second != null) first to second else null
    }
}
