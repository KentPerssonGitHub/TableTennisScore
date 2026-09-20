package com.example.tabletennisscore

import com.example.tabletennisscore.GameViewModel.GameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoringEngineTest {

    private val firstServer = 1

    private fun running(
        score1: Int = 0,
        score2: Int = 0,
        sets1: Int = 0,
        sets2: Int = 0,
        bestOfSets: Int = 5,
        server: Int = 1,
        setResults: List<Pair<Int, Int>> = emptyList(),
        matchMode: String = GameViewModel.MATCH_MODE_SINGLES,
        decidingSetFiveSwapDone: Boolean = false,
        sidesSwapped: Boolean = false,
    ) = GameState(
        score1 = score1,
        score2 = score2,
        sets1 = sets1,
        sets2 = sets2,
        bestOfSets = bestOfSets,
        server = server,
        isMatchRunning = true,
        hasMatchStarted = true,
        setResults = setResults,
        pointHistory = List(setResults.size + 1) { emptyList() },
        matchMode = matchMode,
        decidingSetFiveSwapDone = decidingSetFiveSwapDone,
        sidesSwapped = sidesSwapped,
        tournamentName = "Cup",
        matchRound = "Final",
    )

    private fun paused(
        score1: Int = 0,
        score2: Int = 0,
        sets1: Int = 0,
        sets2: Int = 0,
        bestOfSets: Int = 5,
        setResults: List<Pair<Int, Int>> = emptyList(),
        matchMode: String = GameViewModel.MATCH_MODE_SINGLES,
    ) = running(
        score1 = score1,
        score2 = score2,
        sets1 = sets1,
        sets2 = sets2,
        bestOfSets = bestOfSets,
        setResults = setResults,
        matchMode = matchMode,
    ).copy(isMatchRunning = false)

    private fun point(state: GameState, player: Int) = ScoringEngine.addPoint(state, player, firstServer)

    /** Plays [points] winners in order and returns the final change; every change must keep the match going. */
    private fun play(start: GameState, vararg points: Int): ScoreChange {
        var change = ScoreChange(start)
        for (player in points) change = point(change.state, player)
        return change
    }

    // ----- Ordinary points -----

    @Test
    fun pointIsCountedAndRecorded() {
        val change = point(running(), player = 1)
        assertEquals(1, change.state.score1)
        assertEquals(0, change.state.score2)
        assertEquals(listOf(listOf(1)), change.state.pointHistory)
        assertFalse(change.stopClock)
        assertNull(change.finishedMatch)
    }

    @Test
    fun secondPlayerPointIsCountedForPlayerTwo() {
        val change = point(running(), player = 2)
        assertEquals(0, change.state.score1)
        assertEquals(1, change.state.score2)
        assertEquals(listOf(listOf(2)), change.state.pointHistory)
    }

    @Test
    fun serverChangesEveryTwoPoints() {
        val servers = (1..6).scan(ScoreChange(running())) { change, _ -> point(change.state, 1) }
            .map { it.state.server }
        assertEquals(listOf(1, 1, 2, 2, 1, 1, 2), servers)
    }

    @Test
    fun serverChangesEveryPointFromTenTen() {
        val atDeuce = running(score1 = 10, score2 = 10, server = 1)
        val afterOne = point(atDeuce, 1)
        val afterTwo = point(afterOne.state, 2)
        assertEquals(2, afterOne.state.server)
        assertEquals(1, afterTwo.state.server)
    }

    // ----- End of a set -----

    @Test
    fun setIsWonAtElevenAndNextSetStartsWithTheOtherServer() {
        val change = play(running(), *IntArray(11) { 1 })
        val state = change.state
        assertEquals(1, state.sets1)
        assertEquals(0, state.score1)
        assertEquals(0, state.score2)
        assertEquals(listOf(11 to 0), state.setResults)
        assertEquals(2, state.server)
        assertTrue("players switch ends after a set", state.sidesSwapped)
        assertEquals(2, state.pointHistory.size)
        assertEquals(11, state.pointHistory.first().size)
        assertTrue(state.pointHistory.last().isEmpty())
        assertNull(state.matchWinner)
        assertNull(change.finishedMatch)
        assertFalse(change.stopClock)
    }

    @Test
    fun setIsNotWonAtElevenTenButAtTwelveTen() {
        val at1110 = point(running(score1 = 10, score2 = 10), 1)
        assertEquals(11, at1110.state.score1)
        assertEquals(0, at1110.state.sets1)

        val at1210 = point(at1110.state, 1)
        assertEquals(1, at1210.state.sets1)
        assertEquals(listOf(12 to 10), at1210.state.setResults)
    }

    @Test
    fun secondSetStartsWithTheOtherPlayerServingFirst() {
        val afterFirstSet = play(running(), *IntArray(11) { 1 }).state
        val servers = (1..4).scan(ScoreChange(afterFirstSet)) { c, _ -> point(c.state, 2) }.map { it.state.server }
        assertEquals(listOf(2, 2, 1, 1, 2), servers)
    }

    @Test
    fun sidesAreSwappedBackAfterTheSecondSet() {
        val afterSet = play(running(sidesSwapped = true), *IntArray(11) { 1 }).state
        assertFalse(afterSet.sidesSwapped)
    }

    // ----- End of the match -----

    @Test
    fun matchEndsWhenTheDecidingSetOfABestOfOneIsWon() {
        val change = play(running(bestOfSets = 1), *IntArray(11) { 1 })
        val state = change.state
        assertEquals(1, state.matchWinner)
        assertFalse(state.isMatchRunning)
        assertEquals(1, state.sets1)
        assertFalse("sides are not swapped when the match is over", state.sidesSwapped)
        assertTrue(change.stopClock)
    }

    @Test
    fun finishedMatchCarriesEverythingNeededToStoreIt() {
        val change = play(running(bestOfSets = 1), *IntArray(11) { 1 })
        val finished = change.finishedMatch
        assertNotNull(finished)
        finished!!
        assertEquals("Cup", finished.tournamentName)
        assertEquals("Final", finished.matchRound)
        assertEquals(1, finished.winner)
        assertEquals(1, finished.sets1)
        assertEquals(0, finished.sets2)
        assertEquals(1, finished.bestOfSets)
        assertEquals(firstServer, finished.matchFirstServer)
        assertEquals(listOf(11 to 0), finished.setResults)
        assertEquals(listOf(List(11) { 1 }), finished.pointHistory)
    }

    @Test
    fun secondPlayerCanWinTheMatch() {
        val start = running(bestOfSets = 3, sets2 = 1, score1 = 4, score2 = 10)
        val change = point(start, 2)
        assertEquals(2, change.state.matchWinner)
        assertEquals(2, change.state.sets2)
        assertEquals(2, change.finishedMatch!!.winner)
        assertEquals(listOf(4 to 11), change.finishedMatch!!.setResults)
    }

    @Test
    fun matchContinuesUntilASetMajorityIsReached() {
        val change = point(running(bestOfSets = 5, sets1 = 1, score1 = 10, score2 = 4), 1)
        assertEquals(2, change.state.sets1)
        assertNull(change.state.matchWinner)
        assertNull(change.finishedMatch)
    }

    // ----- Deciding set: swap ends at 5 -----

    @Test
    fun sidesSwapWhenAPlayerReachesFiveInTheDecidingSet() {
        val start = running(sets1 = 2, sets2 = 2, score1 = 4, score2 = 3, server = 1)
        val change = point(start, 1)
        val state = change.state
        assertEquals(5, state.score1)
        assertTrue(state.sidesSwapped)
        assertTrue(state.decidingSetFiveSwapDone)
        assertEquals(1, state.decidingSetSwapNoticeVersion)
        assertFalse("play pauses until the swap is confirmed", state.isMatchRunning)
        assertTrue(state.awaitingDecidingSetSwapConfirmation)
        assertTrue(state.resumeAfterDecidingSetSwapConfirmation)
        assertTrue(change.stopClock)
    }

    @Test
    fun sidesSwapWhenTheOtherPlayerReachesFiveFirst() {
        val change = point(running(sets1 = 2, sets2 = 2, score1 = 1, score2 = 4), 2)
        assertTrue(change.state.sidesSwapped)
        assertTrue(change.stopClock)
    }

    @Test
    fun sidesSwapOnlyOnceInTheDecidingSet() {
        val start = running(sets1 = 2, sets2 = 2, score1 = 5, score2 = 3, decidingSetFiveSwapDone = true, sidesSwapped = true)
        val change = point(start, 1)
        assertTrue(change.state.sidesSwapped)
        assertEquals(0, change.state.decidingSetSwapNoticeVersion)
        assertFalse(change.stopClock)
    }

    @Test
    fun noSwapAtFiveInAnEarlierSet() {
        val change = point(running(sets1 = 1, sets2 = 1, score1 = 4, score2 = 3), 1)
        assertFalse(change.state.sidesSwapped)
        assertFalse(change.stopClock)
    }

    @Test
    fun noSwapAtFiveInABestOfThree() {
        val change = point(running(bestOfSets = 3, sets1 = 1, sets2 = 1, score1 = 4, score2 = 3), 1)
        assertFalse(change.state.sidesSwapped)
        assertTrue(change.state.isMatchRunning)
    }

    @Test
    fun winningTheDecidingSetEndsTheMatchWithoutSwapConfirmation() {
        val start = running(
            sets1 = 2, sets2 = 2, score1 = 10, score2 = 6, decidingSetFiveSwapDone = true, sidesSwapped = true,
        )
        val change = point(start, 1)
        assertEquals(1, change.state.matchWinner)
        assertFalse(change.state.awaitingDecidingSetSwapConfirmation)
        assertFalse(change.state.decidingSetFiveSwapDone)
        assertTrue(change.state.sidesSwapped)
        assertTrue(change.stopClock)
    }

    // ----- Doubles: server order rotates -----

    private fun doubles(score1: Int, score2: Int, server: Int) = running(
        score1 = score1,
        score2 = score2,
        server = server,
        matchMode = GameViewModel.MATCH_MODE_DOUBLES,
    ).copy(
        team1PlayerA = "Ann",
        team1PlayerB = "Bea",
        team2PlayerA = "Cid",
        team2PlayerB = "Dan",
        player1Name = "Ann / Bea",
        player2Name = "Cid / Dan",
    )

    @Test
    fun doublesOrderIsUnchangedWhileTheSameTeamKeepsServing() {
        val state = point(doubles(0, 0, server = 1), 1).state
        assertEquals("Ann / Bea", state.player1Name)
        assertEquals("Cid / Dan", state.player2Name)
    }

    @Test
    fun team1RotatesWhenItsServiceTurnEnds() {
        val state = point(doubles(1, 0, server = 1), 1).state
        assertEquals(2, state.server)
        assertEquals("Bea", state.team1PlayerA)
        assertEquals("Ann", state.team1PlayerB)
        assertEquals("Bea / Ann", state.player1Name)
        assertEquals("Cid / Dan", state.player2Name)
    }

    @Test
    fun team2RotatesWhenItsServiceTurnEnds() {
        val state = point(doubles(2, 1, server = 2), 1).state
        assertEquals(1, state.server)
        assertEquals("Ann / Bea", state.player1Name)
        assertEquals("Dan", state.team2PlayerA)
        assertEquals("Cid", state.team2PlayerB)
        assertEquals("Dan / Cid", state.player2Name)
    }

    @Test
    fun singlesNamesAreNeverRotated() {
        val singles = running(score1 = 1, score2 = 0).copy(
            team1PlayerA = "Ann", team1PlayerB = "Bea", player1Name = "P1",
        )
        val state = point(singles, 1).state
        assertEquals("P1", state.player1Name)
        assertEquals("Ann", state.team1PlayerA)
    }

    // ----- Manual edits while paused -----

    @Test
    fun scoresCannotBeEditedWhileTheMatchRuns() {
        assertNull(ScoringEngine.editCurrentSetScore(running(), 3, 2, firstServer))
        assertNull(ScoringEngine.editPausedMatchScores(running(), emptyList(), 3, 2, firstServer))
    }

    @Test
    fun scoresCannotBeEditedBeforeTheMatchStartsOrAfterItEnds() {
        assertNull(ScoringEngine.editCurrentSetScore(paused().copy(hasMatchStarted = false), 3, 2, firstServer))
        assertNull(ScoringEngine.editCurrentSetScore(paused().copy(matchWinner = 1), 3, 2, firstServer))
    }

    @Test
    fun currentSetScoreEditRejectsNegativeAndFinishedSetScores() {
        assertNull(ScoringEngine.editCurrentSetScore(paused(), -1, 2, firstServer))
        assertNull(ScoringEngine.editCurrentSetScore(paused(), 11, 3, firstServer))
    }

    @Test
    fun currentSetScoreEditUpdatesScoreAndServer() {
        val edited = ScoringEngine.editCurrentSetScore(paused(score1 = 1), 2, 2, firstServer)!!
        assertEquals(2, edited.score1)
        assertEquals(2, edited.score2)
        assertEquals(1, edited.server)

        val edited2 = ScoringEngine.editCurrentSetScore(paused(score1 = 1), 2, 1, firstServer)!!
        assertEquals(2, edited2.server)
    }

    @Test
    fun matchScoreEditRejectsInvalidSetResults() {
        assertNull(ScoringEngine.editPausedMatchScores(paused(), listOf(10 to 9), 0, 0, firstServer))
        assertNull(ScoringEngine.editPausedMatchScores(paused(), listOf(-11 to 5), 0, 0, firstServer))
        assertNull(ScoringEngine.editPausedMatchScores(paused(), emptyList(), -1, 0, firstServer))
    }

    @Test
    fun matchScoreEditRejectsSetsThatAlreadyDecideTheMatch() {
        val threeSets = listOf(11 to 5, 11 to 6, 11 to 7)
        assertNull(ScoringEngine.editPausedMatchScores(paused(), threeSets, 0, 0, firstServer))
    }

    @Test
    fun matchScoreEditWithAnUnfinishedSetKeepsTheMatchGoing() {
        val sets = listOf(11 to 5, 4 to 11)
        val change = ScoringEngine.editPausedMatchScores(paused(), sets, 6, 3, firstServer)!!
        val state = change.state
        assertEquals(1, state.sets1)
        assertEquals(1, state.sets2)
        assertEquals(sets, state.setResults)
        assertEquals(6, state.score1)
        assertEquals(3, state.score2)
        assertEquals(3, state.pointHistory.size)
        assertTrue(state.pointHistory.all { it.isEmpty() })
        assertNull(state.matchWinner)
        assertNull(change.finishedMatch)
        assertFalse(change.stopClock)
    }

    @Test
    fun matchScoreEditThatFinishesASetStartsTheNextSet() {
        val change = ScoringEngine.editPausedMatchScores(paused(), emptyList(), 11, 5, firstServer)!!
        val state = change.state
        assertEquals(1, state.sets1)
        assertEquals(0, state.score1)
        assertEquals(0, state.score2)
        assertEquals(listOf(11 to 5), state.setResults)
        assertEquals(2, state.server)
        assertTrue(state.sidesSwapped)
        assertEquals(2, state.pointHistory.size)
        assertNull(state.matchWinner)
        assertNull(change.finishedMatch)
    }

    @Test
    fun matchScoreEditThatDecidesTheMatchProducesAFinishedMatch() {
        val change = ScoringEngine.editPausedMatchScores(
            paused(bestOfSets = 3), listOf(11 to 5), 6, 11, firstServer,
        )!!
        val state = change.state
        assertEquals(1, state.sets1)
        assertEquals(1, state.sets2)
        assertNull("1-1 in a best of 3 is not decided", state.matchWinner)

        val decided = ScoringEngine.editPausedMatchScores(
            paused(bestOfSets = 3), listOf(11 to 5), 11, 6, firstServer,
        )!!
        assertEquals(1, decided.state.matchWinner)
        assertFalse(decided.state.sidesSwapped)
        assertFalse(decided.state.isMatchRunning)
        val finished = decided.finishedMatch!!
        assertEquals(1, finished.winner)
        assertEquals(2, finished.sets1)
        assertEquals(0, finished.sets2)
        assertEquals(listOf(11 to 5, 11 to 6), finished.setResults)
        assertEquals("Cup", finished.tournamentName)
        assertFalse(decided.stopClock)
    }

    @Test
    fun matchScoreEditReachingFiveInTheDecidingSetAsksForASideSwap() {
        val sets = listOf(11 to 5, 5 to 11, 11 to 5, 5 to 11)
        val state = ScoringEngine.editPausedMatchScores(paused(), sets, 5, 2, firstServer)!!.state
        assertTrue(state.decidingSetFiveSwapDone)
        assertTrue(state.awaitingDecidingSetSwapConfirmation)
        assertTrue(state.sidesSwapped)
        assertEquals(1, state.decidingSetSwapNoticeVersion)
        assertFalse(state.resumeAfterDecidingSetSwapConfirmation)
    }

    @Test
    fun matchScoreEditBelowFiveInTheDecidingSetDoesNotSwapSides() {
        val sets = listOf(11 to 5, 5 to 11, 11 to 5, 5 to 11)
        val state = ScoringEngine.editPausedMatchScores(paused(), sets, 4, 2, firstServer)!!.state
        assertFalse(state.decidingSetFiveSwapDone)
        assertFalse(state.sidesSwapped)
        assertFalse(state.awaitingDecidingSetSwapConfirmation)
    }

    @Test
    fun matchScoreEditRotatesDoublesOrderForTheEditedScore() {
        val start = paused(matchMode = GameViewModel.MATCH_MODE_DOUBLES).copy(
            team1PlayerA = "Ann", team1PlayerB = "Bea",
            team2PlayerA = "Cid", team2PlayerB = "Dan",
            player1Name = "Ann / Bea", player2Name = "Cid / Dan",
        )
        val state = ScoringEngine.editPausedMatchScores(start, emptyList(), 2, 0, firstServer)!!.state
        assertEquals("Bea / Ann", state.player1Name)
        assertEquals("Cid / Dan", state.player2Name)
    }
}
