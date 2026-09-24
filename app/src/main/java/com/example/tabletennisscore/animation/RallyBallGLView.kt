package com.example.tabletennisscore.animation

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.os.SystemClock
import android.util.AttributeSet
import android.view.TextureView

/** Aim for about 60 frames a second. */
private const val FRAME_INTERVAL_MS = 16L

/**
 * Draws the rally ball with OpenGL ([RallyBallRenderer]) on its own render thread.
 *
 * A TextureView, not a GLSurfaceView: a GLSurfaceView's drawing lives in a separate window layer, either
 * above or below the whole screen, so no ordinary view could ever be drawn over the ball. A TextureView is
 * layered like any other view, so a bat raised above it (a higher translationZ) covers the ball.
 */
class RallyBallGLView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TextureView(context, attrs), TextureView.SurfaceTextureListener {

    val renderer = RallyBallRenderer(context)

    private var renderThread: RenderThread? = null
    private var paused = false

    init {
        isOpaque = false
        surfaceTextureListener = this
    }

    /** Resumes drawing; call from the activity's onResume. */
    fun onResume() {
        paused = false
        renderThread?.setPaused(false)
    }

    /** Stops drawing until [onResume]; call from the activity's onPause. */
    fun onPause() {
        paused = true
        renderThread?.setPaused(true)
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        renderThread = RenderThread(surface, renderer).also {
            it.setSize(width, height)
            it.setPaused(paused)
            it.start()
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        renderThread?.setSize(width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        renderThread?.finish()
        renderThread = null
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    /** Sets up OpenGL ES 2 on [surfaceTexture] and draws [renderer] into it, frame after frame. */
    private class RenderThread(
        private val surfaceTexture: SurfaceTexture,
        private val renderer: RallyBallRenderer,
    ) : Thread("RallyBallRender") {

        private val lock = Object()
        private var width = 0
        private var height = 0
        private var sizeChanged = true
        private var paused = false
        private var running = true

        fun setSize(width: Int, height: Int) = synchronized(lock) {
            this.width = width
            this.height = height
            sizeChanged = true
        }

        fun setPaused(paused: Boolean) = synchronized(lock) {
            this.paused = paused
            lock.notifyAll()
        }

        /** Stops the thread and waits until it has let go of the surface. */
        fun finish() {
            synchronized(lock) {
                running = false
                lock.notifyAll()
            }
            join()
        }

        override fun run() {
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val version = IntArray(2)
            EGL14.eglInitialize(display, version, 0, version, 1)

            val configAttributes = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val configCount = IntArray(1)
            EGL14.eglChooseConfig(display, configAttributes, 0, configs, 0, 1, configCount, 0)
            val config = configs[0] ?: return

            val eglContext = EGL14.eglCreateContext(
                display, config, EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
            )
            val eglSurface = EGL14.eglCreateWindowSurface(
                display, config, surfaceTexture, intArrayOf(EGL14.EGL_NONE), 0,
            )
            EGL14.eglMakeCurrent(display, eglSurface, eglSurface, eglContext)

            try {
                renderer.onSurfaceCreated(null, null)
                while (true) {
                    var newWidth = -1
                    var newHeight = -1
                    synchronized(lock) {
                        while (paused && running) lock.wait()
                        if (!running) return
                        if (sizeChanged) {
                            newWidth = width
                            newHeight = height
                            sizeChanged = false
                        }
                    }
                    if (newWidth >= 0) renderer.onSurfaceChanged(null, newWidth, newHeight)

                    val frameStart = SystemClock.uptimeMillis()
                    renderer.onDrawFrame(null)
                    EGL14.eglSwapBuffers(display, eglSurface)

                    val wait = FRAME_INTERVAL_MS - (SystemClock.uptimeMillis() - frameStart)
                    if (wait > 0) synchronized(lock) { if (running && !paused) lock.wait(wait) }
                }
            } catch (_: InterruptedException) {
                // Asked to stop.
            } finally {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                EGL14.eglDestroySurface(display, eglSurface)
                EGL14.eglDestroyContext(display, eglContext)
            }
        }
    }
}
