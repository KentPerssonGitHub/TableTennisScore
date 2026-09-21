package com.example.tabletennisscore.animation
import com.example.tabletennisscore.GameViewModel

import android.animation.ValueAnimator
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import com.example.tabletennisscore.databinding.ActivityMainBinding
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val SERVE_BOUNCE_TRAVEL_OVERFLOW = 0.05f
private const val SERVE_BOUNCE_HEIGHT_RATIO = 0.20f
// How far above and below the middle line a return may end, as a share of the table height.
// Downwards (towards the near edge) is allowed a bit further than upwards.
private const val RETURN_Y_UP_SPREAD_RATIO = 0.16f
private const val RETURN_Y_DOWN_SPREAD_RATIO = 0.40f
private const val BAT_IDLE_SWING_ANGLE = 52f
private const val BAT_SWING_ANGLE = 34f
private const val BAT_SWING_WINDOW = 0.085f
private const val BAT_HEAD_CENTER_Y_RATIO = 35f / 112f
// Pivot near the middle of the handle/shaft (instead of its very end) for a more natural swing.
private const val BAT_PIVOT_Y_RATIO = 0.66f

/**
 * Drives the match-mode serve/rally animation: the GL ball ([RallyBallRenderer]) and the two
 * bat ImageViews swinging in sync with it. The animation restarts whenever the score changes.
 */
class RallyAnimationController(
    private val binding: ActivityMainBinding,
    private val currentState: () -> GameViewModel.GameState?,
) {

    private var rallyStartsFromLeft = true
    private var lastRallyScoreKey: List<Int>? = null
    private var rallyBatAnimator: ValueAnimator? = null
    private var batsPositioned = false
    private var leftBatBaseY = 0f
    private var rightBatBaseY = 0f

    private val density: Float
        get() = binding.root.resources.displayMetrics.density

    fun resetBatAngles() {
        binding.ivBatLeft.rotation = BAT_IDLE_SWING_ANGLE
        binding.ivBatRight.rotation = -BAT_IDLE_SWING_ANGLE
        if (batsPositioned) {
            binding.ivBatLeft.y = leftBatBaseY
            binding.ivBatRight.y = rightBatBaseY
        }
    }

    fun startIfNeeded() {
        binding.rootLayout.post {
            val latestState = currentState() ?: return@post
            if (!latestState.isMatchRunning) {
                binding.glRallyBall.renderer.isAnimating = false
                return@post
            }

            val tableRect = computeTableDisplayRect(binding.ivTableBackground) ?: return@post
            val tableWidth = tableRect.width
            val tableHeight = tableRect.height
            if (tableWidth <= 0f || tableHeight <= 0f) return@post

            val tableLeft = tableRect.left
            val tableTop = tableRect.top
            val travelOverflow = tableWidth * SERVE_BOUNCE_TRAVEL_OVERFLOW

            val ballWidth = 20 * density
            val ballHeight = 20 * density

            val travelStartX = (tableLeft - travelOverflow) - (ballWidth / 2f)
            val travelEndX = (tableLeft + tableWidth + travelOverflow) - (ballWidth / 2f)

            val p1OnLeft = !latestState.sidesSwapped
            val serverOnLeft = (p1OnLeft && latestState.server == 1) || (!p1OnLeft && latestState.server == 2)
            val startX = if (serverOnLeft) travelStartX else travelEndX
            val endX = if (serverOnLeft) travelEndX else travelStartX

            val bounceLineY = tableTop + (tableHeight * 0.50f)
            val baseY = bounceLineY - (ballHeight / 2f)
            val arcHeight = tableHeight * SERVE_BOUNCE_HEIGHT_RATIO
            // A point (or undo) changes the score, so the serve animation restarts every time.
            val scoreKey = listOf(latestState.score1, latestState.score2, latestState.sets1, latestState.sets2)
            val scoreChanged = lastRallyScoreKey != scoreKey
            lastRallyScoreKey = scoreKey
            val shouldRestartCycle = !binding.glRallyBall.renderer.isAnimating ||
                rallyStartsFromLeft != serverOnLeft || scoreChanged
            rallyStartsFromLeft = serverOnLeft

            // Bats stay at the fixed left/right table ends and meet the ball at its edge-peak
            // height (t=0/1), matching the current bounce shape without altering it.
            val contactY = baseY - (arcHeight * SERVE_RALLY_PEAK_AMPLITUDE_AT_EDGES)
            updateBatPositions(travelStartX, travelEndX, contactY, ballWidth, ballHeight)

            binding.glRallyBall.renderer.apply {
                if (shouldRestartCycle) {
                    resetAnimationPhase()
                }
                this.leftX = startX
                this.rightX = endX
                this.baseY = baseY
                this.arcHeight = arcHeight
                this.ballWidth = ballWidth
                this.ballHeight = ballHeight
                useServeThenRallyStyle()
                this.returnYUpSpread = tableHeight * RETURN_Y_UP_SPREAD_RATIO
                this.returnYDownSpread = tableHeight * RETURN_Y_DOWN_SPREAD_RATIO
                this.isAnimating = true
            }

            if (shouldRestartCycle || rallyBatAnimator == null) {
                startBatAnimation()
            }
        }
    }

    fun stop() {
        lastRallyScoreKey = null
        binding.glRallyBall.renderer.apply {
            isAnimating = false
            resetAnimationPhase()
        }
        stopBatAnimation()
    }

    private data class TableDisplayRect(val left: Float, val top: Float, val width: Float, val height: Float)

    /**
     * [ivTableBackground] is a full-screen ImageView (fitCenter + a 1.08 extra scale), so its raw
     * view bounds are the whole screen, not the actual rendered table graphic. On top of that, the
     * `bg_table_tennis_table` vector asset itself draws the green playing surface as a small inset
     * rectangle (57,31.62 .. 217,120.38) inside a much larger 274x152 canvas, so ~20.8% margin on
     * every side is baked into the artwork. This computes the real on-screen rectangle of just the
     * green table surface so the ball only travels edge-to-edge of the table itself.
     */
    private fun computeTableDisplayRect(imageView: View): TableDisplayRect? {
        val drawable = (imageView as? ImageView)?.drawable ?: return null
        val intrinsicWidth = drawable.intrinsicWidth.toFloat()
        val intrinsicHeight = drawable.intrinsicHeight.toFloat()
        val viewWidth = imageView.width.toFloat()
        val viewHeight = imageView.height.toFloat()
        if (intrinsicWidth <= 0f || intrinsicHeight <= 0f || viewWidth <= 0f || viewHeight <= 0f) return null

        val fitScale = minOf(viewWidth / intrinsicWidth, viewHeight / intrinsicHeight)
        val displayedWidth = intrinsicWidth * fitScale * imageView.scaleX
        val displayedHeight = intrinsicHeight * fitScale * imageView.scaleY

        val centerX = imageView.x + viewWidth / 2f
        val centerY = imageView.y + viewHeight / 2f

        val canvasLeft = centerX - displayedWidth / 2f
        val canvasTop = centerY - displayedHeight / 2f

        // Green table region within the 274x152 vector viewport.
        val greenLeftRatio = 57f / 274f
        val greenTopRatio = 31.62f / 152f
        val greenWidthRatio = 160f / 274f
        val greenHeightRatio = 88.76f / 152f

        return TableDisplayRect(
            left = canvasLeft + (displayedWidth * greenLeftRatio),
            top = canvasTop + (displayedHeight * greenTopRatio),
            width = displayedWidth * greenWidthRatio,
            height = displayedHeight * greenHeightRatio,
        )
    }

    private fun updateBatPositions(
        leftBallX: Float,
        rightBallX: Float,
        baseY: Float,
        ballWidth: Float,
        ballHeight: Float,
    ) {
        val leftHitCenterX = leftBallX + (ballWidth / 2f)
        val rightHitCenterX = rightBallX + (ballWidth / 2f)
        val hitCenterY = baseY + (ballHeight / 2f)

        binding.ivBatLeft.apply {
            val strikeRotation = BAT_IDLE_SWING_ANGLE - BAT_SWING_ANGLE
            positionBatForContact(this, leftHitCenterX, hitCenterY, strikeRotation)
        }

        binding.ivBatRight.apply {
            val strikeRotation = -(BAT_IDLE_SWING_ANGLE - BAT_SWING_ANGLE)
            positionBatForContact(this, rightHitCenterX, hitCenterY, strikeRotation)
        }

        // Where the bats rest on the middle line; they glide up and down from here to meet the ball.
        leftBatBaseY = binding.ivBatLeft.y
        rightBatBaseY = binding.ivBatRight.y
        batsPositioned = true

        if (rallyBatAnimator == null) {
            resetBatAngles()
        }
    }

    private fun positionBatForContact(
        batView: View,
        contactCenterX: Float,
        contactCenterY: Float,
        strikeRotationDegrees: Float,
    ) {
        val pivotX = batView.width * 0.5f
        val pivotY = batView.height * BAT_PIVOT_Y_RATIO
        val headCenterX = batView.width * 0.5f
        val headCenterY = batView.height * BAT_HEAD_CENTER_Y_RATIO
        val dx = headCenterX - pivotX
        val dy = headCenterY - pivotY
        val radians = Math.toRadians(strikeRotationDegrees.toDouble())
        val rotatedDx = (dx * cos(radians) - dy * sin(radians)).toFloat()
        val rotatedDy = (dx * sin(radians) + dy * cos(radians)).toFloat()

        batView.pivotX = pivotX
        batView.pivotY = pivotY
        batView.x = contactCenterX - pivotX - rotatedDx
        batView.y = contactCenterY - pivotY - rotatedDy
    }

    private fun startBatAnimation() {
        stopBatAnimation()
        rallyBatAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = binding.glRallyBall.renderer.fullCycleDurationMillis
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                updateBatAngles(progress)
            }
            start()
        }
    }

    private fun stopBatAnimation() {
        rallyBatAnimator?.cancel()
        rallyBatAnimator = null
        resetBatAngles()
    }

    private fun updateBatAngles(progress: Float) {
        val leftStrikePoint = if (rallyStartsFromLeft) 0f else 0.5f
        val rightStrikePoint = if (rallyStartsFromLeft) 0.5f else 0f
        val leftSwing = strikePulse(progress, leftStrikePoint)
        val rightSwing = strikePulse(progress, rightStrikePoint)

        binding.ivBatLeft.rotation = BAT_IDLE_SWING_ANGLE - (BAT_SWING_ANGLE * leftSwing)
        binding.ivBatRight.rotation = -BAT_IDLE_SWING_ANGLE + (BAT_SWING_ANGLE * rightSwing)

        // Follow the ball's up/down drift so each bat is at the right height when it hits.
        val renderer = binding.glRallyBall.renderer
        val path = renderer.yPath
        val legPosition = renderer.legPosition
        binding.ivBatLeft.y = leftBatBaseY + path.batOffset(strikesOnEvenBoundaries = rallyStartsFromLeft, legPosition)
        binding.ivBatRight.y = rightBatBaseY + path.batOffset(strikesOnEvenBoundaries = !rallyStartsFromLeft, legPosition)
    }

    private fun strikePulse(progress: Float, strikePoint: Float): Float {
        val directDistance = abs(progress - strikePoint)
        val wrappedDistance = minOf(directDistance, 1f - directDistance)
        if (wrappedDistance >= BAT_SWING_WINDOW) return 0f

        val normalizedDistance = 1f - (wrappedDistance / BAT_SWING_WINDOW)
        return sin(normalizedDistance * (PI.toFloat() / 2f))
    }
}
