package com.example.tabletennisscore
import com.example.tabletennisscore.history.HistoryActivity
import com.example.tabletennisscore.dialogs.styleDialogButtons
import com.example.tabletennisscore.dialogs.showEditTournamentNameDialog
import com.example.tabletennisscore.dialogs.showEditNameDialog
import com.example.tabletennisscore.dialogs.showEditScoreDialog
import com.example.tabletennisscore.dialogs.confirmSetupMatch
import com.example.tabletennisscore.animation.RallyAnimationController

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.os.Handler
import android.os.Bundle
import android.os.Looper
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.MotionEvent
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.tabletennisscore.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: GameViewModel by viewModels()
    private var serveDragStartRawX = 0f
    private var serveDragStartRawY = 0f
    private var activeServeDragView: View? = null
    private var isHandlingServeDrag = false
    private var isDraggingServeBall = false
    private var lastScoreTouchX = 0f
    private var lastScoreTouchY = 0f
    private var lastShownDecidingSwapNoticeVersion = 0
    private var decidingSwapSnackbar: Snackbar? = null
    private var previousIsMatchRunning = false
    private val rallyAnimation by lazy { RallyAnimationController(binding) { viewModel.state.value } }
    private val prefs by lazy { getSharedPreferences(APP_PREFS_NAME, MODE_PRIVATE) }
    private var showInGame = true
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerTick = object : Runnable {
        override fun run() {
            updateMatchTimerText()
            if (viewModel.state.value?.isMatchRunning == true) {
                timerHandler.postDelayed(this, 500L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        hideSystemBarsImmersive()
        rallyAnimation.resetBatAngles()
        showInGame = prefs.getBoolean(PREF_SHOW_INGAME, true)
        renderInGameVisibility()

        setupClickListeners()
        observeState()
    }

    override fun onStop() {
        viewModel.saveMatchInProgress()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        binding.glRallyBall.onResume()
        if (viewModel.state.value?.isMatchRunning == true) {
            rallyAnimation.startIfNeeded()
        }
    }

    override fun onPause() {
        rallyAnimation.stop()
        super.onPause()
        binding.glRallyBall.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBarsImmersive()
    }

    private fun setupClickListeners() {
        // Score taps – tap anywhere on a half of the screen to add a point to that player
        binding.tapAreaLeft.setOnClickListener {
            viewModel.addPoint(if (viewModel.state.value?.sidesSwapped == true) 2 else 1)
        }
        binding.tapAreaRight.setOnClickListener {
            viewModel.addPoint(if (viewModel.state.value?.sidesSwapped == true) 1 else 2)
        }
        binding.tvScore1.setOnClickListener {
            viewModel.addPoint(if (viewModel.state.value?.sidesSwapped == true) 2 else 1)
        }
        binding.tvScore2.setOnClickListener {
            viewModel.addPoint(if (viewModel.state.value?.sidesSwapped == true) 1 else 2)
        }

        // Long-press directly on the score digits opens the manual score editor.
        // Touch coordinates are recorded first so the long-press can be limited to the glyphs.
        val scoreTouchRecorder = View.OnTouchListener { _, event ->
            lastScoreTouchX = event.x
            lastScoreTouchY = event.y
            false
        }
        binding.tvScore1.setOnTouchListener(scoreTouchRecorder)
        binding.tvScore2.setOnTouchListener(scoreTouchRecorder)

        val scoreLongClick = View.OnLongClickListener { view ->
            val scoreView = view as? TextView ?: return@OnLongClickListener false
            if (isHandlingServeDrag) return@OnLongClickListener false
            if (!isTouchOnScoreDigits(scoreView, lastScoreTouchX, lastScoreTouchY)) {
                return@OnLongClickListener false
            }
            val state = viewModel.state.value
            if (state != null && !state.isMatchRunning && state.matchWinner == null && state.hasMatchStarted) {
                showEditScoreDialog(viewModel)
                true
            } else {
                false
            }
        }
        binding.tvScore1.setOnLongClickListener(scoreLongClick)
        binding.tvScore2.setOnLongClickListener(scoreLongClick)

        // Set boards – tap adds a point to that side, long-press opens the same score/set editor.
        binding.tvSet1.setOnClickListener {
            viewModel.addPoint(if (viewModel.state.value?.sidesSwapped == true) 2 else 1)
        }
        binding.tvSet2.setOnClickListener {
            viewModel.addPoint(if (viewModel.state.value?.sidesSwapped == true) 1 else 2)
        }
        val setLongClick = View.OnLongClickListener {
            if (isHandlingServeDrag) return@OnLongClickListener false
            openEditScoreDialogIfAllowed()
        }
        binding.tvSet1.setOnLongClickListener(setLongClick)
        binding.tvSet2.setOnLongClickListener(setLongClick)

        // Name long-press – long-press name to edit to avoid accidental taps near swap icon
        // A normal tap on the name keeps the usual "add a point" behaviour for that side.
        binding.tvPlayer1Name.setOnClickListener {
            viewModel.addPoint(if (viewModel.state.value?.sidesSwapped == true) 2 else 1)
        }
        binding.tvPlayer2Name.setOnClickListener {
            viewModel.addPoint(if (viewModel.state.value?.sidesSwapped == true) 1 else 2)
        }
        binding.tvPlayer1Name.setOnLongClickListener {
            if (isHandlingServeDrag) return@setOnLongClickListener false
            showEditNameDialog(if (viewModel.state.value?.sidesSwapped == true) 2 else 1, viewModel)
            true
        }
        binding.tvPlayer2Name.setOnLongClickListener {
            if (isHandlingServeDrag) return@setOnLongClickListener false
            showEditNameDialog(if (viewModel.state.value?.sidesSwapped == true) 1 else 2, viewModel)
            true
        }

        binding.btnSetupMatch.setOnClickListener { confirmSetupMatch(viewModel) }
        binding.btnStartMatch.setOnClickListener { viewModel.startOrResumeMatch() }
        binding.btnPauseMatchText.setOnClickListener { viewModel.pauseMatch() }
        binding.btnUndoText.setOnClickListener { viewModel.undo() }
        binding.btnToggleInGame.setOnClickListener {
            showInGame = !showInGame
            prefs.edit().putBoolean(PREF_SHOW_INGAME, showInGame).apply()
            renderInGameVisibility()
        }
        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        binding.tvTournamentName.setOnLongClickListener {
            showEditTournamentNameDialog(viewModel)
            true
        }
        binding.ivSwapSides.setOnLongClickListener {
            viewModel.swapSides()
            true
        }
        setupServeBallDrag()
    }

    /**
     * Opens the manual score/set editor when the match is paused mid-way.
     * Returns true when the dialog was shown so long-press listeners can report consumption.
     */
    private fun openEditScoreDialogIfAllowed(): Boolean {
        val state = viewModel.state.value ?: return false
        if (state.isMatchRunning || state.matchWinner != null || !state.hasMatchStarted) return false
        showEditScoreDialog(viewModel)
        return true
    }

    /**
     * True when the touch point (in [scoreView] coordinates) landed on the rendered digits,
     * so long-pressing the empty area around the score does nothing.
     */
    private fun isTouchOnScoreDigits(scoreView: TextView, touchX: Float, touchY: Float): Boolean {
        val layout = scoreView.layout ?: return false
        if (layout.lineCount == 0) return false
        val slop = 8f * resources.displayMetrics.density
        val left = scoreView.totalPaddingLeft + layout.getLineLeft(0) - slop
        val right = scoreView.totalPaddingLeft + layout.getLineRight(0) + slop
        val top = scoreView.totalPaddingTop + layout.getLineTop(0) - slop
        val bottom = scoreView.totalPaddingTop + layout.getLineBottom(0) + slop
        return touchX >= left && touchX <= right && touchY >= top && touchY <= bottom
    }

    private fun setupServeBallDrag() {
        val density = resources.displayMetrics.density
        val restingElevation = 10f * density
        val draggingElevation = restingElevation + (14f * density)

        fun releaseServeDrag(draggedView: View, animateBack: Boolean) {
            if (animateBack) {
                draggedView.animate()
                    .translationX(0f)
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(160)
                    .withEndAction { draggedView.elevation = restingElevation }
                    .start()
            } else {
                draggedView.animate().cancel()
                draggedView.translationX = 0f
                draggedView.translationY = 0f
                draggedView.scaleX = 1f
                draggedView.scaleY = 1f
                draggedView.alpha = 1f
                draggedView.elevation = restingElevation
            }
            activeServeDragView = null
            isHandlingServeDrag = false
            isDraggingServeBall = false
        }

        fun crossedToOtherSide(draggedView: View, rawX: Float): Boolean {
            val dividerCenterX = binding.rootLayout.width / 2f
            val ballCenterX = draggedView.left + draggedView.width / 2f + draggedView.translationX
            val location = IntArray(2)
            binding.rootLayout.getLocationOnScreen(location)
            val fingerXInRoot = rawX - location[0]
            return if (draggedView.id == R.id.ivServe1) {
                ballCenterX > dividerCenterX || fingerXInRoot > dividerCenterX
            } else {
                ballCenterX < dividerCenterX || fingerXInRoot < dividerCenterX
            }
        }

        val dragListener = View.OnTouchListener { overlay, event ->
            val state = viewModel.state.value ?: return@OnTouchListener false
            if (state.matchWinner != null) return@OnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val draggedView = findServeIndicatorUnderTouch(event.rawX, event.rawY)
                        ?: return@OnTouchListener false
                    // Kill any snap-back animation still running, otherwise it fights the drag.
                    draggedView.animate().cancel()
                    draggedView.translationX = 0f
                    draggedView.translationY = 0f

                    activeServeDragView = draggedView
                    isHandlingServeDrag = true
                    // The ball follows the finger straight away – this is a drag, not a long-press.
                    isDraggingServeBall = true
                    serveDragStartRawX = event.rawX
                    serveDragStartRawY = event.rawY
                    draggedView.elevation = draggingElevation
                    overlay.parent?.requestDisallowInterceptTouchEvent(true)
                    overlay.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    draggedView.animate()
                        .scaleX(1.35f)
                        .scaleY(1.35f)
                        .alpha(0.92f)
                        .setDuration(90)
                        .start()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val draggedView = activeServeDragView ?: return@OnTouchListener false
                    draggedView.translationX = event.rawX - serveDragStartRawX
                    draggedView.translationY = event.rawY - serveDragStartRawY
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val draggedView = activeServeDragView ?: return@OnTouchListener false
                    val swap = crossedToOtherSide(draggedView, event.rawX)
                    if (swap) {
                        overlay.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        // Reset instantly so the indicator can appear on the new side cleanly.
                        releaseServeDrag(draggedView, animateBack = false)
                        viewModel.swapServer()
                    } else {
                        draggedView.performClick()
                        releaseServeDrag(draggedView, animateBack = true)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    val draggedView = activeServeDragView ?: return@OnTouchListener false
                    releaseServeDrag(draggedView, animateBack = true)
                    true
                }
                else -> false
            }
        }
        binding.serveDragTouchOverlay.setOnTouchListener(dragListener)
    }

    private fun findServeIndicatorUnderTouch(rawX: Float, rawY: Float): View? {
        val hitRect = Rect()
        fun hits(view: View): Boolean {
            if (view.visibility != View.VISIBLE) return false
            if (!view.getGlobalVisibleRect(hitRect)) return false
            val density = resources.displayMetrics.density
            val extraHitSlop = (16f * density).roundToInt()
            hitRect.inset(-extraHitSlop, -extraHitSlop)
            return hitRect.contains(rawX.roundToInt(), rawY.roundToInt())
        }

        return when {
            hits(binding.ivServe1) -> binding.ivServe1
            hits(binding.ivServe2) -> binding.ivServe2
            else -> null
        }
    }

    private fun observeState() {
        viewModel.ongoingMatchExists.observe(this) { exists ->
            if (exists && !viewModel.hasRespondedToOngoingMatch && viewModel.state.value?.hasMatchStarted == false) {
                showResumeMatchDialog()
            }
        }

        viewModel.state.observe(this) { state ->
            renderPlayersAndScores(state)
            binding.rootLayout.post { alignCurrentScoreGlyphsToSetGlyphs() }
            updateMatchTimerText()
            updateMatchSummaryPanel(binding, viewModel, state)
            renderTournamentName(state)
            handleDecidingSetSwapNotice(state)
            renderServeIndicators(state)
            renderBackground(state)
            binding.btnStartMatch.text = getString(
                if (state.hasMatchStarted) R.string.btn_resume_match else R.string.btn_start_match,
            )
            renderControls(state)
        }
    }

    private fun isLeftPlayerServing(state: GameViewModel.GameState): Boolean {
        val p1OnLeft = !state.sidesSwapped
        return (p1OnLeft && state.server == 1) || (!p1OnLeft && state.server == 2)
    }

    private fun renderPlayersAndScores(state: GameViewModel.GameState) {
        val p1OnLeft = !state.sidesSwapped
        val isDoubles = state.matchMode == GameViewModel.MATCH_MODE_DOUBLES
        val leftServing = isLeftPlayerServing(state)
        fun formatDisplayName(name: String): String {
            return if (isDoubles) name.replace(" / ", "\n") else name
        }
        val leftName = formatDisplayName(if (p1OnLeft) state.player1Name else state.player2Name)
        val rightName = formatDisplayName(if (p1OnLeft) state.player2Name else state.player1Name)
        binding.tvPlayer1Name.text = serveBallText(leftName, isDoubles && leftServing)
        binding.tvPlayer2Name.text = serveBallText(rightName, isDoubles && !leftServing, placeAtEnd = true)
        val playerNameTextSizeSp = if (isDoubles) 14f else 18f
        binding.tvPlayer1Name.setTextSize(TypedValue.COMPLEX_UNIT_SP, playerNameTextSizeSp)
        binding.tvPlayer2Name.setTextSize(TypedValue.COMPLEX_UNIT_SP, playerNameTextSizeSp)
        binding.tvPlayer1Name.maxLines = if (isDoubles) 2 else 1
        binding.tvPlayer2Name.maxLines = if (isDoubles) 2 else 1
        binding.tvScore1.text = (if (p1OnLeft) state.score1 else state.score2).toString()
        binding.tvScore2.text = (if (p1OnLeft) state.score2 else state.score1).toString()
        binding.tvSet1.text = (if (p1OnLeft) state.sets1 else state.sets2).toString()
        binding.tvSet2.text = (if (p1OnLeft) state.sets2 else state.sets1).toString()
    }

    private fun renderTournamentName(state: GameViewModel.GameState) {
        val tName = state.tournamentName
        binding.tvTournamentName.text = tName.ifBlank { getString(R.string.tournament_name_default) }
        binding.tvTournamentName.alpha = if (tName.isBlank()) 0.35f else 0.70f
    }

    private fun handleDecidingSetSwapNotice(state: GameViewModel.GameState) {
        if (state.decidingSetSwapNoticeVersion > lastShownDecidingSwapNoticeVersion) {
            lastShownDecidingSwapNoticeVersion = state.decidingSetSwapNoticeVersion
            decidingSwapSnackbar?.dismiss()
            decidingSwapSnackbar = Snackbar.make(
                binding.rootLayout,
                R.string.notice_swap_sides_now,
                Snackbar.LENGTH_INDEFINITE,
            ).setAction(R.string.notice_done) {
                viewModel.confirmDecidingSetSideSwapDone()
            }
            decidingSwapSnackbar?.show()
        }

        if (!state.awaitingDecidingSetSwapConfirmation) {
            decidingSwapSnackbar?.dismiss()
            decidingSwapSnackbar = null
        }
    }

    /** Serve indicator follows the player, not the side. */
    private fun renderServeIndicators(state: GameViewModel.GameState) {
        val leftServing = isLeftPlayerServing(state)
        binding.ivServe1.visibility = if (leftServing) View.VISIBLE else View.INVISIBLE
        binding.ivServe2.visibility = if (!leftServing) View.VISIBLE else View.INVISIBLE
    }

    private fun renderBackground(state: GameViewModel.GameState) {
        val backgroundColorRes = when {
            state.isMatchRunning -> R.color.background_running
            state.hasMatchStarted -> R.color.background_paused
            else -> R.color.background
        }
        val targetColor = ContextCompat.getColor(this, backgroundColorRes)

        if (state.isMatchRunning && !previousIsMatchRunning) {
            // Flash effect: White -> Game Green
            ValueAnimator.ofObject(ArgbEvaluator(), Color.WHITE, targetColor).apply {
                duration = 500L
                addUpdateListener { animator ->
                    binding.rootLayout.setBackgroundColor(animator.animatedValue as Int)
                }
                start()
            }
        } else {
            binding.rootLayout.setBackgroundColor(targetColor)
        }
        previousIsMatchRunning = state.isMatchRunning
    }

    private fun renderControls(state: GameViewModel.GameState) {
        val isMatchFinished = state.matchWinner != null
        val isAwaitingSwapConfirm = state.awaitingDecidingSetSwapConfirmation

        if (state.isMatchRunning) {
            binding.btnPauseMatchText.visibility = View.VISIBLE
            binding.btnUndoText.visibility = View.VISIBLE

            binding.btnStartMatch.visibility = View.GONE
            binding.btnSetupMatch.visibility = View.GONE
            binding.btnHistory.visibility = View.GONE
            binding.btnToggleInGame.visibility = View.GONE

            binding.centerControlsRow.visibility = View.VISIBLE
            binding.ivSwapSides.visibility = View.GONE
            rallyAnimation.startIfNeeded()
            startMatchTimerTickerIfNeeded()
        } else {
            binding.btnPauseMatchText.visibility = View.GONE
            binding.btnUndoText.visibility = View.GONE

            // Show Start/Resume, Setup, and History when not running, unless awaiting swap
            val showControls = !isAwaitingSwapConfirm
            binding.btnStartMatch.visibility = if (showControls) View.VISIBLE else View.GONE
            binding.btnSetupMatch.visibility = if (showControls) View.VISIBLE else View.GONE
            binding.btnHistory.visibility = if (showControls) View.VISIBLE else View.GONE
            val isPausedMidMatch = state.hasMatchStarted && !isMatchFinished
            binding.btnToggleInGame.visibility = if (showControls && isPausedMidMatch) View.VISIBLE else View.GONE

            binding.centerControlsRow.visibility = if (showControls) View.VISIBLE else View.GONE
            binding.ivSwapSides.visibility = if (!isMatchFinished && !isAwaitingSwapConfirm) View.VISIBLE else View.GONE
            rallyAnimation.stop()
            stopMatchTimerTicker()
        }
    }

    /**
     * Shows or hides the in-game animation: both bats and the rally ball. They are INVISIBLE rather than
     * GONE when hidden, so they keep their size and the rally animation can go on placing them; they come
     * back in the right spot when shown again.
     */
    private fun renderInGameVisibility() {
        val inGameVisibility = if (showInGame) View.VISIBLE else View.INVISIBLE
        binding.ivBatLeft.visibility = inGameVisibility
        binding.ivBatRight.visibility = inGameVisibility
        binding.glRallyBall.visibility = inGameVisibility
        binding.btnToggleInGame.setText(if (showInGame) R.string.btn_hide_ingame else R.string.btn_show_ingame)
    }

    override fun onDestroy() {
        decidingSwapSnackbar?.dismiss()
        decidingSwapSnackbar = null
        rallyAnimation.stop()
        stopMatchTimerTicker()
        super.onDestroy()
    }

    private fun alignCurrentScoreGlyphsToSetGlyphs() {
        alignGlyphTop(binding.tvScore1, binding.tvSet1)
        alignGlyphTop(binding.tvScore2, binding.tvSet2)
    }

    private fun alignGlyphTop(scoreView: TextView, targetView: TextView) {
        val scoreBaseline = scoreView.baseline
        val targetBaseline = targetView.baseline
        if (scoreBaseline < 0 || targetBaseline < 0) return

        val scoreGlyphTop = scoreView.top + scoreBaseline + scoreView.paint.fontMetrics.ascent
        val targetGlyphTop = targetView.top + targetBaseline + targetView.paint.fontMetrics.ascent
        val density = resources.displayMetrics.density
        val visualCalibrationPx = -12f * density
        scoreView.translationY = (targetGlyphTop - scoreGlyphTop) + visualCalibrationPx
    }

    private fun startMatchTimerTickerIfNeeded() {
        timerHandler.removeCallbacks(timerTick)
        timerHandler.post(timerTick)
    }

    private fun stopMatchTimerTicker() {
        timerHandler.removeCallbacks(timerTick)
    }

    private fun showResumeMatchDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dialog_resume_match_title)
            .setMessage(R.string.dialog_resume_match_message)
            .setPositiveButton(R.string.dialog_yes) { _, _ ->
                viewModel.resumeMatch()
            }
            .setNegativeButton(R.string.dialog_no) { _, _ ->
                viewModel.discardMatch()
            }
            .setCancelable(false)
            .create()
        dialog.setOnShowListener { styleDialogButtons(dialog) }
        dialog.show()
    }

    private fun updateMatchTimerText() {
        val elapsed = viewModel.getElapsedPlayedMs()
        binding.tvMatchTimer.text = formatClockDuration(elapsed)
    }
}
