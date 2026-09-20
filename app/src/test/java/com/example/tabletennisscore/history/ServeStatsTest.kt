package com.example.tabletennisscore.history

import org.junit.Assert.assertEquals
import org.junit.Test

class ServeStatsTest {

    @Test
    fun percentageIsZeroWithoutServes() {
        assertEquals(0.0, ServeStats().percentage, 0.0)
    }

    @Test
    fun percentageIsShareOfServicePointsWon() {
        assertEquals(75.0, ServeStats(pointsWonOnServe = 3, totalServes = 4).percentage, 0.0001)
    }

    @Test
    fun statsAreAddedTogether() {
        assertEquals(ServeStats(5, 9), ServeStats(2, 4) + ServeStats(3, 5))
    }

    @Test
    fun serverKeepsServingTwoPointsThenTheOtherPlayerTakesOver() {
        // First server is player 1: points 1-2 are on player 1's serve, points 3-4 on player 2's.
        val stats = serveStatsForSet("1122", setFirstServer = 1)
        assertEquals(ServeStats(pointsWonOnServe = 2, totalServes = 2), stats.player1)
        assertEquals(ServeStats(pointsWonOnServe = 2, totalServes = 2), stats.player2)
    }

    @Test
    fun pointsLostOnServeCountAsServesButNotWins() {
        val stats = serveStatsForSet("22", setFirstServer = 1)
        assertEquals(ServeStats(pointsWonOnServe = 0, totalServes = 2), stats.player1)
        assertEquals(ServeStats(), stats.player2)
    }

    @Test
    fun theOtherPlayerServesFirstWhenSetFirstServerIsTwo() {
        val stats = serveStatsForSet("11", setFirstServer = 2)
        assertEquals(ServeStats(), stats.player1)
        assertEquals(ServeStats(pointsWonOnServe = 0, totalServes = 2), stats.player2)
    }

    @Test
    fun serviceAlternatesEveryPointAtDeuce() {
        // 10 points each (alternating winners keep the score level), then five more points at deuce.
        val toDeuce = "12".repeat(10)
        val atDeuce = "1212" + "1"
        val stats = serveStatsForSet(toDeuce + atDeuce, setFirstServer = 1)
        // 20 points before deuce: 10 each. Then 5 deuce points: servers 1,2,1,2,1 (3 for player 1, 2 for player 2).
        assertEquals(13, stats.player1.totalServes)
        assertEquals(12, stats.player2.totalServes)
    }

    @Test
    fun matchStatsAlternateTheFirstServerEverySet() {
        // Set 1: player 1 serves first; set 2: player 2 serves first.
        val match = serveStatsForMatch(listOf("11", "11"), matchFirstServer = 1)
        assertEquals(2, match.perSet.size)
        assertEquals(ServeStats(2, 2), match.perSet[0].player1)
        assertEquals(ServeStats(0, 2), match.perSet[1].player2)
        assertEquals(ServeStats(2, 2), match.total.player1)
        assertEquals(ServeStats(0, 2), match.total.player2)
    }

    @Test
    fun matchWithoutSetsHasEmptyStats() {
        val match = serveStatsForMatch(emptyList(), matchFirstServer = 1)
        assertEquals(emptyList<PlayersServeStats>(), match.perSet)
        assertEquals(ServeStats(), match.total.player1)
    }
}
