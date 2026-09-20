package com.example.tabletennisscore.dialogs

import android.graphics.Typeface
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.tabletennisscore.GameViewModel
import com.example.tabletennisscore.R

/** Lets the user correct the set scores and the current score while the match is paused. */
fun AppCompatActivity.showEditScoreDialog(viewModel: GameViewModel) {
    val state = viewModel.state.value ?: return
    if (state.isMatchRunning || state.matchWinner != null || !state.hasMatchStarted) return

    val form = EditScoreForm(this, state)
    val dialog = AlertDialog.Builder(this)
        .setTitle(R.string.dialog_edit_score_title)
        .setView(ScrollView(this).apply { addView(form.buildContent()) })
        .setPositiveButton(R.string.dialog_ok) { _, _ ->
            val scores = form.readScores()
            val accepted = viewModel.updatePausedMatchScores(
                scores.completedSets,
                scores.currentScore1,
                scores.currentScore2,
            )
            if (!accepted) {
                Toast.makeText(this, R.string.error_invalid_manual_score, Toast.LENGTH_SHORT).show()
            }
        }
        .setNegativeButton(R.string.dialog_cancel, null)
        .create()
    dialog.setOnShowListener { styleDialogButtons(dialog) }
    dialog.show()
}

private class EditedScores(
    val completedSets: List<Pair<Int, Int>>,
    val currentScore1: Int,
    val currentScore2: Int,
)

/**
 * The table inside the edit-score dialog: a header, one deletable row per finished set,
 * and a final row for the set in progress. [readScores] returns what the user typed.
 */
private class EditScoreForm(
    private val activity: AppCompatActivity,
    private val state: GameViewModel.GameState,
) {
    private class SetRow(
        val container: LinearLayout,
        val label: TextView,
        val player1Input: EditText,
        val player2Input: EditText,
    )

    private val content = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(12), dp(20), 0)
    }
    private val completedRows = mutableListOf<SetRow>()
    private lateinit var currentSetRow: SetRow

    // Column geometry shared by the header and every set row so everything lines up.
    private val deleteColumnWidth = dp(40)
    private val columnGap = dp(6)
    private val mutedColor = ContextCompat.getColor(activity, R.color.player_name)

    private fun dp(value: Int) = activity.dp(value)

    fun buildContent(): View {
        addHeader()
        addDivider(topMarginDp = 6)

        state.setResults.forEachIndexed { index, set ->
            completedRows.add(
                addSetRow(
                    activity.getString(R.string.dialog_set_label, index + 1),
                    set.first,
                    set.second,
                    deletable = true,
                ),
            )
        }

        // Separate the in-progress set from the finished ones.
        addDivider(topMarginDp = 8, bottomMarginDp = 4)
        currentSetRow = addSetRow(
            activity.getString(R.string.dialog_current_set_label),
            state.score1,
            state.score2,
            deletable = false,
        )

        content.addView(
            TextView(activity).apply {
                text = activity.getString(R.string.dialog_edit_score_hint)
                textSize = 12f
                alpha = 0.6f
                setPadding(0, dp(12), 0, 0)
            },
        )
        return content
    }

    fun readScores(): EditedScores {
        fun EditText.score() = text.toString().toIntOrNull() ?: 0
        return EditedScores(
            completedSets = completedRows.map { it.player1Input.score() to it.player2Input.score() },
            currentScore1 = currentSetRow.player1Input.score(),
            currentScore2 = currentSetRow.player2Input.score(),
        )
    }

    // ----- Layout pieces -----

    private fun labelParams() = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, LABEL_WEIGHT)

    private fun scoreParams() = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, SCORE_WEIGHT).apply {
        marginStart = columnGap
        marginEnd = columnGap
    }

    /** "Set | <player 1> | <player 2>" */
    private fun addHeader() {
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            TextView(activity).apply {
                text = activity.getString(R.string.dialog_set_column_header)
                setTypeface(null, Typeface.BOLD)
                textSize = 13f
                alpha = 0.7f
            },
            labelParams(),
        )
        listOf(state.player1Name, state.player2Name).forEach { playerName ->
            header.addView(
                TextView(activity).apply {
                    text = playerName
                    setTypeface(null, Typeface.BOLD)
                    textSize = 13f
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(mutedColor)
                },
                scoreParams(),
            )
        }
        header.addView(View(activity), LinearLayout.LayoutParams(deleteColumnWidth, 1))
        content.addView(header)
    }

    private fun addDivider(topMarginDp: Int, bottomMarginDp: Int = 0) {
        content.addView(
            View(activity).apply { setBackgroundColor(DIVIDER_COLOR) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
                topMargin = dp(topMarginDp)
                bottomMargin = dp(bottomMarginDp)
            },
        )
    }

    private fun addSetRow(labelText: String, score1: Int, score2: Int, deletable: Boolean): SetRow {
        val label = TextView(activity).apply {
            text = labelText
            setTypeface(null, if (deletable) Typeface.NORMAL else Typeface.BOLD)
            textSize = 15f
        }
        val player1Input = scoreInput(activity.getString(R.string.dialog_score_player1, state.player1Name), score1)
        val player2Input = scoreInput(activity.getString(R.string.dialog_score_player2, state.player2Name), score2)

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(2))
        }
        container.addView(label, labelParams())
        container.addView(player1Input, scoreParams())
        container.addView(player2Input, scoreParams())

        val row = SetRow(container, label, player1Input, player2Input)
        if (deletable) {
            container.addView(
                deleteButton(labelText) {
                    content.removeView(container)
                    completedRows.remove(row)
                    renumberCompletedRows()
                },
                LinearLayout.LayoutParams(deleteColumnWidth, LinearLayout.LayoutParams.WRAP_CONTENT),
            )
        } else {
            container.addView(View(activity), LinearLayout.LayoutParams(deleteColumnWidth, 1))
        }

        content.addView(container)
        return row
    }

    private fun scoreInput(contentDescriptionText: String, score: Int): EditText = EditText(activity).apply {
        setText(score.toString())
        setSelection(text.length)
        gravity = Gravity.CENTER
        inputType = InputType.TYPE_CLASS_NUMBER
        filters = arrayOf(InputFilter.LengthFilter(3))
        contentDescription = contentDescriptionText
    }

    private fun deleteButton(setLabel: String, onClick: () -> Unit): TextView = TextView(activity).apply {
        text = "✕"
        textSize = 16f
        gravity = Gravity.CENTER
        contentDescription = activity.getString(R.string.cd_delete_set, setLabel)
        setTextColor(ContextCompat.getColor(activity, android.R.color.holo_red_dark))
        background = null
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun renumberCompletedRows() {
        completedRows.forEachIndexed { index, row ->
            row.label.text = activity.getString(R.string.dialog_set_label, index + 1)
        }
    }

    private companion object {
        const val LABEL_WEIGHT = 1.6f
        const val SCORE_WEIGHT = 1f
        const val DIVIDER_COLOR = 0x40808080
    }
}
