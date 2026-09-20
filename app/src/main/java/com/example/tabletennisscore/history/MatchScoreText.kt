package com.example.tabletennisscore.history

import android.content.Context
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import androidx.core.content.ContextCompat
import com.example.tabletennisscore.R

private fun SpannableStringBuilder.appendColored(text: String, color: Int) {
    val start = length
    append(text)
    setSpan(ForegroundColorSpan(color), start, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
}

/** "(3 - 1)": the sets won by the match winner (left) and by the loser (right). */
fun Context.matchScoreText(sets1: Int, sets2: Int, matchWinner: Int): CharSequence {
    val winColor = ContextCompat.getColor(this, R.color.win_vibrant)
    val lossColor = ContextCompat.getColor(this, R.color.loss_vibrant)
    val normalColor = ContextCompat.getColor(this, R.color.player_name)

    // Match winner score always on the left
    val leftSets = if (matchWinner == 1) sets1 else sets2
    val rightSets = if (matchWinner == 1) sets2 else sets1

    return SpannableStringBuilder("(").apply {
        appendColored(leftSets.toString(), if (leftSets > 0) winColor else normalColor)
        append(" - ")
        appendColored(rightSets.toString(), if (rightSets > 0) lossColor else normalColor)
        append(")")
    }
}

/** "(11 - 5, 9 - 11, ...)": every set score with the match winner on the left. */
fun Context.allSetsScoreText(setResultsJson: String, matchWinner: Int): CharSequence {
    val winColor = ContextCompat.getColor(this, R.color.win_vibrant)
    val lossColor = ContextCompat.getColor(this, R.color.loss_vibrant)
    val normalColor = ContextCompat.getColor(this, R.color.score_text)

    val sets = setResultsJson.split(",").filter { it.isNotBlank() }
    return SpannableStringBuilder("(").apply {
        sets.forEachIndexed { i, set ->
            val parts = set.split("-")
            if (parts.size == 2) {
                val rawS1 = parts[0].toIntOrNull() ?: 0
                val rawS2 = parts[1].toIntOrNull() ?: 0

                // Match winner score always on the left
                val leftScore = if (matchWinner == 1) rawS1 else rawS2
                val rightScore = if (matchWinner == 1) rawS2 else rawS1

                appendColored(leftScore.toString(), if (leftScore > rightScore) winColor else normalColor)
                append(" - ")
                appendColored(rightScore.toString(), if (rightScore > leftScore) lossColor else normalColor)
            }
            if (i < sets.size - 1) append(", ")
        }
        append(")")
    }
}
