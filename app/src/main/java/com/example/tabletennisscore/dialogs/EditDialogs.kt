package com.example.tabletennisscore.dialogs
import com.example.tabletennisscore.GameViewModel
import com.example.tabletennisscore.R

import android.graphics.Typeface
import android.text.InputFilter
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

fun AppCompatActivity.showEditTournamentNameDialog(viewModel: GameViewModel) {
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

fun AppCompatActivity.showEditNameDialog(player: Int, viewModel: GameViewModel) {
    val state = viewModel.state.value ?: return
    if (state.matchMode == GameViewModel.MATCH_MODE_DOUBLES) {
        showEditDoublesTeamNameDialog(player, state, viewModel)
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
            setAdapter(ArrayAdapter(this@showEditNameDialog, android.R.layout.simple_dropdown_item_1line, nameGroup))
            setOnClickListener { showDropDown() }
        }
    }

    val selectFromGroupButton = outlinedButton(getString(R.string.dialog_select_from_group), topMarginDp = 6).apply {
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

fun AppCompatActivity.showEditDoublesTeamNameDialog(player: Int, state: GameViewModel.GameState, viewModel: GameViewModel) {
    val nameGroup = viewModel.getPlayerNameGroup()

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
                setAdapter(ArrayAdapter(this@showEditDoublesTeamNameDialog, android.R.layout.simple_dropdown_item_1line, nameGroup))
                setOnClickListener { showDropDown() }
            }
        }
    }

    fun createSelectButton(target: AutoCompleteTextView): MaterialButton {
        return outlinedButton(getString(R.string.dialog_select_from_group), bottomMarginDp = 8).apply {
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
    val swapTopBottomButton = outlinedButton(getString(R.string.dialog_swap_top_bottom), bottomMarginDp = 8).apply {
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

fun AppCompatActivity.showEditScoreDialog(viewModel: GameViewModel) {
    val state = viewModel.state.value ?: return
    if (state.isMatchRunning || state.matchWinner != null || !state.hasMatchStarted) return

    fun dp(value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value,
        resources.displayMetrics,
    ).toInt()

    // Column geometry shared by the header and every set row so everything lines up.
    val labelWeight = 1.6f
    val scoreWeight = 1f
    val deleteColumnWidth = dp(40f)
    val columnGap = dp(6f)
    val mutedColor = ContextCompat.getColor(this, R.color.player_name)

    fun labelParams() = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, labelWeight)
    fun scoreParams() = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, scoreWeight).apply {
        marginStart = columnGap
        marginEnd = columnGap
    }

    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20f), dp(12f), dp(20f), 0)
    }

    // ----- Header: "Set | <player 1> | <player 2>" -----
    val header = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    header.addView(
        TextView(this).apply {
            text = getString(R.string.dialog_set_column_header)
            setTypeface(null, Typeface.BOLD)
            textSize = 13f
            alpha = 0.7f
        },
        labelParams(),
    )
    listOf(state.player1Name, state.player2Name).forEach { playerName ->
        header.addView(
            TextView(this).apply {
                text = playerName
                setTypeface(null, Typeface.BOLD)
                textSize = 13f
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(mutedColor)
            },
            scoreParams(),
        )
    }
    header.addView(View(this), LinearLayout.LayoutParams(deleteColumnWidth, 1))
    content.addView(header)

    content.addView(
        View(this).apply { setBackgroundColor(0x40808080) },
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1f)).apply {
            topMargin = dp(6f)
        },
    )

    class SetRow(
        val container: LinearLayout,
        val label: TextView,
        val player1Input: EditText,
        val player2Input: EditText,
    )

    val completedRows = mutableListOf<SetRow>()

    fun renumberCompletedRows() {
        completedRows.forEachIndexed { index, row ->
            row.label.text = getString(R.string.dialog_set_label, index + 1)
        }
    }

    fun buildScoreInput(contentDescriptionText: String, score: Int): EditText {
        return EditText(this).apply {
            setText(score.toString())
            setSelection(text.length)
            gravity = Gravity.CENTER
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(3))
            contentDescription = contentDescriptionText
        }
    }

    fun addSetRow(labelText: String, score1: Int, score2: Int, deletable: Boolean): SetRow {
        val label = TextView(this).apply {
            text = labelText
            setTypeface(null, if (deletable) Typeface.NORMAL else Typeface.BOLD)
            textSize = 15f
        }
        val player1Input = buildScoreInput(
            getString(R.string.dialog_score_player1, state.player1Name),
            score1,
        )
        val player2Input = buildScoreInput(
            getString(R.string.dialog_score_player2, state.player2Name),
            score2,
        )

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2f), 0, dp(2f))
        }
        container.addView(label, labelParams())
        container.addView(player1Input, scoreParams())
        container.addView(player2Input, scoreParams())

        val row = SetRow(container, label, player1Input, player2Input)

        if (deletable) {
            container.addView(
                TextView(this).apply {
                    text = "✕"
                    textSize = 16f
                    gravity = Gravity.CENTER
                    contentDescription = getString(R.string.cd_delete_set, labelText)
                    setTextColor(ContextCompat.getColor(this@showEditScoreDialog, android.R.color.holo_red_dark))
                    background = null
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        content.removeView(container)
                        completedRows.remove(row)
                        renumberCompletedRows()
                    }
                },
                LinearLayout.LayoutParams(deleteColumnWidth, LinearLayout.LayoutParams.WRAP_CONTENT),
            )
        } else {
            container.addView(View(this), LinearLayout.LayoutParams(deleteColumnWidth, 1))
        }

        content.addView(container)
        return row
    }

    state.setResults.forEachIndexed { index, set ->
        completedRows.add(
            addSetRow(
                getString(R.string.dialog_set_label, index + 1),
                set.first,
                set.second,
                deletable = true,
            ),
        )
    }

    // Separate the in-progress set from the finished ones.
    content.addView(
        View(this).apply { setBackgroundColor(0x40808080) },
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1f)).apply {
            topMargin = dp(8f)
            bottomMargin = dp(4f)
        },
    )

    val currentSetRow = addSetRow(
        getString(R.string.dialog_current_set_label),
        state.score1,
        state.score2,
        deletable = false,
    )

    content.addView(
        TextView(this).apply {
            text = getString(R.string.dialog_edit_score_hint)
            textSize = 12f
            alpha = 0.6f
            setPadding(0, dp(12f), 0, 0)
        },
    )

    val scrollContent = ScrollView(this).apply {
        addView(content)
    }

    val dialog = AlertDialog.Builder(this)
        .setTitle(R.string.dialog_edit_score_title)
        .setView(scrollContent)
        .setPositiveButton(R.string.dialog_ok) { _, _ ->
            val completedSets = completedRows.map {
                val score1 = it.player1Input.text.toString().toIntOrNull() ?: 0
                val score2 = it.player2Input.text.toString().toIntOrNull() ?: 0
                score1 to score2
            }
            val currentScore1 = currentSetRow.player1Input.text.toString().toIntOrNull() ?: 0
            val currentScore2 = currentSetRow.player2Input.text.toString().toIntOrNull() ?: 0
            if (!viewModel.updatePausedMatchScores(completedSets, currentScore1, currentScore2)) {
                Toast.makeText(this, R.string.error_invalid_manual_score, Toast.LENGTH_SHORT).show()
            }
        }
        .setNegativeButton(R.string.dialog_cancel, null)
        .create()
    dialog.setOnShowListener { styleDialogButtons(dialog) }
    dialog.show()
}
