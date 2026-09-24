package com.example.tabletennisscore.animation
import com.example.tabletennisscore.R

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.SystemClock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
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

class RallyBallRenderer(private val context: Context) : GLSurfaceView.Renderer {

    private val vertexShaderCode = """
        uniform mat4 uMVPMatrix;
        attribute vec4 vPosition;
        attribute vec2 vTexCoord;
        varying vec2 vfTexCoord;
        void main() {
            gl_Position = uMVPMatrix * vPosition;
            vfTexCoord = vTexCoord;
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        precision mediump float;
        uniform sampler2D uTexture;
        varying vec2 vfTexCoord;
        void main() {
            gl_FragColor = texture2D(uTexture, vfTexCoord);
        }
    """.trimIndent()

    private val shadowFragmentShaderCode = """
        precision mediump float;
        uniform float uAlpha;
        varying vec2 vfTexCoord;
        void main() {
            float d = length(vfTexCoord - vec2(0.5)) * 2.0;
            gl_FragColor = vec4(0.0, 0.0, 0.0, uAlpha * (1.0 - smoothstep(0.55, 1.0, d)));
        }
    """.trimIndent()

    private var program: Int = 0
    private var shadowProgram: Int = 0
    private var vPositionHandle: Int = 0
    private var vTexCoordHandle: Int = 0
    private var uMVPMatrixHandle: Int = 0
    private var uTextureHandle: Int = 0

    private val vpc = 3
    private val quadCoords = floatArrayOf(
        -0.5f,  0.5f, 0.0f, // top left
        -0.5f, -0.5f, 0.0f, // bottom left
         0.5f, -0.5f, 0.0f, // bottom right
         0.5f,  0.5f, 0.0f  // top right
    )
    private val texCoords = floatArrayOf(
        0.0f, 0.0f,
        0.0f, 1.0f,
        1.0f, 1.0f,
        1.0f, 0.0f
    )
    private val drawOrder = shortArrayOf(0, 1, 2, 0, 2, 3)

    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var texBuffer: FloatBuffer
    private lateinit var drawListBuffer: java.nio.ShortBuffer

    private val pMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val mMatrix = FloatArray(16)

    private var textureId: Int = 0

    // Animation parameters
    @Volatile var isAnimating = false
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
    @Volatile var returnYUpSpread = 0f

    /** Like [returnYUpSpread], but how far the destination may drift below the middle line. */
    @Volatile var returnYDownSpread = 0f

    /**
     * Left and right edges of the table, in pixels. When both are set, the ball casts a shadow on the
     * table while it is over it; when either is null (e.g. the splash screen, which has no table) no
     * shadow is drawn.
     */
    @Volatile var shadowTableLeft: Float? = null
    @Volatile var shadowTableRight: Float? = null

    private var startTime = 0L
    @Volatile private var pathSeed = Random.nextInt()
    private val duration = 1800L
    val fullCycleDurationMillis: Long
        get() = duration * 2

    /** The up/down drift of the current rally; a new random one starts with every animation restart. */
    val yPath: RallyYPath
        get() = RallyYPath(pathSeed, returnYUpSpread, returnYDownSpread)

    /** The current leg number plus how far (0..1) the ball is through it; 0 before the first frame. */
    val legPosition: Float
        get() {
            val started = startTime
            if (started == 0L) return 0f
            return yPath.legPositionAt(SystemClock.uptimeMillis() - started, duration)
        }

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

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }

        val shadowFragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, shadowFragmentShaderCode)
        shadowProgram = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, shadowFragmentShader)
            GLES20.glLinkProgram(it)
        }

        vertexBuffer = ByteBuffer.allocateDirect(quadCoords.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply {
                put(quadCoords)
                position(0)
            }
        }

        texBuffer = ByteBuffer.allocateDirect(texCoords.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply {
                put(texCoords)
                position(0)
            }
        }

        drawListBuffer = ByteBuffer.allocateDirect(drawOrder.size * 2).run {
            order(ByteOrder.nativeOrder())
            asShortBuffer().apply {
                put(drawOrder)
                position(0)
            }
        }

        textureId = loadTexture(context, R.drawable.stigaperform40size128)

        GLES20.glEnable(GLES20.GL_BLEND)
        // The view blends the result onto the screen as premultiplied alpha, so build the alpha channel
        // the same way (ONE, not SRC_ALPHA): otherwise see-through parts, like the shadow, come out too faint.
        GLES20.glBlendFuncSeparate(
            GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA,
            GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA,
        )
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        Matrix.orthoM(pMatrix, 0, 0f, width.toFloat(), height.toFloat(), 0f, -1f, 1f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        if (!isAnimating) return

        if (startTime == 0L) startTime = SystemClock.uptimeMillis()

        val currentX: Float
        val currentY: Float
        val rotation: Float
        // The ball's Y (same top-left convention as currentY) when it rests on the table right below it.
        val tableY: Float

        if (!enableServeThenRallyBounce) {
            // Legacy behavior (used by the splash screen): every leg repeats the exact same
            // double-bounce serve profile, unchanged from the original implementation.
            val elapsed = (SystemClock.uptimeMillis() - startTime) % (duration * 2)

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
            val totalElapsed = SystemClock.uptimeMillis() - startTime
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

            val spinDirection = if (forwardLeg) 1f else -1f
            rotation = pLocal * 360f * 3f * spinDirection
        }

        drawShadow(currentX, currentY, tableY)

        GLES20.glUseProgram(program)

        vPositionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        GLES20.glEnableVertexAttribArray(vPositionHandle)
        GLES20.glVertexAttribPointer(vPositionHandle, vpc, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        vTexCoordHandle = GLES20.glGetAttribLocation(program, "vTexCoord")
        GLES20.glEnableVertexAttribArray(vTexCoordHandle)
        GLES20.glVertexAttribPointer(vTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texBuffer)

        uMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        
        Matrix.setIdentityM(mMatrix, 0)
        Matrix.translateM(mMatrix, 0, currentX + ballWidth / 2f, currentY + ballHeight / 2f, 0f)
        Matrix.rotateM(mMatrix, 0, rotation, 0f, 0f, 1f)
        Matrix.scaleM(mMatrix, 0, ballWidth, ballHeight, 1f)

        Matrix.multiplyMM(mvpMatrix, 0, pMatrix, 0, mMatrix, 0)
        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mvpMatrix, 0)

        uTextureHandle = GLES20.glGetUniformLocation(program, "uTexture")
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(uTextureHandle, 0)

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawOrder.size, GLES20.GL_UNSIGNED_SHORT, drawListBuffer)

        GLES20.glDisableVertexAttribArray(vPositionHandle)
        GLES20.glDisableVertexAttribArray(vTexCoordHandle)
    }

    private fun drawShadow(ballX: Float, ballY: Float, tableY: Float) {
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

        GLES20.glUseProgram(shadowProgram)

        val positionHandle = GLES20.glGetAttribLocation(shadowProgram, "vPosition")
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, vpc, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        val texCoordHandle = GLES20.glGetAttribLocation(shadowProgram, "vTexCoord")
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texBuffer)

        Matrix.setIdentityM(mMatrix, 0)
        Matrix.translateM(mMatrix, 0, centerX, centerY, 0f)
        Matrix.scaleM(mMatrix, 0, shadowWidth, shadowHeight, 1f)
        Matrix.multiplyMM(mvpMatrix, 0, pMatrix, 0, mMatrix, 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(shadowProgram, "uMVPMatrix"), 1, false, mvpMatrix, 0)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(shadowProgram, "uAlpha"), alpha)

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawOrder.size, GLES20.GL_UNSIGNED_SHORT, drawListBuffer)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }

    private fun loadTexture(context: Context, resourceId: Int): Int {
        val textureHandle = IntArray(1)
        GLES20.glGenTextures(1, textureHandle, 0)

        if (textureHandle[0] != 0) {
            val options = BitmapFactory.Options().apply { inScaled = false }
            val bitmap = BitmapFactory.decodeResource(context.resources, resourceId, options)

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            bitmap.recycle()
        }
        return textureHandle[0]
    }
}
