package com.example.tabletennisscore
import com.example.tabletennisscore.GameViewModel
import com.example.tabletennisscore.R

import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import com.example.tabletennisscore.databinding.ActivityMainBinding

fun AppCompatActivity.updateMatchSummaryPanel(
binding: ActivityMainBinding,
viewModel: GameViewModel,
state: GameViewModel.GameState,
) {
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
    renderMatchSummaryScoreTable(binding, state)
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

private fun AppCompatActivity.renderMatchSummaryScoreTable(
binding: ActivityMainBinding,
state: GameViewModel.GameState,
) {
    val table = binding.tableMatchSummaryScores
    table.removeAllViews()
    val density = resources.displayMetrics.density
    val winnerColor = ContextCompat.getColor(this, R.color.summary_winner_text)
    val whiteColor = ContextCompat.getColor(this, android.R.color.white)
    val dividerColor = ContextCompat.getColor(this, R.color.divider)
    fun Int.dp() = (this * density).toInt()

    // Size of the square showing the number of sets won
    val setBoxSize = 28.dp()

    // Text sizes are in sp; minW and the margins are in dp
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
        Triple(1, state.player1Name, state.sets1),
        Triple(2, state.player2Name, state.sets2),
    ).forEach { (player, name, sets) ->
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
