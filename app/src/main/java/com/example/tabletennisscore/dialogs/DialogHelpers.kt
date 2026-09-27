package com.example.tabletennisscore.dialogs
import com.example.tabletennisscore.R

import android.text.InputFilter
import android.text.Spanned
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

fun AppCompatActivity.styleDialogButtons(dialog: AlertDialog) {
    dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        ?.setTextColor(ContextCompat.getColor(this, R.color.score_text))
    dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        ?.setTextColor(ContextCompat.getColor(this, R.color.player_name))
    dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
        ?.setTextColor(ContextCompat.getColor(this, R.color.player_name))
}

/**
 * Input filter that ensures words are capitalized.
 * Useful when the keyboard ignores standard capitalization flags.
 */
class TitleCaseInputFilter : InputFilter {
    override fun filter(
        source: CharSequence, start: Int, end: Int,
        dest: Spanned, dstart: Int, dend: Int
    ): CharSequence? {
        if (source.isEmpty()) return null

        val result = StringBuilder()
        for (i in start until end) {
            val char = source[i]
            val isFirstChar = (dstart + i - start) == 0
            val isAfterSpace = !isFirstChar && (if (i > start) source[i - 1] == ' ' else dest[dstart + i - start - 1] == ' ')

            if (isFirstChar || isAfterSpace) {
                result.append(char.uppercaseChar())
            } else {
                result.append(char)
            }
        }
        return if (result.toString() == source.subSequence(start, end).toString()) null else result
    }
}
