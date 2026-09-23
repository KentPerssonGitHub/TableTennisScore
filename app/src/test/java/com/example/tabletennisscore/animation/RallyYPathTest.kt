package com.example.tabletennisscore.animation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.floor

class RallyYPathTest {

    private val spread = 40f
    private val seeds = listOf(0, 1, 42, -7, 123_456, Int.MAX_VALUE, Int.MIN_VALUE)
    private val delta = 0.0001f

    @Test
    fun theServeAndTheStartOfTheFirstReturnStayOnTheMiddleLine() {
        seeds.forEach { seed ->
            val path = RallyYPath(seed, spread)
            assertEquals(0f, path.boundaryOffset(0), delta)
            assertEquals(0f, path.boundaryOffset(1), delta)
            assertEquals(0f, path.boundaryOffset(-3), delta)
        }
    }

    @Test
    fun theWholeServeLegIsOnTheMiddleLine() {
        val path = RallyYPath(5, spread)
        listOf(0f, 0.25f, 0.5f, 0.99f).forEach { assertEquals(0f, path.ballOffset(it), delta) }
    }

    @Test
    fun everyReturnEndsWithinTheSpread() {
        seeds.forEach { seed ->
            val path = RallyYPath(seed, spread)
            for (boundary in 2..2_000) {
                assertTrue(abs(path.boundaryOffset(boundary)) <= spread)
            }
        }
    }

    @Test
    fun returnsEndAtVaryingHeightsAboveAndBelowTheMiddle() {
        val offsets = (2..200).map { RallyYPath(99, spread).boundaryOffset(it) }
        assertTrue("some returns end above the middle line", offsets.any { it < -spread / 4 })
        assertTrue("some returns end below the middle line", offsets.any { it > spread / 4 })
        assertTrue("heights differ from return to return", offsets.toSet().size > 100)
    }

    @Test
    fun consecutiveReturnsUsuallyGetANewDestination() {
        val path = RallyYPath(7, spread)
        val repeats = (2..300).count { path.boundaryOffset(it) == path.boundaryOffset(it + 1) }
        assertEquals(0, repeats)
    }

    @Test
    fun theSameSeedGivesTheSameRallyAndAnotherSeedAnotherOne() {
        val first = RallyYPath(11, spread)
        val again = RallyYPath(11, spread)
        val other = RallyYPath(12, spread)
        val boundaries = 2..30
        assertEquals(boundaries.map { first.boundaryOffset(it) }, boundaries.map { again.boundaryOffset(it) })
        assertNotEquals(boundaries.map { first.boundaryOffset(it) }, boundaries.map { other.boundaryOffset(it) })
    }

    @Test
    fun aSpreadOfZeroGivesTheStraightRally() {
        val path = RallyYPath(3, 0f)
        for (position in listOf(0f, 1.3f, 2.5f, 7.9f)) {
            assertEquals(0f, path.ballOffset(position), delta)
            assertEquals(0f, path.batOffset(true, position), delta)
            assertEquals(0f, path.batOffset(false, position), delta)
        }
    }

    // ----- The ball -----

    @Test
    fun theBallIsAtTheBoundaryOffsetAtEveryHit() {
        val path = RallyYPath(21, spread)
        for (boundary in 0..20) {
            assertEquals(path.boundaryOffset(boundary), path.ballOffset(boundary.toFloat()), delta)
        }
    }

    @Test
    fun theBallFliesInAStraightLineBetweenTwoHits() {
        val path = RallyYPath(21, spread)
        val from = path.boundaryOffset(4)
        val to = path.boundaryOffset(5)
        assertEquals(from + (to - from) * 0.25f, path.ballOffset(4.25f), delta)
        assertEquals((from + to) / 2f, path.ballOffset(4.5f), delta)
    }

    @Test
    fun theBallNeverLeavesTheSpread() {
        seeds.forEach { seed ->
            val path = RallyYPath(seed, spread)
            var position = 0f
            while (position < 200f) {
                assertTrue(abs(path.ballOffset(position)) <= spread + delta)
                position += 0.37f
            }
        }
    }

    // ----- The bats -----

    @Test
    fun eachBatIsAtTheBallsHeightWhenItHitsIt() {
        seeds.forEach { seed ->
            val path = RallyYPath(seed, spread)
            for (boundary in 0..40) {
                val strikesOnEven = boundary % 2 == 0
                assertEquals(
                    "bat hitting at boundary $boundary",
                    path.ballOffset(boundary.toFloat()),
                    path.batOffset(strikesOnEven, boundary.toFloat()),
                    delta,
                )
            }
        }
    }

    @Test
    fun aBatGlidesBetweenItsHitsAndStaysWithinTheSpread() {
        seeds.forEach { seed ->
            val path = RallyYPath(seed, spread)
            var position = 0f
            while (position < 100f) {
                assertTrue(abs(path.batOffset(true, position)) <= spread + delta)
                assertTrue(abs(path.batOffset(false, position)) <= spread + delta)
                position += 0.11f
            }
        }
    }

    @Test
    fun aBatStaysStillAfterItsHitUntilTheBallHasPassedTheNet() {
        seeds.forEach { seed ->
            val path = RallyYPath(seed, spread)
            // The left bat hits at every even boundary. The ball crosses the net at .5 of each leg,
            // and passes it on its way back to this bat at 1.5 legs after the hit.
            for (hit in listOf(0, 2, 4, 10)) {
                var position = hit.toFloat()
                while (position < hit + 1.5f) {
                    assertEquals(
                        "hit $hit, position $position",
                        path.boundaryOffset(hit),
                        path.batOffset(true, position),
                        delta,
                    )
                    position += 0.05f
                }
            }
        }
    }

    @Test
    fun aBatDoesNotMoveWhileTheBallIsStillOnItsWayToTheOtherPlayer() {
        val path = RallyYPath(1234, spread)
        // Ball just hit by the left bat (boundary 2) and now crossing the net away from it.
        assertEquals(path.boundaryOffset(2), path.batOffset(true, 2.5f), delta)
        // The other bat, waiting for the ball to arrive, is also still until the ball is back over the net.
        assertEquals(path.boundaryOffset(1), path.batOffset(false, 2.4f), delta)
    }

    @Test
    fun aBatStartsMovingOnlyAfterTheBallPassesTheNetOnTheWayBack() {
        val path = RallyYPath(1234, spread)
        val restingHeight = path.boundaryOffset(2)
        val nextHitHeight = path.boundaryOffset(4)
        assertNotEquals("the random heights differ", restingHeight, nextHitHeight, 1f)

        assertEquals(restingHeight, path.batOffset(true, 3.49f), delta)
        val moving = path.batOffset(true, 3.75f)
        assertTrue("bat has started moving towards the next hit", abs(moving - restingHeight) > delta)
        assertTrue(
            "and is between the two hit heights",
            moving in minOf(restingHeight, nextHitHeight)..maxOf(restingHeight, nextHitHeight),
        )
    }

    @Test
    fun aBatReachesTheNextHitHeightExactlyAtTheHit() {
        val path = RallyYPath(1234, spread)
        // Just before the hit at boundary 4 the bat is (almost) at the hit height; at the hit it is exact.
        assertEquals(path.boundaryOffset(4), path.batOffset(true, 3.999f), 0.05f)
        assertEquals(path.boundaryOffset(4), path.batOffset(true, 4f), delta)
    }

    @Test
    fun aBatEasesInAndOutOfItsMove() {
        val path = RallyYPath(1234, spread)
        val restingHeight = path.boundaryOffset(2)
        val travel = abs(path.boundaryOffset(4) - restingHeight)
        // Right after starting to move and right before arriving it has hardly moved / has almost arrived.
        assertTrue(abs(path.batOffset(true, 3.52f) - restingHeight) < travel * 0.02f + delta)
        assertTrue(abs(path.batOffset(true, 3.98f) - path.boundaryOffset(4)) < travel * 0.02f + delta)
    }

    @Test
    fun theBatThatHitsFirstInARallyDoesNotMoveBeforeTheFirstReturn() {
        seeds.forEach { seed ->
            val path = RallyYPath(seed, spread)
            // Server's bat hits at boundary 0; the receiver's bat at boundary 1. Both rest on the middle line
            // until the ball has passed the net towards them, and the serve itself is straight.
            var position = 0f
            while (position < 1.5f) {
                assertEquals(0f, path.batOffset(true, position), delta)
                position += 0.05f
            }
            position = 0f
            while (position < 2.5f) {
                assertEquals(0f, path.batOffset(false, position), delta)
                position += 0.05f
            }
        }
    }

    // ----- More room downwards than upwards -----

    @Test
    fun returnsMayEndFurtherBelowTheMiddleLineThanAboveIt() {
        val up = 40f
        val down = 60f
        seeds.forEach { seed ->
            val path = RallyYPath(seed, up, down)
            val offsets = (2..2_000).map { path.boundaryOffset(it) }
            assertTrue("never above the upper limit", offsets.all { it >= -up - delta })
            assertTrue("never below the lower limit", offsets.all { it <= down + delta })
            assertTrue("some returns use the extra room downwards", offsets.any { it > up })
        }
    }

    @Test
    fun theAverageReturnEndsBelowTheMiddleLineWhenMoreRoomIsGivenDownwards() {
        val path = RallyYPath(2024, 40f, 60f)
        val offsets = (2..2_000).map { path.boundaryOffset(it) }
        // Expected average: half the time -20 (half of 40 up), half the time +30 (half of 60 down) = +5.
        assertTrue("average was ${offsets.average()}", offsets.average() > 2.0)
    }

    @Test
    fun theServeIsStillStraightWithUnevenSpreads() {
        val path = RallyYPath(9, 40f, 60f)
        assertEquals(0f, path.boundaryOffset(0), delta)
        assertEquals(0f, path.boundaryOffset(1), delta)
    }

    @Test
    fun theBallAndTheBatsStayWithinTheUnevenLimits() {
        val path = RallyYPath(31, 40f, 60f)
        var position = 0f
        while (position < 100f) {
            listOf(
                path.ballOffset(position),
                path.batOffset(true, position),
                path.batOffset(false, position),
            ).forEach { assertTrue("$it at $position", it in (-40f - delta)..(60f + delta)) }
            position += 0.13f
        }
    }

    @Test
    fun spreadsOfZeroInBothDirectionsGiveTheStraightRally() {
        val path = RallyYPath(3, 0f, 0f)
        assertEquals(0f, path.ballOffset(5.5f), delta)
        assertEquals(0f, path.batOffset(true, 5.5f), delta)
    }

    // ----- Backhand -----

    @Test
    fun aBatFarDownTheTablePlaysBackhand() {
        assertTrue(isBackhandHeight(offset = 55f, downSpread = 60f))
        assertTrue(isBackhandHeight(offset = 60f, downSpread = 60f))
    }

    @Test
    fun aBatOnTheMiddleLineOrUpTheTablePlaysForehand() {
        assertTrue(!isBackhandHeight(offset = 0f, downSpread = 60f))
        assertTrue(!isBackhandHeight(offset = -40f, downSpread = 60f))
        assertTrue(!isBackhandHeight(offset = 30f, downSpread = 60f))
    }

    @Test
    fun theBackhandLimitIsSeventyPercentOfTheDownwardRange() {
        assertTrue(!isBackhandHeight(offset = 41.9f, downSpread = 60f))
        assertTrue(isBackhandHeight(offset = 42.1f, downSpread = 60f))
    }

    @Test
    fun noBatPlaysBackhandInAStraightRally() {
        assertTrue(!isBackhandHeight(offset = 0f, downSpread = 0f))
        assertTrue(!isBackhandHeight(offset = 10f, downSpread = 0f))
    }

    @Test
    fun onlySomeReturnsAreHitBackhandButNotMostOfThem() {
        val path = RallyYPath(2024, 40f, 60f)
        val hits = (2..2_000).map { path.boundaryOffset(it) }
        val backhandShare = hits.count { isBackhandHeight(it, 60f) } / hits.size.toDouble()
        assertTrue("backhand share was $backhandShare", backhandShare in 0.05..0.30)
    }

    // ----- How fast each leg is hit -----

    @Test
    fun theRallyStartsAtLegPositionZero() {
        val path = RallyYPath(5, spread)
        assertEquals(0f, path.legPositionAt(0L, 1_000L), delta)
    }

    @Test
    fun legPositionAdvancesLinearlyWithinALeg() {
        val path = RallyYPath(5, spread)
        val leg0Duration = legDurationMillis(path, leg = 0, baseDurationMillis = 1_000L)
        assertEquals(0.5f, path.legPositionAt(leg0Duration / 2, 1_000L), 0.02f)
    }

    @Test
    fun legsVaryInPaceAndAreAllPlayedFasterThanTheBaseDuration() {
        val path = RallyYPath(5, spread)
        val base = 1_000L
        val durations = (0 until 40).map { legDurationMillis(path, it, base) }
        durations.forEach {
            assertTrue("$it is between 65% and 90% of $base", it in (base * 0.65).toLong()..(base * 0.90).toLong())
        }
        val fastest = durations.minOrNull()!!
        val slowest = durations.maxOrNull()!!
        assertTrue("legs vary in pace (fastest $fastest, slowest $slowest)", slowest - fastest > base * 0.1)
    }

    @Test
    fun theSamePaceComesFromTheSameSeedAndAnotherOneFromAnotherSeed() {
        val first = RallyYPath(11, spread)
        val again = RallyYPath(11, spread)
        val other = RallyYPath(12, spread)
        val base = 1_000L
        assertEquals(legDurationMillis(first, 3, base), legDurationMillis(again, 3, base))
        assertNotEquals(legDurationMillis(first, 3, base), legDurationMillis(other, 3, base))
    }

    /** How many milliseconds leg [leg] takes to play, found by stepping through [path]'s timeline. */
    private fun legDurationMillis(path: RallyYPath, leg: Int, baseDurationMillis: Long): Long {
        var elapsed = 0L
        while (floor(path.legPositionAt(elapsed, baseDurationMillis)).toInt() < leg) elapsed += 1L
        val start = elapsed
        while (floor(path.legPositionAt(elapsed, baseDurationMillis)).toInt() == leg) elapsed += 1L
        return elapsed - start
    }
}
