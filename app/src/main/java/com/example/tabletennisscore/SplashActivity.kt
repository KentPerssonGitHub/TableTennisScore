package com.example.tabletennisscore

import android.animation.ValueAnimator
import android.content.Intent
import android.app.ActivityOptions
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class SplashActivity : AppCompatActivity() {

    companion object {
        private const val FLASH_COLOR = -0x2f2f30
    }

    private var hasContinued = false
    private var tapHintAnimator: ValueAnimator? = null
    private var titleAnimator: ValueAnimator? = null
    private var splashBallAnimator: ValueAnimator? = null
    private var splashBallSpinDirection = 1f
    private val titleLetterViews = mutableListOf<TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        hideSystemBars()

        val titleLayout = findViewById<LinearLayout>(R.id.layoutSplashTitle)
        val tapHintView = findViewById<TextView>(R.id.tvTapToContinue)

        buildSplashTitle(titleLayout)
        startTitleFlashAnimation()

        tapHintAnimator = ValueAnimator.ofArgb(Color.WHITE, FLASH_COLOR).apply {
            duration = 900L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                tapHintView.setTextColor(animator.animatedValue as Int)
            }
            start()
        }

        startSplashBallAnimationWhenReady()

        findViewById<View>(R.id.splashRoot).setOnClickListener {
            continueToApp()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onDestroy() {
        titleAnimator?.cancel()
        titleAnimator = null
        tapHintAnimator?.cancel()
        tapHintAnimator = null
        splashBallAnimator?.cancel()
        splashBallAnimator = null
        super.onDestroy()
    }

    private fun buildSplashTitle(titleLayout: LinearLayout) {
        val titleText = getString(R.string.app_name)
        val density = resources.displayMetrics.density

        titleLetterViews.clear()
        titleLayout.removeAllViews()

        titleText.forEach { char ->
            val letterView = TextView(this).apply {
                text = char.toString()
                setTextColor(Color.WHITE)
                textSize = 44f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setShadowLayer(16f, 4f, 8f, Color.parseColor("#CC000000"))
                includeFontPadding = false
                if (char == ' ') {
                    setPadding((8 * density).toInt(), 0, (8 * density).toInt(), 0)
                }
            }
            titleLayout.addView(letterView)
            if (!char.isWhitespace()) {
                titleLetterViews.add(letterView)
            }
        }
    }

    private fun startTitleFlashAnimation() {
        if (titleLetterViews.isEmpty()) return

        val baseColor = Color.WHITE
        val flashColor = FLASH_COLOR
        val perLetterDelay = 0.06f

        titleAnimator?.cancel()
        titleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1700L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                titleLetterViews.forEachIndexed { index, letterView ->
                    val shifted = ((progress - (index * perLetterDelay)) % 1f + 1f) % 1f
                    val intensity = if (shifted <= 0.5f) {
                        shifted / 0.5f
                    } else {
                        (1f - shifted) / 0.5f
                    }.coerceIn(0f, 1f)

                    letterView.setTextColor(blendColors(baseColor, flashColor, intensity))
                }
            }
            start()
        }
    }

    private fun blendColors(startColor: Int, endColor: Int, fraction: Float): Int {
        val clamped = fraction.coerceIn(0f, 1f)
        val startA = Color.alpha(startColor)
        val startR = Color.red(startColor)
        val startG = Color.green(startColor)
        val startB = Color.blue(startColor)
        val endA = Color.alpha(endColor)
        val endR = Color.red(endColor)
        val endG = Color.green(endColor)
        val endB = Color.blue(endColor)

        val a = (startA + ((endA - startA) * clamped)).toInt()
        val r = (startR + ((endR - startR) * clamped)).toInt()
        val g = (startG + ((endG - startG) * clamped)).toInt()
        val b = (startB + ((endB - startB) * clamped)).toInt()

        return Color.argb(a, r, g, b)
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
        titleAnimator?.cancel()
        tapHintAnimator?.cancel()
        splashBallAnimator?.cancel()
        titleAnimator = null
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




