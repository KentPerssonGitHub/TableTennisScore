package com.example.tabletennisscore.animation
import com.example.tabletennisscore.GameViewModel
import com.example.tabletennisscore.R

import android.animation.ValueAnimator
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import com.example.tabletennisscore.databinding.ActivityMainBinding
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

private const val SERVE_BOUNCE_TRAVEL_OVERFLOW = 0.05f
private const val SERVE_BOUNCE_HEIGHT_RATIO = 0.20f
// How far above and below the middle line a return may end, as a share of the table height.
// Downwards (towards the near edge) is allowed a bit further than upwards.
private const val RETURN_Y_UP_SPREAD_RATIO = 0.16f
private const val RETURN_Y_DOWN_SPREAD_RATIO = 0.40f
private const val BAT_IDLE_SWING_ANGLE = 52f
private const val BAT_SWING_ANGLE = 34f
// A stroke plays out like a real one. From its ready position the bat winds up: it pulls back, away from
// the table, and turns its head back past the angle it hits at. It waits there a moment, then swings
// forward, accelerating all the way into the ball, so it is at its fastest as it meets it and its head
// drives forward, into the ball. It doesn't stop dead at the ball but carries on through it, slowing down,
// and then settles back to its ready position. Times are in progress units, over a 2-leg cycle, so a leg is
// half of this unit; at the baseline pace (a leg a bit under 2 seconds) 0.1 is roughly a third of a second.
/** How long before the hit the bat starts to wind up: just after the ball has left the other player's bat. */
private const val BAT_WINDUP_START = 0.4f
/** How long the bat waits at the top of its windup before it swings forward. */
private const val BAT_PAUSE_WINDOW = 0.1f
/** How long the forward swing takes, from the top of the windup to the hit. */
private const val BAT_STRIKE_WINDOW = 0.105f
/** How long the bat takes to settle back to its ready position after its follow-through. */
private const val BAT_SETTLE_WINDOW = 0.16f
/**
 * How far the bat is turned at the top of its windup, as a share of BAT_SWING_ANGLE (see [SwingPose.swing]):
 * past the angle it hits at, so its head comes forward, into the ball, as it turns back to hit.
 */
private const val BAT_WINDUP_SWING = 1.45f
// Distances from where the bat meets the ball, as a share of its width.
/** How far back, away from the table, the bat waits in its ready position. */
private const val BAT_READY_BACK_RATIO = 0.16f
/** How far back the bat pulls at the top of its windup. */
private const val BAT_WINDUP_BACK_RATIO = 0.35f
/** How far forward, past where it met the ball, the bat carries on after the hit. */
private const val BAT_FOLLOW_FORWARD_RATIO = 0.18f
// Shifts the whole stroke this much later relative to when the ball reaches the bat; negative makes it
// earlier. Slightly early, so the bat is still driving forward through the ball as the ball turns around
// and leaves. In progress units; at the baseline pace this is about -70ms.
private const val BAT_SWING_DELAY = -0.02f
private const val BAT_HEAD_CENTER_Y_RATIO = 35f / 112f
// Pivot near the far end of the handle, like a wrist. The rubber (red/black) part that actually meets the
// ball sits far from it, so the same swing angle sweeps that part through a much bigger arc than the
// handle end, which stays close to the pivot and barely moves — the impact-making part does the visible work.
private const val BAT_PIVOT_Y_RATIO = 0.85f
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
private const val TOPSPIN_BACK_RATIO = 0.92f
/** At the top of a topspin windup the bat is lowered below the ball: drawn this much smaller. */
private const val TOPSPIN_WINDUP_SINK = 0.12f
/** After the hit, how far the bat keeps going forward, towards the table, over the ball. */
private const val TOPSPIN_FOLLOW_FORWARD_RATIO = 0.35f
/** After the hit the bat lifts up over the ball, towards the viewer: drawn at most this much bigger. */
private const val TOPSPIN_FOLLOW_LIFT = 0.3f
/** How long after the hit the bat takes to lift up over the ball, brushing up it, before it settles. */
private const val TOPSPIN_LIFT_WINDOW = 0.08f
/**
 * While a bat is lifted over the ball it is raised above the ball's view (which has none) so it covers the
 * ball; kept below the tap areas (2dp) so they still get every tap.
 */
private const val BAT_OVER_BALL_Z_DP = 1f
/**
 * How far a topspin windup turns the bat (replaces BAT_WINDUP_SWING): its head tilts much further back, so
 * that it leans away from the table, towards its own end of the screen.
 */
private const val TOPSPIN_WINDUP_SWING = 2.2f
/**
 * A topspin windup starts later than a normal one (BAT_WINDUP_START) and waits at its top for a shorter
 * time (instead of BAT_PAUSE_WINDOW), so the bat only gets ready shortly before the ball arrives.
 */
private const val TOPSPIN_WINDUP_START = 0.3f
private const val TOPSPIN_PAUSE_WINDOW = 0.04f
/** In a replayed miss (see [MistakeType.WHIFF]) the bat swings this much late, in cycle units, and misses. */
private const val WHIFF_LATE_SWING = 0.06f

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
     * A finite rally (replay) instead of the endless one of a live match: no bat hits after the mistake, and
     * [onRallyFinished] is called once when it is over. Set it before the score change that starts the rally.
     */
    var script: RallyScript?
        get() = binding.glRallyBall.renderer.script
        set(value) {
            // The finished flag is only cleared when the new rally actually restarts (see startIfNeeded);
            // until then the old rally's frames must not report this one as finished.
            binding.glRallyBall.renderer.script = value
        }

    /** Called once, on the main thread, when a scripted rally (see [script]) has fully played out. */
    var onRallyFinished: (() -> Unit)? = null
    private var rallyFinishedReported = false

    /**
     * Where a bat's head meets the ball on the middle line, the bat's swing angle at that moment, and
     * which horizontal direction ([forwardSign]: +1 or -1) is "forward", into its stroke.
     */
    private class BatContact(val x: Float, val y: Float, val strikeRotation: Float, val forwardSign: Float)

    /**
     * Where a bat is in its stroke (see [swingPose]). [swing] is how far it is turned from its ready angle,
     * as a fraction of [BAT_SWING_ANGLE]: 1 at the angle it hits the ball at, above 1 turned further, its head
     * tilted back away from the table as at the top of a windup. [back] is how far it is from where it meets
     * the ball, away from the table (negative is towards it), as a share of its width. [lift] is its height
     * above the ball as a share of its size (negative is below it), which shows as the bat drawn bigger.
     */
    private class SwingPose(val swing: Float, val back: Float, val lift: Float = 0f) {
        companion object {
            /** At rest in the ready position, waiting for the ball. */
            val READY = SwingPose(0f, BAT_READY_BACK_RATIO)
        }
    }

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
        leftContact?.let { placeBat(binding.ivBatLeft, leftFlipper, it, heightOffset = 0f, SwingPose.READY) }
        rightContact?.let { placeBat(binding.ivBatRight, rightFlipper, it, heightOffset = 0f, SwingPose.READY) }
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

            if (shouldRestartCycle) rallyFinishedReported = false
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
        placeBat(binding.ivBatLeft, leftFlipper, leftContact, heightOffset = 0f, SwingPose.READY)
        placeBat(binding.ivBatRight, rightFlipper, rightContact, heightOffset = 0f, SwingPose.READY)

        if (rallyBatAnimator == null) {
            resetBatAngles()
        }
    }

    /**
     * Sets the position of [bat] so that its head is at [contact] (moved [heightOffset] pixels down) at the
     * moment of the strike, when the [pose] is turned to the hit angle (swing 1) and not back at all. The
     * pose's [SwingPose.back] moves it back from there (or forward, past the ball), so it visibly moves into
     * the ball as it swings, not just rotates; its lift draws it bigger around its head, so the head stays
     * on the ball's line. Also accounts for the bat possibly being turned over: [BatFlipper.flip] is 1 with
     * the handle down, -1 with the handle up, and passes through 0 while it flips.
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
        val backTravel = bat.width * pose.back * contact.forwardSign

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
        val script = renderer.script
        val rawLegPosition = renderer.legPosition
        if (script != null && !rallyFinishedReported && rawLegPosition >= script.rallyEnd) {
            rallyFinishedReported = true
            onRallyFinished?.invoke()
        }
        // After a scripted mistake there is no next hit: the bats stay where they are, at rest.
        val legPosition = if (script != null) minOf(rawLegPosition, script.mistakeLeg + 0.5f) else rawLegPosition
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
        val leftPose = scriptedSwingPose(script, legPosition, cycleProgress, leftStrikePoint, leftTopspin)
        val rightPose = scriptedSwingPose(script, legPosition, cycleProgress, rightStrikePoint, rightTopspin)

        // Far down the table a bat plays the ball backhand: it turns over (handle up) and shows its other rubber.
        val downSpread = renderer.returnYDownSpread
        leftFlipper.setBackhand(isBackhandHeight(leftOffset, downSpread))
        rightFlipper.setBackhand(isBackhandHeight(rightOffset, downSpread))

        // A turned-over bat is the mirror image of the normal one, so it also swings the other way around.
        binding.ivBatLeft.rotation = leftFlipper.flip * (BAT_IDLE_SWING_ANGLE - (BAT_SWING_ANGLE * leftPose.swing))
        binding.ivBatRight.rotation = rightFlipper.flip * (-BAT_IDLE_SWING_ANGLE + (BAT_SWING_ANGLE * rightPose.swing))
        leftContact?.let { placeBat(binding.ivBatLeft, leftFlipper, it, leftOffset, leftPose) }
        rightContact?.let { placeBat(binding.ivBatRight, rightFlipper, it, rightOffset, rightPose) }
    }

    /**
     * [swingPose], but in a scripted rally a bat never swings at a ball that won't come (any hit after the
     * mistake), and a missed ball is swung at a little late, so the ball visibly gets past the bat.
     */
    private fun scriptedSwingPose(
        script: RallyScript?,
        legPosition: Float,
        progress: Float,
        strikePoint: Float,
        topspin: Float,
    ): SwingPose {
        if (script == null) return swingPose(progress, strikePoint, topspin)
        val boundary = strikeBoundary(legPosition, strikePoint)
        if (boundary > script.mistakeLeg) return SwingPose.READY
        val late = if (boundary == script.mistakeLeg && script.mistake == MistakeType.WHIFF) WHIFF_LATE_SWING else 0f
        return swingPose(progress - late, strikePoint, topspin)
    }

    /**
     * The boundary (hit) a bat's stroke belongs to at [legPosition]: the one [swingPose] times its swing
     * around. [strikePoint] is where in the 2-leg cycle the bat hits, 0 (even boundaries) or 0.5 (odd ones).
     */
    private fun strikeBoundary(legPosition: Float, strikePoint: Float): Int {
        val distance = legPosition / 2f - strikePoint - BAT_SWING_DELAY
        return ((floor(distance + 0.5f) + strikePoint) * 2f).roundToInt()
    }

    /**
     * Where a bat is in its stroke at this point in its cycle. Ready at rest; then it winds up, back and
     * turned past the angle it hits at, waits there a moment, and swings forward, at its fastest as it meets
     * the ball [BAT_SWING_DELAY] after the strike point. It carries on through the ball, slowing down, and
     * then settles back to ready.
     *
     * With [topspin] (0..1) the windup starts later and the bat pulls far back and low, below the ball, its
     * head tilted further back; it drives forward and up into the ball, and after it keeps going forward and
     * up over the top of the ball, before settling back to ready.
     */
    private fun swingPose(progress: Float, strikePoint: Float, topspin: Float): SwingPose {
        var distance = progress - strikePoint - BAT_SWING_DELAY
        distance -= floor(distance + 0.5f) // wrap into (-0.5, 0.5]

        val ready = SwingPose.READY
        val windup = SwingPose(
            swing = lerp(BAT_WINDUP_SWING, TOPSPIN_WINDUP_SWING, topspin),
            back = lerp(BAT_WINDUP_BACK_RATIO, TOPSPIN_BACK_RATIO, topspin),
            lift = -TOPSPIN_WINDUP_SINK * topspin,
        )
        if (distance < -BAT_STRIKE_WINDOW) {
            // Wind up smoothly from ready, then wait at the top of the windup for the ball.
            val windupStart = -lerp(BAT_WINDUP_START, TOPSPIN_WINDUP_START, topspin)
            val windupEnd = -BAT_STRIKE_WINDOW - lerp(BAT_PAUSE_WINDOW, TOPSPIN_PAUSE_WINDOW, topspin)
            val windUp = smoothstep(((distance - windupStart) / (windupEnd - windupStart)).coerceIn(0f, 1f))
            return SwingPose(
                lerp(ready.swing, windup.swing, windUp),
                lerp(ready.back, windup.back, windUp),
                lerp(ready.lift, windup.lift, windUp),
            )
        }
        val followForward = -lerp(BAT_FOLLOW_FORWARD_RATIO, TOPSPIN_FOLLOW_FORWARD_RATIO, topspin)
        return SwingPose(
            // Turns on through the hit angle, without stopping, all the way back to the ready angle.
            swing = strokeMotion(distance, windup.swing, contact = 1f, followThrough = ready.swing, ready.swing),
            back = strokeMotion(distance, windup.back, contact = 0f, followForward, ready.back),
            lift = strokeMotion(
                distance,
                windup.lift,
                contact = 0f,
                followThrough = TOPSPIN_FOLLOW_LIFT * topspin,
                ready.lift,
                followWindow = TOPSPIN_LIFT_WINDOW,
            ),
        )
    }

    /**
     * One part of a stroke ([SwingPose.swing], [SwingPose.back] or [SwingPose.lift]) from the top of its
     * windup on, at [distance] from the hit. From [windup] it accelerates into [contact], reaching it at its
     * fastest exactly at the hit, carries on past it the same way, slowing down to a stop at [followThrough],
     * and then settles back to [ready]. Unless a [followWindow] is given, the follow-through starts out
     * exactly as fast as the hit, so the bat goes smoothly on through the ball instead of stopping dead at it.
     */
    private fun strokeMotion(
        distance: Float,
        windup: Float,
        contact: Float,
        followThrough: Float,
        ready: Float,
        followWindow: Float = matchingFollowWindow(windup, contact, followThrough),
    ): Float {
        if (distance <= 0f) {
            val strike = 1f + distance / BAT_STRIKE_WINDOW // 0 at the top of the windup, 1 at the hit
            // Ease-in: gentle start, hardest acceleration right at the ball.
            return windup + (contact - windup) * strike * strike
        }
        if (distance < followWindow) {
            val follow = distance / followWindow // 0 at the hit, 1 at the end of the follow-through
            return contact + (followThrough - contact) * follow * (2f - follow) // ease-out
        }
        val settleFrom = if (followWindow > 0f) followThrough else contact
        val settle = ((distance - followWindow) / BAT_SETTLE_WINDOW).coerceAtMost(1f)
        return settleFrom + (ready - settleFrom) * smoothstep(settle)
    }

    /**
     * How long a follow-through from [contact] to [followThrough] takes when it starts out exactly as fast as
     * the strike from [windup] ended and slows down evenly; 0 when it doesn't carry on the same way.
     */
    private fun matchingFollowWindow(windup: Float, contact: Float, followThrough: Float): Float {
        val strike = contact - windup
        val follow = followThrough - contact
        return if (strike * follow > 0f) BAT_STRIKE_WINDOW * follow / strike else 0f
    }
}

private fun lerp(from: Float, to: Float, amount: Float): Float = from + (to - from) * amount

/** Eases [t] (0..1) in and out: slow at both ends. */
private fun smoothstep(t: Float): Float = t * t * (3f - 2f * t)
