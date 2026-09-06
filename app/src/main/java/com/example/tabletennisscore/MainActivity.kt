package com.example.tabletennisscore

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.text.InputFilter
import android.text.Spanned
import android.text.InputType
import android.os.Bundle
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import android.graphics.Typeface
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.graphics.toColorInt
import com.example.tabletennisscore.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: GameViewModel by viewModels()
    private var serveDragStartRawX = 0f
    private var serveDragStartRawY = 0f
    private var lastShownDecidingSwapNoticeVersion = 0
    private var decidingSwapSnackbar: Snackbar? = null
    private var previousIsMatchRunning = false
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
        hideSystemBars()

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
    }

    override fun onPause() {
        super.onPause()
        binding.glRallyBall.onPause()
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

    private fun setupClickListeners() {
        // Score taps – tap the score area to add a point
        binding.tvScore1.setOnClickListener {
            viewModel.addPoint(if (viewModel.state.value?.sidesSwapped == true) 2 else 1)
        }
        binding.tvScore2.setOnClickListener {
            viewModel.addPoint(if (viewModel.state.value?.sidesSwapped == true) 1 else 2)
        }

        binding.tvScore1.setOnLongClickListener {
            val state = viewModel.state.value
            if (state?.isMatchRunning == false && state.matchWinner == null && state.hasMatchStarted) {
                showEditScoreDialog()
                true
            } else false
        }
        binding.tvScore2.setOnLongClickListener {
            val state = viewModel.state.value
            if (state?.isMatchRunning == false && state.matchWinner == null && state.hasMatchStarted) {
                showEditScoreDialog()
                true
            } else false
        }

        // Name long-press – long-press name to edit to avoid accidental taps near swap icon
        binding.tvPlayer1Name.setOnLongClickListener {
            showEditNameDialog(if (viewModel.state.value?.sidesSwapped == true) 2 else 1)
            true
        }
        binding.tvPlayer2Name.setOnLongClickListener {
            showEditNameDialog(if (viewModel.state.value?.sidesSwapped == true) 1 else 2)
            true
        }

        binding.btnSetupMatch.setOnClickListener { confirmSetupMatch() }
        binding.btnStartMatch.setOnClickListener { viewModel.startOrResumeMatch() }
        binding.btnPauseMatchText.setOnClickListener { viewModel.pauseMatch() }
        binding.btnUndoText.setOnClickListener { viewModel.undo() }
        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        binding.tvTournamentName.setOnLongClickListener {
            showEditTournamentNameDialog()
            true
        }
        binding.ivSwapSides.setOnLongClickListener {
            viewModel.swapSides()
            true
        }
        setupServeBallDrag()
    }

    private fun setupServeBallDrag() {
        val dragListener = View.OnTouchListener { view, event ->
            val state = viewModel.state.value ?: return@OnTouchListener false
            if (state.matchWinner != null) return@OnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    serveDragStartRawX = event.rawX
                    serveDragStartRawY = event.rawY
                    view.elevation = 24f
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    view.translationX = event.rawX - serveDragStartRawX
                    view.translationY = event.rawY - serveDragStartRawY
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (event.action == MotionEvent.ACTION_UP) {
                        val ballCenterX = view.left + view.width / 2f + view.translationX
                        val dividerCenterX = binding.divider.x + binding.divider.width / 2f
                        val crossedOver = if (view.id == R.id.ivServe1)
                            ballCenterX > dividerCenterX
                        else
                            ballCenterX < dividerCenterX
                        if (crossedOver) viewModel.swapServer()
                        view.performClick()
                    }
                    view.animate().translationX(0f).translationY(0f).setDuration(150).start()
                    view.elevation = 0f
                    true
                }
                else -> false
            }
        }
        binding.ivServe1.setOnTouchListener(dragListener)
        binding.ivServe2.setOnTouchListener(dragListener)
    }

    private fun observeState() {
        viewModel.ongoingMatchExists.observe(this) { exists ->
            if (exists && !viewModel.hasRespondedToOngoingMatch && viewModel.state.value?.hasMatchStarted == false) {
                showResumeMatchDialog()
            }
        }

        viewModel.state.observe(this) { state ->
            val p1OnLeft = !state.sidesSwapped
            binding.tvPlayer1Name.text = if (p1OnLeft) state.player1Name else state.player2Name
            binding.tvPlayer2Name.text = if (p1OnLeft) state.player2Name else state.player1Name
            binding.tvScore1.text = (if (p1OnLeft) state.score1 else state.score2).toString()
            binding.tvScore2.text = (if (p1OnLeft) state.score2 else state.score1).toString()
            val leftSets = if (p1OnLeft) state.sets1 else state.sets2
            val rightSets = if (p1OnLeft) state.sets2 else state.sets1
            binding.tvSets.text = getString(R.string.score_sets_format, leftSets, rightSets)
            updateMatchTimerText()
            updateMatchSummaryPanel(state)

            // Tournament name
            val tName = state.tournamentName
            binding.tvTournamentName.text = tName.ifBlank { getString(R.string.tournament_name_default) }
            binding.tvTournamentName.alpha = if (tName.isBlank()) 0.35f else 0.70f

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

            // Serve indicator follows the player, not the side
            val leftServing = (p1OnLeft && state.server == 1) || (!p1OnLeft && state.server == 2)
            binding.ivServe1.visibility = if (leftServing) View.VISIBLE else View.INVISIBLE
            binding.ivServe2.visibility = if (!leftServing) View.VISIBLE else View.INVISIBLE

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

            binding.btnStartMatch.text = getString(
                if (state.hasMatchStarted) R.string.btn_resume_match else R.string.btn_start_match,
            )

            val isMatchFinished = state.matchWinner != null
            val isAwaitingSwapConfirm = state.awaitingDecidingSetSwapConfirmation

            if (state.isMatchRunning) {
                binding.btnPauseMatchText.visibility = View.VISIBLE
                binding.dividerPauseTop.visibility = View.VISIBLE
                binding.btnUndoText.visibility = View.VISIBLE
                binding.dividerUndoTop.visibility = View.VISIBLE
                
                binding.btnStartMatch.visibility = View.GONE
                binding.dividerStartTop.visibility = View.GONE
                binding.btnSetupMatch.visibility = View.GONE
                binding.dividerSetupTop.visibility = View.GONE
                binding.btnHistory.visibility = View.GONE
                binding.dividerHistoryTop.visibility = View.GONE
                
                binding.centerControlsRow.visibility = View.VISIBLE
                binding.ivSwapSides.visibility = View.GONE
                startRallyBallAnimationIfNeeded()
                startMatchTimerTickerIfNeeded()
            } else {
                binding.btnPauseMatchText.visibility = View.GONE
                binding.dividerPauseTop.visibility = View.GONE
                binding.btnUndoText.visibility = View.GONE
                binding.dividerUndoTop.visibility = View.GONE
                
                // Show Start/Resume, Setup, and History when not running, unless awaiting swap
                val showControls = !isAwaitingSwapConfirm
                binding.btnStartMatch.visibility = if (showControls) View.VISIBLE else View.GONE
                binding.dividerStartTop.visibility = if (showControls) View.VISIBLE else View.GONE
                binding.btnSetupMatch.visibility = if (showControls) View.VISIBLE else View.GONE
                binding.dividerSetupTop.visibility = if (showControls) View.VISIBLE else View.GONE
                binding.btnHistory.visibility = if (showControls) View.VISIBLE else View.GONE
                binding.dividerHistoryTop.visibility = if (showControls) View.VISIBLE else View.GONE
                
                binding.centerControlsRow.visibility = if (showControls) View.VISIBLE else View.GONE
                binding.ivSwapSides.visibility = if (!isMatchFinished && !isAwaitingSwapConfirm) View.VISIBLE else View.GONE
                stopRallyBallAnimation()
                stopMatchTimerTicker()
            }

        }
    }

    override fun onDestroy() {
        decidingSwapSnackbar?.dismiss()
        decidingSwapSnackbar = null
        stopRallyBallAnimation()
        stopMatchTimerTicker()
        super.onDestroy()
    }

    private fun startMatchTimerTickerIfNeeded() {
        timerHandler.removeCallbacks(timerTick)
        timerHandler.post(timerTick)
    }

    private fun stopMatchTimerTicker() {
        timerHandler.removeCallbacks(timerTick)
    }

    private fun showResumeMatchDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_resume_match_title)
            .setMessage(R.string.dialog_resume_match_message)
            .setPositiveButton(R.string.dialog_yes) { _, _ ->
                viewModel.resumeMatch()
            }
            .setNegativeButton(R.string.dialog_no) { _, _ ->
                viewModel.discardMatch()
            }
            .setCancelable(false)
            .show()
    }

    private fun updateMatchTimerText() {
        val elapsed = viewModel.getElapsedPlayedMs()
        binding.tvMatchTimer.text = formatElapsedTime(elapsed)
    }

    private fun formatElapsedTime(elapsedMs: Long): String {
        val totalSeconds = elapsedMs / 1000L
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    private fun updateMatchSummaryPanel(state: GameViewModel.GameState) {
        if (state.matchWinner == null) {
            binding.matchSummaryPanel.visibility = View.GONE
            binding.tvMatchTimer.visibility = View.VISIBLE
            return
        }
        val winnerName = if (state.matchWinner == 1) state.player1Name else state.player2Name
        val winnerColor = ContextCompat.getColor(this, R.color.summary_winner_text)
        
        binding.matchSummaryPanel.visibility = View.VISIBLE
        binding.tvMatchTimer.visibility = View.GONE
        // Hide the controls while the summary is showing to avoid clutter
        binding.centerControlsRow.visibility = View.GONE
        
        binding.tvMatchSummaryWinner.text = getString(R.string.match_summary_winner, winnerName)
        binding.tvMatchSummaryWinner.setTextColor(winnerColor)
        renderMatchSummaryScoreTable(state)
        binding.tvMatchSummaryTime.text = getString(
            R.string.match_summary_time,
            formatElapsedTime(viewModel.getElapsedPlayedMs()),
        )
        binding.matchSummaryPanel.setBackgroundColor("#CC220000".toColorInt())

        // Position panel in the center of the screen
        val margin = (12 * resources.displayMetrics.density).toInt()
        ConstraintSet().apply {
            clone(binding.rootLayout)
            clear(R.id.matchSummaryPanel, ConstraintSet.START)
            clear(R.id.matchSummaryPanel, ConstraintSet.END)
            constrainWidth(R.id.matchSummaryPanel, ConstraintSet.WRAP_CONTENT)
            connect(R.id.matchSummaryPanel, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, margin)
            connect(R.id.matchSummaryPanel, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, margin)
            clear(R.id.matchSummaryPanel, ConstraintSet.TOP)
            clear(R.id.matchSummaryPanel, ConstraintSet.BOTTOM)
            connect(R.id.matchSummaryPanel, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, margin)
            connect(R.id.matchSummaryPanel, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, margin)
            applyTo(binding.rootLayout)
        }

        val closeBox = {
            binding.matchSummaryPanel.visibility = View.GONE
            binding.tvMatchTimer.visibility = View.VISIBLE
            // Show the "pillars" (controls) again
            binding.centerControlsRow.visibility = View.VISIBLE
            // Reset match so scores are 0-0 and "Start Match" is shown for next game
            viewModel.resetMatch()
        }

        // Dismiss when tapping the summary panel itself
        binding.matchSummaryPanel.setOnClickListener { closeBox() }
        
        // Remove full-screen click listeners to prevent accidental dismissal outside
        binding.rootLayout.setOnClickListener(null)
        binding.rootLayout.isClickable = false
    }

    private fun renderMatchSummaryScoreTable(state: GameViewModel.GameState) {
        val table = binding.tableMatchSummaryScores
        table.removeAllViews()
        val density = resources.displayMetrics.density
        val winnerColor = ContextCompat.getColor(this, R.color.summary_winner_text)
        val whiteColor = ContextCompat.getColor(this, android.R.color.white)
        val dividerColor = ContextCompat.getColor(this, R.color.divider)
        fun Int.dp() = (this * density).toInt()

        // Ökad storlek på fyrkanten för set-vinster (från 24dp till 34dp)
        val setBoxSize = 28.dp()

        // Höjda textstorlekar (från 15f till 20f) och bredder (minW)
        fun cell(
            text: String,
            textSizeSp: Float,
            grav: Int,
            textColor: Int,
            bold: Boolean = false,
            minW: Int = 0,
            marginStart: Int = 0,
            marginEnd: Int = 0,
        ) =
            TextView(this).apply {
                this.text = text
                textSize = textSizeSp
                gravity = grav
                setTextColor(textColor)
                if (bold) setTypeface(typeface, Typeface.BOLD)
                if (minW > 0) minWidth = minW.dp()
                layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT)
                    .also {
                        if (marginStart > 0) it.marginStart = marginStart.dp()
                        if (marginEnd > 0) it.marginEnd = marginEnd.dp()
                    }
            }

        // Set with red and blue bg
        fun setCountCell(text: String, winnerRow: Boolean) = TextView(this).apply {
            this.text = text
            textSize = 20f  // 20f player wins names and set numbers
            gravity = Gravity.CENTER
            setTextColor(whiteColor)
            includeFontPadding = false
            isSingleLine = true
            setTypeface(typeface, Typeface.BOLD)
            setBackgroundResource(
                if (winnerRow) R.drawable.history_set_count_box else R.drawable.history_set_count_box_loser,
            )
            setPadding(6.dp(), 0, 6.dp(), 0)
            layoutParams = TableRow.LayoutParams(setBoxSize, setBoxSize).also { it.marginEnd = 14.dp() }
        }

        listOf(
            Quadruple(1, state.player1Name, state.sets1, state.setResults.map { it.first }),
            Quadruple(2, state.player2Name, state.sets2, state.setResults.map { it.second }),
        ).forEach { (player, name, sets, _) ->
            val isWinner = player == state.matchWinner
            val nameColor = if (isWinner) winnerColor else whiteColor
            table.addView(TableRow(this).apply {
                addView(cell(name, 20f, Gravity.START or Gravity.CENTER_VERTICAL, nameColor, bold = false, minW = 90, marginStart = 12, marginEnd = 20))
                addView(setCountCell(sets.toString(), isWinner))
                
                // Add vertical "pillar" separator
                addView(cell("|", 18f, Gravity.CENTER, dividerColor, bold = true, marginEnd = 16))
                
                state.setResults.forEach { setResult ->
                    val playerScore = if (player == 1) setResult.first else setResult.second
                    val wonThisSet = if (player == 1) setResult.first > setResult.second else setResult.second > setResult.first
                    val scoreColor = if (wonThisSet) winnerColor else whiteColor
                    // Set result and space between set numbers
                    addView(cell(playerScore.toString(), 20f, Gravity.CENTER, scoreColor, bold = wonThisSet, minW = 28, marginEnd = 8))
                }
            })
        }
    }

    private data class Quadruple<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
    )

    private fun startRallyBallAnimationIfNeeded() {
        binding.rootLayout.post {
            val latestState = viewModel.state.value ?: return@post
            if (!latestState.isMatchRunning) {
                binding.glRallyBall.renderer.isAnimating = false
                return@post
            }

            val leftScore = binding.tvScore1
            val rightScore = binding.tvScore2
            val net = binding.divider

            val dividerCenterX = net.x + (net.width / 2f)
            val leftScoreCenterX = leftScore.x + (leftScore.width / 2f)
            val rightScoreCenterX = rightScore.x + (rightScore.width / 2f)
            val halfTravel = minOf(
                dividerCenterX - leftScoreCenterX,
                rightScoreCenterX - dividerCenterX,
            ) * 0.68f
            if (halfTravel <= 0f) return@post

            val leftCenterX = dividerCenterX - halfTravel
            val rightCenterX = dividerCenterX + halfTravel
            
            val ballWidth = 20 * resources.displayMetrics.density
            val ballHeight = 20 * resources.displayMetrics.density
            
            val leftX = leftCenterX - (ballWidth / 2f)
            val rightX = rightCenterX - (ballWidth / 2f)

            val baseCenterY = (
                (leftScore.y + (leftScore.height * 0.58f)) +
                    (rightScore.y + (rightScore.height * 0.58f))
                ) / 2f
            val baseY = baseCenterY - (ballHeight / 2f)
            val netTopY = net.y + (net.height * 0.20f)
            val desiredArc = abs(rightX - leftX) * 0.20f
            val minArcToClearNet = (baseY - netTopY) + ballHeight
            val arcHeight = maxOf(70f, minOf(220f, maxOf(desiredArc, minArcToClearNet)))

            binding.glRallyBall.renderer.apply {
                this.leftX = leftX
                this.rightX = rightX
                this.baseY = baseY
                this.arcHeight = arcHeight
                this.ballWidth = ballWidth
                this.ballHeight = ballHeight
                this.isAnimating = true
            }
        }
    }

    private fun stopRallyBallAnimation() {
        binding.glRallyBall.renderer.isAnimating = false
    }


    private fun showEditTournamentNameDialog() {
        val currentName = viewModel.state.value?.tournamentName ?: ""
        val editText = EditText(this).apply {
            setText(currentName)
            if (currentName.isNotBlank()) selectAll()
            hint = getString(R.string.tournament_name_hint)
            filters = arrayOf(
                InputFilter.LengthFilter(GameViewModel.MAX_TOURNAMENT_NAME_LENGTH),
                TitleCaseInputFilter()
            )
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            imeOptions = EditorInfo.IME_ACTION_DONE
            maxLines = 1
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dialog_edit_tournament_name)
            .setView(editText)
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                viewModel.setTournamentName(editText.text.toString())
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()
        dialog.setOnShowListener {
            val okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            editText.setOnEditorActionListener { _, actionId, event ->
                val enterPressed = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
                if (actionId == EditorInfo.IME_ACTION_DONE || enterPressed) {
                    okButton.performClick(); true
                } else false
            }
        }
        dialog.show()
    }

    private fun showEditNameDialog(player: Int) {        val state = viewModel.state.value ?: return
        val currentName = if (player == 1) state.player1Name else state.player2Name
        val defaultName = if (player == 1) getString(R.string.player1_default) else getString(R.string.player2_default)
        val isDefaultName = currentName == defaultName

        val editText = EditText(this).apply {
            setText(if (isDefaultName) "" else currentName)
            if (!isDefaultName) {
                selectAll()
            }
            hint = getString(R.string.dialog_hint_name)
            filters = arrayOf(
                InputFilter.LengthFilter(GameViewModel.MAX_PLAYER_NAME_LENGTH),
                TitleCaseInputFilter()
            )
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            imeOptions = EditorInfo.IME_ACTION_DONE
            maxLines = 1
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_edit_name))
            .setView(editText)
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                viewModel.setPlayerName(player, editText.text.toString())
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()

        dialog.setOnShowListener {
            val okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            editText.setOnEditorActionListener { _, actionId, event ->
                val enterPressed = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
                if (actionId == EditorInfo.IME_ACTION_DONE || enterPressed) {
                    okButton.performClick()
                    true
                } else {
                    false
                }
            }
        }
        dialog.show()
    }

    private fun showEditScoreDialog() {
        val state = viewModel.state.value ?: return
        if (state.isMatchRunning || state.matchWinner != null || !state.hasMatchStarted) return

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val horizontalPadding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                20f,
                resources.displayMetrics,
            ).toInt()
            val topPadding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                8f,
                resources.displayMetrics,
            ).toInt()
            setPadding(horizontalPadding, topPadding, horizontalPadding, 0)
        }

        fun buildScoreInput(playerName: String, score: Int): EditText {
            return EditText(this).apply {
                hint = playerName
                setText(score.toString())
                setSelection(text.length)
                inputType = InputType.TYPE_CLASS_NUMBER
            }
        }

        val completedSetInputs = mutableListOf<Pair<EditText, EditText>>()

        fun addSetEditorRow(labelText: String, score1: Int, score2: Int, completedSet: Boolean): Pair<EditText, EditText> {
            val label = TextView(this).apply {
                text = labelText
                setTypeface(null, Typeface.NORMAL)
                setPadding(0, if (completedSet) 12 else 16, 0, 4)
            }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val player1Input = buildScoreInput(getString(R.string.dialog_score_player1, state.player1Name), score1)
            val player2Input = buildScoreInput(getString(R.string.dialog_score_player2, state.player2Name), score2)
            val player1Params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 8
            }
            val player2Params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 8
            }

            row.addView(player1Input, player1Params)
            row.addView(player2Input, player2Params)

            if (completedSet) {
                val deleteSet = TextView(this).apply {
                    text = getString(R.string.dialog_delete_set)
                    setTypeface(null, Typeface.NORMAL)
                    setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_dark))
                    setPadding(12, 0, 0, 0)
                    setOnClickListener {
                        content.removeView(label)
                        content.removeView(row)
                        completedSetInputs.remove(player1Input to player2Input)
                    }
                }
                row.addView(deleteSet)
            }

            content.addView(label)
            content.addView(row)
            return player1Input to player2Input
        }

        state.setResults.forEachIndexed { index, set ->
            completedSetInputs.add(
                addSetEditorRow(getString(R.string.dialog_set_label, index + 1), set.first, set.second, completedSet = true),
            )
        }

        val currentSetInputs = addSetEditorRow(
            getString(R.string.dialog_current_set_label),
            state.score1,
            state.score2,
            completedSet = false,
        )

        val scrollContent = ScrollView(this).apply {
            addView(content)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_edit_score_title)
            .setView(scrollContent)
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                val completedSets = completedSetInputs.map {
                    val score1 = it.first.text.toString().toIntOrNull() ?: 0
                    val score2 = it.second.text.toString().toIntOrNull() ?: 0
                    score1 to score2
                }
                val currentSet = (
                    currentSetInputs.first.text.toString().toIntOrNull() ?: 0
                ) to (
                    currentSetInputs.second.text.toString().toIntOrNull() ?: 0
                )
                if (!viewModel.updatePausedMatchScores(completedSets, currentSet.first, currentSet.second)) {
                    Toast.makeText(this, R.string.error_invalid_manual_score, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun confirmSetupMatch() {
        val state = viewModel.state.value ?: return
        if (!state.hasMatchStarted) {
            showSetupMatchDialog()
            return
        }

        AlertDialog.Builder(this)
            .setMessage(R.string.confirm_setup_match_message)
            .setPositiveButton(R.string.dialog_yes) { _, _ -> showSetupMatchDialog() }
            .setNegativeButton(R.string.dialog_no, null)
            .show()
    }

    private fun showSetupMatchDialog() {
        val state = viewModel.state.value ?: return
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val horizontalPadding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                10f,
                resources.displayMetrics,
            ).toInt()
            val topPadding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                8f,
                resources.displayMetrics,
            ).toInt()
            setPadding(horizontalPadding, topPadding, horizontalPadding, 0)
        }

        val bestOfRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 16, 0, 0)
        }
        val bestOfLabel = androidx.appcompat.widget.AppCompatTextView(this).apply {
            text = getString(R.string.dialog_best_of_sets)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(0, 0, 12, 0)
        }
        val bestOfGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
        }
        val bestOf1 = RadioButton(this).apply {
            id = View.generateViewId()
            text = "1"
        }
        val bestOf3 = RadioButton(this).apply {
            id = View.generateViewId()
            text = "3"
        }
        val bestOf5 = RadioButton(this).apply {
            id = View.generateViewId()
            text = "5"
        }
        val bestOf7 = RadioButton(this).apply {
            id = View.generateViewId()
            text = "7"
        }
        bestOfGroup.addView(bestOf1)
        bestOfGroup.addView(bestOf3)
        bestOfGroup.addView(bestOf5)
        bestOfGroup.addView(bestOf7)

        when (state.bestOfSets) {
            1 -> bestOfGroup.check(bestOf1.id)
            3 -> bestOfGroup.check(bestOf3.id)
            7 -> bestOfGroup.check(bestOf7.id)
            else -> bestOfGroup.check(bestOf5.id)
        }

        bestOfRow.addView(bestOfLabel)
        bestOfRow.addView(bestOfGroup)

        content.addView(bestOfRow)

        // Round Selection
        val roundRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 16, 0, 0)
        }
        val roundLabel = TextView(this).apply {
            text = getString(R.string.history_round_label)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(0, 0, 12, 0)
        }
        val rounds = listOf(
            getString(R.string.round_pool),
            getString(R.string.round_group),
            getString(R.string.round_32),
            getString(R.string.round_16),
            getString(R.string.round_8),
            getString(R.string.round_semi),
            getString(R.string.round_final)
        )
        
        val roundGrid = android.widget.GridLayout(this).apply {
            columnCount = 4
            setPadding(0, 8, 0, 8)
        }
        
        val radioButtons = mutableListOf<RadioButton>()
        rounds.forEach { round ->
            val rb = RadioButton(this).apply {
                text = round
                tag = round
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                isChecked = (state.matchRound == round)
                if (state.matchRound == "" && round == getString(R.string.round_pool)) isChecked = true
                setOnClickListener { view ->
                    radioButtons.forEach { it.isChecked = (it == view) }
                }
            }
            radioButtons.add(rb)
            roundGrid.addView(rb)
        }

        roundRow.addView(roundLabel)
        roundRow.addView(roundGrid)
        content.addView(roundRow)

        val scrollContent = ScrollView(this).apply {
            addView(content)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_setup_match)
            .setView(scrollContent)
            .setPositiveButton(R.string.dialog_done) { _, _ ->
                val selectedBestOf = when (bestOfGroup.checkedRadioButtonId) {
                    bestOf1.id -> 1
                    bestOf3.id -> 3
                    bestOf7.id -> 7
                    else -> 5
                }
                val selectedRb = radioButtons.find { it.isChecked }
                val selectedRound = selectedRb?.tag as? String ?: ""
                viewModel.setMatchRound(selectedRound)
                viewModel.setupMatch(
                    player1Name = state.player1Name,
                    player2Name = state.player2Name,
                    firstServer = state.server,
                    bestOfSets = selectedBestOf,
                )
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    /**
     * Input filter that ensures words are capitalized.
     * Useful when the keyboard ignores standard capitalization flags.
     */
    private class TitleCaseInputFilter : InputFilter {
        override fun filter(
            source: CharSequence, start: Int, end: Int,
            dest: Spanned, dstart: Int, dend: Int
        ): CharSequence? {
            if (source.isEmpty()) return null

            val result = StringBuilder()
            for (i in start until end) {
                val char = source[i]
                val isFirstChar = (dstart + i - start) == 0
                val isAfterSpace = !isFirstChar && (if (i > start) source[i - 1] == ' ' else dest[dstart + i - start - 1] == ' ')

                if (isFirstChar || isAfterSpace) {
                    result.append(char.uppercaseChar())
                } else {
                    result.append(char)
                }
            }
            return if (result.toString() == source.subSequence(start, end).toString()) null else result
        }
    }
}
