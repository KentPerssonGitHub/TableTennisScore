package com.example.tabletennisscore.animation
import com.example.tabletennisscore.GameViewModel
import com.example.tabletennisscore.R

import android.animation.ValueAnimator
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import com.example.tabletennisscore.databinding.ActivityMainBinding
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

private const val SERVE_BOUNCE_TRAVEL_OVERFLOW = 0.05f
private const val SERVE_BOUNCE_HEIGHT_RATIO = 0.20f
// How far above and below the middle line a return may end, as a share of the table height.
// Downwards (towards the near edge) is allowed a bit further than upwards.
private const val RETURN_Y_UP_SPREAD_RATIO = 0.16f
private const val RETURN_Y_DOWN_SPREAD_RATIO = 0.40f
private const val BAT_IDLE_SWING_ANGLE = 52f
private const val BAT_SWING_ANGLE = 34f
// Before the strike a bat winds up towards the ball, part-way, holds there a while (its "top" position),
// then snaps forward the rest of the way in; after the strike it eases back to rest. The windup starts
// early, well before the ball arrives, reaching its top position with plenty of time to spare before the
// short, fast final strike, which finishes exactly at the hit. Progress is over a 2-leg cycle, so a leg is
// half of this unit; at the baseline pace (a leg a bit under 2 seconds) BAT_PAUSE_WINDOW alone is roughly
// half a second of waiting, and BAT_STRIKE_WINDOW is a bit over half a second too.
private const val BAT_BACKSWING_WINDOW = 0.495f
private const val BAT_PAUSE_WINDOW = 0.19f
private const val BAT_STRIKE_WINDOW = 0.105f
// A share of BAT_SWING_ANGLE: how far towards the ball the top position already is, before the final snap
// covers the rest. At 0.25 that was only ~8.5 degrees, too small next to the 34-degree forward stroke to
// read as motion, so the windup went unnoticed and only the fast final strike was visible — as if the bat
// waited until the ball had already arrived. Bigger, so the windup itself is unmistakable.
private const val BAT_BACKSWING_PEAK = 0.55f
private const val BAT_RECOVERY_WINDOW = 0.085f
// Shifts the whole swing (windup, pause, strike, recovery) this much later, so the strike lands relative
// to when the ball reaches the bat; negative (as tuned by feel) makes it land earlier instead. In progress
// units; at the baseline pace this is about -200ms.
private const val BAT_SWING_DELAY = -0.056f
private const val BAT_HEAD_CENTER_Y_RATIO = 35f / 112f
// Pivot near the far end of the handle, like a wrist. The rubber (red/black) part that actually meets the
// ball sits far from it, so the same swing angle sweeps that part through a much bigger arc than the
// handle end, which stays close to the pivot and barely moves — the impact-making part does the visible work.
private const val BAT_PIVOT_Y_RATIO = 0.85f
// A bat also moves forward as it swings, not just rotates: pulled back at rest and further still at the
// peak of the backswing, arriving exactly at the ball at the moment of the strike. As a share of its width.
private const val BAT_FORWARD_TRAVEL_RATIO = 0.16f
// Bats sit this much further out, towards their screen edge, than where the ball actually is — so the ball
// meets each bat nearer the middle of its head instead of right at the edge. The ball's own path is
// unaffected; only where the bats are drawn shifts.
private const val BAT_EDGE_OFFSET_DP = 20f
// Bats are also drawn this much higher than where the ball actually is. Like the edge offset, this only
// shifts where the bats are drawn; the ball's own path is unaffected.
private const val BAT_RAISE_DP = 10f
// High up the table a bat plays topspin (see topspinAmount): it winds up much further back and low, below
// the ball, drives forward and up through it, and follows through over the top of the ball. "Low" and "over"
// are height above the table, towards the viewer looking down on it, so they show as the bat drawn smaller
// or bigger, not as moving up or down the screen. Distances are shares of the bat's width.
/** How far back, away from the table, the bat pulls at the top of a topspin windup. */
private const val TOPSPIN_BACK_RATIO = 0.9f
/** At the top of a topspin windup the bat is lowered below the ball: drawn this much smaller. */
private const val TOPSPIN_WINDUP_SINK = 0.12f
/** After the hit, how far the bat keeps going forward, towards the table, over the ball. */
private const val TOPSPIN_FOLLOW_FORWARD_RATIO = 0.35f
/** After the hit the bat lifts up over the ball, towards the viewer: drawn at most this much bigger. */
private const val TOPSPIN_FOLLOW_LIFT = 0.3f
/**
 * While a bat is lifted over the ball it is raised above the ball's view (which has none) so it covers the
 * ball; kept below the tap areas (2dp) so they still get every tap.
 */
private const val BAT_OVER_BALL_Z_DP = 1f
/** A topspin swing's windup top position, as a share of BAT_SWING_ANGLE (replaces BAT_BACKSWING_PEAK). */
private const val TOPSPIN_BACKSWING_PEAK = 0.9f
/** A topspin swing follows through for longer than the normal BAT_RECOVERY_WINDOW. */
private const val TOPSPIN_FOLLOW_THROUGH_WINDOW = 0.2f
/**
 * At the top of a topspin windup the bat's head is tilted this many degrees further back, so that it leans
 * away from the table, towards its own end of the screen, instead of towards the table like a normal windup.
 */
private const val TOPSPIN_WINDUP_LEAN_ANGLE = 45f
/**
 * A topspin windup starts later than a normal one (BAT_BACKSWING_WINDOW) and holds its top position for a
 * shorter time (instead of BAT_PAUSE_WINDOW), so the bat only gets ready shortly before the ball arrives.
 */
private const val TOPSPIN_BACKSWING_WINDOW = -0.12f
private const val TOPSPIN_PAUSE_WINDOW = 0.04f

/**
 * Drives the match-mode serve/rally animation: the ball ([RallyBallView]) and the two
 * bat ImageViews swinging in sync with it. The animation restarts whenever the score changes.
 */
class RallyAnimationController(
    private val binding: ActivityMainBinding,
    private val currentState: () -> GameViewModel.GameState?,
) {

    private var rallyStartsFromLeft = true
    private var lastRallyScoreKey: List<Int>? = null
    private var rallyBatAnimator: ValueAnimator? = null

    /**
     * Where a bat's head meets the ball on the middle line, the bat's swing angle at that moment, and
     * which horizontal direction ([forwardSign]: +1 or -1) is "forward", into its stroke.
     */
    private class BatContact(val x: Float, val y: Float, val strikeRotation: Float, val forwardSign: Float)

    /**
     * Where a bat is in its stroke: [swing] as a fraction of [BAT_SWING_ANGLE] (see [swingPose]), plus the
     * extra topspin motion: [back] pixels away from the table (negative is towards it) and [lift], the height
     * above the ball as a share of the bat's size (negative is below it), which shows as the bat drawn bigger,
     * and [lean], degrees the head tilts back, away from the table.
     */
    private class SwingPose(val swing: Float, val back: Float = 0f, val lift: Float = 0f, val lean: Float = 0f)

    private var leftContact: BatContact? = null
    private var rightContact: BatContact? = null

    // Each bat shows one color on its forehand and the other on its backhand side.
    private val leftFlipper by lazy {
        BatFlipper(binding.ivBatLeft, R.drawable.ic_table_tennis_bat_black, R.drawable.ic_table_tennis_bat)
    }
    private val rightFlipper by lazy {
        BatFlipper(binding.ivBatRight, R.drawable.ic_table_tennis_bat, R.drawable.ic_table_tennis_bat_black)
    }

    private val density: Float
        get() = binding.root.resources.displayMetrics.density

    fun resetBatAngles() {
        leftFlipper.reset()
        rightFlipper.reset()
        binding.ivBatLeft.rotation = BAT_IDLE_SWING_ANGLE
        binding.ivBatRight.rotation = -BAT_IDLE_SWING_ANGLE
        leftContact?.let { placeBat(binding.ivBatLeft, leftFlipper, it, heightOffset = 0f, SwingPose(0f)) }
        rightContact?.let { placeBat(binding.ivBatRight, rightFlipper, it, heightOffset = 0f, SwingPose(0f)) }
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
                this.shadowTableLeft = tableLeft
                this.shadowTableRight = tableLeft + tableWidth
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
        val hitCenterY = baseY + (ballHeight / 2f) - (BAT_RAISE_DP * density)
        val edgeOffset = BAT_EDGE_OFFSET_DP * density

        val leftContact = BatContact(
            leftHitCenterX - edgeOffset,
            hitCenterY,
            BAT_IDLE_SWING_ANGLE - BAT_SWING_ANGLE,
            forwardSign = 1f,
        )
        val rightContact = BatContact(
            rightHitCenterX + edgeOffset,
            hitCenterY,
            -(BAT_IDLE_SWING_ANGLE - BAT_SWING_ANGLE),
            forwardSign = -1f,
        )
        this.leftContact = leftContact
        this.rightContact = rightContact
        placeBat(binding.ivBatLeft, leftFlipper, leftContact, heightOffset = 0f, SwingPose(0f))
        placeBat(binding.ivBatRight, rightFlipper, rightContact, heightOffset = 0f, SwingPose(0f))

        if (rallyBatAnimator == null) {
            resetBatAngles()
        }
    }

    /**
     * Sets the position of [bat] so that its head is at [contact] (moved [heightOffset] pixels down) once
     * the [pose]'s swing reaches 1, the moment of the strike; at lower (or negative, backswing) values the
     * bat also sits a little behind that point, so it visibly moves forward into the ball as it swings, not
     * just rotates. The pose's topspin motion moves it further back or forward and lifts it (drawn bigger)
     * around its head, so the head stays on the ball's line. Also accounts for the bat possibly being
     * turned over: [BatFlipper.flip] is 1 with the handle down, -1 with the handle up, and passes through 0
     * while it flips.
     */
    private fun placeBat(bat: View, flipper: BatFlipper, contact: BatContact, heightOffset: Float, pose: SwingPose) {
        flipper.zoom = 1f + pose.lift
        // Only a bat above the ball covers it; otherwise the ball, drawn later, stays in front.
        bat.translationZ = if (pose.lift > 0f) BAT_OVER_BALL_Z_DP * density else 0f
        val verticalSign = flipper.flip
        val zoom = flipper.zoom
        val pivotX = bat.width * 0.5f
        val pivotY = bat.height * BAT_PIVOT_Y_RATIO
        val headCenterX = bat.width * 0.5f
        val headCenterY = bat.height * BAT_HEAD_CENTER_Y_RATIO
        val dx = (headCenterX - pivotX) * zoom
        val dy = (headCenterY - pivotY) * verticalSign * zoom
        // A turned-over bat is the mirror image of the normal one, so its swing angle is mirrored too.
        val radians = Math.toRadians((contact.strikeRotation * verticalSign).toDouble())
        val rotatedDx = (dx * cos(radians) - dy * sin(radians)).toFloat()
        val rotatedDy = (dx * sin(radians) + dy * cos(radians)).toFloat()
        val backTravel = (bat.width * BAT_FORWARD_TRAVEL_RATIO * (1f - pose.swing) + pose.back) * contact.forwardSign

        bat.pivotX = pivotX
        bat.pivotY = pivotY
        bat.x = contact.x - backTravel - pivotX - rotatedDx
        bat.y = contact.y + heightOffset - pivotY - rotatedDy
    }

    private fun startBatAnimation() {
        stopBatAnimation()
        rallyBatAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            // Duration/value are unused: this animator is only a frame ticker. The actual timing comes
            // from the renderer's legPosition, which paces each leg a little differently (see updateBatAngles).
            duration = binding.glRallyBall.renderer.fullCycleDurationMillis
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { updateBatAngles() }
            start()
        }
    }

    private fun stopBatAnimation() {
        rallyBatAnimator?.cancel()
        rallyBatAnimator = null
        resetBatAngles()
    }

    private fun updateBatAngles() {
        // Each bat hits once every 2 legs, so a strike cycle is 2 legs long; legs vary a little in speed
        // (see RallyYPath.legPositionAt), and reading the swing timing from legPosition itself, rather
        // than from a fixed-duration animator, keeps a bat's swing in step with its own hit even so.
        val renderer = binding.glRallyBall.renderer
        val path = renderer.yPath
        val legPosition = renderer.legPosition
        val cycleProgress = (legPosition % 2f) / 2f

        val leftStrikePoint = if (rallyStartsFromLeft) 0f else 0.5f
        val rightStrikePoint = if (rallyStartsFromLeft) 0.5f else 0f
        // Follow the ball's up/down drift so each bat is at the right height when it hits.
        val leftOffset = path.batOffset(strikesOnEvenBoundaries = rallyStartsFromLeft, legPosition)
        val rightOffset = path.batOffset(strikesOnEvenBoundaries = !rallyStartsFromLeft, legPosition)

        // High up the table a bat plays topspin. Judged by the hit the bat is busy with, not by where it is
        // right now, so a stroke doesn't change style halfway while the bat glides into place.
        val upSpread = renderer.returnYUpSpread
        val leftTopspin = topspinAmount(path.strikeOffset(strikesOnEvenBoundaries = rallyStartsFromLeft, legPosition), upSpread)
        val rightTopspin = topspinAmount(path.strikeOffset(strikesOnEvenBoundaries = !rallyStartsFromLeft, legPosition), upSpread)
        val leftPose = swingPose(cycleProgress, leftStrikePoint, leftTopspin, binding.ivBatLeft.width.toFloat())
        val rightPose = swingPose(cycleProgress, rightStrikePoint, rightTopspin, binding.ivBatRight.width.toFloat())

        // Far down the table a bat plays the ball backhand: it turns over (handle up) and shows its other rubber.
        val downSpread = renderer.returnYDownSpread
        leftFlipper.setBackhand(isBackhandHeight(leftOffset, downSpread))
        rightFlipper.setBackhand(isBackhandHeight(rightOffset, downSpread))

        // A turned-over bat is the mirror image of the normal one, so it also swings the other way around.
        binding.ivBatLeft.rotation = leftFlipper.flip *
            (BAT_IDLE_SWING_ANGLE - (BAT_SWING_ANGLE * leftPose.swing) - leftPose.lean)
        binding.ivBatRight.rotation = rightFlipper.flip *
            (-BAT_IDLE_SWING_ANGLE + (BAT_SWING_ANGLE * rightPose.swing) + rightPose.lean)
        leftContact?.let { placeBat(binding.ivBatLeft, leftFlipper, it, leftOffset, leftPose) }
        rightContact?.let { placeBat(binding.ivBatRight, rightFlipper, it, rightOffset, rightPose) }
    }

    /**
     * Where a bat is in its stroke at this point in its cycle. The swing is a fraction of [BAT_SWING_ANGLE]:
     * 0 at rest, rising to a partial "top" position (part-way towards the ball), holding there a moment, then
     * a fast final snap the rest of the way up to +1, [BAT_SWING_DELAY] after the strike (swung fully into
     * the ball). After the strike it eases back to rest.
     *
     * With [topspin] (0..1) the windup starts later and the bat pulls far back and low, below the ball, its
     * head tilted back away from the table; it drives forward and up into the ball during the strike, and
     * after it keeps going forward and up over the top of the ball, before settling back to rest. [batWidth]
     * scales the forward/back distances.
     */
    private fun swingPose(progress: Float, strikePoint: Float, topspin: Float, batWidth: Float): SwingPose {
        var distance = progress - strikePoint - BAT_SWING_DELAY
        distance -= floor(distance + 0.5f) // wrap into (-0.5, 0.5]

        val backswingPeak = BAT_BACKSWING_PEAK + (TOPSPIN_BACKSWING_PEAK - BAT_BACKSWING_PEAK) * topspin
        val windupBack = batWidth * TOPSPIN_BACK_RATIO * topspin
        val windupLift = -TOPSPIN_WINDUP_SINK * topspin
        val windupLean = TOPSPIN_WINDUP_LEAN_ANGLE * topspin
        val backswingWindow = BAT_BACKSWING_WINDOW + (TOPSPIN_BACKSWING_WINDOW - BAT_BACKSWING_WINDOW) * topspin
        val pauseWindow = BAT_PAUSE_WINDOW + (TOPSPIN_PAUSE_WINDOW - BAT_PAUSE_WINDOW) * topspin

        val pauseStart = -(BAT_STRIKE_WINDOW + pauseWindow)
        if (distance in -backswingWindow..pauseStart) {
            // Slow windup: ease from rest to the top position, well before the strike.
            val windUpSpan = backswingWindow - BAT_STRIKE_WINDOW - pauseWindow
            val windUp = (distance + backswingWindow) / windUpSpan // 0 at the start, 1 at the top position
            val eased = windUp * windUp * (3f - 2f * windUp) // smoothstep
            return SwingPose(backswingPeak * eased, windupBack * eased, windupLift * eased, windupLean * eased)
        }
        if (distance in pauseStart..(-BAT_STRIKE_WINDOW)) {
            // Wait: hold at the top position before swinging in.
            return SwingPose(backswingPeak, windupBack, windupLift, windupLean)
        }
        if (distance in -BAT_STRIKE_WINDOW..0f) {
            // Fast strike: accelerate hard from the top position the rest of the way into the ball.
            val strike = 1f + (distance / BAT_STRIKE_WINDOW) // 0 at the top position, 1 at the strike
            val eased = strike * strike // ease-in: gentle start, hardest acceleration right at the ball
            val remaining = 1f - eased
            return SwingPose(
                backswingPeak + (1f - backswingPeak) * eased,
                windupBack * remaining,
                windupLift * remaining,
                windupLean * remaining,
            )
        }
        val followThroughWindow = BAT_RECOVERY_WINDOW + (TOPSPIN_FOLLOW_THROUGH_WINDOW - BAT_RECOVERY_WINDOW) * topspin
        if (distance in 0f..followThroughWindow) {
            val followThrough = distance / followThroughWindow // 0 at the strike, 1 back at rest
            val swing = sin((1f - followThrough) * (PI.toFloat() / 2f))
            // Out and back again: furthest over the ball halfway through, at rest at both ends.
            val over = sin(followThrough * PI.toFloat()) * topspin
            return SwingPose(
                swing,
                back = -batWidth * TOPSPIN_FOLLOW_FORWARD_RATIO * over,
                lift = TOPSPIN_FOLLOW_LIFT * over,
            )
        }
        return SwingPose(0f)
    }
}
