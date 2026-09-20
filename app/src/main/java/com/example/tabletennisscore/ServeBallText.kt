package com.example.tabletennisscore

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan
import androidx.core.content.ContextCompat

/** Prefixes [name] (or, with [placeAtEnd], its first line) with the serve-ball icon when [showBall] is true. */
fun Context.serveBallText(name: String, showBall: Boolean, placeAtEnd: Boolean = false): CharSequence {
    if (!showBall) return name
    val density = resources.displayMetrics.density
    val iconSize = (14 * density).toInt()
    val verticalOffsetPx = (4 * density).toInt()
    val trailingHorizontalOffsetPx = (12 * density).toInt()
    val gap = "   "
    val ball = ContextCompat.getDrawable(this, R.drawable.stigaperform40size128)
    if (ball == null) return if (placeAtEnd) "$name  o" else "o  $name"
    ball.setBounds(0, 0, iconSize, iconSize)
    val firstLineEnd = name.indexOf('\n').let { if (it >= 0) it else name.length }
    val text = if (placeAtEnd) {
        // Keep right-side icon on the first line so both sides sit at the same height.
        name.substring(0, firstLineEnd) + gap + name.substring(firstLineEnd)
    } else {
        "$gap$name"
    }
    val spanStart = if (placeAtEnd) firstLineEnd else 0
    return SpannableStringBuilder(text).apply {
        setSpan(object : ImageSpan(ball, ImageSpan.ALIGN_BOTTOM) {
            override fun draw(
                canvas: Canvas,
                text: CharSequence,
                start: Int,
                end: Int,
                x: Float,
                top: Int,
                y: Int,
                bottom: Int,
                paint: Paint,
            ) {
                val d = drawable
                canvas.save()
                val transY = maxOf(top.toFloat(), (bottom - d.bounds.bottom - verticalOffsetPx).toFloat())
                val transX = if (placeAtEnd) x + trailingHorizontalOffsetPx else x
                canvas.translate(transX, transY.toFloat())
                d.draw(canvas)
                canvas.restore()
            }
        }, spanStart, spanStart + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}
