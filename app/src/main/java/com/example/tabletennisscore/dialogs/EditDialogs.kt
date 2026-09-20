package com.example.tabletennisscore.dialogs
import com.example.tabletennisscore.GameViewModel
import com.example.tabletennisscore.R

import android.text.InputFilter
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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
