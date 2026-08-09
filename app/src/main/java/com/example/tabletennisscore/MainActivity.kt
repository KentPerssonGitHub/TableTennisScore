package com.example.tabletennisscore

import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.tabletennisscore.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: GameViewModel by viewModels()
    private var lastShownMatchWinner: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        observeState()
    }

    private fun setupClickListeners() {
        // Score taps – tap the score area to add a point
        binding.tvScore1.setOnClickListener { viewModel.addPoint(1) }
        binding.tvScore2.setOnClickListener { viewModel.addPoint(2) }

        // Name taps – tap name to edit
        binding.tvPlayer1Name.setOnClickListener { showEditNameDialog(1) }
        binding.tvPlayer2Name.setOnClickListener { showEditNameDialog(2) }

        binding.btnSetupMatch.setOnClickListener { confirmSetupMatch() }
        binding.btnStartMatch.setOnClickListener { viewModel.startOrResumeMatch() }
        binding.btnPauseMatch.setOnClickListener { viewModel.pauseMatch() }
        binding.btnUndo.setOnClickListener { viewModel.undo() }
        binding.btnReset.setOnClickListener { confirmReset() }
    }

    private fun observeState() {
        viewModel.state.observe(this) { state ->
            binding.tvScore1.text = state.score1.toString()
            binding.tvScore2.text = state.score2.toString()
            binding.tvSets.text = "${state.sets1} – ${state.sets2}"
            binding.tvPlayer1Name.text = state.player1Name
            binding.tvPlayer2Name.text = state.player2Name

            binding.ivServe1.visibility = if (state.server == 1) View.VISIBLE else View.INVISIBLE
            binding.ivServe2.visibility = if (state.server == 2) View.VISIBLE else View.INVISIBLE

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

            if (state.isMatchRunning) {
                binding.btnPauseMatch.visibility = View.VISIBLE
                binding.btnStartMatch.visibility = View.GONE
                binding.btnSetupMatch.visibility = View.GONE
                binding.btnReset.visibility = View.GONE
                binding.btnUndo.visibility = View.VISIBLE
            } else {
                binding.btnPauseMatch.visibility = View.GONE
                binding.btnStartMatch.visibility = if (isMatchFinished) View.GONE else View.VISIBLE
                binding.btnSetupMatch.visibility = View.VISIBLE
                binding.btnReset.visibility = View.VISIBLE
                binding.btnUndo.visibility = if (state.hasMatchStarted) View.VISIBLE else View.GONE
            }

            if (state.matchWinner == null) {
                lastShownMatchWinner = null
            } else if (state.matchWinner != lastShownMatchWinner) {
                lastShownMatchWinner = state.matchWinner
                showMatchWinnerDialog(state.matchWinner, state.player1Name, state.player2Name)
            }
        }
    }

    private fun showMatchWinnerDialog(winner: Int, player1Name: String, player2Name: String) {
        val winnerName = if (winner == 1) player1Name else player2Name
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_match_winner_title)
            .setMessage(getString(R.string.dialog_match_winner_message, winnerName))
            .setPositiveButton(R.string.dialog_ok, null)
            .show()
    }

    private fun showEditNameDialog(player: Int) {
        val state = viewModel.state.value ?: return
        val currentName = if (player == 1) state.player1Name else state.player2Name

        val editText = EditText(this).apply {
            setText(currentName)
            selectAll()
            hint = getString(R.string.dialog_hint_name)
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
        }
        val player2Input = EditText(this).apply {
            hint = getString(R.string.player2_default)
            setText(state.player2Name)
            setSelection(text.length)
        }

        val serverGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
        }
        val player1ServeOption = RadioButton(this).apply {
            id = View.generateViewId()
            text = getString(R.string.dialog_server_player1)
        }
        val player2ServeOption = RadioButton(this).apply {
            id = View.generateViewId()
            text = getString(R.string.dialog_server_player2)
        }
        serverGroup.addView(player1ServeOption)
        serverGroup.addView(player2ServeOption)
        serverGroup.check(if (state.server == 2) player2ServeOption.id else player1ServeOption.id)

        val bestOfLabel = androidx.appcompat.widget.AppCompatTextView(this).apply {
            text = getString(R.string.dialog_best_of_sets)
            setPadding(0, 16, 0, 8)
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

        content.addView(player1Input)
        content.addView(player2Input)
        content.addView(serverGroup)
        content.addView(bestOfLabel)
        content.addView(bestOfGroup)

        val scrollContent = ScrollView(this).apply {
            addView(content)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_setup_match)
            .setView(scrollContent)
            .setPositiveButton(R.string.dialog_done) { _, _ ->
                val firstServer = if (serverGroup.checkedRadioButtonId == player2ServeOption.id) 2 else 1
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
