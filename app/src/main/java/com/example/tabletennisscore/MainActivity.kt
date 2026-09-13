package com.example.tabletennisscore

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.text.InputFilter
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.InputType
import android.text.style.ImageSpan
import android.os.Bundle
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import android.graphics.Typeface
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import com.example.tabletennisscore.databinding.ActivityMainBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
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
        hideSystemBarsImmersive()

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
        if (hasFocus) hideSystemBarsImmersive()
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
            val isDoubles = state.matchMode == GameViewModel.MATCH_MODE_DOUBLES
            val leftServing = (p1OnLeft && state.server == 1) || (!p1OnLeft && state.server == 2)
            fun formatDisplayName(name: String): String {
                return if (isDoubles) name.replace(" / ", "\n") else name
            }
            fun withServeBall(name: String, showBall: Boolean, placeAtEnd: Boolean = false): CharSequence {
                if (!showBall) return name
                val density = resources.displayMetrics.density
                val iconSize = (14 * density).toInt()
                val verticalOffsetPx = (4 * density).toInt()
                val trailingHorizontalOffsetPx = (12 * density).toInt()
                val gap = "   "
                val ball = ContextCompat.getDrawable(this@MainActivity, R.drawable.stigaperform40size128)
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
                            val transY = bottom - d.bounds.bottom - verticalOffsetPx
                            val transX = if (placeAtEnd) x + trailingHorizontalOffsetPx else x
                            canvas.translate(transX, transY.toFloat())
                            d.draw(canvas)
                            canvas.restore()
                        }
                    }, spanStart, spanStart + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            val leftName = formatDisplayName(if (p1OnLeft) state.player1Name else state.player2Name)
            val rightName = formatDisplayName(if (p1OnLeft) state.player2Name else state.player1Name)
            binding.tvPlayer1Name.text = withServeBall(leftName, isDoubles && leftServing)
            binding.tvPlayer2Name.text = withServeBall(rightName, isDoubles && !leftServing, placeAtEnd = true)
            val playerNameTextSizeSp = if (isDoubles) 16f else 22f
            binding.tvPlayer1Name.setTextSize(TypedValue.COMPLEX_UNIT_SP, playerNameTextSizeSp)
            binding.tvPlayer2Name.setTextSize(TypedValue.COMPLEX_UNIT_SP, playerNameTextSizeSp)
            binding.tvPlayer1Name.maxLines = if (isDoubles) 2 else 1
            binding.tvPlayer2Name.maxLines = if (isDoubles) 2 else 1
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
                binding.btnUndoText.visibility = View.VISIBLE

                binding.btnStartMatch.visibility = View.GONE
                binding.btnSetupMatch.visibility = View.GONE
                binding.btnHistory.visibility = View.GONE

                binding.centerControlsRow.visibility = View.VISIBLE
                binding.ivSwapSides.visibility = View.GONE
                startRallyBallAnimationIfNeeded()
                startMatchTimerTickerIfNeeded()
            } else {
                binding.btnPauseMatchText.visibility = View.GONE
                binding.btnUndoText.visibility = View.GONE

                // Show Start/Resume, Setup, and History when not running, unless awaiting swap
                val showControls = !isAwaitingSwapConfirm
                binding.btnStartMatch.visibility = if (showControls) View.VISIBLE else View.GONE
                binding.btnSetupMatch.visibility = if (showControls) View.VISIBLE else View.GONE
                binding.btnHistory.visibility = if (showControls) View.VISIBLE else View.GONE

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

    private fun styleDialogButtons(dialog: AlertDialog) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            ?.setTextColor(ContextCompat.getColor(this, R.color.score_text))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            ?.setTextColor(ContextCompat.getColor(this, R.color.player_name))
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            ?.setTextColor(ContextCompat.getColor(this, R.color.player_name))
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

    private fun updateMatchSummaryPanel(state: GameViewModel.GameState) {
        if (state.matchWinner == null) {
            binding.matchSummaryPanel.visibility = View.GONE
            binding.tvMatchTimer.visibility = View.VISIBLE
            return
        }
        val winnerName = if (state.matchWinner == 1) state.player1Name else state.player2Name
        val displayWinnerName = winnerName.replace(" / ", "\n")
        val winnerColor = ContextCompat.getColor(this, R.color.summary_winner_text)
        
        binding.matchSummaryPanel.visibility = View.VISIBLE
        binding.tvMatchTimer.visibility = View.GONE
        // Hide the controls while the summary is showing to avoid clutter
        binding.centerControlsRow.visibility = View.GONE
        
        binding.tvMatchSummaryWinner.text = getString(R.string.match_summary_winner, displayWinnerName)
        binding.tvMatchSummaryWinner.setTextColor(winnerColor)
        renderMatchSummaryScoreTable(state)
        binding.tvMatchSummaryTime.text = getString(
            R.string.match_summary_time,
            formatClockDuration(viewModel.getElapsedPlayedMs()),
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
                val displayName = name.replace(" / ", "\n"); addView(cell(displayName, 20f, Gravity.START or Gravity.CENTER_VERTICAL, nameColor, bold = false, minW = 90, marginStart = 12, marginEnd = 20))
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
            styleDialogButtons(dialog)
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

    private fun showEditNameDialog(player: Int) {
        val state = viewModel.state.value ?: return
        if (state.matchMode == GameViewModel.MATCH_MODE_DOUBLES) {
            showEditDoublesTeamNameDialog(player, state)
            return
        }
        val currentName = if (player == 1) state.player1Name else state.player2Name
        val defaultName = if (player == 1) getString(R.string.player1_default) else getString(R.string.player2_default)
        val isDefaultName = currentName == defaultName
        val nameGroup = viewModel.getPlayerNameGroup()

        val editText = AutoCompleteTextView(this).apply {
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
            threshold = 0
            if (nameGroup.isNotEmpty()) {
                setAdapter(ArrayAdapter(this@MainActivity, android.R.layout.simple_dropdown_item_1line, nameGroup))
                setOnClickListener { showDropDown() }
            }
        }

        val buttonDensity = resources.displayMetrics.density
        fun buttonPx(dp: Int): Int = (dp * buttonDensity).toInt()

        val selectFromGroupButton = MaterialButton(this).apply {
            text = getString(R.string.dialog_select_from_group)
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            minimumHeight = buttonPx(30)
            minHeight = buttonPx(30)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(buttonPx(14), buttonPx(2), buttonPx(14), buttonPx(2))
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.player_name))
            strokeWidth = buttonPx(1)
            strokeColor = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.history_loser_text))
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            cornerRadius = buttonPx(18)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = buttonPx(6)
            }
            isEnabled = nameGroup.isNotEmpty()
            alpha = if (nameGroup.isNotEmpty()) 1f else 0.45f
            setOnClickListener {
                showPlayerNamePickerDialog(nameGroup, editText.text.toString()) { selected ->
                    editText.setText(selected)
                    editText.setSelection(editText.text.length)
                }
            }
        }

        val dialogContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(editText)
            addView(selectFromGroupButton)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_edit_name))
            .setView(dialogContent)
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                val entered = editText.text.toString()
                viewModel.setPlayerName(player, entered)
                viewModel.addPlayerNameToGroup(entered)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()

        dialog.setOnShowListener {
            styleDialogButtons(dialog)
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

    private fun showEditDoublesTeamNameDialog(player: Int, state: GameViewModel.GameState) {
        val nameGroup = viewModel.getPlayerNameGroup()
        val buttonDensity = resources.displayMetrics.density
        fun buttonPx(dp: Int): Int = (dp * buttonDensity).toInt()

        val playerAValue: String
        val playerBValue: String
        val hintA: String
        val hintB: String
        val fallbackA: String
        val fallbackB: String
        if (player == 1) {
            playerAValue = state.team1PlayerA
            playerBValue = state.team1PlayerB
            hintA = getString(R.string.dialog_team1_player_a_hint)
            hintB = getString(R.string.dialog_team1_player_b_hint)
            fallbackA = "Player 1A"
            fallbackB = "Player 1B"
        } else {
            playerAValue = state.team2PlayerA
            playerBValue = state.team2PlayerB
            hintA = getString(R.string.dialog_team2_player_a_hint)
            hintB = getString(R.string.dialog_team2_player_b_hint)
            fallbackA = "Player 2A"
            fallbackB = "Player 2B"
        }

        fun createNameInput(initial: String, hintText: String): AutoCompleteTextView {
            return AutoCompleteTextView(this).apply {
                setText(initial)
                hint = hintText
                filters = arrayOf(
                    InputFilter.LengthFilter(GameViewModel.MAX_PLAYER_NAME_LENGTH),
                    TitleCaseInputFilter(),
                )
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
                imeOptions = EditorInfo.IME_ACTION_DONE
                maxLines = 1
                threshold = 0
                if (nameGroup.isNotEmpty()) {
                    setAdapter(ArrayAdapter(this@MainActivity, android.R.layout.simple_dropdown_item_1line, nameGroup))
                    setOnClickListener { showDropDown() }
                }
            }
        }

        fun createSelectButton(target: AutoCompleteTextView): MaterialButton {
            return MaterialButton(this).apply {
                text = getString(R.string.dialog_select_from_group)
                isAllCaps = false
                insetTop = 0
                insetBottom = 0
                minimumHeight = buttonPx(30)
                minHeight = buttonPx(30)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(buttonPx(14), buttonPx(2), buttonPx(14), buttonPx(2))
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.player_name))
                strokeWidth = buttonPx(1)
                strokeColor = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.history_loser_text))
                backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                cornerRadius = buttonPx(18)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    topMargin = buttonPx(4)
                    bottomMargin = buttonPx(8)
                }
                isEnabled = nameGroup.isNotEmpty()
                alpha = if (nameGroup.isNotEmpty()) 1f else 0.45f
                setOnClickListener {
                    showPlayerNamePickerDialog(nameGroup, target.text.toString()) { selected ->
                        target.setText(selected)
                        target.setSelection(target.text.length)
                    }
                }
            }
        }

        val playerAInput = createNameInput(playerAValue, hintA)
        val playerBInput = createNameInput(playerBValue, hintB)
        val playerAGroupButton = createSelectButton(playerAInput)
        val playerBGroupButton = createSelectButton(playerBInput)
        val swapTopBottomButton = MaterialButton(this).apply {
            text = getString(R.string.dialog_swap_top_bottom)
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            minimumHeight = buttonPx(30)
            minHeight = buttonPx(30)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(buttonPx(14), buttonPx(2), buttonPx(14), buttonPx(2))
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.player_name))
            strokeWidth = buttonPx(1)
            strokeColor = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.history_loser_text))
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            cornerRadius = buttonPx(18)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = buttonPx(4)
                bottomMargin = buttonPx(8)
            }
            setOnClickListener {
                val topName = playerAInput.text?.toString().orEmpty()
                val bottomName = playerBInput.text?.toString().orEmpty()
                playerAInput.setText(bottomName)
                playerAInput.setSelection(playerAInput.text.length)
                playerBInput.setText(topName)
                playerBInput.setSelection(playerBInput.text.length)
            }
        }

        val dialogContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(playerAInput)
            addView(playerAGroupButton)
            addView(playerBInput)
            addView(playerBGroupButton)
            addView(swapTopBottomButton)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_edit_name))
            .setView(dialogContent)
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                val enteredA = playerAInput.text.toString().ifBlank { fallbackA }
                val enteredB = playerBInput.text.toString().ifBlank { fallbackB }
                viewModel.setDoublesTeamNames(player, enteredA, enteredB)
                viewModel.addPlayerNameToGroup(enteredA)
                viewModel.addPlayerNameToGroup(enteredB)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()

        dialog.setOnShowListener {
            styleDialogButtons(dialog)
            val okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            playerBInput.setOnEditorActionListener { _, actionId, event ->
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

    private fun showPlayerNamePickerDialog(
        names: List<String>,
        current: String,
        onPicked: (String) -> Unit,
    ) {
        if (names.isEmpty()) return
        val checkedIndex = names.indexOfFirst { it.equals(current.trim(), ignoreCase = true) }
        var selectedIndex = checkedIndex
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dialog_select_from_group)
            .setSingleChoiceItems(names.toTypedArray(), checkedIndex) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                if (selectedIndex in names.indices) {
                    onPicked(names[selectedIndex])
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()
        dialog.setOnShowListener { styleDialogButtons(dialog) }
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

        val dialog = AlertDialog.Builder(this)
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
            .create()
        dialog.setOnShowListener { styleDialogButtons(dialog) }
        dialog.show()
    }

    private fun confirmSetupMatch() {
        val state = viewModel.state.value ?: return
        if (!state.hasMatchStarted) {
            showSetupMatchDialog()
            return
        }

        val dialog = AlertDialog.Builder(this)
            .setMessage(R.string.confirm_setup_match_message)
            .setPositiveButton(R.string.dialog_yes) { _, _ -> showSetupMatchDialog() }
            .setNegativeButton(R.string.dialog_no, null)
            .create()
        dialog.setOnShowListener { styleDialogButtons(dialog) }
        dialog.show()
    }

    private fun showSetupMatchDialog() {
        val state = viewModel.state.value ?: return
        val editableNameGroup = viewModel.getPlayerNameGroup().toMutableList()
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
        val density = resources.displayMetrics.density
        fun px(dp: Int): Int = (dp * density).toInt()
        val setupNameInputs = mutableListOf<AutoCompleteTextView>()
        val setupGroupButtons = mutableListOf<MaterialButton>()
        fun bindNameGroup(input: AutoCompleteTextView) {
            if (editableNameGroup.isNotEmpty()) {
                input.setAdapter(ArrayAdapter(this@MainActivity, android.R.layout.simple_dropdown_item_1line, editableNameGroup))
                input.threshold = 0
                input.setOnClickListener { input.showDropDown() }
                input.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) input.showDropDown()
                }
            } else {
                input.setAdapter(null)
                input.setOnClickListener(null)
                input.setOnFocusChangeListener(null)
            }
        }
        fun refreshSetupGroupButtons() {
            val hasNames = editableNameGroup.isNotEmpty()
            setupGroupButtons.forEach { button ->
                button.isEnabled = hasNames
                button.alpha = if (hasNames) 1f else 0.45f
            }
        }
        fun createSelectFromGroupButton(targetInput: AutoCompleteTextView): MaterialButton {
            return MaterialButton(this).apply {
                text = getString(R.string.dialog_select_from_group)
                isAllCaps = false
                insetTop = 0
                insetBottom = 0
                minimumHeight = px(30)
                minHeight = px(30)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(px(14), px(2), px(14), px(2))
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.player_name))
                strokeWidth = px(1)
                strokeColor = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.history_loser_text))
                backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                cornerRadius = px(18)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    topMargin = px(4)
                    bottomMargin = px(8)
                }
                setOnClickListener {
                    showPlayerNamePickerDialog(editableNameGroup, targetInput.text.toString()) { selected ->
                        targetInput.setText(selected)
                        targetInput.setSelection(targetInput.text.length)
                    }
                }
                setupGroupButtons.add(this)
            }
        }
        fun createNameInput(hintRes: Int, initial: String): AutoCompleteTextView {
            return AutoCompleteTextView(this).apply {
                hint = getString(hintRes)
                setText(initial)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
                filters = arrayOf(InputFilter.LengthFilter(GameViewModel.MAX_PLAYER_NAME_LENGTH), TitleCaseInputFilter())
                maxLines = 1
                bindNameGroup(this)
                setupNameInputs.add(this)
            }
        }
        fun choiceChip(label: String, selected: Boolean, allowTwoLines: Boolean = false): Chip {
            return Chip(this).apply {
                id = View.generateViewId()
                text = label
                isCheckable = true
                isChecked = selected
                isClickable = true
                isAllCaps = false
                isCheckedIconVisible = false
                if (allowTwoLines) {
                    // Material Chip does not support multiline text; keep a wider single-line pill.
                    minWidth = px(84)
                    chipMinHeight = px(36).toFloat()
                } else {
                    minWidth = px(52)
                    chipMinHeight = px(34).toFloat()
                }
            }
        }

        val editNameGroupButton = MaterialButton(this).apply {
            text = getString(R.string.dialog_edit_player_names)
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            minimumHeight = px(34)
            minHeight = px(34)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(px(18), px(4), px(18), px(4))
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.player_name))
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            strokeWidth = px(1)
            strokeColor = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.history_loser_text))
            cornerRadius = px(20)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = px(6)
            }
            setOnClickListener {
                showNameGroupEditorDialog(editableNameGroup) { updatedNames ->
                    editableNameGroup.clear()
                    editableNameGroup.addAll(updatedNames)
                    viewModel.setPlayerNameGroup(updatedNames)
                    setupNameInputs.forEach { bindNameGroup(it) }
                    refreshSetupGroupButtons()
                }
            }
        }

        content.addView(editNameGroupButton)

        val modeSection = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(12), px(12), px(12), px(12))
        }
        val modeLabel = androidx.appcompat.widget.AppCompatTextView(this).apply {
            text = getString(R.string.dialog_match_mode)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(0, 0, 12, 0)
        }
        val modeGroup = ChipGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
            chipSpacingHorizontal = px(8)
            chipSpacingVertical = px(8)
        }
        var selectedMode = when (state.matchMode) {
            GameViewModel.MATCH_MODE_DOUBLES -> GameViewModel.MATCH_MODE_DOUBLES
            else -> GameViewModel.MATCH_MODE_SINGLES
        }
        var onModeChanged: (() -> Unit)? = null
        fun refreshModeOutline() {
            for (i in 0 until modeGroup.childCount) {
                val chip = modeGroup.getChildAt(i) as? Chip ?: continue
                val isSelected = (chip.tag as? String) == selectedMode
                chip.chipBackgroundColor = ColorStateList.valueOf(Color.TRANSPARENT)
                chip.chipStrokeWidth = if (isSelected) px(2).toFloat() else px(1).toFloat()
                chip.chipStrokeColor = ColorStateList.valueOf(
                    ContextCompat.getColor(this@MainActivity, if (isSelected) R.color.score_text else R.color.history_loser_text),
                )
                chip.setTextColor(
                    ContextCompat.getColor(this@MainActivity, if (isSelected) R.color.score_text else R.color.player_name),
                )
            }
        }
        listOf(
            GameViewModel.MATCH_MODE_SINGLES to getString(R.string.dialog_mode_singles),
            GameViewModel.MATCH_MODE_DOUBLES to getString(R.string.dialog_mode_doubles),
        ).forEach { (modeValue, modeText) ->
            modeGroup.addView(
                choiceChip(modeText, modeValue == selectedMode).apply {
                    tag = modeValue
                }
            )
        }
        refreshModeOutline()
        modeGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            val selectedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            selectedMode = (group.findViewById<Chip>(selectedId).tag as? String) ?: selectedMode
            refreshModeOutline()
            onModeChanged?.invoke()
        }

        modeSection.addView(modeLabel)
        modeSection.addView(modeGroup)
        val modeCard = MaterialCardView(this).apply {
            setCardBackgroundColor(Color.TRANSPARENT)
            strokeWidth = px(1)
            strokeColor = ContextCompat.getColor(this@MainActivity, R.color.player_name)
            radius = px(8).toFloat()
            useCompatPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = px(14) }
            addView(modeSection)
        }
        content.addView(modeCard)

        val singlesSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(12), px(12), px(12), px(12))
        }
        val singlesLabel = TextView(this).apply {
            text = getString(R.string.dialog_name_group)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, px(8))
        }
        val singlesPlayer1Input = createNameInput(R.string.dialog_player1_name, state.player1Name)
        val singlesPlayer1GroupButton = createSelectFromGroupButton(singlesPlayer1Input)
        val singlesPlayer2Input = createNameInput(R.string.dialog_player2_name, state.player2Name)
        val singlesPlayer2GroupButton = createSelectFromGroupButton(singlesPlayer2Input)
        singlesSection.addView(singlesLabel)
        singlesSection.addView(singlesPlayer1Input)
        singlesSection.addView(singlesPlayer1GroupButton)
        singlesSection.addView(singlesPlayer2Input)
        singlesSection.addView(singlesPlayer2GroupButton)

        val singlesCard = MaterialCardView(this).apply {
            setCardBackgroundColor(Color.TRANSPARENT)
            strokeWidth = px(1)
            strokeColor = ContextCompat.getColor(this@MainActivity, R.color.player_name)
            radius = px(8).toFloat()
            useCompatPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = px(10) }
            addView(singlesSection)
            visibility = if (selectedMode == GameViewModel.MATCH_MODE_SINGLES) View.VISIBLE else View.GONE
        }
        content.addView(singlesCard)

        val doublesSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(12), px(12), px(12), px(12))
        }
        val doublesLabel = TextView(this).apply {
            text = getString(R.string.dialog_doubles_team_names)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, px(8))
        }
        val team1AInput = createNameInput(R.string.dialog_team1_player_a_hint, state.team1PlayerA)
        val team1AGroupButton = createSelectFromGroupButton(team1AInput)
        val team1BInput = createNameInput(R.string.dialog_team1_player_b_hint, state.team1PlayerB)
        val team1BGroupButton = createSelectFromGroupButton(team1BInput)
        val team2AInput = createNameInput(R.string.dialog_team2_player_a_hint, state.team2PlayerA)
        val team2AGroupButton = createSelectFromGroupButton(team2AInput)
        val team2BInput = createNameInput(R.string.dialog_team2_player_b_hint, state.team2PlayerB)
        val team2BGroupButton = createSelectFromGroupButton(team2BInput)
        val swapPartnersButton = MaterialButton(this).apply {
            text = getString(R.string.dialog_swap_partners)
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            minimumHeight = px(34)
            minHeight = px(34)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(px(16), px(4), px(16), px(4))
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.player_name))
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            strokeWidth = px(1)
            strokeColor = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.history_loser_text))
            cornerRadius = px(20)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = px(8)
            }
            setOnClickListener {
                val team1Partner = team1BInput.text?.toString().orEmpty()
                val team2Partner = team2BInput.text?.toString().orEmpty()
                team1BInput.setText(team2Partner)
                team1BInput.setSelection(team1BInput.text.length)
                team2BInput.setText(team1Partner)
                team2BInput.setSelection(team2BInput.text.length)
            }
        }
        val swapTeamsButton = MaterialButton(this).apply {
            text = getString(R.string.dialog_swap_teams)
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            minimumHeight = px(34)
            minHeight = px(34)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(px(16), px(4), px(16), px(4))
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.player_name))
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            strokeWidth = px(1)
            strokeColor = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.history_loser_text))
            cornerRadius = px(20)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = px(8)
            }
            setOnClickListener {
                val t1A = team1AInput.text?.toString().orEmpty()
                val t1B = team1BInput.text?.toString().orEmpty()
                val t2A = team2AInput.text?.toString().orEmpty()
                val t2B = team2BInput.text?.toString().orEmpty()

                team1AInput.setText(t2A)
                team1BInput.setText(t2B)
                team2AInput.setText(t1A)
                team2BInput.setText(t1B)

                team2BInput.setSelection(team2BInput.text.length)
            }
        }
        doublesSection.addView(doublesLabel)
        doublesSection.addView(team1AInput)
        doublesSection.addView(team1AGroupButton)
        doublesSection.addView(team1BInput)
        doublesSection.addView(team1BGroupButton)
        doublesSection.addView(team2AInput)
        doublesSection.addView(team2AGroupButton)
        doublesSection.addView(team2BInput)
        doublesSection.addView(team2BGroupButton)
        doublesSection.addView(swapPartnersButton)
        doublesSection.addView(swapTeamsButton)

        val doublesCard = MaterialCardView(this).apply {
            setCardBackgroundColor(Color.TRANSPARENT)
            strokeWidth = px(1)
            strokeColor = ContextCompat.getColor(this@MainActivity, R.color.player_name)
            radius = px(8).toFloat()
            useCompatPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = px(10) }
            addView(doublesSection)
            visibility = if (selectedMode == GameViewModel.MATCH_MODE_DOUBLES) View.VISIBLE else View.GONE
        }
        content.addView(doublesCard)

        onModeChanged = {
            singlesCard.visibility = if (selectedMode == GameViewModel.MATCH_MODE_SINGLES) View.VISIBLE else View.GONE
            doublesCard.visibility = if (selectedMode == GameViewModel.MATCH_MODE_DOUBLES) View.VISIBLE else View.GONE
        }
        refreshSetupGroupButtons()

        val bestOfSection = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(12), px(12), px(12), px(12))
        }
        val bestOfLabel = androidx.appcompat.widget.AppCompatTextView(this).apply {
            text = getString(R.string.dialog_best_of_sets)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(0, 0, 12, 0)
        }
        val bestOfGroup = ChipGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
            chipSpacingHorizontal = px(8)
            chipSpacingVertical = px(8)
        }
        var selectedBestOf = if (state.bestOfSets in setOf(1, 3, 5, 7)) state.bestOfSets else 5
        fun refreshBestOfOutline() {
            for (i in 0 until bestOfGroup.childCount) {
                val chip = bestOfGroup.getChildAt(i) as? Chip ?: continue
                val isSelected = (chip.tag as? Int) == selectedBestOf
                chip.chipBackgroundColor = ColorStateList.valueOf(Color.TRANSPARENT)
                chip.chipStrokeWidth = if (isSelected) px(2).toFloat() else px(1).toFloat()
                chip.chipStrokeColor = ColorStateList.valueOf(
                    ContextCompat.getColor(this@MainActivity, if (isSelected) R.color.score_text else R.color.history_loser_text),
                )
                chip.setTextColor(
                    ContextCompat.getColor(this@MainActivity, if (isSelected) R.color.score_text else R.color.player_name),
                )
            }
        }
        listOf(1, 3, 5, 7).forEach { bestOf ->
            bestOfGroup.addView(
                choiceChip(bestOf.toString(), bestOf == selectedBestOf).apply {
                    tag = bestOf
                }
            )
        }
        refreshBestOfOutline()
        bestOfGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            val selectedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            selectedBestOf = (group.findViewById<Chip>(selectedId).tag as? Int) ?: selectedBestOf
            refreshBestOfOutline()
        }

        bestOfSection.addView(bestOfLabel)
        bestOfSection.addView(bestOfGroup)
        val bestOfCard = MaterialCardView(this).apply {
            setCardBackgroundColor(Color.TRANSPARENT)
            strokeWidth = px(1)
            strokeColor = ContextCompat.getColor(this@MainActivity, R.color.player_name)
            radius = px(8).toFloat()
            useCompatPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = px(14) }
            addView(bestOfSection)
        }
        content.addView(bestOfCard)

        // Round Selection
        val roundSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(12), px(12), px(12), px(12))
        }
        val roundLabel = TextView(this).apply {
            text = getString(R.string.history_round_label)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, px(8))
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
        
        val roundGroup = ChipGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
            chipSpacingHorizontal = px(8)
            chipSpacingVertical = px(8)
            setPadding(0, 6, 0, 8)
        }

        var selectedRound = if (state.matchRound.isNotBlank()) state.matchRound else getString(R.string.round_pool)
        fun refreshRoundOutline() {
            for (i in 0 until roundGroup.childCount) {
                val chip = roundGroup.getChildAt(i) as? Chip ?: continue
                val isSelected = (chip.tag as? String) == selectedRound
                chip.chipBackgroundColor = ColorStateList.valueOf(Color.TRANSPARENT)
                chip.chipStrokeWidth = if (isSelected) px(2).toFloat() else px(1).toFloat()
                chip.chipStrokeColor = ColorStateList.valueOf(
                    ContextCompat.getColor(this@MainActivity, if (isSelected) R.color.score_text else R.color.history_loser_text),
                )
                chip.setTextColor(
                    ContextCompat.getColor(this@MainActivity, if (isSelected) R.color.score_text else R.color.player_name),
                )
            }
        }
        rounds.forEach { round ->
            roundGroup.addView(
                choiceChip(round, round == selectedRound, allowTwoLines = true).apply {
                    tag = round
                }
            )
        }
        refreshRoundOutline()
        roundGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            val selectedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            selectedRound = (group.findViewById<Chip>(selectedId).tag as? String) ?: selectedRound
            refreshRoundOutline()
        }

        roundSection.addView(roundLabel)
        roundSection.addView(roundGroup)
        val roundCard = MaterialCardView(this).apply {
            setCardBackgroundColor(Color.TRANSPARENT)
            strokeWidth = px(1)
            strokeColor = ContextCompat.getColor(this@MainActivity, R.color.player_name)
            radius = px(8).toFloat()
            useCompatPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = px(10) }
            addView(roundSection)
        }
        content.addView(roundCard)

        val scrollContent = ScrollView(this).apply {
            addView(content)
        }

        val setupDialog = AlertDialog.Builder(this)
            .setTitle(R.string.dialog_setup_match)
            .setView(scrollContent)
            .setPositiveButton(R.string.dialog_done) { _, _ ->
                viewModel.setMatchRound(selectedRound)
                viewModel.setupMatch(
                    player1Name = singlesPlayer1Input.text.toString(),
                    player2Name = singlesPlayer2Input.text.toString(),
                    firstServer = state.server,
                    bestOfSets = selectedBestOf,
                    matchMode = selectedMode,
                    team1PlayerA = team1AInput.text.toString(),
                    team1PlayerB = team1BInput.text.toString(),
                    team2PlayerA = team2AInput.text.toString(),
                    team2PlayerB = team2BInput.text.toString(),
                )
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()
        setupDialog.setOnShowListener {
            styleDialogButtons(setupDialog)
        }
        setupDialog.show()
    }

    private fun showNameGroupEditorDialog(existingNames: List<String>, onSave: (List<String>) -> Unit) {
        val density = resources.displayMetrics.density
        fun px(dp: Int): Int = (dp * density).toInt()

        // Working copy of the names, kept sorted & de-duplicated as it changes.
        val workingNames = existingNames
            .map { sanitizeGroupName(it) }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .sortedBy { it.lowercase(Locale.ROOT) }
            .toMutableList()

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20), px(8), px(20), px(4))
        }

        // Container that holds one row per name.
        val namesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val emptyView = TextView(this).apply {
            text = getString(R.string.name_group_empty)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.player_name))
            alpha = 0.7f
            setPadding(0, px(8), 0, px(8))
        }

        fun rebuildRows() {
            namesContainer.removeAllViews()
            if (workingNames.isEmpty()) {
                namesContainer.addView(emptyView)
                return
            }
            workingNames.toList().forEach { name ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, px(1), 0, px(1))
                }
                val nameLabel = TextView(this).apply {
                    text = name
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.player_name))
                    layoutParams = LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    )
                }
                val removeButton = TextView(this).apply {
                    text = "✕"
                    gravity = Gravity.CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_dark))
                    val side = px(28)
                    layoutParams = LinearLayout.LayoutParams(side, side).apply {
                        marginStart = px(8)
                    }
                    contentDescription = getString(R.string.cd_remove_name, name)
                    isClickable = true
                    isFocusable = true
                    val outValue = TypedValue()
                    context.theme.resolveAttribute(
                        android.R.attr.selectableItemBackgroundBorderless, outValue, true
                    )
                    setBackgroundResource(outValue.resourceId)
                    setOnClickListener {
                        val confirmDialog = AlertDialog.Builder(this@MainActivity)
                            .setTitle(R.string.confirm_remove_name_title)
                            .setMessage(getString(R.string.confirm_remove_name_message, name))
                            .setPositiveButton(R.string.dialog_yes) { _, _ ->
                                workingNames.removeAll { it.equals(name, ignoreCase = true) }
                                rebuildRows()
                            }
                            .setNegativeButton(R.string.dialog_no, null)
                            .create()
                        confirmDialog.setOnShowListener { styleDialogButtons(confirmDialog) }
                        confirmDialog.show()
                    }
                }
                row.addView(nameLabel)
                row.addView(removeButton)
                namesContainer.addView(row)
            }
        }

        val namesScroll = ScrollView(this).apply {
            // Use a weighted height so the list fills the space the dialog actually has
            // (instead of a fixed height that can be clipped when the keyboard is open),
            // ensuring it always scrolls to reveal every name.
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            minimumHeight = px(140)
            isVerticalScrollBarEnabled = true
            isScrollbarFadingEnabled = false
            addView(namesContainer)
        }

        // Add-name row: a single full-width input. The keyboard's Done key adds the name.
        val addRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, px(8))
        }
        val addInput = EditText(this).apply {
            hint = getString(R.string.dialog_add_name_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            filters = arrayOf(
                InputFilter.LengthFilter(GameViewModel.MAX_PLAYER_NAME_LENGTH),
                TitleCaseInputFilter()
            )
            // Keep editing inline (no full-screen keyboard editor in landscape) so the
            // Done key adds the name reliably.
            imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setSingleLine(true)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        fun addTypedName() {
            val candidate = sanitizeGroupName(addInput.text.toString())
            if (candidate.isBlank()) {
                addInput.setText("")
                return
            }
            val exists = workingNames.any { it.equals(candidate, ignoreCase = true) }
            if (exists) {
                Toast.makeText(
                    this,
                    getString(R.string.name_group_duplicate, candidate),
                    Toast.LENGTH_SHORT
                ).show()
                addInput.setText("")
                return
            }
            workingNames.add(candidate)
            workingNames.sortBy { it.lowercase(Locale.ROOT) }
            rebuildRows()
            addInput.setText("")
            // Hide the keyboard and drop focus after adding a name.
            (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.hideSoftInputFromWindow(addInput.windowToken, 0)
            addInput.clearFocus()
            // Scroll so the newly added name is visible (it may sort anywhere in the list).
            val newIndex = workingNames.indexOfFirst { it.equals(candidate, ignoreCase = true) }
            if (newIndex >= 0) {
                namesScroll.post {
                    namesContainer.getChildAt(newIndex)?.let { child ->
                        namesScroll.smoothScrollTo(0, child.top)
                    }
                }
            }
        }

        addInput.setOnEditorActionListener { _, actionId, event ->
            val isDoneAction = actionId == EditorInfo.IME_ACTION_DONE ||
                actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_NEXT ||
                actionId == EditorInfo.IME_NULL
            val isEnterKey = event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                event.action == KeyEvent.ACTION_DOWN
            if (isDoneAction || isEnterKey) {
                addTypedName()
                true
            } else {
                false
            }
        }

        addRow.addView(addInput)

        // Add row stays pinned at the top (under the title) so it is always visible,
        // while the list of names scrolls below it.
        rootLayout.addView(addRow)
        rootLayout.addView(namesScroll)

        rebuildRows()

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dialog_edit_name_group)
            .setView(rootLayout)
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                // Fold in any text left in the add field without pressing Add.
                addTypedName()
                val cleaned = workingNames
                    .map { sanitizeGroupName(it) }
                    .filter { it.isNotBlank() }
                    .distinctBy { it.lowercase(Locale.ROOT) }
                    .sortedBy { it.lowercase(Locale.ROOT) }
                    .toList()
                onSave(cleaned)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()
        dialog.setOnShowListener { styleDialogButtons(dialog) }
        dialog.show()
    }

    private fun sanitizeGroupName(name: String): String {
        return normalizeTitleCaseWords(name).take(GameViewModel.MAX_PLAYER_NAME_LENGTH)
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


