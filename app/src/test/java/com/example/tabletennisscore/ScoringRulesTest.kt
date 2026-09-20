package com.example.tabletennisscore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoringRulesTest {

    // ----- Sets and matches -----

    @Test
    fun setIsWonAtElevenWithTwoPointLead() {
        assertTrue(isSetWon(11, 0))
        assertTrue(isSetWon(11, 9))
        assertTrue(isSetWon(0, 11))
        assertTrue(isSetWon(9, 11))
    }

    @Test
    fun setIsNotWonWithoutTwoPointLeadOrEleven() {
        assertFalse(isSetWon(0, 0))
        assertFalse(isSetWon(10, 8))
        assertFalse(isSetWon(11, 10))
        assertFalse(isSetWon(10, 11))
        assertFalse(isSetWon(12, 11))
    }

    @Test
    fun setContinuesPastElevenUntilTwoPointLead() {
        assertFalse(isSetWon(13, 12))
        assertTrue(isSetWon(13, 11))
        assertTrue(isSetWon(15, 13))
    }

    @Test
    fun matchIsWonWhenMajorityOfSetsReached() {
        assertTrue(isMatchWon(1, 0, bestOfSets = 1))
        assertTrue(isMatchWon(2, 1, bestOfSets = 3))
        assertTrue(isMatchWon(0, 3, bestOfSets = 5))
        assertTrue(isMatchWon(4, 2, bestOfSets = 7))
    }

    @Test
    fun matchIsNotWonBeforeMajority() {
        assertFalse(isMatchWon(0, 0, bestOfSets = 1))
        assertFalse(isMatchWon(1, 1, bestOfSets = 3))
        assertFalse(isMatchWon(2, 2, bestOfSets = 5))
        assertFalse(isMatchWon(3, 3, bestOfSets = 7))
    }

    @Test
    fun decidingSetIsWhenBothPlayersAreOneSetFromWinning() {
        assertTrue(isDecidingSet(2, 2, bestOfSets = 5))
        assertTrue(isDecidingSet(3, 3, bestOfSets = 7))
        assertFalse(isDecidingSet(1, 1, bestOfSets = 5))
        assertFalse(isDecidingSet(2, 1, bestOfSets = 5))
    }

    @Test
    fun decidingSetIsNeverReportedInShortMatches() {
        assertFalse(isDecidingSet(0, 0, bestOfSets = 1))
        assertFalse(isDecidingSet(1, 1, bestOfSets = 3))
    }

    // ----- Serving -----

    @Test
    fun serviceAlternatesEveryTwoPointsBeforeDeuce() {
        val servers = (0..7).map { total -> nextServer(0, total, total, firstServer = 1) }
        assertEquals(listOf(1, 1, 2, 2, 1, 1, 2, 2), servers)
    }

    @Test
    fun serviceStartsWithTheGivenFirstServer() {
        assertEquals(2, nextServer(0, 0, 0, firstServer = 2))
        assertEquals(1, nextServer(0, 2, 2, firstServer = 2))
    }

    @Test
    fun serviceAlternatesEveryPointFromTenTen() {
        assertEquals(1, nextServer(10, 10, 20, firstServer = 1))
        assertEquals(2, nextServer(11, 10, 21, firstServer = 1))
        assertEquals(1, nextServer(11, 11, 22, firstServer = 1))
        assertEquals(2, nextServer(12, 11, 23, firstServer = 1))
    }

    @Test
    fun otherPlayerFlipsBetweenOneAndTwo() {
        assertEquals(2, otherPlayer(1))
        assertEquals(1, otherPlayer(2))
    }

    @Test
    fun firstServerOfSetAlternatesEverySet() {
        assertEquals(1, firstServerOfSet(matchFirstServer = 1, completedSetCount = 0))
        assertEquals(2, firstServerOfSet(matchFirstServer = 1, completedSetCount = 1))
        assertEquals(1, firstServerOfSet(matchFirstServer = 1, completedSetCount = 2))
        assertEquals(2, firstServerOfSet(matchFirstServer = 2, completedSetCount = 0))
        assertEquals(1, firstServerOfSet(matchFirstServer = 2, completedSetCount = 1))
    }

    // ----- Doubles service turns -----

    @Test
    fun noServiceTurnsAreCompletedAtZeroZero() {
        assertEquals(0 to 0, countCompletedServiceTurnsByTeam(0, 0, firstServer = 1))
    }

    @Test
    fun serviceTurnsAreCountedPerTeam() {
        // First server is player 1: points 1-2 are team 1's turn, 3-4 team 2's, and so on.
        assertEquals(0 to 0, countCompletedServiceTurnsByTeam(1, 0, firstServer = 1))
        assertEquals(1 to 0, countCompletedServiceTurnsByTeam(2, 0, firstServer = 1))
        assertEquals(1 to 0, countCompletedServiceTurnsByTeam(2, 1, firstServer = 1))
        assertEquals(1 to 1, countCompletedServiceTurnsByTeam(2, 2, firstServer = 1))
        assertEquals(2 to 1, countCompletedServiceTurnsByTeam(3, 3, firstServer = 1))
    }

    @Test
    fun serviceTurnsFollowTheFirstServer() {
        assertEquals(0 to 1, countCompletedServiceTurnsByTeam(2, 0, firstServer = 2))
    }

    @Test
    fun serviceTurnsAtTenTenCountFivePerTeam() {
        assertEquals(5 to 5, countCompletedServiceTurnsByTeam(10, 10, firstServer = 1))
    }

    // ----- Doubles server order after a manual score edit -----

    private fun doublesState(
        score1: Int = 0,
        score2: Int = 0,
        team1A: String = "Ann",
        team1B: String = "Bea",
        team2A: String = "Cid",
        team2B: String = "Dan",
        completedSets: List<Pair<Int, Int>> = emptyList(),
        matchMode: String = GameViewModel.MATCH_MODE_DOUBLES,
    ) = GameViewModel.GameState(
        score1 = score1,
        score2 = score2,
        setResults = completedSets,
        matchMode = matchMode,
        team1PlayerA = team1A,
        team1PlayerB = team1B,
        team2PlayerA = team2A,
        team2PlayerB = team2B,
    )

    @Test
    fun doublesOrderIsNotRecalculatedInSingles() {
        val state = doublesState(matchMode = GameViewModel.MATCH_MODE_SINGLES)
        assertNull(recalculateDoublesOrderForCurrentSetEdit(state, 2, 0, 0, matchFirstServer = 1))
    }

    @Test
    fun doublesOrderIsNotRecalculatedWhenCompletedSetCountChanges() {
        val state = doublesState()
        assertNull(recalculateDoublesOrderForCurrentSetEdit(state, 2, 0, editedCompletedSetCount = 1, matchFirstServer = 1))
    }

    @Test
    fun doublesOrderIsUnchangedWhenNoTurnHasEnded() {
        val order = recalculateDoublesOrderForCurrentSetEdit(doublesState(), 1, 0, 0, matchFirstServer = 1)
        assertNotNull(order)
        assertEquals("Ann / Bea", order!!.player1Name)
        assertEquals("Cid / Dan", order.player2Name)
    }

    @Test
    fun team1RotatesAfterItsFirstServiceTurn() {
        val order = recalculateDoublesOrderForCurrentSetEdit(doublesState(), 2, 0, 0, matchFirstServer = 1)!!
        assertEquals("Bea", order.team1PlayerA)
        assertEquals("Ann", order.team1PlayerB)
        assertEquals("Bea / Ann", order.player1Name)
        assertEquals("Cid / Dan", order.player2Name)
    }

    @Test
    fun bothTeamsRotateAfterTheirFirstServiceTurns() {
        val order = recalculateDoublesOrderForCurrentSetEdit(doublesState(), 2, 2, 0, matchFirstServer = 1)!!
        assertEquals("Bea / Ann", order.player1Name)
        assertEquals("Dan / Cid", order.player2Name)
    }

    @Test
    fun editingBackToTheStartRestoresTheOriginalOrder() {
        // At 2-0 team 1 has already rotated, so the state shows Bea first.
        val rotated = doublesState(score1 = 2, score2 = 0, team1A = "Bea", team1B = "Ann")
        val order = recalculateDoublesOrderForCurrentSetEdit(rotated, 0, 0, 0, matchFirstServer = 1)!!
        assertEquals("Ann", order.team1PlayerA)
        assertEquals("Bea", order.team1PlayerB)
        assertEquals("Ann / Bea", order.player1Name)
    }

    @Test
    fun doublesOrderFollowsWhoServedFirstInTheSet() {
        // After one completed set the other team serves first, so team 2 rotates first.
        val state = doublesState(completedSets = listOf(11 to 5))
        val order = recalculateDoublesOrderForCurrentSetEdit(state, 2, 0, 1, matchFirstServer = 1)!!
        assertEquals("Ann / Bea", order.player1Name)
        assertEquals("Dan / Cid", order.player2Name)
    }

    @Test
    fun teamNameJoinsBothPlayers() {
        assertEquals("Ann / Bea", composeDoublesTeamName("Ann", "Bea"))
    }
}
