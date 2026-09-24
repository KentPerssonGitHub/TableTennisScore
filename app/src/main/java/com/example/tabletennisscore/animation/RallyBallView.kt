package com.example.tabletennisscore.animation

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import android.view.animation.AnimationUtils

/**
 * Shows the rally ball ([RallyBallRenderer]).
 *
 * Drawn on the main thread like any other view, once per screen refresh while the ball is animating. That
 * keeps it in the very same frame as the bats (and with the same frame time), so the ball moves and spins
 * smoothly and never drifts out of step with them. Being an ordinary view, it is also layered like one, so a
 * bat raised above it (a higher translationZ) covers the ball.
 */
class RallyBallView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    val renderer = RallyBallRenderer(context).also { it.requestFrame = { postInvalidateOnAnimation() } }

    private var paused = false

    /** Resumes drawing; call from the activity's onResume. */
    fun onResume() {
        paused = false
        postInvalidateOnAnimation()
    }

    /** Stops drawing until [onResume]; call from the activity's onPause. */
    fun onPause() {
        paused = true
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (paused || !renderer.isAnimating) return
        renderer.draw(canvas, AnimationUtils.currentAnimationTimeMillis())
        postInvalidateOnAnimation()
    }
}
