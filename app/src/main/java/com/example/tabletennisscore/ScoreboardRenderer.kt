package com.example.tabletennisscore

import android.util.TypedValue
import android.view.View
import android.widget.TextView
import com.example.tabletennisscore.databinding.ActivityMainBinding

/**
 * Draws the scoreboard of the match screen from a [GameViewModel.GameState]: names, points, sets, the
 * serve indicator and the tournament name, each on the side the player is at. Shared by the live match
 * ([MainActivity]) and the match replay.
 */
class ScoreboardRenderer(private val binding: ActivityMainBinding) {

    private val context get() = binding.root.context

    fun render(state: GameViewModel.GameState) {
        renderPlayersAndScores(state)
        binding.rootLayout.post { alignCurrentScoreGlyphsToSetGlyphs() }
        renderTournamentName(state)
        renderServeIndicators(state)
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
        binding.tvPlayer1Name.text = context.serveBallText(leftName, isDoubles && leftServing)
        binding.tvPlayer2Name.text = context.serveBallText(rightName, isDoubles && !leftServing, placeAtEnd = true)
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
        binding.tvTournamentName.text = tName.ifBlank { context.getString(R.string.tournament_name_default) }
        binding.tvTournamentName.alpha = if (tName.isBlank()) 0.35f else 0.70f
    }

    /** Serve indicator follows the player, not the side. */
    private fun renderServeIndicators(state: GameViewModel.GameState) {
        val leftServing = isLeftPlayerServing(state)
        binding.ivServe1.visibility = if (leftServing) View.VISIBLE else View.INVISIBLE
        binding.ivServe2.visibility = if (!leftServing) View.VISIBLE else View.INVISIBLE
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
        val density = binding.root.resources.displayMetrics.density
        val visualCalibrationPx = -12f * density
        scoreView.translationY = (targetGlyphTop - scoreGlyphTop) + visualCalibrationPx
    }
}
