package com.example.tabletennisscore

import com.example.tabletennisscore.GameViewModel.GameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Plays many complete matches with random point winners through [ScoringEngine] and checks that the
 * rules hold at every step and in the end result, for singles and doubles and every match length.
 */
class MatchSimulationTest {

    private class PlayedMatch(val finalState: GameState, val finished: List<FinishedMatch>)

    private fun startState(bestOfSets: Int, matchFirstServer: Int, doubles: Boolean) = GameState(
        bestOfSets = bestOfSets,
        server = matchFirstServer,
        isMatchRunning = true,
        hasMatchStarted = true,
        tournamentName = "Cup",
        matchRound = "Final",
        matchMode = if (doubles) GameViewModel.MATCH_MODE_DOUBLES else GameViewModel.MATCH_MODE_SINGLES,
        player1Name = if (doubles) "Ann / Bea" else "Ann",
        player2Name = if (doubles) "Cid / Dan" else "Cid",
        team1PlayerA = if (doubles) "Ann" else "",
        team1PlayerB = if (doubles) "Bea" else "",
        team2PlayerA = if (doubles) "Cid" else "",
        team2PlayerB = if (doubles) "Dan" else "",
    )

    /** Plays a match to the end, asserting the per-point rules on the way. */
    private fun playMatch(random: Random, bestOfSets: Int, matchFirstServer: Int, doubles: Boolean): PlayedMatch {
        var state = startState(bestOfSets, matchFirstServer, doubles)
        val finished = mutableListOf<FinishedMatch>()
        var points = 0

        while (state.matchWinner == null) {
            assertTrue("match did not finish", points++ < MAX_POINTS)
            val player = if (random.nextBoolean()) 1 else 2
            val before = state
            val change = ScoringEngine.addPoint(state, player, matchFirstServer)
            state = change.state
            change.finishedMatch?.let { finished.add(it) }

            val setEnded = state.setResults.size > before.setResults.size
            val firstServerOfCurrentSet = firstServerOfSet(matchFirstServer, state.setResults.size)
            when {
                state.matchWinner != null -> Unit
                setEnded -> {
                    assertEquals(firstServerOfCurrentSet, state.server)
                    assertEquals(0, state.score1 + state.score2)
                    assertTrue(state.pointHistory.last().isEmpty())
                }
                else -> {
                    val total = state.score1 + state.score2
                    assertEquals(nextServer(state.score1, state.score2, total, firstServerOfCurrentSet), state.server)
                    assertEquals(total, state.pointHistory.last().size)
                }
            }

            // Confirm a deciding-set side swap the way the app does, so play continues.
            if (state.awaitingDecidingSetSwapConfirmation) {
                assertTrue(change.stopClock)
                state = state.copy(
                    awaitingDecidingSetSwapConfirmation = false,
                    resumeAfterDecidingSetSwapConfirmation = false,
                    isMatchRunning = true,
                )
            }
        }
        return PlayedMatch(state, finished)
    }

    private fun forEveryMatch(check: (state: GameState, played: PlayedMatch, bestOfSets: Int, doubles: Boolean) -> Unit) {
        for (bestOfSets in listOf(1, 3, 5, 7)) {
            for (matchFirstServer in 1..2) {
                for (doubles in listOf(false, true)) {
                    for (seed in 0 until SEEDS) {
                        val random = Random(seed * 31 + bestOfSets * 7 + matchFirstServer)
                        val played = playMatch(random, bestOfSets, matchFirstServer, doubles)
                        check(played.finalState, played, bestOfSets, doubles)
                    }
                }
            }
        }
    }

    @Test
    fun everyMatchEndsWithExactlyOneWinnerWhoReachedTheRequiredSets() = forEveryMatch { state, _, bestOfSets, _ ->
        val setsToWin = bestOfSets / 2 + 1
        val winnerSets = if (state.matchWinner == 1) state.sets1 else state.sets2
        val loserSets = if (state.matchWinner == 1) state.sets2 else state.sets1
        assertEquals(setsToWin, winnerSets)
        assertTrue(loserSets < setsToWin)
        assertEquals(state.sets1 + state.sets2, state.setResults.size)
        assertTrue(!state.isMatchRunning)
        assertEquals(0, state.score1)
        assertEquals(0, state.score2)
    }

    @Test
    fun everyRecordedSetIsAValidFinishedSetWonByTheRightPlayer() = forEveryMatch { state, _, _, _ ->
        state.setResults.forEach { (s1, s2) -> assertTrue("$s1-$s2", isSetWon(s1, s2)) }
        assertEquals(state.sets1, state.setResults.count { it.first > it.second })
        assertEquals(state.sets2, state.setResults.count { it.second > it.first })
    }

    @Test
    fun pointHistoryMatchesTheSetResults() = forEveryMatch { state, _, _, _ ->
        assertEquals(state.setResults.size, state.pointHistory.size)
        state.setResults.forEachIndexed { index, (s1, s2) ->
            val points = state.pointHistory[index]
            assertEquals(s1, points.count { it == 1 })
            assertEquals(s2, points.count { it == 2 })
        }
    }

    @Test
    fun theFinishedMatchIsReportedOnceAndAgreesWithTheFinalState() = forEveryMatch { state, played, bestOfSets, _ ->
        assertEquals(1, played.finished.size)
        val finished = played.finished.single()
        assertEquals(state.matchWinner, finished.winner)
        assertEquals(state.sets1, finished.sets1)
        assertEquals(state.sets2, finished.sets2)
        assertEquals(bestOfSets, finished.bestOfSets)
        assertEquals(state.setResults, finished.setResults)
        assertEquals(state.pointHistory, finished.pointHistory)
        assertEquals(state.player1Name, finished.player1Name)
        assertEquals(state.player2Name, finished.player2Name)
        assertEquals("Cup", finished.tournamentName)
        assertEquals("Final", finished.matchRound)
    }

    @Test
    fun sidesSwapAfterEverySetExceptTheLastAndOnceInTheDecidingSet() = forEveryMatch { state, _, bestOfSets, _ ->
        val setsToWin = bestOfSets / 2 + 1
        val loserSets = if (state.matchWinner == 1) state.sets2 else state.sets1
        val wasDecidingSet = bestOfSets >= 5 && loserSets == setsToWin - 1

        val expectedSwapNotices = if (wasDecidingSet) 1 else 0
        assertEquals(expectedSwapNotices, state.decidingSetSwapNoticeVersion)

        val swaps = (state.setResults.size - 1) + expectedSwapNotices
        assertEquals(swaps % 2 == 1, state.sidesSwapped)
    }

    @Test
    fun doublesTeamsKeepTheirPlayersAndTheNamesMatchTheOrder() = forEveryMatch { state, _, _, doubles ->
        if (!doubles) {
            assertEquals("Ann", state.player1Name)
            assertEquals("Cid", state.player2Name)
            return@forEveryMatch
        }
        assertEquals(setOf("Ann", "Bea"), setOf(state.team1PlayerA, state.team1PlayerB))
        assertEquals(setOf("Cid", "Dan"), setOf(state.team2PlayerA, state.team2PlayerB))
        assertEquals(composeDoublesTeamName(state.team1PlayerA, state.team1PlayerB), state.player1Name)
        assertEquals(composeDoublesTeamName(state.team2PlayerA, state.team2PlayerB), state.player2Name)
    }

    @Test
    fun aMatchCanBeWonByEitherPlayer() {
        val winners = mutableSetOf<Int?>()
        forEveryMatch { state, _, _, _ -> winners.add(state.matchWinner) }
        assertEquals(setOf<Int?>(1, 2), winners)
    }

    @Test
    fun aPlayerWinningEveryPointWinsInTheFewestSets() {
        for (bestOfSets in listOf(1, 3, 5, 7)) {
            var state = startState(bestOfSets, matchFirstServer = 1, doubles = false)
            var finished: FinishedMatch? = null
            var points = 0
            while (state.matchWinner == null) {
                assertTrue(points++ < MAX_POINTS)
                val change = ScoringEngine.addPoint(state, 1, matchFirstServer = 1)
                state = change.state
                finished = change.finishedMatch ?: finished
                if (state.awaitingDecidingSetSwapConfirmation) {
                    state = state.copy(awaitingDecidingSetSwapConfirmation = false, isMatchRunning = true)
                }
            }
            val setsToWin = bestOfSets / 2 + 1
            assertEquals(setsToWin * 11, points)
            assertEquals(1, state.matchWinner)
            assertEquals(setsToWin, state.sets1)
            assertEquals(0, state.sets2)
            assertEquals(List(setsToWin) { 11 to 0 }, state.setResults)
            assertNotNull(finished)
        }
    }

    @Test
    fun aMatchThatIsAlreadyDecidedNeverProducesAnotherFinishedMatchOnEdit() {
        val decided = startState(3, 1, false).copy(isMatchRunning = false, matchWinner = 1)
        assertNull(ScoringEngine.editPausedMatchScores(decided, emptyList(), 0, 0, 1))
    }

    private companion object {
        const val SEEDS = 25
        const val MAX_POINTS = 5_000
    }
}
