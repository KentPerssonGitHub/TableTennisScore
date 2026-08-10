package com.example.tabletennisscore

import android.animation.ValueAnimator
import android.content.Intent
import android.app.ActivityOptions
import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private var hasContinued = false
    private var tapHintAnimator: ValueAnimator? = null

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

        findViewById<android.view.View>(R.id.splashRoot).setOnClickListener {
            continueToApp()
        }
    }

    override fun onDestroy() {
        tapHintAnimator?.cancel()
        tapHintAnimator = null
        super.onDestroy()
    }

    private fun continueToApp() {
        if (hasContinued) return
        hasContinued = true
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




