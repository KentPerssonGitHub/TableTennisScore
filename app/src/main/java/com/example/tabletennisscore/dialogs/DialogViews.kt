package com.example.tabletennisscore.dialogs

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.example.tabletennisscore.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip

/** Converts density-independent pixels to pixels for this context. */
fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

/**
 * Transparent, outlined, centered button used for the small actions inside dialogs.
 * Defaults give the compact "select from group" look; pass bigger values for the larger variants.
 */
fun Context.outlinedButton(
    label: String,
    textSizeSp: Float = 12f,
    heightDp: Int = 30,
    horizontalPaddingDp: Int = 14,
    verticalPaddingDp: Int = 2,
    cornerRadiusDp: Int = 18,
    topMarginDp: Int = 4,
    bottomMarginDp: Int = 0,
): MaterialButton = MaterialButton(this).apply {
    text = label
    isAllCaps = false
    insetTop = 0
    insetBottom = 0
    minimumHeight = dp(heightDp)
    minHeight = dp(heightDp)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
    setPadding(dp(horizontalPaddingDp), dp(verticalPaddingDp), dp(horizontalPaddingDp), dp(verticalPaddingDp))
    setTextColor(ContextCompat.getColor(context, R.color.player_name))
    backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
    strokeWidth = dp(1)
    strokeColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.history_loser_text))
    cornerRadius = dp(cornerRadiusDp)
    layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply {
        gravity = Gravity.CENTER_HORIZONTAL
        topMargin = dp(topMarginDp)
        bottomMargin = dp(bottomMarginDp)
    }
}

/** Full-width, transparent card with a thin outline that wraps one dialog section. */
fun Context.outlinedCard(topMarginDp: Int, content: View): MaterialCardView = MaterialCardView(this).apply {
    setCardBackgroundColor(Color.TRANSPARENT)
    strokeWidth = dp(1)
    strokeColor = ContextCompat.getColor(context, R.color.player_name)
    radius = dp(8).toFloat()
    useCompatPadding = false
    layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = dp(topMarginDp) }
    addView(content)
}

/** Outline-only chip styling: a thicker, brighter outline marks the selected choice. */
fun Context.styleChoiceChip(chip: Chip, isSelected: Boolean) {
    chip.chipBackgroundColor = ColorStateList.valueOf(Color.TRANSPARENT)
    chip.chipStrokeWidth = dp(if (isSelected) 2 else 1).toFloat()
    chip.chipStrokeColor = ColorStateList.valueOf(
        ContextCompat.getColor(this, if (isSelected) R.color.score_text else R.color.history_loser_text),
    )
    chip.setTextColor(ContextCompat.getColor(this, if (isSelected) R.color.score_text else R.color.player_name))
}
