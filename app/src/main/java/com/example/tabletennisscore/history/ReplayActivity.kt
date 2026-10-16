package com.example.tabletennisscore.history

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.tabletennisscore.GameViewModel.GameState
import com.example.tabletennisscore.R
import com.example.tabletennisscore.ScoreboardRenderer
import com.example.tabletennisscore.animation.DEFAULT_LEG_DURATION_MILLIS
import com.example.tabletennisscore.animation.RallyAnimationController
import com.example.tabletennisscore.animation.rallyScriptFor
import com.example.tabletennisscore.data.MatchDatabase
import com.example.tabletennisscore.data.MatchResult
import com.example.tabletennisscore.databinding.ActivityMainBinding
import com.example.tabletennisscore.hideSystemBarsImmersive
import com.example.tabletennisscore.updateMatchSummaryPanel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.random.Random

/** A replay plays this many times faster than a live match. */
private const val REPLAY_SPEED = 2

/** Pause on the scoreboard when players switch ends (after a set, or at 5 in the deciding set). */
private const val SIDE_SWITCH_PAUSE_MS = 1500L

/** The "R" badge pulses between its normal size and this much bigger, once out and back per period. */
private const val BADGE_PULSE_SCALE = 1.25f
private const val BADGE_PULSE_ALPHA = 0.7f
private const val BADGE_PULSE_HALF_PERIOD_MS = 700L

/** Room kept free for the "R" badge (its 60dp plus margins) at the end of the right-hand player's name. */
private const val BADGE_CLEARANCE_DP = 84f

/**
 * Replays a finished match on the match screen: every recorded point as a short rally, ended by a mistake
 * of the player who lost it, with the scoreboard as it was during the match. Only shown, never stored: it
 * doesn't touch the live match, the saved match in progress, or the history.
 */
class ReplayActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val dao by lazy { MatchDatabase.getInstance(this).matchResultDao() }
    private val scoreboard by lazy { ScoreboardRenderer(binding) }
    private var state: GameState? = null
    private val rallyAnimation by lazy { RallyAnimationController(binding) { state } }
    private val handler = Handler(Looper.getMainLooper())
    private val playNextPoint = Runnable { playPoint() }
    private var badgePulse: ObjectAnimator? = null

    private var match: MatchResult? = null
    private var points: List<ReplayPoint> = emptyList()

    /** The point being played (or next to play); [points].size once the match is over. */
    private var pointIndex = 0
    private var isResumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        hideSystemBarsImmersive()

        val matchId = intent.getIntExtra(EXTRA_MATCH_ID, -1)
        if (matchId == -1) {
            finish()
            return
        }
        pointIndex = savedInstanceState?.getInt(STATE_POINT_INDEX) ?: 0

        setUpScreen()
        rallyAnimation.onRallyFinished = { onPointPlayed() }

        lifecycleScope.launch {
            val result = dao.getById(matchId).first()
            val replay = result?.let { replayPoints(it) }
            if (result == null || replay.isNullOrEmpty()) {
                finish()
                return@launch
            }
            match = result
            points = replay
            pointIndex = pointIndex.coerceIn(0, points.size)
            if (isResumed) playPoint()
        }
    }

    /** Only the scoreboard, the bats and ball (always shown, whatever the in-game setting) and Stop. */
    private fun setUpScreen() {
        listOf(
            binding.btnStartMatch, binding.btnPauseMatchText, binding.btnSetupMatch, binding.btnUndoText,
            binding.btnHistory, binding.btnToggleInGame, binding.btnSwapSides,
        ).forEach { it.visibility = View.GONE }
        binding.centerControlsRow.visibility = View.VISIBLE
        binding.btnStopReplay.visibility = View.VISIBLE
        binding.btnStopReplay.setOnClickListener { finish() }
        binding.tvReplayBadge.visibility = View.VISIBLE
        val density = resources.displayMetrics.density
        ConstraintSet().apply {
            clone(binding.rootLayout)
            // Down by the near edge of the table, out of the ball's way: a ball into the net is right in the middle.
            clear(R.id.centerControlsRow, ConstraintSet.TOP)
            connect(R.id.centerControlsRow, ConstraintSet.BOTTOM, R.id.tvTournamentName, ConstraintSet.TOP)
            // A long right-hand name stops short of the "R" badge in the corner instead of running under it.
            setMargin(R.id.tvPlayer2Name, ConstraintSet.END, (BADGE_CLEARANCE_DP * density).toInt())
            applyTo(binding.rootLayout)
        }

        binding.ivBatLeft.visibility = View.VISIBLE
        binding.ivBatRight.visibility = View.VISIBLE
        binding.glRallyBall.visibility = View.VISIBLE
        binding.glRallyBall.renderer.legDurationMillis = (DEFAULT_LEG_DURATION_MILLIS / REPLAY_SPEED)
        rallyAnimation.resetBatAngles()

        binding.rootLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.background_running))
        binding.tvMatchTimer.text = getString(R.string.replay_label)
    }

    /** Shows the score before the current point and plays its rally; at the end, the match summary. */
    private fun playPoint() {
        handler.removeCallbacks(playNextPoint)
        val point = points.getOrNull(pointIndex)
        if (point == null) {
            showMatchEnd()
            return
        }
        show(point.before)
        rallyAnimation.script = rallyScriptFor(serverWon = point.winner == point.server, random = Random.Default)
        rallyAnimation.startIfNeeded()
    }

    /** The rally's mistake has played out: count the point, then go on to the next one. */
    private fun onPointPlayed() {
        val point = points.getOrNull(pointIndex) ?: return
        pointIndex++
        show(point.after)
        when {
            point.after.matchWinner != null -> {
                rallyAnimation.stop()
                showMatchEnd()
            }
            // Players switch ends: hold the new scoreboard a moment, the way they'd walk around the table.
            point.after.sidesSwapped != point.before.sidesSwapped -> {
                rallyAnimation.stop()
                handler.postDelayed(playNextPoint, SIDE_SWITCH_PAUSE_MS)
            }
            else -> playPoint()
        }
    }

    private fun show(newState: GameState) {
        state = newState
        scoreboard.render(newState)
    }

    private fun showMatchEnd() {
        val result = match ?: return
        val finalState = points.lastOrNull()?.after ?: return
        show(finalState)
        rallyAnimation.stop()
        // Tapping the summary ends the replay, like Stop (which the summary covers).
        updateMatchSummaryPanel(binding, finalState, result.durationMs) { finish() }
    }

    override fun onResume() {
        super.onResume()
        isResumed = true
        binding.glRallyBall.onResume()
        startBadgePulse()
        // Starts the current point again from its first stroke.
        if (points.isNotEmpty()) playPoint()
    }

    override fun onPause() {
        isResumed = false
        handler.removeCallbacks(playNextPoint)
        rallyAnimation.stop()
        stopBadgePulse()
        super.onPause()
        binding.glRallyBall.onPause()
    }

    /** The "R" badge keeps growing and shrinking (and dimming a little) for as long as the replay is shown. */
    private fun startBadgePulse() {
        stopBadgePulse()
        badgePulse = ObjectAnimator.ofPropertyValuesHolder(
            binding.tvReplayBadge,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, BADGE_PULSE_SCALE),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, BADGE_PULSE_SCALE),
            PropertyValuesHolder.ofFloat(View.ALPHA, 1f, BADGE_PULSE_ALPHA),
        ).apply {
            duration = BADGE_PULSE_HALF_PERIOD_MS
            interpolator = AccelerateDecelerateInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
    }

    private fun stopBadgePulse() {
        badgePulse?.cancel()
        badgePulse = null
        binding.tvReplayBadge.apply {
            scaleX = 1f
            scaleY = 1f
            alpha = 1f
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_POINT_INDEX, pointIndex)
    }

    override fun onDestroy() {
        handler.removeCallbacks(playNextPoint)
        rallyAnimation.onRallyFinished = null
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBarsImmersive()
    }

    companion object {
        const val EXTRA_MATCH_ID = "extra_match_id"
        private const val STATE_POINT_INDEX = "point_index"
    }
}
