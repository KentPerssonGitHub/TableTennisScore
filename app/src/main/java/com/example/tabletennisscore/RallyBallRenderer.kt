package com.example.tabletennisscore

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
import kotlin.math.sin

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

    private var program: Int = 0
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
    
    private var startTime = 0L
    private val duration = 1500L
    val fullCycleDurationMillis: Long
        get() = duration * 2

    fun resetAnimationPhase() {
        startTime = 0L
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
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        Matrix.orthoM(pMatrix, 0, 0f, width.toFloat(), height.toFloat(), 0f, -1f, 1f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        if (!isAnimating) return

        if (startTime == 0L) startTime = SystemClock.uptimeMillis()
        val elapsed = (SystemClock.uptimeMillis() - startTime) % (duration * 2)
        
        val tRaw = elapsed.toFloat() / duration
        val t = if (tRaw > 1f) 2f - tRaw else tRaw
        
        val currentX = leftX + (rightX - leftX) * t
        val netArc = sin(PI.toFloat() * t)
        val currentY = baseY - (arcHeight * netArc)
        
        val spinDirection = if (tRaw > 1f) -1f else 1f
        val rotation = (elapsed.toFloat() / duration) * 360f * 3f * spinDirection

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
