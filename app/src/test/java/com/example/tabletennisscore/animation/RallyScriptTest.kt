package com.example.tabletennisscore.animation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RallyScriptTest {

    /** Where the green table surface ends along a leg (the ball travels 5% past each end). */
    private val tableStart = 0.05f / 1.1f
    private val tableEnd = 1f - tableStart

    private fun progressSteps() = (0..200).map { it / 200f }

    @Test
    fun theLoserAlwaysPlaysTheMistakeOnTheServeItsReturnOrAfterThreeToSixGoodStrokes() {
        val random = Random(1)
        repeat(500) {
            val serverWon = random.nextBoolean()
            val script = rallyScriptFor(serverWon, random)
            assertTrue(script.goodLegs in setOf(0, 1, 3, 4, 5, 6))
            // Leg k is hit by the server when k is even.
            val serverHitsMistake = script.mistakeLeg % 2 == 0
            assertEquals(!serverWon, serverHitsMistake)
        }
    }

    @Test
    fun everyMistakeTypeAndRallyLengthComesUp() {
        val random = Random(2)
        val scripts = (1..500).map { rallyScriptFor(random.nextBoolean(), random) }
        assertEquals(MistakeType.entries.toSet(), scripts.map { it.mistake }.toSet())
        assertEquals(setOf(0, 1, 3, 4, 5, 6), scripts.map { it.goodLegs }.toSet())
    }

    @Test
    fun aboutAThirdOfPointsEndOnTheServeOrItsReturn() {
        val random = Random(3)
        val scripts = (1..2000).map { rallyScriptFor(random.nextBoolean(), random) }
        val shortShare = scripts.count { it.goodLegs <= 1 } / scripts.size.toFloat()
        assertEquals(RallyScript.SHORT_POINT_CHANCE, shortShare, 0.05f)
    }

    @Test
    fun aServeIsNeverMissedOnlyHitLongOrIntoTheNet() {
        val random = Random(4)
        val serveFaults = (1..2000).map { rallyScriptFor(serverWon = false, random) }.filter { it.goodLegs == 0 }
        assertTrue(serveFaults.isNotEmpty())
        assertEquals(setOf(MistakeType.LONG, MistakeType.NET), serveFaults.map { it.mistake }.toSet())
    }

    @Test
    fun aRallyEndsAfterItsMistakeHasPlayedOut() {
        val script = RallyScript(goodLegs = 4, mistake = MistakeType.NET)
        assertEquals(4, script.mistakeLeg)
        assertTrue(script.mistakeEnd > script.mistakeLeg)
        assertTrue(script.rallyEnd > script.mistakeEnd)
    }

    @Test
    fun allMistakesStartWhereTheBatMeetsTheBall() {
        for (type in MistakeType.entries) {
            val start = mistakeFlight(type, 0f)
            assertEquals(0f, start.travel, 1e-4f)
            assertEquals(1f, start.height, 1e-4f)
            assertEquals(1f, start.alpha, 1e-4f)
        }
    }

    @Test
    fun aLongBallClearsTheWholeTableAndLandsBeyondIt() {
        for (p in progressSteps()) {
            val flight = mistakeFlight(MistakeType.LONG, p)
            if (flight.travel <= tableEnd) assertTrue("touched the table at $p", flight.height > 0.3f)
        }
        val end = mistakeFlight(MistakeType.LONG, 1f)
        assertTrue(end.travel > tableEnd)
        assertEquals(0f, end.alpha, 1e-4f)
    }

    @Test
    fun aNetBallStopsAtTheNetAndBouncesOutOnTheHittersOwnHalf() {
        val flights = progressSteps().map { mistakeFlight(MistakeType.NET, it) }
        flights.forEach { flight ->
            assertTrue(flight.travel <= 0.5f + 1e-4f)
            assertTrue(flight.height >= 0f)
        }
        // Some bounces: the ball touches the table more than once after the net.
        val touches = flights.zipWithNext().count { (a, b) -> a.height > 0.001f && b.height <= 0.001f }
        assertTrue("bounces: $touches", touches >= 2)
        val end = flights.last()
        assertTrue(end.travel in tableStart..0.5f)
        assertEquals(0f, end.height, 1e-3f)
    }

    @Test
    fun aMissedBallCarriesOnPastTheBatOffTheTable() {
        progressSteps().forEach { assertTrue(mistakeFlight(MistakeType.WHIFF, it).travel <= 0f) }
        val end = mistakeFlight(MistakeType.WHIFF, 1f)
        assertTrue(end.travel < -tableStart)
        assertTrue(end.height < 0f)
        assertEquals(0f, end.alpha, 1e-4f)
    }
}
