package com.example.tabletennisscore.dialogs
import com.example.tabletennisscore.GameViewModel
import com.example.tabletennisscore.R
import com.example.tabletennisscore.sanitizePlayerName

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.doOnPreDraw
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

/**
 * Lets the user pick a player name with a single tap.
 *
 * A field at the top both filters the list and adds new players: when the typed name is not in
 * [names], an "Add …" entry appears after the matches. An added name is passed to [onAdded] (so the
 * caller can save it to the group) and then picked. The [current] name is highlighted.
 */
fun AppCompatActivity.showPlayerNamePickerDialog(
    names: List<String>,
    current: String,
    onAdded: (String) -> Unit,
    onPicked: (String) -> Unit,
) {
    val currentName = current.trim()
    val screenHeight = resources.displayMetrics.heightPixels
    // Cap on the list height; lifted while typing so the list can fill the room above the keyboard.
    var maxListHeight = (screenHeight * 0.7f).toInt()
    lateinit var dialog: AlertDialog

    fun pick(name: String) {
        onPicked(name)
        dialog.dismiss()
    }

    fun addAndPick(typed: String) {
        val name = sanitizePlayerName(typed, "")
        if (name.isBlank()) return
        val existing = names.firstOrNull { it.equals(name, ignoreCase = true) }
        if (existing == null) onAdded(name)
        pick(existing ?: name)
    }

    val searchInput = EditText(this).apply {
        hint = getString(R.string.dialog_search_or_add_player_hint)
        filters = arrayOf(
            InputFilter.LengthFilter(GameViewModel.MAX_PLAYER_NAME_LENGTH),
            TitleCaseInputFilter()
        )
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        setSingleLine(true)
    }

    val listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    // Wraps its content but never grows taller than the room the dialog has (or maxListHeight),
    // so the search field stays visible.
    val listScroll = object : ScrollView(this) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val available = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
                maxListHeight
            } else {
                minOf(maxListHeight, MeasureSpec.getSize(heightMeasureSpec))
            }
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST))
        }
    }.apply {
        isVerticalScrollBarEnabled = true
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(4) }
        addView(listContainer)
    }

    // True while the keyboard is open: the room above it is short, so names are shown as
    // compact chips that wrap across the full width instead of the two-column list.
    var typing = false

    fun rebuildList() {
        listContainer.removeAllViews()
        val query = searchInput.text.toString().trim()
        val matches = matchingNames(names, query)
        val typedName = sanitizePlayerName(query, "")
        val canAdd = typedName.isNotBlank() && names.none { it.equals(typedName, ignoreCase = true) }

        if (typing) {
            val chips = ChipGroup(this).apply {
                chipSpacingHorizontal = dp(8)
                chipSpacingVertical = dp(4)
            }
            matches.forEach { name ->
                chips.addView(pickerChip(name, isCurrent = name.equals(currentName, ignoreCase = true)) { pick(name) })
            }
            if (canAdd) {
                chips.addView(pickerChip(getString(R.string.dialog_add_named_player, typedName), isAddChip = true) {
                    addAndPick(typedName)
                })
            }
            listContainer.addView(chips)
            return
        }

        // Two names side by side per line.
        matches.chunked(NAME_COLUMNS).forEach { lineNames ->
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            lineNames.forEach { name ->
                val isCurrent = name.equals(currentName, ignoreCase = true)
                line.addView(pickerRow(name, isCurrent = isCurrent) { pick(name) }.apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
            }
            // Keep a lone last name in the left column.
            repeat(NAME_COLUMNS - lineNames.size) {
                line.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))
            }
            listContainer.addView(line)
        }
        // Offer to add the typed name after the matches, since a match is usually what is wanted.
        if (canAdd) {
            listContainer.addView(pickerRow(getString(R.string.dialog_add_named_player, typedName), isAddRow = true) {
                addAndPick(typedName)
            })
        }
        if (names.isEmpty() && query.isEmpty()) {
            listContainer.addView(TextView(this).apply {
                text = getString(R.string.dialog_name_picker_empty)
                setTextColor(ContextCompat.getColor(this@showPlayerNamePickerDialog, R.color.history_loser_text))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(dp(4), dp(16), dp(4), dp(16))
            })
        }
    }

    searchInput.doAfterTextChanged {
        rebuildList()
        listScroll.scrollTo(0, 0)
    }
    // Switches between browsing (two-column list) and typing (in-app keyboard + compact chips).
    // Assigned further down, once all the views exist.
    var setTyping: (Boolean) -> Unit = {}

    // Done picks the only match or adds a new name; with several matches it just
    // closes the keyboard so the whole list can be seen.
    fun onDone() {
        val query = searchInput.text.toString().trim()
        val matches = matchingNames(names, query)
        when {
            query.isEmpty() -> setTyping(false)
            matches.size == 1 -> pick(matches.single())
            matches.isEmpty() -> addAndPick(query)
            else -> matches.firstOrNull { it.equals(query, ignoreCase = true) }
                ?.let { pick(it) }
                ?: setTyping(false)
        }
    }

    // Only the in-app keyboard is used here: it is much shorter than the system keyboard,
    // leaving room to see the names while typing.
    searchInput.showSoftInputOnFocus = false
    searchInput.setOnTouchListener { _, event ->
        if (event.action == MotionEvent.ACTION_UP) setTyping(true)
        false
    }
    // A hardware keyboard's Enter works like the ✓ key.
    searchInput.setOnEditorActionListener { _, actionId, event ->
        val enterPressed = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
        if (actionId == EditorInfo.IME_ACTION_DONE || enterPressed) {
            onDone()
            true
        } else {
            false
        }
    }
    val keyboard = NameKeyboardView(this, searchInput, onDone = ::onDone, onHide = { setTyping(false) }).apply {
        visibility = View.GONE
    }
    rebuildList()

    // The title lives in the content (not the dialog's title bar) so it can be hidden while the
    // keyboard is open, leaving room for the matching names on short (landscape) screens.
    val title = TextView(this).apply {
        text = getString(R.string.dialog_select_from_group)
        setTextColor(ContextCompat.getColor(this@showPlayerNamePickerDialog, R.color.score_text))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        setPadding(0, dp(20), 0, dp(4))
    }

    // Also part of the content (not a dialog button) so it can be hidden while typing;
    // the back key hides the keyboard then.
    val cancelButton = TextView(this).apply {
        text = getString(R.string.dialog_cancel)
        setTextColor(ContextCompat.getColor(this@showPlayerNamePickerDialog, R.color.player_name))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        gravity = Gravity.CENTER
        minimumHeight = dp(48)
        setPadding(dp(16), 0, dp(16), 0)
        val ripple = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
        setBackgroundResource(ripple.resourceId)
        setOnClickListener { dialog.dismiss() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.END
            topMargin = dp(4)
            bottomMargin = dp(8)
        }
    }

    // The list takes whatever height is left, so the field and Cancel always stay visible.
    listScroll.layoutParams = (listScroll.layoutParams as LinearLayout.LayoutParams).apply { weight = 1f }

    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), 0, dp(24), 0)
        addView(title)
        addView(searchInput)
        addView(listScroll)
        addView(keyboard)
        addView(cancelButton)
    }
    dialog = AlertDialog.Builder(this)
        .setView(content)
        .create()
    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
    dialog.show()

    val normalWidth = dialog.window?.attributes?.width ?: WindowManager.LayoutParams.WRAP_CONTENT
    val typingWidth = (resources.displayMetrics.widthPixels * 0.94f).toInt()
    val normalFieldPadding = searchInput.paddingTop
    setTyping = { on ->
        if (on != typing) {
            typing = on
            title.visibility = if (typing) View.GONE else View.VISIBLE
            cancelButton.visibility = if (typing) View.GONE else View.VISIBLE
            keyboard.visibility = if (typing) View.VISIBLE else View.GONE
            // A slimmer field leaves another line of names visible above the keyboard.
            val fieldPadding = if (typing) dp(6) else normalFieldPadding
            searchInput.setPadding(searchInput.paddingLeft, fieldPadding, searchInput.paddingRight, fieldPadding)
            // Use (nearly) the whole screen while typing so as many names as possible fit. The list
            // then always takes all the height it can get, so the dialog does not shrink as names
            // are filtered out and the keyboard stays put under the fingers.
            dialog.window?.setLayout(if (typing) typingWidth else normalWidth, WindowManager.LayoutParams.WRAP_CONTENT)
            maxListHeight = if (typing) screenHeight else (screenHeight * 0.7f).toInt()
            listScroll.minimumHeight = if (typing) screenHeight else 0
            rebuildList()
            listScroll.scrollTo(0, 0)
        }
        if (on) searchInput.requestFocus()
    }
    // Back closes the keyboard first, then the dialog.
    dialog.setOnKeyListener { _, keyCode, event ->
        if (keyCode == KeyEvent.KEYCODE_BACK && typing) {
            if (event.action == KeyEvent.ACTION_UP) setTyping(false)
            true
        } else {
            false
        }
    }

    // Scroll the current player into view once the list has been laid out.
    listScroll.doOnPreDraw {
        val currentLine = names.indexOfFirst { it.equals(currentName, ignoreCase = true) } / NAME_COLUMNS
        if (currentLine > 0) {
            listContainer.getChildAt(currentLine)?.let { listScroll.scrollTo(0, it.top) }
        }
    }
}

/** One tappable list row: the current name is bold with a check, the add row is highlighted. */
private fun AppCompatActivity.pickerRow(
    label: String,
    isCurrent: Boolean = false,
    isAddRow: Boolean = false,
    onClick: () -> Unit,
): View {
    val textColor = ContextCompat.getColor(
        this,
        when {
            isAddRow -> R.color.sets_text
            isCurrent -> R.color.score_text
            else -> R.color.player_name
        },
    )
    return LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(48)
        setPadding(dp(4), dp(6), dp(4), dp(6))
        val ripple = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
        setBackgroundResource(ripple.resourceId)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }

        addView(TextView(context).apply {
            text = if (isAddRow) "＋" else ""
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(28), LinearLayout.LayoutParams.WRAP_CONTENT)
            visibility = if (isAddRow) View.VISIBLE else View.GONE
        })
        addView(TextView(context).apply {
            text = label
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            if (isCurrent || isAddRow) setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        if (isCurrent) {
            addView(TextView(context).apply {
                text = "✓"
                setTextColor(ContextCompat.getColor(context, R.color.sets_text))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setPadding(dp(8), 0, 0, 0)
            })
        }
    }
}

/** Compact, outlined name chip used while typing; the add chip is highlighted like the add row. */
private fun AppCompatActivity.pickerChip(
    label: String,
    isCurrent: Boolean = false,
    isAddChip: Boolean = false,
    onClick: () -> Unit,
): Chip = Chip(this).apply {
    text = if (isAddChip) "＋ $label" else label
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
    setEnsureMinTouchTargetSize(false)
    chipMinHeight = dp(34).toFloat()
    chipStartPadding = dp(2).toFloat()
    chipEndPadding = dp(2).toFloat()
    isCheckable = false
    styleChoiceChip(this, isSelected = isCurrent)
    if (isAddChip) {
        val accent = ContextCompat.getColor(context, R.color.sets_text)
        setTextColor(accent)
        chipStrokeColor = ColorStateList.valueOf(accent)
    }
    val font = ResourcesCompat.getFont(context, R.font.goldman)
    setTypeface(font, if (isCurrent || isAddChip) Typeface.BOLD else Typeface.NORMAL)
    setOnClickListener { onClick() }
}

/**
 * Names containing [query], best matches first: names starting with it, then names with a word
 * starting with it, then the rest. Alphabetical order is kept within each group.
 */
private fun matchingNames(names: List<String>, query: String): List<String> {
    if (query.isEmpty()) return names
    return names
        .filter { it.contains(query, ignoreCase = true) }
        .sortedBy { name ->
            when {
                name.startsWith(query, ignoreCase = true) -> 0
                name.split(' ').any { it.startsWith(query, ignoreCase = true) } -> 1
                else -> 2
            }
        }
}

private const val NAME_COLUMNS = 2
