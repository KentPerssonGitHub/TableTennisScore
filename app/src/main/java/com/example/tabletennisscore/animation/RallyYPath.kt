package com.example.tabletennisscore.animation

import kotlin.math.floor

/**
 * The sideways drift of a rally: where, up or down on the table, each hit of the ball ends.
 *
 * A rally is a series of legs. Leg 0 is the serve, leg 1 the first return, and so on. Boundary `k` is the
 * moment leg `k - 1` ends and leg `k` starts, which is when a bat hits the ball. The serve starts and ends
 * on the middle line; from the first return on, every hit lands at a new random height between [upSpread]
 * pixels above and [downSpread] pixels below the middle line. The same [seed] always gives the same rally,
 * so the ball and the bats, drawn on different threads, agree on where every hit is.
 *
 * Offsets are in pixels, positive is down. Spreads of 0 give the plain straight rally.
 */
class RallyYPath(
    private val seed: Int,
    private val upSpread: Float,
    private val downSpread: Float = upSpread,
) {

    /**
     * Vertical offset of the ball at the hit that ends leg `boundary - 1` and starts leg `boundary`.
     *
     * A bat never goes straight from a backhand stroke to a topspin one: when its previous hit (two
     * boundaries back) was backhand, this hit is kept low enough on the table not to be topspin.
     */
    fun boundaryOffset(boundary: Int): Float {
        val offset = randomOffset(boundary)
        if (offset < 0f && isBackhandHeight(randomOffset(boundary - LEGS_BETWEEN_STRIKES), downSpread)) {
            return offset * TOPSPIN_START_RATIO
        }
        return offset
    }

    /**
     * The random hit height at [boundary], before [boundaryOffset]'s backhand-to-topspin rule. That rule
     * only lowers hits up the table, never makes one backhand, so a hit is backhand here exactly when it is
     * in [boundaryOffset] too.
     */
    private fun randomOffset(boundary: Int): Float {
        if (boundary <= FIRST_RANDOM_BOUNDARY - 1) return 0f
        val random = unitRandom(boundary) * 2f - 1f // -1 (highest) .. +1 (lowest)
        return if (random < 0f) random * upSpread else random * downSpread
    }

    /** Vertical offset of the ball at [legPosition]: the leg number plus how far (0..1) it is through it. */
    fun ballOffset(legPosition: Float): Float {
        val leg = floor(legPosition).toInt()
        val progress = legPosition - leg
        val from = boundaryOffset(leg)
        val to = boundaryOffset(leg + 1)
        return from + (to - from) * progress
    }

    /**
     * Vertical offset of a bat at [legPosition]. A bat hits every second boundary: the even ones when
     * [strikesOnEvenBoundaries], otherwise the odd ones. After a hit it stays still while the ball flies
     * to the other side and back. Only when the ball has passed the net on its way to the bat does the bat
     * start to glide, smoothly, to the height of the next hit, arriving at rest exactly where the ball
     * meets it.
     */
    fun batOffset(strikesOnEvenBoundaries: Boolean, legPosition: Float): Float {
        val parity = if (strikesOnEvenBoundaries) 0 else 1
        var previousStrike = floor(legPosition).toInt()
        if (Math.floorMod(previousStrike, 2) != parity) previousStrike -= 1

        val legsSinceStrike = legPosition - previousStrike
        val fraction = ((legsSinceStrike - BAT_GLIDE_START) / (LEGS_BETWEEN_STRIKES - BAT_GLIDE_START))
            .coerceIn(0f, 1f)
        val eased = fraction * fraction * (3f - 2f * fraction)
        val from = boundaryOffset(previousStrike)
        val to = boundaryOffset(previousStrike + LEGS_BETWEEN_STRIKES)
        return from + (to - from) * eased
    }

    /**
     * Vertical offset of the hit a bat is busy with at [legPosition]: the one it is winding up for, or the
     * one it just played while it follows through. It switches halfway between two of the bat's hits, while
     * the bat is resting, so a whole stroke (windup, strike, follow-through) belongs to the same hit.
     */
    fun strikeOffset(strikesOnEvenBoundaries: Boolean, legPosition: Float): Float {
        val parity = if (strikesOnEvenBoundaries) 0 else 1
        var previousStrike = floor(legPosition).toInt()
        if (Math.floorMod(previousStrike, 2) != parity) previousStrike -= 1
        val nearestStrike = if (legPosition - previousStrike > LEGS_BETWEEN_STRIKES / 2f) {
            previousStrike + LEGS_BETWEEN_STRIKES
        } else {
            previousStrike
        }
        return boundaryOffset(nearestStrike)
    }

    /**
     * How long leg [leg] takes to play, as a share of the base duration: bats hit the ball a little faster
     * or slower each time, so some legs look a bit quicker than others.
     */
    private fun legDurationFactor(leg: Int): Float {
        val random = unitRandom(leg, LEG_SPEED_SALT)
        return LEG_SPEED_MIN + random * (LEG_SPEED_MAX - LEG_SPEED_MIN)
    }

    /**
     * The leg position (the leg number plus how far, 0..1, through it) at [elapsedMillis] since the rally
     * started, given [baseDurationMillis] as how long a leg takes at the normal pace. Each leg's own pace
     * varies a little (see [legDurationFactor]), so some hits look a bit quicker or slower than others; the
     * same [seed] always gives the same pacing, so the ball (drawn on one thread) and the bats (drawn on
     * another) agree on when every leg ends.
     */
    fun legPositionAt(elapsedMillis: Long, baseDurationMillis: Long): Float {
        var leg = 0
        var remaining = elapsedMillis
        while (true) {
            val legDuration = (baseDurationMillis * legDurationFactor(leg)).toLong().coerceAtLeast(1L)
            if (remaining < legDuration) return leg + remaining.toFloat() / legDuration.toFloat()
            remaining -= legDuration
            leg++
        }
    }

    /** A repeatable pseudo-random number in [0, 1) for [index], one independent stream per [salt]. */
    private fun unitRandom(index: Int, salt: Int = 0): Float {
        var x = seed xor (index * GOLDEN_RATIO_HASH) xor salt
        x = x xor (x ushr 16)
        x *= MIX_MULTIPLIER
        x = x xor (x ushr 16)
        x *= MIX_MULTIPLIER
        x = x xor (x ushr 16)
        return (x ushr 8) / 16_777_216f
    }

    private companion object {
        /** The serve (boundaries 0 and 1) stays on the middle line; returns start at boundary 2. */
        const val FIRST_RANDOM_BOUNDARY = 2

        /** A bat hits the ball every second boundary. */
        const val LEGS_BETWEEN_STRIKES = 2

        /** Legs after its own hit that the ball passes the net on its way back: the bat starts to move. */
        const val BAT_GLIDE_START = 1.5f
        const val GOLDEN_RATIO_HASH = -1640531535
        const val MIX_MULTIPLIER = 0x45d9f3b

        /**
         * How long a leg takes, as a share of the base duration: every leg plays faster than the base,
         * some a lot faster and some only a little.
         */
        const val LEG_SPEED_MIN = 0.65f
        const val LEG_SPEED_MAX = 0.9f

        /** Keeps leg-speed randomness independent of the Y-offset randomness, which uses the default salt. */
        const val LEG_SPEED_SALT = 0x2545f491
    }
}

/** A bat hits backhand when it is this far into the downward range, measured from the middle line. */
private const val BACKHAND_DEPTH_RATIO = 0.7f

/**
 * Whether a bat at the given vertical [offset] (pixels, positive is down) is so far down the table that it
 * plays the ball backhand. [downSpread] is the lowest offset a return can reach.
 */
fun isBackhandHeight(offset: Float, downSpread: Float): Boolean {
    return downSpread > 0f && offset > downSpread * BACKHAND_DEPTH_RATIO
}

/** Hits higher up than this share of the upward range, measured from the middle line, start to get topspin. */
private const val TOPSPIN_START_RATIO = 0.25f

/** Hits at least this high up, as a share of the upward range, are played with a full topspin stroke. */
private const val TOPSPIN_FULL_RATIO = 0.6f

/**
 * How much of a topspin stroke a bat plays for a hit at vertical [offset] (pixels, positive is down): 0 for a
 * normal stroke, rising smoothly to 1 for a full topspin stroke high up the table. [upSpread] is the highest a
 * return can reach above the middle line.
 */
fun topspinAmount(offset: Float, upSpread: Float): Float {
    if (upSpread <= 0f) return 0f
    val height = -offset / upSpread
    val amount = ((height - TOPSPIN_START_RATIO) / (TOPSPIN_FULL_RATIO - TOPSPIN_START_RATIO)).coerceIn(0f, 1f)
    return amount * amount * (3f - 2f * amount)
}
