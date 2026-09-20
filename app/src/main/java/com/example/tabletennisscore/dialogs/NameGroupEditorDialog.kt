package com.example.tabletennisscore.dialogs
import com.example.tabletennisscore.GameViewModel
import com.example.tabletennisscore.R
import com.example.tabletennisscore.normalizeTitleCaseWords

import android.text.InputFilter
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Locale

fun AppCompatActivity.showNameGroupEditorDialog(existingNames: List<String>, onSave: (List<String>) -> Unit) {
    fun px(value: Int): Int = dp(value)

    // Working copy of the names, kept sorted & de-duplicated as it changes.
    val workingNames = existingNames
        .map { sanitizeGroupName(it) }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase(Locale.ROOT) }
        .sortedBy { it.lowercase(Locale.ROOT) }
        .toMutableList()

    val rootLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(px(20), px(8), px(20), px(4))
    }

    // Container that holds one row per name.
    val namesContainer = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
    }

    val emptyView = TextView(this).apply {
        text = getString(R.string.name_group_empty)
        setTextColor(ContextCompat.getColor(this@showNameGroupEditorDialog, R.color.player_name))
        alpha = 0.7f
        setPadding(0, px(8), 0, px(8))
    }

    fun rebuildRows() {
        namesContainer.removeAllViews()
        if (workingNames.isEmpty()) {
            namesContainer.addView(emptyView)
            return
        }
        workingNames.toList().forEach { name ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, px(1), 0, px(1))
            }
            val nameLabel = TextView(this).apply {
                text = name
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(ContextCompat.getColor(this@showNameGroupEditorDialog, R.color.player_name))
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
            }
            val removeButton = TextView(this).apply {
                text = "✕"
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(ContextCompat.getColor(this@showNameGroupEditorDialog, android.R.color.holo_red_dark))
                val side = px(28)
                layoutParams = LinearLayout.LayoutParams(side, side).apply {
                    marginStart = px(8)
                }
                contentDescription = getString(R.string.cd_remove_name, name)
                isClickable = true
                isFocusable = true
                val outValue = TypedValue()
                context.theme.resolveAttribute(
                    android.R.attr.selectableItemBackgroundBorderless, outValue, true
                )
                setBackgroundResource(outValue.resourceId)
                setOnClickListener {
                    val confirmDialog = AlertDialog.Builder(this@showNameGroupEditorDialog)
                        .setTitle(R.string.confirm_remove_name_title)
                        .setMessage(getString(R.string.confirm_remove_name_message, name))
                        .setPositiveButton(R.string.dialog_yes) { _, _ ->
                            workingNames.removeAll { it.equals(name, ignoreCase = true) }
                            rebuildRows()
                        }
                        .setNegativeButton(R.string.dialog_no, null)
                        .create()
                    confirmDialog.setOnShowListener { styleDialogButtons(confirmDialog) }
                    confirmDialog.show()
                }
            }
            row.addView(nameLabel)
            row.addView(removeButton)
            namesContainer.addView(row)
        }
    }

    val namesScroll = ScrollView(this).apply {
        // Use a weighted height so the list fills the space the dialog actually has
        // (instead of a fixed height that can be clipped when the keyboard is open),
        // ensuring it always scrolls to reveal every name.
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
        minimumHeight = px(140)
        isVerticalScrollBarEnabled = true
        isScrollbarFadingEnabled = false
        addView(namesContainer)
    }

    // Add-name row: a single full-width input. The keyboard's Done key adds the name.
    val addRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 0, 0, px(8))
    }
    val addInput = EditText(this).apply {
        hint = getString(R.string.dialog_add_name_hint)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        filters = arrayOf(
            InputFilter.LengthFilter(GameViewModel.MAX_PLAYER_NAME_LENGTH),
            TitleCaseInputFilter()
        )
        // Keep editing inline (no full-screen keyboard editor in landscape) so the
        // Done key adds the name reliably.
        imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        setSingleLine(true)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    fun addTypedName() {
        val candidate = sanitizeGroupName(addInput.text.toString())
        if (candidate.isBlank()) {
            addInput.setText("")
            return
        }
        val exists = workingNames.any { it.equals(candidate, ignoreCase = true) }
        if (exists) {
            Toast.makeText(
                this,
                getString(R.string.name_group_duplicate, candidate),
                Toast.LENGTH_SHORT
            ).show()
            addInput.setText("")
            return
        }
        workingNames.add(candidate)
        workingNames.sortBy { it.lowercase(Locale.ROOT) }
        rebuildRows()
        addInput.setText("")
        // Hide the keyboard and drop focus after adding a name.
        (getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(addInput.windowToken, 0)
        addInput.clearFocus()
        // Scroll so the newly added name is visible (it may sort anywhere in the list).
        val newIndex = workingNames.indexOfFirst { it.equals(candidate, ignoreCase = true) }
        if (newIndex >= 0) {
            namesScroll.post {
                namesContainer.getChildAt(newIndex)?.let { child ->
                    namesScroll.smoothScrollTo(0, child.top)
                }
            }
        }
    }

    addInput.setOnEditorActionListener { _, actionId, event ->
        val isDoneAction = actionId == EditorInfo.IME_ACTION_DONE ||
            actionId == EditorInfo.IME_ACTION_GO ||
            actionId == EditorInfo.IME_ACTION_NEXT ||
            actionId == EditorInfo.IME_NULL
        val isEnterKey = event?.keyCode == KeyEvent.KEYCODE_ENTER &&
            event.action == KeyEvent.ACTION_DOWN
        if (isDoneAction || isEnterKey) {
            addTypedName()
            true
        } else {
            false
        }
    }

    addRow.addView(addInput)

    // Add row stays pinned at the top (under the title) so it is always visible,
    // while the list of names scrolls below it.
    rootLayout.addView(addRow)
    rootLayout.addView(namesScroll)

    rebuildRows()

    val dialog = AlertDialog.Builder(this)
        .setTitle(R.string.dialog_edit_name_group)
        .setView(rootLayout)
        .setPositiveButton(R.string.dialog_ok) { _, _ ->
            // Fold in any text left in the add field without pressing Add.
            addTypedName()
            val cleaned = workingNames
                .map { sanitizeGroupName(it) }
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase(Locale.ROOT) }
                .sortedBy { it.lowercase(Locale.ROOT) }
                .toList()
            onSave(cleaned)
        }
        .setNegativeButton(R.string.dialog_cancel, null)
        .create()
    dialog.setOnShowListener { styleDialogButtons(dialog) }
    dialog.show()
}

private fun sanitizeGroupName(name: String): String {
    return normalizeTitleCaseWords(name).take(GameViewModel.MAX_PLAYER_NAME_LENGTH)
}
