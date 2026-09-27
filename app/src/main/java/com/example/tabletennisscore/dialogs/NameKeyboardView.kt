package com.example.tabletennisscore.dialogs
import com.example.tabletennisscore.R

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * A compact, letters-only keyboard for typing player names inside the app, so the name picker
 * does not need the (much taller) system keyboard. It types into [target]; capital letters come
 * from the target's TitleCaseInputFilter, so there is no shift key.
 *
 * With [allowDigits] (for tournament names, which often contain a year) a "123" key switches the
 * top row between letters and digits.
 */
@SuppressLint("ViewConstructor")
class NameKeyboardView(
    context: Context,
    private val target: EditText,
    allowDigits: Boolean = false,
    private val onDone: () -> Unit,
    private val onHide: () -> Unit,
) : LinearLayout(context) {

    private val keyHeight = context.dp(34)
    private val keyGap = context.dp(2)
    private lateinit var topRow: LinearLayout
    private var showingDigits = false

    init {
        orientation = VERTICAL
        setPadding(0, context.dp(4), 0, context.dp(2))
        LETTER_ROWS.forEachIndexed { index, row ->
            val keyRow = addKeyRow {
                row.forEach { letter -> addView(letterKey(letter)) }
                if (index == LETTER_ROWS.lastIndex) {
                    addView(letterKey('-'))
                    addView(backspaceKey())
                }
            }
            if (index == 0) topRow = keyRow
        }
        addKeyRow {
            addView(hideKey())
            if (allowDigits) {
                addView(actionKey(DIGITS_LABEL, weight = 1.5f, description = R.string.cd_keyboard_digits) {}.apply {
                    setOnClickListener {
                        tap()
                        showingDigits = !showingDigits
                        text = if (showingDigits) LETTERS_LABEL else DIGITS_LABEL
                        contentDescription = context.getString(
                            if (showingDigits) R.string.cd_keyboard_letters else R.string.cd_keyboard_digits,
                        )
                        topRow.removeAllViews()
                        (if (showingDigits) DIGIT_ROW else LETTER_ROWS.first()).forEach { topRow.addView(letterKey(it)) }
                    }
                })
            }
            addView(key(" ", weight = if (allowDigits) 4.5f else 6f, special = false).apply {
                contentDescription = context.getString(R.string.cd_keyboard_space)
                setOnClickListener { tap(); type(' ') }
            })
            addView(actionKey("✓", weight = 2.5f, description = R.string.cd_keyboard_done, accent = true) { onDone() })
        }
    }

    private fun addKeyRow(fill: LinearLayout.() -> Unit): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            fill()
        }
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, keyHeight + keyGap * 2))
        return row
    }

    private fun letterKey(letter: Char) = key(letter.uppercase(), weight = 1f, special = false).apply {
        setOnClickListener { tap(); type(letter) }
    }

    private fun backspaceKey() = actionKey("⌫", weight = 1.5f, description = R.string.cd_keyboard_backspace) {
        deleteBackward()
    }.apply {
        // Holding backspace clears the whole field.
        setOnLongClickListener {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            target.text.clear()
            true
        }
    }

    private fun actionKey(
        label: String,
        weight: Float,
        description: Int,
        accent: Boolean = false,
        onClick: () -> Unit,
    ) = key(label, weight, special = true, accent = accent).apply {
        contentDescription = context.getString(description)
        setOnClickListener { tap(); onClick() }
    }

    private fun key(label: String, weight: Float, special: Boolean, accent: Boolean = false) = TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, if (special) 18f else 16f)
        setTextColor(ContextCompat.getColor(context, if (accent) R.color.background else R.color.score_text))
        val fill = when {
            accent -> ContextCompat.getColor(context, R.color.sets_text)
            special -> KEY_SPECIAL_COLOR
            else -> KEY_COLOR
        }
        styleAsKey(fill, weight)
    }

    // An icon rather than a "⌄" character, which the app font draws as a plain "v".
    private fun hideKey() = ImageView(context).apply {
        setImageResource(R.drawable.ic_expand_more)
        imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.score_text))
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        contentDescription = context.getString(R.string.cd_keyboard_hide)
        styleAsKey(KEY_SPECIAL_COLOR, weight = 1.5f)
        setOnClickListener { tap(); onHide() }
    }

    private fun View.styleAsKey(fill: Int, weight: Float) {
        val shape = GradientDrawable().apply {
            cornerRadius = context.dp(6).toFloat()
            setColor(fill)
        }
        background = RippleDrawable(ColorStateList.valueOf(KEY_RIPPLE_COLOR), shape, null)
        isClickable = true
        isFocusable = false
        layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, weight).apply {
            setMargins(keyGap, keyGap, keyGap, keyGap)
        }
    }

    private fun tap() = performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

    private fun type(char: Char) {
        val text = target.text
        val start = target.selectionStart.coerceAtLeast(0)
        val end = target.selectionEnd.coerceAtLeast(0)
        // Replacing through the Editable runs the field's input filters (length limit, title case).
        text.replace(minOf(start, end), maxOf(start, end), char.toString())
    }

    private fun deleteBackward() {
        val text = target.text
        val start = target.selectionStart.coerceAtLeast(0)
        val end = target.selectionEnd.coerceAtLeast(0)
        when {
            start != end -> text.delete(minOf(start, end), maxOf(start, end))
            start > 0 -> text.delete(start - 1, start)
        }
    }

    private companion object {
        val LETTER_ROWS = listOf("qwertyuiopå", "asdfghjklöä", "zxcvbnm")
        const val DIGIT_ROW = "1234567890"
        const val DIGITS_LABEL = "123"
        const val LETTERS_LABEL = "ABC"
        const val KEY_COLOR = 0xFF4A4F5C.toInt()
        const val KEY_SPECIAL_COLOR = 0xFF33363F.toInt()
        const val KEY_RIPPLE_COLOR = 0x55FFFFFF
    }
}
