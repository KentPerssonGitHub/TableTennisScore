package com.example.tabletennisscore.animation

import android.animation.ValueAnimator
import android.widget.ImageView

private const val FLIP_DURATION_MS = 220L

/**
 * Turns a bat over between its forehand and backhand side, like a wrist flip. The bat squashes to nothing
 * and opens again turned around both ways: mirrored sideways and upside down, so the handle that pointed
 * down now points up. The other-colored rubber ([backhandRes] instead of [forehandRes]) shows once it
 * passes edge-on. While it flips, [flip] goes from 1 to -1 (and back).
 *
 * Also owns how big the bat is drawn ([zoom]): the table is seen from above, so a bat lifted higher over it,
 * nearer the viewer, looks bigger.
 */
internal class BatFlipper(
    private val bat: ImageView,
    private val forehandRes: Int,
    private val backhandRes: Int,
) {
    /** The bat's own horizontal scale (the right bat is mirrored in the layout so it faces left). */
    private val baseScaleX = bat.scaleX

    private var backhand = false
    private var showingBackhandFace = false
    private var flipAnimator: ValueAnimator? = null

    init {
        // The bat is raised (translationZ) to cover the ball; it shouldn't cast an elevation shadow when it is.
        bat.outlineProvider = null
    }

    /** 1 with the forehand side up (handle down), -1 with the backhand side up; passes through 0 while it flips. */
    var flip = 1f
        private set

    /** How much bigger than normal the bat is drawn; above 1 it is lifted towards the viewer, below 1 lowered. */
    var zoom = 1f
        set(value) {
            if (field == value) return
            field = value
            applyScale()
        }

    /** Flips the bat to its backhand ([value] true) or forehand side. Does nothing if it already is. */
    fun setBackhand(value: Boolean) {
        if (value == backhand) return
        backhand = value
        flipAnimator?.cancel()
        flipAnimator = ValueAnimator.ofFloat(flip, if (value) -1f else 1f).apply {
            duration = FLIP_DURATION_MS
            addUpdateListener {
                flip = it.animatedValue as Float
                applyScale()
            }
            start()
        }
    }

    /** Puts the bat straight back on its forehand side at normal size, without animation. */
    fun reset() {
        flipAnimator?.cancel()
        flipAnimator = null
        backhand = false
        flip = 1f
        zoom = 1f
        applyScale()
    }

    private fun applyScale() {
        bat.scaleX = baseScaleX * flip * zoom
        bat.scaleY = flip * zoom
        // Past edge-on the other side of the bat is visible.
        showFace(backhandFace = flip < 0f)
    }

    private fun showFace(backhandFace: Boolean) {
        if (backhandFace == showingBackhandFace) return
        showingBackhandFace = backhandFace
        bat.setImageResource(if (backhandFace) backhandRes else forehandRes)
    }
}
