package com.example.tabletennisscore.animation

import android.widget.ImageView

private const val FLIP_DURATION_MS = 220L

/**
 * Turns a bat over between its forehand and backhand side, like a wrist flip. The bat squashes to nothing
 * and opens again turned around both ways: mirrored sideways and upside down, so the handle that pointed
 * down now points up. The other-colored rubber ([backhandRes] instead of [forehandRes]) shows once it
 * passes edge-on. While it flips, [android.view.View.getScaleY] goes from 1 to -1 (and back).
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

    /** Flips the bat to its backhand ([value] true) or forehand side. Does nothing if it already is. */
    fun setBackhand(value: Boolean) {
        if (value == backhand) return
        backhand = value
        bat.animate().cancel()
        bat.animate()
            .scaleX(if (value) -baseScaleX else baseScaleX)
            .scaleY(if (value) -1f else 1f)
            .setDuration(FLIP_DURATION_MS)
            .setUpdateListener { showFaceForCurrentScale() }
            .start()
    }

    /** Puts the bat straight back on its forehand side, without animation. */
    fun reset() {
        if (!backhand && !showingBackhandFace && bat.scaleX == baseScaleX && bat.scaleY == 1f) return
        bat.animate().cancel()
        backhand = false
        bat.scaleX = baseScaleX
        bat.scaleY = 1f
        showFace(backhandFace = false)
    }

    private fun showFaceForCurrentScale() {
        // Past edge-on (scale changed sign compared with the base) the other side of the bat is visible.
        showFace(backhandFace = bat.scaleX * baseScaleX < 0f)
    }

    private fun showFace(backhandFace: Boolean) {
        if (backhandFace == showingBackhandFace) return
        showingBackhandFace = backhandFace
        bat.setImageResource(if (backhandFace) backhandRes else forehandRes)
    }
}
