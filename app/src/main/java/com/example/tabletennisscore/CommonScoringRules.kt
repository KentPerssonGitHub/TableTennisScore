package com.example.tabletennisscore

import kotlin.math.abs

/** Shared table-tennis scoring helpers used by both game logic and detail screens. */
fun nextServer(s1: Int, s2: Int, total: Int, firstServer: Int): Int {
    // At deuce (10-10 and beyond) service alternates every point.
    return if (s1 >= 10 && s2 >= 10) {
        val pointsSinceDeuce = (s1 - 10) + (s2 - 10)
        if (pointsSinceDeuce % 2 == 0) firstServer else otherPlayer(firstServer)
    } else {
        // Before deuce, service alternates every two points.
        val block = total / 2
        if (block % 2 == 0) firstServer else otherPlayer(firstServer)
    }
}

fun otherPlayer(p: Int): Int = if (p == 1) 2 else 1

/** The player who serves first in a set: the match's first server, alternating every completed set. */
fun firstServerOfSet(matchFirstServer: Int, completedSetCount: Int): Int {
    return if (completedSetCount % 2 == 0) matchFirstServer else otherPlayer(matchFirstServer)
}

/** A set is won at 11 points or more with at least a 2-point lead. */
fun isSetWon(s1: Int, s2: Int): Boolean {
    return (s1 >= 11 || s2 >= 11) && abs(s1 - s2) >= 2
}

fun isMatchWon(sets1: Int, sets2: Int, bestOfSets: Int): Boolean {
    val setsToWin = (bestOfSets / 2) + 1
    return sets1 >= setsToWin || sets2 >= setsToWin
}

/** The deciding set is only special (side swap at 5 points) in best-of-5 or longer matches. */
fun isDecidingSet(sets1: Int, sets2: Int, bestOfSets: Int): Boolean {
    if (bestOfSets < 5) return false
    val setsToWin = (bestOfSets / 2) + 1
    return sets1 == setsToWin - 1 && sets2 == setsToWin - 1
}

fun composeDoublesTeamName(playerA: String, playerB: String): String {
    return "$playerA / $playerB"
}

/** How many service turns each team has completed in a set at the given score. */
fun countCompletedServiceTurnsByTeam(score1: Int, score2: Int, firstServer: Int): Pair<Int, Int> {
    val totalPoints = score1 + score2
    if (totalPoints <= 0) return 0 to 0

    var previousServer = firstServer
    var team1ServiceTurns = 0
    var team2ServiceTurns = 0
    val isDeucePhase = score1 >= 10 && score2 >= 10

    for (pointIndex in 1..totalPoints) {
        val simulatedScore1: Int
        val simulatedScore2: Int
        if (isDeucePhase && pointIndex >= 20) {
            simulatedScore1 = 10 + (pointIndex - 20)
            simulatedScore2 = 10
        } else {
            simulatedScore1 = 0
            simulatedScore2 = pointIndex
        }

        val currentServer = nextServer(
            s1 = simulatedScore1,
            s2 = simulatedScore2,
            total = pointIndex,
            firstServer = firstServer,
        )

        if (currentServer != previousServer) {
            if (previousServer == 1) team1ServiceTurns++ else team2ServiceTurns++
        }
        previousServer = currentServer
    }

    return team1ServiceTurns to team2ServiceTurns
}

/** The in-team server order (who is "A" and who is "B") of both doubles teams. */
data class DoublesOrder(
    val team1PlayerA: String,
    val team1PlayerB: String,
    val team2PlayerA: String,
    val team2PlayerB: String,
    val player1Name: String,
    val player2Name: String,
)

/**
 * Works out the doubles server order that matches an edited score in the current set. Returns null when
 * the match is not doubles or the number of completed sets changed (the order can't be reconstructed then).
 */
fun recalculateDoublesOrderForCurrentSetEdit(
    state: GameViewModel.GameState,
    editedScore1: Int,
    editedScore2: Int,
    editedCompletedSetCount: Int,
    matchFirstServer: Int,
): DoublesOrder? {
    if (state.matchMode != GameViewModel.MATCH_MODE_DOUBLES) return null
    if (editedCompletedSetCount != state.setResults.size) return null

    val firstServer = firstServerOfSet(matchFirstServer, editedCompletedSetCount)
    val (oldTeam1ServiceTurns, oldTeam2ServiceTurns) = countCompletedServiceTurnsByTeam(
        score1 = state.score1,
        score2 = state.score2,
        firstServer = firstServer,
    )

    var setStartTeam1A = state.team1PlayerA
    var setStartTeam1B = state.team1PlayerB
    var setStartTeam2A = state.team2PlayerA
    var setStartTeam2B = state.team2PlayerB

    if (oldTeam1ServiceTurns % 2 != 0) {
        setStartTeam1A = state.team1PlayerB
        setStartTeam1B = state.team1PlayerA
    }
    if (oldTeam2ServiceTurns % 2 != 0) {
        setStartTeam2A = state.team2PlayerB
        setStartTeam2B = state.team2PlayerA
    }

    val (newTeam1ServiceTurns, newTeam2ServiceTurns) = countCompletedServiceTurnsByTeam(
        score1 = editedScore1,
        score2 = editedScore2,
        firstServer = firstServer,
    )

    var editedTeam1A = setStartTeam1A
    var editedTeam1B = setStartTeam1B
    var editedTeam2A = setStartTeam2A
    var editedTeam2B = setStartTeam2B

    if (newTeam1ServiceTurns % 2 != 0) {
        editedTeam1A = setStartTeam1B
        editedTeam1B = setStartTeam1A
    }
    if (newTeam2ServiceTurns % 2 != 0) {
        editedTeam2A = setStartTeam2B
        editedTeam2B = setStartTeam2A
    }

    return DoublesOrder(
        team1PlayerA = editedTeam1A,
        team1PlayerB = editedTeam1B,
        team2PlayerA = editedTeam2A,
        team2PlayerB = editedTeam2B,
        player1Name = composeDoublesTeamName(editedTeam1A, editedTeam1B),
        player2Name = composeDoublesTeamName(editedTeam2A, editedTeam2B),
    )
}
