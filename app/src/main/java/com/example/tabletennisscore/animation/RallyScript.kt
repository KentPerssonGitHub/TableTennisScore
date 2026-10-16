package com.example.tabletennisscore.animation

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** How a rally in a replay ends: the losing player's last stroke goes wrong. */
enum class MistakeType {
    /** The ball flies over the far end of the table without touching it. */
    LONG,

    /** The ball hits the net and drops back, bouncing a few times on the hitter's own side. */
    NET,

    /** The bat misses: the ball doesn't turn around but carries on past the bat, off the table. */
    WHIFF,
}

/**
 * A finite rally, as replayed from a match: [goodLegs] normal legs (leg 0 is the serve), then the mistake,
 * played on leg [goodLegs] by the player who lost the point.
 */
data class RallyScript(val goodLegs: Int, val mistake: MistakeType) {
    /** The leg on which the mistake happens; also the boundary where the loser hits (or misses) the ball. */
    val mistakeLeg: Int get() = goodLegs

    /** The leg position at which the mistake has fully played out. */
    val mistakeEnd: Float get() = mistakeLeg + MISTAKE_LEGS

    /** The leg position at which the whole rally, including a short pause after the mistake, is over. */
    val rallyEnd: Float get() = mistakeEnd + HOLD_AFTER_MISTAKE_LEGS

    companion object {
        /** How many legs of time the mistake itself takes to play out. */
        const val MISTAKE_LEGS = 1.6f

        /** A short pause, in legs, after the mistake before the next point starts. */
        const val HOLD_AFTER_MISTAKE_LEGS = 0.6f
        const val MIN_GOOD_LEGS = 3
        const val MAX_GOOD_LEGS = 6

        /**
         * Share of points that end straight away, which also keeps a replay moving: the server faults the
         * serve, or the receiver misses the return of serve.
         */
        const val SHORT_POINT_CHANCE = 0.3f
    }
}

/**
 * A rally for a replayed point. Leg `k` is hit by the server when `k` is even, and the mistake is played on
 * leg `goodLegs`, so `goodLegs` is chosen with the parity that makes the point's loser play it: even when
 * the server lost the point, odd when the receiver did. Some points (see [RallyScript.SHORT_POINT_CHANCE])
 * end on the serve (0 good legs) or its return (1); the rest after 3 to 6 good strokes.
 */
fun rallyScriptFor(serverWon: Boolean, random: Random): RallyScript {
    val wantEven = !serverWon
    val goodLegs = if (random.nextFloat() < RallyScript.SHORT_POINT_CHANCE) {
        if (wantEven) 0 else 1
    } else {
        (RallyScript.MIN_GOOD_LEGS..RallyScript.MAX_GOOD_LEGS).filter { (it % 2 == 0) == wantEven }.random(random)
    }
    // A serve can go long or into the net, but there is no incoming ball for the server to miss.
    val mistakes = if (goodLegs == 0) MistakeType.entries - MistakeType.WHIFF else MistakeType.entries
    return RallyScript(goodLegs, mistakes.random(random))
}

/**
 * Where the ball is during a mistake, at [progress] (0..1) through it.
 *
 * @property travel how far along the leg the ball is: 0 at the hitter's end, 1 at the far end; below 0 is
 *   beyond the hitter's own end, above 1 beyond the far end. The table itself spans roughly 0.05..0.95.
 * @property height the ball's height above the table, as a share of a normal rally's peak (1 is the height a
 *   bat meets it at); 0 is on the table, below 0 is falling past the table's edge.
 * @property alpha how visible the ball is: 1 normally, fading to 0 as it leaves the scene.
 */
data class MistakeFlight(val travel: Float, val height: Float, val alpha: Float)

/** Net height as a share of a normal rally's peak. */
private const val NET_HEIGHT = 0.35f

/** Share of the mistake taken by the ball's flight into the net; the rest is it dropping back and bouncing. */
private const val NET_HIT_AT = 0.35f

fun mistakeFlight(type: MistakeType, progress: Float): MistakeFlight {
    val p = progress.coerceIn(0f, 1f)
    return when (type) {
        MistakeType.LONG -> MistakeFlight(
            travel = 1.4f * p,
            // A high, long arc that is still well above the table when it passes the far end.
            height = 1f + 0.5f * sin(PI.toFloat() * p) - 0.8f * p,
            alpha = 1f - smoothstep(0.75f, 1f, p),
        )
        MistakeType.NET -> if (p <= NET_HIT_AT) {
            val s = p / NET_HIT_AT
            MistakeFlight(
                travel = 0.5f * s,
                height = 1f + 0.2f * sin(PI.toFloat() * s) - (1f - NET_HEIGHT) * s,
                alpha = 1f,
            )
        } else {
            val q = (p - NET_HIT_AT) / (1f - NET_HIT_AT)
            val decay = (1f - q) * (1f - q)
            MistakeFlight(
                // Drifts back a little from the net, slowing down, on the hitter's own half.
                travel = 0.5f - 0.12f * q * (2f - q),
                // Three bounces, each lower than the one before, until it lies still on the table.
                height = NET_HEIGHT * decay * abs(cos(3.5f * PI.toFloat() * q)),
                alpha = 1f - smoothstep(0.9f, 1f, q),
            )
        }
        MistakeType.WHIFF -> MistakeFlight(
            // Keeps going the way it came, past the bat and off the hitter's end, slowing down as it falls.
            travel = -0.6f * (1f - (1f - p) * (1f - p)),
            height = 1f - 2.2f * p,
            alpha = 1f - smoothstep(0.35f, 0.8f, p),
        )
    }
}

private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
