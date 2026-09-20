package com.example.tabletennisscore
import com.example.tabletennisscore.animation.RallyBallGLView

import android.animation.ValueAnimator
import android.content.Intent
import android.app.ActivityOptions
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import kotlin.math.abs

class SplashActivity : AppCompatActivity() {

    companion object {
        private const val FLASH_COLOR = -0x2f2f30
    }

    private var hasContinued = false
    private var tapHintAnimator: ValueAnimator? = null
    private var titleAnimator: ValueAnimator? = null
    private val titleLetterViews = mutableListOf<TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        hideSystemBarsImmersive()

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

    override fun onResume() {
        super.onResume()
        findViewById<RallyBallGLView>(R.id.glSplashBall).onResume()
    }

    override fun onPause() {
        super.onPause()
        findViewById<RallyBallGLView>(R.id.glSplashBall).onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBarsImmersive()
    }

    override fun onDestroy() {
        titleAnimator?.cancel()
        titleAnimator = null
        tapHintAnimator?.cancel()
        tapHintAnimator = null
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

                    letterView.setTextColor(ColorUtils.blendARGB(baseColor, flashColor, intensity))
                }
            }
            start()
        }
    }

    private fun startSplashBallAnimationWhenReady() {
        val rootView = findViewById<View>(R.id.splashRoot)
        val glView = findViewById<RallyBallGLView>(R.id.glSplashBall)
        rootView.post {
            if (isFinishing || isDestroyed) return@post
            startSplashBallAnimation(rootView, glView)
        }
    }

    private fun startSplashBallAnimation(rootView: View, glView: RallyBallGLView) {
        val ballSize = 20 * resources.displayMetrics.density
        val rootWidth = rootView.width.toFloat()
        val rootHeight = rootView.height.toFloat()

        if (rootWidth <= 0f || rootHeight <= 0f) return

        val leftX = rootWidth * 0.3f
        val rightX = rootWidth * 0.7f - ballSize
        val horizontalTravel = rightX - leftX
        if (horizontalTravel <= 0f) return

        val baseY = (rootHeight * 0.5f) - (ballSize / 2f)
        val desiredArc = abs(horizontalTravel) * 0.20f
        val arcHeight = maxOf(70f, minOf(220f, desiredArc))

        glView.renderer.apply {
            this.leftX = leftX
            this.rightX = rightX
            this.baseY = baseY
            this.arcHeight = arcHeight
            this.ballWidth = ballSize
            this.ballHeight = ballSize
            useServeThenRallyStyle()
            this.isAnimating = true
        }
    }

    private fun continueToApp() {
        if (hasContinued) return
        hasContinued = true
        titleAnimator?.cancel()
        tapHintAnimator?.cancel()
        titleAnimator = null
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
