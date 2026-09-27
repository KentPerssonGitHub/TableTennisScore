package com.example.tabletennisscore.dialogs
import com.example.tabletennisscore.GameViewModel
import com.example.tabletennisscore.R
import com.example.tabletennisscore.sanitizePlayerName

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.InputFilter
import android.text.InputType
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
import androidx.activity.OnBackPressedCallback
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
        setPadding(paddingLeft, dp(8), paddingRight, dp(8))
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

    // True while the in-app keyboard is open.
    var typing = false

    // Names are compact chips that wrap across the width, so many fit at once.
    val chips = ChipGroup(this).apply {
        chipSpacingHorizontal = dp(8)
        chipSpacingVertical = dp(4)
    }

    fun rebuildList() {
        listContainer.removeAllViews()
        chips.removeAllViews()
        val query = searchInput.text.toString().trim()
        val matches = matchingNames(names, query)
        val typedName = sanitizePlayerName(query, "")
        val canAdd = typedName.isNotBlank() && names.none { it.equals(typedName, ignoreCase = true) }

        matches.forEach { name ->
            chips.addView(pickerChip(name, isCurrent = name.equals(currentName, ignoreCase = true)) { pick(name) })
        }
        // Offer to add the typed name after the matches, since a match is usually what is wanted.
        if (canAdd) {
            chips.addView(pickerChip(getString(R.string.dialog_add_named_player, typedName), isAddChip = true) {
                addAndPick(typedName)
            })
        }
        listContainer.addView(chips)
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
    // Switches between browsing and typing (in-app keyboard shown, title row hidden).
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

    // Title and Cancel share one row at the top (instead of Cancel getting a row of its own at the
    // bottom) to leave more room for names. The whole row is hidden while typing; the back key
    // hides the keyboard then.
    val titleRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(10), 0, 0)
        addView(TextView(context).apply {
            text = getString(R.string.dialog_select_from_group)
            setTextColor(ContextCompat.getColor(this@showPlayerNamePickerDialog, R.color.score_text))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(TextView(context).apply {
            text = getString(R.string.dialog_cancel)
            setTextColor(ContextCompat.getColor(this@showPlayerNamePickerDialog, R.color.player_name))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
            minimumHeight = dp(40)
            setPadding(dp(12), 0, dp(12), 0)
            val ripple = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
            setBackgroundResource(ripple.resourceId)
            setOnClickListener { dialog.dismiss() }
        })
    }

    // The list takes whatever height is left, so the field always stays visible.
    listScroll.layoutParams = (listScroll.layoutParams as LinearLayout.LayoutParams).apply {
        weight = 1f
        topMargin = 0
        bottomMargin = dp(8)
    }

    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), 0, dp(24), 0)
        addView(titleRow)
        addView(searchInput)
        addView(listScroll)
        addView(keyboard)
    }
    dialog = AlertDialog.Builder(this)
        .setView(content)
        .create()
    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
    dialog.show()

    // (Nearly) the full screen width, so many names fit per line. The width stays the same
    // while typing so nothing jumps sideways when the keyboard opens.
    dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.94f).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
    val normalFieldPadding = dp(8)
    setTyping = { on ->
        if (on != typing) {
            typing = on
            titleRow.visibility = if (typing) View.GONE else View.VISIBLE
            keyboard.visibility = if (typing) View.VISIBLE else View.GONE
            // A slimmer field leaves another line of names visible above the keyboard.
            val fieldPadding = if (typing) dp(6) else normalFieldPadding
            searchInput.setPadding(searchInput.paddingLeft, fieldPadding, searchInput.paddingRight, fieldPadding)
            // While typing the list takes all the height it can get, so the dialog does not shrink
            // as names are filtered out and the keyboard stays put under the fingers.
            maxListHeight = if (typing) screenHeight else (screenHeight * 0.7f).toInt()
            listScroll.minimumHeight = if (typing) screenHeight else 0
            rebuildList()
            listScroll.scrollTo(0, 0)
        }
        if (on) searchInput.requestFocus()
    }
    // Back closes the keyboard first, then the dialog. (A back callback rather than a key
    // listener, since newer Android versions deliver back gestures without key events.)
    val closeKeyboardOnBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = setTyping(false)
    }
    dialog.onBackPressedDispatcher.addCallback(closeKeyboardOnBack)
    val applyTyping = setTyping
    setTyping = { on ->
        applyTyping(on)
        closeKeyboardOnBack.isEnabled = on
    }

    // Scroll the current player into view once the list has been laid out.
    listScroll.doOnPreDraw {
        val currentIndex = names.indexOfFirst { it.equals(currentName, ignoreCase = true) }
        chips.getChildAt(currentIndex)?.let { chip ->
            if (chip.bottom > listScroll.height) listScroll.scrollTo(0, chip.top)
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

