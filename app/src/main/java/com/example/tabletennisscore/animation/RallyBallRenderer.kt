package com.example.tabletennisscore.animation
import com.example.tabletennisscore.R

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.view.animation.AnimationUtils
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private const val DEFAULT_PEAK_AMPLITUDE_AT_EDGES = 0.45f
private const val DEFAULT_PEAK_AMPLITUDE_AT_CENTER = 1f
private const val DEFAULT_EDGE_DOWN_OFFSET_RATIO = 0.10f
private const val DEFAULT_RALLY_BOUNCE_POSITION_RATIO = 0.68f

// The serve-then-rally look used by both the match screen and the splash screen: tall near the
// table edges (server/receiver side), only slightly lower while crossing over the net, and after
// the first leg a single deep bounce on the far side (no bounce right after the hitter's side).
/** Peak height multiplier at the table edges in the serve-then-rally look. */
const val SERVE_RALLY_PEAK_AMPLITUDE_AT_EDGES = 1f
private const val SERVE_RALLY_PEAK_AMPLITUDE_AT_CENTER = 0.82f
private const val SERVE_RALLY_EDGE_DOWN_OFFSET_RATIO = 0f
private const val SERVE_RALLY_BOUNCE_POSITION_RATIO = 0.68f

// The ball's shadow on the table: darkest and widest when the ball touches the table, smaller and
// fainter the higher the ball flies, so the height over the table is easy to read.
private const val SHADOW_ALPHA_AT_TABLE = 0.75f
private const val SHADOW_ALPHA_AT_PEAK = 0.4f
private const val SHADOW_SCALE_AT_TABLE = 1.3f
private const val SHADOW_SCALE_AT_PEAK = 0.9f
/** The table is seen at an angle, so the round shadow is squashed to this share of its width. */
private const val SHADOW_FLATTEN_RATIO = 0.5f
/** How far below the ball's center the shadow sits, as a share of the ball's height (its bottom edge). */
private const val SHADOW_DROP_RATIO = 0.4f
/** The shadow is solid out to this share of its radius, then fades out smoothly to its edge. */
private const val SHADOW_SOLID_RATIO = 0.55f
private const val SHADOW_BITMAP_SIZE = 64

/**
 * Works out where the rally ball is at a given moment and draws it (and its shadow on the table) onto a
 * [Canvas]. Drawn by [RallyBallView] on the main thread, in the same frame as the bats and with the same
 * frame time ([AnimationUtils.currentAnimationTimeMillis]), so the ball moves smoothly, one step per screen
 * refresh, and always in step with the bats.
 */
class RallyBallRenderer(context: Context) {

    /** Asks the view to draw a new frame; set by [RallyBallView]. */
    internal var requestFrame: () -> Unit = {}

    // Animation parameters
    /** Starting this draws frames again; stopping it redraws once more, so the ball disappears. */
    var isAnimating = false
        set(value) {
            if (field == value) return
            field = value
            requestFrame()
        }
    var leftX = 0f
    var rightX = 0f
    var baseY = 0f
    var arcHeight = 0f
    var ballWidth = 20f
    var ballHeight = 20f
    /** Peak height multiplier at t=0/1 (ball arriving/leaving near the table edges). */
    var peakAmplitudeAtEdges = DEFAULT_PEAK_AMPLITUDE_AT_EDGES
    /** Peak height multiplier at t=0.5 (ball crossing over the net). */
    var peakAmplitudeAtCenter = DEFAULT_PEAK_AMPLITUDE_AT_CENTER
    /** Extra downward push applied only near t=0/1, as a ratio of arcHeight. */
    var edgeDownOffsetRatio = DEFAULT_EDGE_DOWN_OFFSET_RATIO
    /**
     * When true: only the very first leg after a phase reset uses the double-bounce serve
     * profile above; every leg after that uses a single deep bounce near the far end of the
     * leg with no bounce right after leaving the hitter's side (real rally behavior), looping
     * indefinitely while alternating direction each leg. When false (default), every leg keeps
     * reusing the same double-bounce serve profile, preserving the original repeating look.
     */
    var enableServeThenRallyBounce = false
    /** Fraction of a rally leg (0..1) where the single bounce lands, deep on the far side. */
    var rallyBouncePositionRatio = DEFAULT_RALLY_BOUNCE_POSITION_RATIO

    /**
     * How far, in pixels, the ball's destination may drift above the middle line on each return
     * (0 = always straight along the middle). Only used when [enableServeThenRallyBounce] is on.
     */
    var returnYUpSpread = 0f

    /** Like [returnYUpSpread], but how far the destination may drift below the middle line. */
    var returnYDownSpread = 0f

    /**
     * Left and right edges of the table, in pixels. When both are set, the ball casts a shadow on the
     * table while it is over it; when either is null (e.g. the splash screen, which has no table) no
     * shadow is drawn.
     */
    var shadowTableLeft: Float? = null
    var shadowTableRight: Float? = null

    private var startTime = 0L
    private var pathSeed = Random.nextInt()
    // TEST ONLY: slow motion. Everything (ball, spin, bats) follows this leg duration. Set back to 1 when done.
    private val slowMotionFactor = 1L
    private val duration = 1800L * slowMotionFactor
    val fullCycleDurationMillis: Long
        get() = duration * 2

    /** The up/down drift of the current rally; a new random one starts with every animation restart. */
    val yPath: RallyYPath
        get() = RallyYPath(pathSeed, returnYUpSpread, returnYDownSpread)

    /**
     * The current leg number plus how far (0..1) the ball is through it, at this frame's time; 0 before
     * the first frame.
     */
    val legPosition: Float
        get() {
            val started = startTime
            if (started == 0L) return 0f
            return yPath.legPositionAt(AnimationUtils.currentAnimationTimeMillis() - started, duration)
        }

    private val ballBitmap: Bitmap = BitmapFactory.decodeResource(
        context.resources,
        R.drawable.stigaperform40size128,
        BitmapFactory.Options().apply { inScaled = false },
    )
    private val shadowBitmap = createShadowBitmap()
    private val ballPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val ballMatrix = Matrix()
    private val shadowRect = RectF()

    fun resetAnimationPhase() {
        startTime = 0L
        pathSeed = Random.nextInt()
    }

    /** Uses the serve-then-rally look shared by the match screen and the splash screen. */
    fun useServeThenRallyStyle() {
        peakAmplitudeAtEdges = SERVE_RALLY_PEAK_AMPLITUDE_AT_EDGES
        peakAmplitudeAtCenter = SERVE_RALLY_PEAK_AMPLITUDE_AT_CENTER
        edgeDownOffsetRatio = SERVE_RALLY_EDGE_DOWN_OFFSET_RATIO
        enableServeThenRallyBounce = true
        rallyBouncePositionRatio = SERVE_RALLY_BOUNCE_POSITION_RATIO
    }

    /** Draws the ball as it is at [frameTimeMillis]. Does nothing while not [isAnimating]. */
    fun draw(canvas: Canvas, frameTimeMillis: Long) {
        if (!isAnimating) return

        if (startTime == 0L) startTime = frameTimeMillis

        val currentX: Float
        val currentY: Float
        val rotation: Float
        // The ball's Y (same top-left convention as currentY) when it rests on the table right below it.
        val tableY: Float

        if (!enableServeThenRallyBounce) {
            // Legacy behavior (used by the splash screen): every leg repeats the exact same
            // double-bounce serve profile, unchanged from the original implementation.
            val elapsed = (frameTimeMillis - startTime) % (duration * 2)

            val tRaw = elapsed.toFloat() / duration
            val t = if (tRaw > 1f) 2f - tRaw else tRaw

            currentX = leftX + (rightX - leftX) * t
            tableY = baseY
            val bounceWave = abs(sin((2f * PI.toFloat() * t) - (PI.toFloat() / 2f)))
            val edgeFactor = abs((2f * t) - 1f)
            val amplitude = peakAmplitudeAtCenter + (peakAmplitudeAtEdges - peakAmplitudeAtCenter) * edgeFactor
            val edgeDownOffset = arcHeight * edgeDownOffsetRatio * edgeFactor
            currentY = baseY - (arcHeight * bounceWave * amplitude) + edgeDownOffset

            val spinDirection = if (tRaw > 1f) -1f else 1f
            rotation = (elapsed.toFloat() / duration) * 360f * 3f * spinDirection
        } else {
            // Match behavior: leg 0 (the initial serve, server -> receiver) keeps the exact
            // double-bounce profile above. Every leg after that is a real rally hit: no bounce
            // right after leaving the hitter's side, a smooth arc over the net, then a single
            // bounce deep on the far side before rising again to the other player, looping
            // indefinitely while alternating direction each leg.
            val totalElapsed = frameTimeMillis - startTime
            val path = yPath
            val legPositionNow = path.legPositionAt(totalElapsed, duration)
            val legIndex = floor(legPositionNow).toLong()
            val pLocal = legPositionNow - legIndex
            val forwardLeg = (legIndex % 2L) == 0L

            currentX = if (forwardLeg) {
                leftX + (rightX - leftX) * pLocal
            } else {
                rightX + (leftX - rightX) * pLocal
            }

            val returnYOffset = path.ballOffset(legPositionNow)
            tableY = baseY + returnYOffset

            currentY = returnYOffset + if (legIndex == 0L) {
                val bounceWave = abs(sin((2f * PI.toFloat() * pLocal) - (PI.toFloat() / 2f)))
                val edgeFactor = abs((2f * pLocal) - 1f)
                val amplitude = peakAmplitudeAtCenter + (peakAmplitudeAtEdges - peakAmplitudeAtCenter) * edgeFactor
                val edgeDownOffset = arcHeight * edgeDownOffsetRatio * edgeFactor
                baseY - (arcHeight * bounceWave * amplitude) + edgeDownOffset
            } else {
                val bouncePos = rallyBouncePositionRatio.coerceIn(0.05f, 0.95f)
                val heightFactor = if (pLocal <= bouncePos) {
                    cos((pLocal / bouncePos) * (PI.toFloat() / 2f))
                } else {
                    sin(((pLocal - bouncePos) / (1f - bouncePos)) * (PI.toFloat() / 2f))
                }
                baseY - (arcHeight * peakAmplitudeAtEdges * heightFactor)
            }

            // A topspin hit (the one that starts this leg, see topspinAmount) sends the ball off rolling
            // forwards; every other stroke, the serve included, sends it spinning the other way (backspin).
            val travelDirection = if (forwardLeg) 1f else -1f
            val topspinHit = topspinAmount(path.boundaryOffset(legIndex.toInt()), returnYUpSpread) > 0f
            val spinDirection = if (topspinHit) travelDirection else -travelDirection
            rotation = pLocal * 360f * 3f * spinDirection
        }

        drawShadow(canvas, currentX, currentY, tableY)

        // The ball image, scaled to the ball's size and spun around its center. Flipped upside down, the
        // way the earlier OpenGL version always showed it.
        ballMatrix.reset()
        ballMatrix.postScale(ballWidth / ballBitmap.width, -ballHeight / ballBitmap.height)
        ballMatrix.postTranslate(-ballWidth / 2f, ballHeight / 2f)
        ballMatrix.postRotate(rotation)
        ballMatrix.postTranslate(currentX + ballWidth / 2f, currentY + ballHeight / 2f)
        canvas.drawBitmap(ballBitmap, ballMatrix, ballPaint)
    }

    private fun drawShadow(canvas: Canvas, ballX: Float, ballY: Float, tableY: Float) {
        val tableLeft = shadowTableLeft ?: return
        val tableRight = shadowTableRight ?: return
        val peakHeight = arcHeight * peakAmplitudeAtEdges
        if (peakHeight <= 0f) return

        // 0 when the ball touches the table, 1 at the top of its arc.
        val height = ((tableY - ballY) / peakHeight).coerceIn(0f, 1f)
        val scale = SHADOW_SCALE_AT_TABLE + (SHADOW_SCALE_AT_PEAK - SHADOW_SCALE_AT_TABLE) * height
        val shadowWidth = ballWidth * scale
        val shadowHeight = ballHeight * scale * SHADOW_FLATTEN_RATIO
        val centerX = ballX + ballWidth / 2f
        val centerY = tableY + ballHeight / 2f + ballHeight * SHADOW_DROP_RATIO

        // The shadow only falls on the table: fade it out as it slides off either end.
        val halfWidth = shadowWidth / 2f
        val onTable = minOf(
            (centerX - tableLeft + halfWidth) / shadowWidth,
            (tableRight - centerX + halfWidth) / shadowWidth,
        ).coerceIn(0f, 1f)
        val alpha = (SHADOW_ALPHA_AT_TABLE + (SHADOW_ALPHA_AT_PEAK - SHADOW_ALPHA_AT_TABLE) * height) * onTable
        if (alpha <= 0f) return

        shadowPaint.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
        shadowRect.set(centerX - halfWidth, centerY - shadowHeight / 2f, centerX + halfWidth, centerY + shadowHeight / 2f)
        canvas.drawBitmap(shadowBitmap, null, shadowRect, shadowPaint)
    }

    /** A soft black disc: solid in the middle, fading smoothly to nothing at its edge. */
    private fun createShadowBitmap(): Bitmap {
        val size = SHADOW_BITMAP_SIZE
        val pixels = IntArray(size * size)
        val center = (size - 1) / 2f
        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = (x - center) / center
                val dy = (y - center) / center
                val distance = sqrt(dx * dx + dy * dy)
                val fade = ((distance - SHADOW_SOLID_RATIO) / (1f - SHADOW_SOLID_RATIO)).coerceIn(0f, 1f)
                val smooth = fade * fade * (3f - 2f * fade)
                pixels[y * size + x] = Color.argb(((1f - smooth) * 255f).toInt(), 0, 0, 0)
            }
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }
}
