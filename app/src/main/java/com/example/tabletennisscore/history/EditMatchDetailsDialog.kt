package com.example.tabletennisscore.history
import com.example.tabletennisscore.dialogs.styleChoiceChip
import com.example.tabletennisscore.dialogs.outlinedButton
import com.example.tabletennisscore.dialogs.dp
import com.example.tabletennisscore.dialogs.showPlayerNamePickerDialog
import com.example.tabletennisscore.GameViewModel
import com.example.tabletennisscore.R

import android.text.InputFilter
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.tabletennisscore.data.MatchResult
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

/** Shows the match-details editor; [onSave] receives the edited copy once the input is valid. */
fun AppCompatActivity.showEditMatchDetailsDialog(
    result: MatchResult,
    nameGroup: List<String>,
    onSave: (MatchResult) -> Unit,
) {
    val form = EditMatchDetailsForm(this, result, nameGroup)

    val dialog = AlertDialog.Builder(this)
        .setTitle(R.string.history_match_details_title)
        .setView(ScrollView(this).apply { addView(form.buildContent()) })
        .setPositiveButton(R.string.dialog_ok, null)
        .setNegativeButton(R.string.dialog_cancel, null)
        .create()

    dialog.setOnShowListener {
        val okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        okButton.setTextColor(ContextCompat.getColor(this, R.color.score_text))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            .setTextColor(ContextCompat.getColor(this, R.color.player_name))
        // Keep the dialog open when the names are invalid, so use a custom click listener.
        okButton.setOnClickListener {
            val edited = form.editedResult() ?: return@setOnClickListener
            onSave(edited)
            dialog.dismiss()
        }
        form.setOnDone { okButton.performClick() }
    }
    dialog.show()
}

/** The fields of the match-details dialog: player names, round, protection and data status. */
private class EditMatchDetailsForm(
    private val activity: AppCompatActivity,
    private val result: MatchResult,
    private val nameGroup: List<String>,
) {
    private val hasNameGroup = nameGroup.isNotEmpty()
    private var selectedRound = result.matchRound.ifBlank { activity.getString(R.string.round_pool) }

    private lateinit var player1Edit: AutoCompleteTextView
    private lateinit var player2Edit: AutoCompleteTextView
    private lateinit var protectMatchCheck: CheckBox
    private lateinit var dataOkOption: RadioButton

    private fun dp(value: Int) = activity.dp(value)

    fun buildContent(): View {
        player1Edit = nameEdit(R.string.history_edit_player1, result.player1Name, EditorInfo.IME_ACTION_NEXT)
        player2Edit = nameEdit(R.string.history_edit_player2, result.player2Name, EditorInfo.IME_ACTION_DONE)
        if (hasNameGroup) {
            val namesAdapter = ArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, nameGroup)
            listOf(player1Edit, player2Edit).forEach { edit ->
                edit.setAdapter(namesAdapter)
                edit.setOnClickListener { edit.showDropDown() }
            }
        }

        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), 0)
            addView(player1Edit)
            addView(selectFromGroupButton(player1Edit, bottomMarginDp = 0))
            addView(player2Edit)
            addView(selectFromGroupButton(player2Edit, bottomMarginDp = 8))
            addView(sectionLabel(R.string.history_round_label, topPadding = 16))
            addView(roundChips())
            protectMatchCheck = protectCheckBox()
            addView(protectMatchCheck)
            addView(sectionLabel(R.string.history_data_status_label, topPadding = 8))
            addView(dataStatusOptions())
        }
    }

    /** The edited match, or null (after telling the user) when a player name is missing. */
    fun editedResult(): MatchResult? {
        val player1 = player1Edit.text.toString().trim()
        val player2 = player2Edit.text.toString().trim()
        if (player1.isEmpty() || player2.isEmpty()) {
            Toast.makeText(activity, R.string.history_edit_names_required, Toast.LENGTH_SHORT).show()
            return null
        }
        return result.copy(
            player1Name = player1,
            player2Name = player2,
            matchRound = selectedRound,
            isDataValid = dataOkOption.isChecked,
            isProtected = protectMatchCheck.isChecked,
        )
    }

    /** Runs [action] when the user presses Done / Enter in the last name field. */
    fun setOnDone(action: () -> Unit) {
        player2Edit.setOnEditorActionListener { _, actionId, event ->
            val enterPressed = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_DONE || enterPressed) {
                action()
                true
            } else {
                false
            }
        }
    }

    // ----- Building blocks -----

    private fun nameEdit(hintRes: Int, initial: String, imeAction: Int) = AutoCompleteTextView(activity).apply {
        hint = activity.getString(hintRes)
        setText(initial)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        filters = arrayOf(InputFilter.LengthFilter(GameViewModel.MAX_PLAYER_NAME_LENGTH))
        imeOptions = imeAction
        maxLines = 1
        threshold = 0
    }

    private fun selectFromGroupButton(target: AutoCompleteTextView, bottomMarginDp: Int): MaterialButton =
        activity.outlinedButton(activity.getString(R.string.dialog_select_from_group), bottomMarginDp = bottomMarginDp).apply {
            isEnabled = hasNameGroup
            alpha = if (hasNameGroup) 1f else 0.45f
            setOnClickListener {
                activity.showPlayerNamePickerDialog(nameGroup, target.text.toString()) { selected ->
                    target.setText(selected)
                    target.setSelection(target.text.length)
                }
            }
        }

    private fun sectionLabel(textRes: Int, topPadding: Int) = TextView(activity).apply {
        text = activity.getString(textRes)
        setPadding(0, topPadding, 0, 8)
    }

    private fun roundChips(): ChipGroup {
        val rounds = listOf(
            R.string.round_pool,
            R.string.round_group,
            R.string.round_32,
            R.string.round_16,
            R.string.round_8,
            R.string.round_semi,
            R.string.round_final,
        ).map { activity.getString(it) }

        val group = ChipGroup(activity).apply {
            isSingleSelection = true
            isSelectionRequired = true
            chipSpacingHorizontal = dp(8)
            chipSpacingVertical = dp(8)
            setPadding(0, 6, 0, 6)
        }
        fun refreshOutlines() {
            for (i in 0 until group.childCount) {
                val chip = group.getChildAt(i) as? Chip ?: continue
                activity.styleChoiceChip(chip, (chip.tag as? String) == selectedRound)
            }
        }
        rounds.forEach { round ->
            group.addView(
                Chip(activity).apply {
                    id = View.generateViewId()
                    text = round
                    tag = round
                    isCheckable = true
                    isChecked = round == selectedRound
                    isAllCaps = false
                    chipMinHeight = dp(34).toFloat()
                },
            )
        }
        refreshOutlines()
        group.setOnCheckedStateChangeListener { _, checkedIds ->
            val chip = checkedIds.firstOrNull()?.let { group.findViewById<Chip>(it) }
                ?: return@setOnCheckedStateChangeListener
            selectedRound = (chip.tag as? String) ?: selectedRound
            refreshOutlines()
        }
        return group
    }

    private fun protectCheckBox() = CheckBox(activity).apply {
        text = activity.getString(R.string.history_protect_match_checkbox)
        isChecked = result.isProtected
        setPadding(0, 12, 0, 8)
    }

    /** Two radio buttons (data OK / data bad) that behave as a group without a RadioGroup. */
    private fun dataStatusOptions(): LinearLayout {
        dataOkOption = RadioButton(activity).apply {
            text = activity.getString(R.string.history_data_status_ok_option)
            isChecked = result.isDataValid
        }
        val dataBadOption = RadioButton(activity).apply {
            text = activity.getString(R.string.history_data_status_bad_option)
            isChecked = !result.isDataValid
        }
        dataOkOption.setOnClickListener {
            dataOkOption.isChecked = true
            dataBadOption.isChecked = false
        }
        dataBadOption.setOnClickListener {
            dataOkOption.isChecked = false
            dataBadOption.isChecked = true
        }
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(dataOkOption)
            addView(dataBadOption)
        }
    }
}
