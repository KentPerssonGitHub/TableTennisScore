package com.example.tabletennisscore

import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.tabletennisscore.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: GameViewModel by viewModels()

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
        }
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
}
