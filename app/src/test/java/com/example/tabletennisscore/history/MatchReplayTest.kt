package com.example.tabletennisscore.history

import com.example.tabletennisscore.FinishedMatch
import com.example.tabletennisscore.GameViewModel
import com.example.tabletennisscore.GameViewModel.GameState
import com.example.tabletennisscore.ScoringEngine
import com.example.tabletennisscore.data.MatchResult
import com.example.tabletennisscore.isDecidingSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MatchReplayTest {

    /** Plays a random match through the scoring rules and stores it the way the app does. */
    private fun recordedMatch(random: Random, bestOfSets: Int, firstServer: Int, doubles: Boolean): MatchResult {
        var state = GameState(
            bestOfSets = bestOfSets,
            server = firstServer,
            isMatchRunning = true,
            hasMatchStarted = true,
            tournamentName = "Cup",
            matchMode = if (doubles) GameViewModel.MATCH_MODE_DOUBLES else GameViewModel.MATCH_MODE_SINGLES,
            player1Name = if (doubles) "Ann / Bea" else "Ann",
            player2Name = if (doubles) "Cid / Dan" else "Cid",
            team1PlayerA = if (doubles) "Ann" else "",
            team1PlayerB = if (doubles) "Bea" else "",
            team2PlayerA = if (doubles) "Cid" else "",
            team2PlayerB = if (doubles) "Dan" else "",
        )
        var finished: FinishedMatch? = null
        while (finished == null) {
            val change = ScoringEngine.addPoint(state, if (random.nextBoolean()) 1 else 2, firstServer)
            state = change.state
            finished = change.finishedMatch
            if (state.awaitingDecidingSetSwapConfirmation) {
                state = state.copy(awaitingDecidingSetSwapConfirmation = false, isMatchRunning = true)
            }
        }
        return MatchResult(
            tournamentName = finished.tournamentName,
            player1Name = finished.player1Name,
            player2Name = finished.player2Name,
            matchMode = finished.matchMode,
            sets1 = finished.sets1,
            sets2 = finished.sets2,
            winner = finished.winner,
            bestOfSets = finished.bestOfSets,
            durationMs = 60_000L,
            setResultsJson = finished.setResults.joinToString(",") { "${it.first}-${it.second}" },
            pointHistoryJson = finished.pointHistory.joinToString(",") { it.joinToString("") },
            matchFirstServer = finished.matchFirstServer,
        )
    }

    private fun forEveryMatchShape(check: (MatchResult) -> Unit) {
        val random = Random(42)
        for (bestOfSets in listOf(1, 3, 5, 7)) {
            for (firstServer in 1..2) {
                for (doubles in listOf(false, true)) {
                    repeat(5) { check(recordedMatch(random, bestOfSets, firstServer, doubles)) }
                }
            }
        }
    }

    @Test
    fun aRecordedMatchReplaysToItsStoredResult() = forEveryMatchShape { result ->
        val points = replayPoints(result)
        assertNotNull(points)
        points!!
        assertEquals(result.pointHistoryJson.count { it == '1' || it == '2' }, points.size)
        val end = points.last().after
        assertEquals(result.winner, end.matchWinner)
        assertEquals(result.sets1, end.sets1)
        assertEquals(result.sets2, end.sets2)
        assertEquals(result.setResultsJson, end.setResults.joinToString(",") { "${it.first}-${it.second}" })
    }

    @Test
    fun pointsFollowOnFromEachOtherAndPlayNeverWaits() = forEveryMatchShape { result ->
        val points = replayPoints(result)!!
        points.zipWithNext().forEach { (previous, next) -> assertEquals(previous.after, next.before) }
        points.forEach { point ->
            assertTrue(point.before.isMatchRunning)
            assertFalse(point.after.awaitingDecidingSetSwapConfirmation)
            assertEquals(point.before.server, point.server)
        }
    }

    @Test
    fun playersSwitchEndsAfterEverySetAndAtFiveInTheDecidingSet() = forEveryMatchShape { result ->
        for (point in replayPoints(result)!!) {
            val before = point.before
            val after = point.after
            val setEnded = after.setResults.size > before.setResults.size
            val reachedFiveInDecidingSet = !setEnded &&
                isDecidingSet(before.sets1, before.sets2, before.bestOfSets) &&
                maxOf(before.score1, before.score2) < 5 && maxOf(after.score1, after.score2) == 5
            val shouldSwitch = (setEnded && after.matchWinner == null) || reachedFiveInDecidingSet
            assertEquals(shouldSwitch, after.sidesSwapped != before.sidesSwapped)
        }
    }

    @Test
    fun doublesTeamsKeepTheirPlayersNames() {
        val result = recordedMatch(Random(7), bestOfSets = 3, firstServer = 1, doubles = true)
        replayPoints(result)!!.forEach { point ->
            assertTrue(point.after.player1Name in setOf("Ann / Bea", "Bea / Ann"))
            assertTrue(point.after.player2Name in setOf("Cid / Dan", "Dan / Cid"))
        }
    }

    @Test
    fun aMatchWithoutCompletePointHistoryCannotBeReplayed() {
        val result = recordedMatch(Random(3), bestOfSets = 3, firstServer = 1, doubles = false)
        assertTrue(canReplay(result))

        val sets = result.pointHistoryJson.split(",")
        // A set whose score was edited by hand has no points.
        val missingSet = result.copy(pointHistoryJson = (listOf("") + sets.drop(1)).joinToString(","))
        // A set whose points don't add up to its score.
        val wrongCount = result.copy(pointHistoryJson = (listOf(sets[0].drop(1)) + sets.drop(1)).joinToString(","))
        // Older matches stored no points at all.
        val noHistory = result.copy(pointHistoryJson = "")

        for (broken in listOf(missingSet, wrongCount, noHistory)) {
            assertFalse(canReplay(broken))
            assertNull(replayPoints(broken))
        }
    }
}
