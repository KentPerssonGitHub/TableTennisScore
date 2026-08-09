package com.example.tabletennisscore

import android.animation.ValueAnimator
import android.os.Handler
import android.text.InputFilter
import android.text.InputType
import android.os.Bundle
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.MotionEvent
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
import com.example.tabletennisscore.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: GameViewModel by viewModels()
    private var rallyBallAnimator: ValueAnimator? = null
    private var rallyBallSpinDirection = 1f
    private var serveDragStartRawX = 0f
    private var serveDragStartRawY = 0f
    private var lastShownDecidingSwapNoticeVersion = 0
    private var decidingSwapSnackbar: Snackbar? = null
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

        setupClickListeners()
        observeState()
    }

    private fun setupClickListeners() {
        // Score taps – tap the score area to add a point
        binding.tvScore1.setOnClickListener {
            viewModel.addPoint(if (viewModel.state.value?.sidesSwapped == true) 2 else 1)
        }
        binding.tvScore2.setOnClickListener {
            viewModel.addPoint(if (viewModel.state.value?.sidesSwapped == true) 1 else 2)
        }

        // Name taps – tap name to edit
        binding.tvPlayer1Name.setOnClickListener {
            showEditNameDialog(if (viewModel.state.value?.sidesSwapped == true) 2 else 1)
        }
        binding.tvPlayer2Name.setOnClickListener {
            showEditNameDialog(if (viewModel.state.value?.sidesSwapped == true) 1 else 2)
        }

        binding.btnSetupMatch.setOnClickListener { confirmSetupMatch() }
        binding.btnEditScore.setOnClickListener { showEditScoreDialog() }
        binding.btnStartMatch.setOnClickListener { viewModel.startOrResumeMatch() }
        binding.btnPauseMatch.setOnClickListener { viewModel.pauseMatch() }
        binding.btnUndo.setOnClickListener { viewModel.undo() }
        binding.btnSwapSides.setOnClickListener { viewModel.swapSides() }
        setupServeBallDrag()
    }

    private fun setupServeBallDrag() {
        val dragListener = View.OnTouchListener { view, event ->
            val state = viewModel.state.value ?: return@OnTouchListener false
            if (!state.hasMatchStarted || state.matchWinner != null) return@OnTouchListener false
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

            val backgroundColor = when {
                state.isMatchRunning -> R.color.background_running
                state.hasMatchStarted -> R.color.background_paused
                else -> R.color.background
            }
            binding.rootLayout.setBackgroundColor(ContextCompat.getColor(this, backgroundColor))

            binding.btnStartMatch.text = getString(
                if (state.hasMatchStarted) R.string.btn_resume_match else R.string.btn_start_match,
            )

            val isMatchFinished = state.matchWinner != null
            val isAwaitingSwapConfirm = state.awaitingDecidingSetSwapConfirmation

            if (state.isMatchRunning) {
                binding.btnPauseMatch.visibility = View.VISIBLE
                binding.btnStartMatch.visibility = View.GONE
                binding.btnSetupMatch.visibility = View.GONE
                binding.btnEditScore.visibility = View.GONE
                binding.btnSwapSides.visibility = View.GONE
                binding.btnUndo.visibility = View.VISIBLE
                startRallyBallAnimationIfNeeded()
                startMatchTimerTickerIfNeeded()
            } else {
                binding.btnPauseMatch.visibility = View.GONE
                binding.btnStartMatch.visibility = if (isMatchFinished || isAwaitingSwapConfirm) View.GONE else View.VISIBLE
                binding.btnSetupMatch.visibility = View.VISIBLE
                binding.btnEditScore.visibility = if (state.hasMatchStarted && !isMatchFinished && !isAwaitingSwapConfirm) View.VISIBLE else View.GONE
                binding.btnSwapSides.visibility = if (state.hasMatchStarted && !isMatchFinished && !isAwaitingSwapConfirm) View.VISIBLE else View.GONE
                binding.btnUndo.visibility = if (state.hasMatchStarted && !isAwaitingSwapConfirm) View.VISIBLE else View.GONE
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
        binding.matchSummaryPanel.visibility = View.VISIBLE
        binding.tvMatchTimer.visibility = View.GONE
        binding.tvMatchSummaryWinner.text = getString(R.string.match_summary_winner, winnerName)
        renderMatchSummaryScoreTable(state)
        binding.tvMatchSummaryTime.text = getString(
            R.string.match_summary_time,
            formatElapsedTime(viewModel.getElapsedPlayedMs()),
        )

        // Position panel on the winning player's displayed side of the screen
        val winnerOnLeft = (state.matchWinner == 1) != state.sidesSwapped
        val margin = (12 * resources.displayMetrics.density).toInt()
        ConstraintSet().apply {
            clone(binding.rootLayout)
            clear(R.id.matchSummaryPanel, ConstraintSet.START)
            clear(R.id.matchSummaryPanel, ConstraintSet.END)
            if (winnerOnLeft) {
                // Left-aligned, fills up to 75% of left half
                constrainWidth(R.id.matchSummaryPanel, 0)
                connect(R.id.matchSummaryPanel, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, margin)
                connect(R.id.matchSummaryPanel, ConstraintSet.END, R.id.guidelineP1SummaryEnd, ConstraintSet.START, 0)
            } else {
                // Starts at center divider, exactly 75% of the right half wide
                constrainWidth(R.id.matchSummaryPanel, 0)
                constrainDefaultWidth(R.id.matchSummaryPanel, ConstraintSet.MATCH_CONSTRAINT_SPREAD)
                connect(R.id.matchSummaryPanel, ConstraintSet.START, R.id.divider, ConstraintSet.END, margin)
                connect(R.id.matchSummaryPanel, ConstraintSet.END, R.id.guidelineP2SummaryEnd, ConstraintSet.START, 0)
            }
            connect(R.id.matchSummaryPanel, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, margin)
            applyTo(binding.rootLayout)
        }
    }

    private fun renderMatchSummaryScoreTable(state: GameViewModel.GameState) {
        val table = binding.tableMatchSummaryScores
        table.removeAllViews()
        val density = resources.displayMetrics.density
        fun Int.dp() = (this * density).toInt()

        fun cell(text: String, textSizeSp: Float, grav: Int, bold: Boolean = false, minW: Int = 0, marginEnd: Int = 0) =
            TextView(this).apply {
                this.text = text
                textSize = textSizeSp
                gravity = grav
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.score_text))
                if (bold) setTypeface(typeface, Typeface.BOLD)
                if (minW > 0) minWidth = minW.dp()
                layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT)
                    .also { if (marginEnd > 0) it.marginEnd = marginEnd.dp() }
            }

        listOf(
            Triple(state.player1Name, state.sets1, state.setResults.map { it.first }),
            Triple(state.player2Name, state.sets2, state.setResults.map { it.second }),
        ).forEach { (name, sets, scores) ->
            table.addView(TableRow(this).apply {
                addView(cell(name, 12f, Gravity.START or Gravity.CENTER_VERTICAL, bold = true, minW = 68, marginEnd = 8))
                addView(cell(sets.toString(), 16f, Gravity.CENTER, bold = true, minW = 22, marginEnd = 10))
                scores.forEach { addView(cell(it.toString(), 12f, Gravity.CENTER, minW = 18, marginEnd = 4)) }
            })
        }
    }

    private fun startRallyBallAnimationIfNeeded() {
        if (rallyBallAnimator?.isRunning == true) return
        binding.rootLayout.post {
            val latestState = viewModel.state.value ?: return@post
            if (!latestState.isMatchRunning) return@post

            val ball = binding.ivRallyBall
            val leftScore = binding.tvScore1
            val rightScore = binding.tvScore2
            val net = binding.divider

            val horizontalTravel = rightScore.x - leftScore.x
            if (horizontalTravel <= 0f) return@post

            val leftX = leftScore.x + (leftScore.width * 0.68f)
            val rightX = rightScore.x + (rightScore.width * 0.18f)
            val baseY = (leftScore.y + (leftScore.height * 0.58f)) - (ball.height / 2f)
            val netTopY = net.y + (net.height * 0.20f)
            val desiredArc = abs(rightX - leftX) * 0.20f
            val minArcToClearNet = (baseY - netTopY) + ball.height
            val arcHeight = maxOf(70f, minOf(220f, maxOf(desiredArc, minArcToClearNet)))

            // Keep spin continuous and flip direction on each side bounce.
            var lastT = 0f
            var spinTurns = 0f
            rallyBallSpinDirection = 1f
            ball.rotation = 0f

            ball.visibility = View.VISIBLE
            rallyBallAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1500L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = LinearInterpolator()
                addUpdateListener { animator ->
                    val t = animator.animatedValue as Float
                    if ((lastT < 0.02f && t >= 0.02f) || (lastT > 0.98f && t <= 0.98f)) {
                        rallyBallSpinDirection *= -1f
                    }
                    lastT = t

                    ball.x = leftX + (rightX - leftX) * t
                    val netArc = sin(PI.toFloat() * t)
                    ball.y = baseY - (arcHeight * netArc)

                    spinTurns += 12f * rallyBallSpinDirection
                    ball.rotation = spinTurns
                }
                start()
            }
        }
    }

    private fun stopRallyBallAnimation() {
        rallyBallAnimator?.cancel()
        rallyBallAnimator = null
        binding.ivRallyBall.rotation = 0f
        binding.ivRallyBall.visibility = View.GONE
    }


    private fun showEditNameDialog(player: Int) {
        val state = viewModel.state.value ?: return
        val currentName = if (player == 1) state.player1Name else state.player2Name

        val editText = EditText(this).apply {
            setText(currentName)
            selectAll()
            hint = getString(R.string.dialog_hint_name)
            filters = arrayOf(InputFilter.LengthFilter(GameViewModel.MAX_PLAYER_NAME_LENGTH))
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_edit_name))
            .setView(editText)
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                viewModel.setPlayerName(player, editText.text.toString())
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
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
                setTypeface(null, Typeface.BOLD)
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
                    setTypeface(null, Typeface.BOLD)
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

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_reset_title)
            .setMessage(R.string.confirm_reset_message)
            .setPositiveButton(R.string.dialog_ok) { _, _ -> viewModel.resetMatch() }
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

        val player1Input = EditText(this).apply {
            hint = getString(R.string.player1_default)
            setText(state.player1Name)
            setSelection(text.length)
            filters = arrayOf(InputFilter.LengthFilter(GameViewModel.MAX_PLAYER_NAME_LENGTH))
        }
        val player2Input = EditText(this).apply {
            hint = getString(R.string.player2_default)
            setText(state.player2Name)
            setSelection(text.length)
            filters = arrayOf(InputFilter.LengthFilter(GameViewModel.MAX_PLAYER_NAME_LENGTH))
        }

        val player1Row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val player2Row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val serveHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val nameLayoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        val selectorLayoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        val serveLabelLayoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )

        player1Input.layoutParams = nameLayoutParams
        player2Input.layoutParams = nameLayoutParams

        val serveLabelSpacer = androidx.appcompat.widget.AppCompatTextView(this).apply {
            layoutParams = nameLayoutParams
        }
        val serveLabel = androidx.appcompat.widget.AppCompatTextView(this).apply {
            text = getString(R.string.dialog_serve_label)
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.player_name))
            layoutParams = serveLabelLayoutParams
            setPadding(0, 0, 0, 4)
        }

        val player1ServeOption = RadioButton(this).apply {
            id = View.generateViewId()
            contentDescription = getString(R.string.dialog_server_player1)
            layoutParams = selectorLayoutParams
        }
        val player2ServeOption = RadioButton(this).apply {
            id = View.generateViewId()
            contentDescription = getString(R.string.dialog_server_player2)
            layoutParams = selectorLayoutParams
        }

        player1ServeOption.setOnClickListener {
            player1ServeOption.isChecked = true
            player2ServeOption.isChecked = false
        }
        player2ServeOption.setOnClickListener {
            player2ServeOption.isChecked = true
            player1ServeOption.isChecked = false
        }

        if (state.server == 2) {
            player2ServeOption.isChecked = true
            player1ServeOption.isChecked = false
        } else {
            player1ServeOption.isChecked = true
            player2ServeOption.isChecked = false
        }

        player1Row.addView(player1Input)
        player1Row.addView(player1ServeOption)
        player2Row.addView(player2Input)
        player2Row.addView(player2ServeOption)

        serveHeaderRow.addView(serveLabelSpacer)
        serveHeaderRow.addView(serveLabel)

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

        content.addView(serveHeaderRow)
        content.addView(player1Row)
        content.addView(player2Row)
        content.addView(bestOfRow)

        val scrollContent = ScrollView(this).apply {
            addView(content)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_setup_match)
            .setView(scrollContent)
            .setPositiveButton(R.string.dialog_done) { _, _ ->
                val firstServer = if (player2ServeOption.isChecked) 2 else 1
                val selectedBestOf = when (bestOfGroup.checkedRadioButtonId) {
                    bestOf1.id -> 1
                    bestOf3.id -> 3
                    bestOf7.id -> 7
                    else -> 5
                }
                viewModel.setupMatch(
                    player1Name = player1Input.text.toString(),
                    player2Name = player2Input.text.toString(),
                    firstServer = firstServer,
                    bestOfSets = selectedBestOf,
                )
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }
}
