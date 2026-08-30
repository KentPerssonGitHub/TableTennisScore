package com.example.tabletennisscore

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.MotionEvent

class RallyBallGLView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    val renderer: RallyBallRenderer

    init {
        setEGLContextClientVersion(2)
        
        // Transparency setup
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderOnTop(true)
        
        renderer = RallyBallRenderer(context)
        setRenderer(renderer)
        
        // Continuous rendering for smoothness
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        // Allow touches to pass through to views behind
        return false
    }
}
