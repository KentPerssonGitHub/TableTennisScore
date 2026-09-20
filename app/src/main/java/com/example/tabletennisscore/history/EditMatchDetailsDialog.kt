package com.example.tabletennisscore.history
import com.example.tabletennisscore.dialogs.showPlayerNamePickerDialog
import com.example.tabletennisscore.GameViewModel
import com.example.tabletennisscore.R

import android.content.res.ColorStateList
import android.graphics.Color
import android.text.InputFilter
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
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
    val layout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val padding = (20 * resources.displayMetrics.density).toInt()
        setPadding(padding, padding, padding, 0)
    }
    val p1Edit = AutoCompleteTextView(this).apply {
        hint = getString(R.string.history_edit_player1)
        setText(result.player1Name)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        filters = arrayOf(InputFilter.LengthFilter(GameViewModel.MAX_PLAYER_NAME_LENGTH))
        imeOptions = EditorInfo.IME_ACTION_NEXT
        maxLines = 1
        threshold = 0
    }
    val p2Edit = AutoCompleteTextView(this).apply {
        hint = getString(R.string.history_edit_player2)
        setText(result.player2Name)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        filters = arrayOf(InputFilter.LengthFilter(GameViewModel.MAX_PLAYER_NAME_LENGTH))
        imeOptions = EditorInfo.IME_ACTION_DONE
        maxLines = 1
        threshold = 0
    }
    if (nameGroup.isNotEmpty()) {
        val namesAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, nameGroup)
        p1Edit.setAdapter(namesAdapter)
        p2Edit.setAdapter(namesAdapter)
        p1Edit.setOnClickListener { p1Edit.showDropDown() }
        p2Edit.setOnClickListener { p2Edit.showDropDown() }
    }

    val buttonDensity = resources.displayMetrics.density
    fun buttonPx(dp: Int): Int = (dp * buttonDensity).toInt()

    val selectFromGroupP1 = MaterialButton(this).apply {
        text = getString(R.string.dialog_select_from_group)
        isAllCaps = false
        insetTop = 0
        insetBottom = 0
        minimumHeight = buttonPx(30)
        minHeight = buttonPx(30)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setPadding(buttonPx(14), buttonPx(2), buttonPx(14), buttonPx(2))
        setTextColor(ContextCompat.getColor(this@showEditMatchDetailsDialog, R.color.player_name))
        strokeWidth = buttonPx(1)
        strokeColor = ColorStateList.valueOf(ContextCompat.getColor(this@showEditMatchDetailsDialog, R.color.history_loser_text))
        backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        cornerRadius = buttonPx(18)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = buttonPx(4)
        }
        isEnabled = nameGroup.isNotEmpty()
        alpha = if (nameGroup.isNotEmpty()) 1f else 0.45f
        setOnClickListener {
            showPlayerNamePickerDialog(nameGroup, p1Edit.text.toString()) { selected ->
                p1Edit.setText(selected)
                p1Edit.setSelection(p1Edit.text.length)
            }
        }
    }
    val selectFromGroupP2 = MaterialButton(this).apply {
        text = getString(R.string.dialog_select_from_group)
        isAllCaps = false
        insetTop = 0
        insetBottom = 0
        minimumHeight = buttonPx(30)
        minHeight = buttonPx(30)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setPadding(buttonPx(14), buttonPx(2), buttonPx(14), buttonPx(2))
        setTextColor(ContextCompat.getColor(this@showEditMatchDetailsDialog, R.color.player_name))
        strokeWidth = buttonPx(1)
        strokeColor = ColorStateList.valueOf(ContextCompat.getColor(this@showEditMatchDetailsDialog, R.color.history_loser_text))
        backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        cornerRadius = buttonPx(18)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = buttonPx(4)
            bottomMargin = buttonPx(8)
        }
        isEnabled = nameGroup.isNotEmpty()
        alpha = if (nameGroup.isNotEmpty()) 1f else 0.45f
        setOnClickListener {
            showPlayerNamePickerDialog(nameGroup, p2Edit.text.toString()) { selected ->
                p2Edit.setText(selected)
                p2Edit.setSelection(p2Edit.text.length)
            }
        }
    }
    
    val roundLabel = TextView(this).apply {
        text = getString(R.string.history_round_label)
        setPadding(0, 16, 0, 8)
    }
    val rounds = listOf(
        getString(R.string.round_pool),
        getString(R.string.round_group),
        getString(R.string.round_32),
        getString(R.string.round_16),
        getString(R.string.round_8),
        getString(R.string.round_semi),
        getString(R.string.round_final)
    )
    
    val density = resources.displayMetrics.density
    fun px(dp: Int): Int = (dp * density).toInt()

    val roundGroup = ChipGroup(this).apply {
        isSingleSelection = true
        isSelectionRequired = true
        chipSpacingHorizontal = px(8)
        chipSpacingVertical = px(8)
        setPadding(0, 6, 0, 6)
    }

    var selectedRound = if (result.matchRound.isNotBlank()) result.matchRound else rounds.firstOrNull().orEmpty()
    fun refreshRoundOutline() {
        for (i in 0 until roundGroup.childCount) {
            val chip = roundGroup.getChildAt(i) as? Chip ?: continue
            val isSelected = (chip.tag as? String) == selectedRound
            chip.chipBackgroundColor = ColorStateList.valueOf(Color.TRANSPARENT)
            chip.chipStrokeWidth = if (isSelected) px(2).toFloat() else px(1).toFloat()
            chip.chipStrokeColor = ColorStateList.valueOf(
                ContextCompat.getColor(this@showEditMatchDetailsDialog, if (isSelected) R.color.score_text else R.color.history_loser_text),
            )
            chip.setTextColor(
                ContextCompat.getColor(this@showEditMatchDetailsDialog, if (isSelected) R.color.score_text else R.color.player_name),
            )
        }
    }
    rounds.forEach { round ->
        val btn = Chip(this).apply {
            id = View.generateViewId()
            text = round
            tag = round
            isCheckable = true
            isChecked = (round == selectedRound)
            isAllCaps = false
            chipMinHeight = px(34).toFloat()
        }
        roundGroup.addView(btn)
    }
    refreshRoundOutline()
    roundGroup.setOnCheckedStateChangeListener { group, checkedIds ->
        val selectedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
        selectedRound = (group.findViewById<Chip>(selectedId).tag as? String) ?: selectedRound
        refreshRoundOutline()
    }

    val dataStatusLabel = TextView(this).apply {
        text = getString(R.string.history_data_status_label)
        setPadding(0, 8, 0, 8)
    }
    val dataStatusLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
    }
    val protectMatchCheck = CheckBox(this).apply {
        text = getString(R.string.history_protect_match_checkbox)
        isChecked = result.isProtected
        setPadding(0, 12, 0, 8)
    }
    val dataOkRb = RadioButton(this).apply {
        text = getString(R.string.history_data_status_ok_option)
        isChecked = result.isDataValid
    }
    val dataBadRb = RadioButton(this).apply {
        text = getString(R.string.history_data_status_bad_option)
        isChecked = !result.isDataValid
    }
    dataOkRb.setOnClickListener {
        dataOkRb.isChecked = true
        dataBadRb.isChecked = false
    }
    dataBadRb.setOnClickListener {
        dataOkRb.isChecked = false
        dataBadRb.isChecked = true
    }
    dataStatusLayout.addView(dataOkRb)
    dataStatusLayout.addView(dataBadRb)

    layout.addView(p1Edit)
    layout.addView(selectFromGroupP1)
    layout.addView(p2Edit)
    layout.addView(selectFromGroupP2)
    layout.addView(roundLabel)
    layout.addView(roundGroup)
    layout.addView(protectMatchCheck)
    layout.addView(dataStatusLabel)
    layout.addView(dataStatusLayout)

    val scrollContent = ScrollView(this).apply {
        addView(layout)
    }

    fun saveChanges(): Boolean {
        val p1 = p1Edit.text.toString().trim()
        val p2 = p2Edit.text.toString().trim()
        val isDataValid = dataOkRb.isChecked
        if (p1.isEmpty() || p2.isEmpty()) {
            Toast.makeText(this, R.string.history_edit_names_required, Toast.LENGTH_SHORT).show()
            return false
        }
        onSave(
            result.copy(
                player1Name = p1,
                player2Name = p2,
                matchRound = selectedRound,
                isDataValid = isDataValid,
                isProtected = protectMatchCheck.isChecked,
            )
        )
        return true
    }

    val dialog = AlertDialog.Builder(this)
        .setTitle(R.string.history_match_details_title)
        .setView(scrollContent)
        .setPositiveButton(R.string.dialog_ok, null)
        .setNegativeButton(R.string.dialog_cancel, null)
        .create()

    dialog.setOnShowListener {
        val okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        okButton.setTextColor(ContextCompat.getColor(this, R.color.score_text))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            .setTextColor(ContextCompat.getColor(this, R.color.player_name))
        okButton.setOnClickListener {
            if (saveChanges()) dialog.dismiss()
        }
        p2Edit.setOnEditorActionListener { _, actionId, event ->
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
