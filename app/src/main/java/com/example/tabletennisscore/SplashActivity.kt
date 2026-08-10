package com.example.tabletennisscore

import android.animation.ValueAnimator
import android.content.Intent
import android.app.ActivityOptions
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class SplashActivity : AppCompatActivity() {

    private var hasContinued = false
    private var tapHintAnimator: ValueAnimator? = null
    private var splashBallAnimator: ValueAnimator? = null
    private var splashBallSpinDirection = 1f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val tapHintView = findViewById<TextView>(R.id.tvTapToContinue)
        tapHintAnimator = ValueAnimator.ofArgb(Color.WHITE, Color.parseColor("#AAAAAA")).apply {
            duration = 900L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                tapHintView.setTextColor(animator.animatedValue as Int)
            }
            start()
        }

        startSplashBallAnimationWhenReady()

        findViewById<android.view.View>(R.id.splashRoot).setOnClickListener {
            continueToApp()
        }
    }

    override fun onDestroy() {
        tapHintAnimator?.cancel()
        tapHintAnimator = null
        splashBallAnimator?.cancel()
        splashBallAnimator = null
        super.onDestroy()
    }

    private fun startSplashBallAnimationWhenReady() {
        val rootView = findViewById<View>(R.id.splashRoot)
        val ballView = findViewById<ImageView>(R.id.ivSplashBall)
        rootView.post {
            if (isFinishing || isDestroyed) return@post
            startSplashBallAnimation(rootView, ballView)
        }
    }

    private fun startSplashBallAnimation(rootView: View, ballView: ImageView) {
        splashBallAnimator?.cancel()

        val ballSize = ballView.width.takeIf { it > 0 }?.toFloat() ?: ballView.layoutParams.width.toFloat()
        val rootWidth = rootView.width.toFloat()
        val rootHeight = rootView.height.toFloat()

        if (rootWidth <= 0f || rootHeight <= 0f || ballSize <= 0f) return

        val leftX = rootWidth * 0.3f
        val rightX = rootWidth * 0.7f - ballSize
        val horizontalTravel = rightX - leftX
        if (horizontalTravel <= 0f) return

        val baseY = (rootHeight * 0.5f) - (ballSize / 2f)
        val desiredArc = abs(horizontalTravel) * 0.20f
        val arcHeight = maxOf(70f, minOf(220f, desiredArc))

        var lastT = 0f
        var spinTurns = 0f

        splashBallAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1200L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                val t = animator.animatedValue as Float
                if ((lastT < 0.02f && t >= 0.02f) || (lastT > 0.98f && t <= 0.98f)) {
                    splashBallSpinDirection *= -1f
                }
                lastT = t

                ballView.x = leftX + horizontalTravel * t
                val netArc = sin(PI.toFloat() * t)
                ballView.y = baseY - (arcHeight * netArc)

                spinTurns += 12f * splashBallSpinDirection
                ballView.rotation = spinTurns
            }
            start()
        }

        splashBallSpinDirection = 1f
        ballView.rotation = 0f
        ballView.visibility = View.VISIBLE
    }

    private fun continueToApp() {
        if (hasContinued) return
        hasContinued = true
        tapHintAnimator?.cancel()
        splashBallAnimator?.cancel()
        splashBallAnimator = null
        val intent = Intent(this, MainActivity::class.java)
        val options = ActivityOptions.makeCustomAnimation(
            this,
            android.R.anim.fade_in,
            android.R.anim.fade_out,
        )
        startActivity(intent, options.toBundle())
        finish()
    }
}




