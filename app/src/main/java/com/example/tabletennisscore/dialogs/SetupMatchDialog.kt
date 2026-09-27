package com.example.tabletennisscore.dialogs
import com.example.tabletennisscore.GameViewModel
import com.example.tabletennisscore.R

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.util.Locale

fun AppCompatActivity.confirmSetupMatch(viewModel: GameViewModel) {
    val state = viewModel.state.value ?: return
    if (!state.hasMatchStarted) {
        showSetupMatchDialog(viewModel)
        return
    }

    val dialog = AlertDialog.Builder(this)
        .setMessage(R.string.confirm_setup_match_message)
        .setPositiveButton(R.string.dialog_yes) { _, _ -> showSetupMatchDialog(viewModel) }
        .setNegativeButton(R.string.dialog_no, null)
        .create()
    dialog.setOnShowListener { styleDialogButtons(dialog) }
    dialog.show()
}

fun AppCompatActivity.showSetupMatchDialog(viewModel: GameViewModel) {
    val state = viewModel.state.value ?: return
    val form = SetupMatchForm(this, viewModel, state)

    val dialog = AlertDialog.Builder(this)
        .setView(ScrollView(this).apply { addView(form.buildContent()) })
        .setPositiveButton(R.string.dialog_done) { _, _ -> form.submit() }
        .setNegativeButton(R.string.dialog_cancel, null)
        .create()
    dialog.setOnShowListener { styleDialogButtons(dialog) }
    dialog.show()
    // Use (nearly) the full width of the landscape screen so the two columns fit side by side.
    dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.94f).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
}

/**
 * The content of the "Setup match" dialog, laid out compactly in two columns: the players on the
 * left, and match mode, best-of and round on the right. [submit] applies the chosen values.
 */
private class SetupMatchForm(
    private val activity: AppCompatActivity,
    private val viewModel: GameViewModel,
    private val state: GameViewModel.GameState,
) {
    private val editableNameGroup = viewModel.getPlayerNameGroup().toMutableList()

    private var selectedMode = when (state.matchMode) {
        GameViewModel.MATCH_MODE_DOUBLES -> GameViewModel.MATCH_MODE_DOUBLES
        else -> GameViewModel.MATCH_MODE_SINGLES
    }
    private var selectedBestOf = if (state.bestOfSets in BEST_OF_OPTIONS) state.bestOfSets else DEFAULT_BEST_OF
    private var selectedRound = state.matchRound.ifBlank { activity.getString(R.string.round_pool) }

    private val singlesPlayer1 = NameBox(state.player1Name, R.string.dialog_player1_name)
    private val singlesPlayer2 = NameBox(state.player2Name, R.string.dialog_player2_name)
    private val team1A = NameBox(state.team1PlayerA, R.string.dialog_team1_player_a_hint)
    private val team1B = NameBox(state.team1PlayerB, R.string.dialog_team1_player_b_hint)
    private val team2A = NameBox(state.team2PlayerA, R.string.dialog_team2_player_a_hint)
    private val team2B = NameBox(state.team2PlayerB, R.string.dialog_team2_player_b_hint)
    private val tournament = NameBox(state.tournamentName, R.string.tournament_name_hint, NamePickerKind.TOURNAMENT)

    private fun dp(value: Int) = activity.dp(value)

    fun buildContent(): View {
        val singlesSection = singlesSection()
        val doublesSection = doublesSection()
        fun showSectionForSelectedMode() {
            singlesSection.visibility = if (selectedMode == GameViewModel.MATCH_MODE_SINGLES) View.VISIBLE else View.GONE
            doublesSection.visibility = if (selectedMode == GameViewModel.MATCH_MODE_DOUBLES) View.VISIBLE else View.GONE
        }
        showSectionForSelectedMode()

        val playersColumn = column().apply {
            addView(singlesSection)
            addView(doublesSection)
        }
        val settingsColumn = column().apply {
            addView(modeRow(onChanged = ::showSectionForSelectedMode))
            addView(bestOfRow())
            addView(roundSection())
        }
        (settingsColumn.layoutParams as LinearLayout.LayoutParams).marginStart = dp(20)

        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), 0)
            addView(header())
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(playersColumn)
                addView(settingsColumn)
            })
        }
    }

    fun submit() {
        viewModel.setTournamentName(tournament.name)
        viewModel.setMatchRound(selectedRound)
        viewModel.setupMatch(
            player1Name = singlesPlayer1.name,
            player2Name = singlesPlayer2.name,
            firstServer = state.server,
            bestOfSets = selectedBestOf,
            matchMode = selectedMode,
            team1PlayerA = team1A.name,
            team1PlayerB = team1B.name,
            team2PlayerA = team2A.name,
            team2PlayerB = team2B.name,
        )
    }

    // ----- Sections -----

    /** Title on the left and the tournament name on the right, sharing one row to save height. */
    private fun header() = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 0, 0, dp(6))
        addView(TextView(activity).apply {
            text = activity.getString(R.string.dialog_setup_match)
            setTextColor(ContextCompat.getColor(activity, R.color.score_text))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setPadding(0, 0, dp(24), 0)
        })
        addView(sectionLabel(R.string.dialog_tournament).apply { setPadding(0, 0, dp(10), 0) })
        addView(tournament.view, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun singlesSection() = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        addView(sectionLabel(R.string.dialog_name_group))
        addView(caption(R.string.player1_default))
        addView(singlesPlayer1.view)
        addView(caption(R.string.player2_default))
        addView(singlesPlayer2.view)
    }

    /** Each team on one line (player A and B side by side), with the swap buttons below. */
    private fun doublesSection() = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        // Half-width boxes: slightly smaller text so longer names fit without being cut off.
        listOf(team1A, team1B, team2A, team2B).forEach { it.view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f) }
        addView(sectionLabel(R.string.dialog_doubles_team_names))
        addView(caption(R.string.dialog_team1))
        addView(sideBySide(team1A.view, team1B.view))
        addView(caption(R.string.dialog_team2))
        addView(sideBySide(team2A.view, team2B.view))
        addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(8), 0, 0)
            addView(smallButton(R.string.dialog_swap_partners) { swapPartners() })
            addView(smallButton(R.string.dialog_swap_teams) { swapTeams() }.apply {
                (layoutParams as LinearLayout.LayoutParams).marginStart = dp(8)
            })
        })
    }

    private fun modeRow(onChanged: () -> Unit): View {
        val modes = listOf(
            GameViewModel.MATCH_MODE_SINGLES to activity.getString(R.string.dialog_mode_singles),
            GameViewModel.MATCH_MODE_DOUBLES to activity.getString(R.string.dialog_mode_doubles),
        )
        val chips = choiceChips(modes, selectedMode) {
            selectedMode = it
            onChanged()
        }
        return labelledRow(R.string.dialog_match_mode, chips)
    }

    private fun bestOfRow(): View {
        val options = BEST_OF_OPTIONS.map { it to it.toString() }
        val chips = choiceChips(options, selectedBestOf) { selectedBestOf = it }
        return labelledRow(R.string.dialog_best_of_sets, chips)
    }

    private fun roundSection(): View {
        val rounds = listOf(
            R.string.round_pool,
            R.string.round_group,
            R.string.round_32,
            R.string.round_16,
            R.string.round_8,
            R.string.round_semi,
            R.string.round_final,
        ).map { activity.getString(it).let { name -> name to name } }
        val chips = choiceChips(rounds, selectedRound) { selectedRound = it }
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(sectionLabel(R.string.history_round_label))
            addView(chips)
        }
    }

    // ----- Building blocks -----

    private fun swapPartners() {
        val team1Partner = team1B.name
        team1B.name = team2B.name
        team2B.name = team1Partner
    }

    private fun swapTeams() {
        val (t1A, t1B) = team1A.name to team1B.name
        team1A.name = team2A.name
        team1B.name = team2B.name
        team2A.name = t1A
        team2B.name = t1B
    }

    private fun column() = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }

    private fun sideBySide(left: View, right: View) = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(right, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(8)
        })
    }

    private fun sectionLabel(textRes: Int) = TextView(activity).apply {
        text = activity.getString(textRes)
        setTypeface(typeface, Typeface.BOLD)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setPadding(0, dp(6), 0, dp(2))
    }

    private fun caption(textRes: Int) = TextView(activity).apply {
        text = activity.getString(textRes)
        setTextColor(ContextCompat.getColor(activity, R.color.history_loser_text))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setPadding(0, dp(4), 0, dp(2))
    }

    /** A label on the left and a group of choice chips on the right, on one line. */
    private fun labelledRow(labelRes: Int, chips: ChipGroup) = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(2), 0, dp(2))
        addView(sectionLabel(labelRes).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(chips)
    }

    private fun smallButton(labelRes: Int, onClick: () -> Unit): MaterialButton = activity.outlinedButton(
        activity.getString(labelRes),
        textSizeSp = 13f, heightDp = 32, horizontalPaddingDp = 14, verticalPaddingDp = 2,
        cornerRadiusDp = 18, topMarginDp = 0,
    ).apply { setOnClickListener { onClick() } }

    /** A single-selection group of compact outlined chips; [onSelected] gets the value of the chosen chip. */
    private fun <T : Any> choiceChips(
        options: List<Pair<T, String>>,
        initial: T,
        onSelected: (T) -> Unit,
    ): ChipGroup {
        val group = ChipGroup(activity).apply {
            isSingleSelection = true
            isSelectionRequired = true
            chipSpacingHorizontal = dp(6)
            chipSpacingVertical = dp(2)
        }
        var selected = initial
        fun refreshOutlines() {
            for (i in 0 until group.childCount) {
                val chip = group.getChildAt(i) as? Chip ?: continue
                activity.styleChoiceChip(chip, chip.tag == selected)
            }
        }
        options.forEach { (value, label) ->
            group.addView(choiceChip(label, value == selected).apply { tag = value })
        }
        refreshOutlines()
        group.setOnCheckedStateChangeListener { _, checkedIds ->
            val chip = checkedIds.firstOrNull()?.let { group.findViewById<Chip>(it) } ?: return@setOnCheckedStateChangeListener
            @Suppress("UNCHECKED_CAST")
            selected = chip.tag as T
            refreshOutlines()
            onSelected(selected)
        }
        return group
    }

    private fun choiceChip(label: String, selected: Boolean): Chip = Chip(activity).apply {
        id = View.generateViewId()
        text = label
        isCheckable = true
        isChecked = selected
        isClickable = true
        isAllCaps = false
        isCheckedIconVisible = false
        setEnsureMinTouchTargetSize(false)
        minWidth = dp(44)
        chipMinHeight = dp(34).toFloat()
    }

    // ----- Player and tournament names, picked from their saved lists -----

    /**
     * A compact, tappable box showing a player's (or, for [kind] TOURNAMENT, the tournament's) name.
     * Tapping it opens the name picker, where a name can be chosen, searched for, added or removed.
     */
    private inner class NameBox(
        initial: String,
        private val placeholderRes: Int,
        private val kind: NamePickerKind = NamePickerKind.PLAYER,
    ) {
        var name: String = initial
            set(value) {
                field = value
                render()
            }

        val view: TextView = TextView(activity).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            minHeight = dp(40)
            setPadding(dp(12), dp(4), dp(8), dp(4))
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), ContextCompat.getColor(activity, R.color.history_loser_text))
            }
            setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_expand_more, 0)
            compoundDrawableTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(activity, R.color.player_name),
            )
            isClickable = true
            isFocusable = true
            setOnClickListener {
                when (kind) {
                    NamePickerKind.PLAYER -> activity.showPlayerNamePickerDialog(
                        editableNameGroup, name,
                        onAdded = ::addToNameGroup,
                        onRemoved = ::removeFromNameGroup,
                    ) { picked -> name = picked }
                    NamePickerKind.TOURNAMENT -> activity.showTournamentPickerDialog(
                        viewModel.getTournamentNameGroup(), name,
                        onAdded = viewModel::addTournamentNameToGroup,
                        onRemoved = viewModel::removeTournamentNameFromGroup,
                    ) { picked -> name = picked }
                }
            }
        }

        init {
            render()
        }

        private fun render() {
            val isEmpty = name.isBlank()
            view.text = if (isEmpty) activity.getString(placeholderRes) else name
            view.setTextColor(
                ContextCompat.getColor(activity, if (isEmpty) R.color.history_loser_text else R.color.score_text),
            )
        }
    }

    private fun addToNameGroup(name: String) {
        viewModel.addPlayerNameToGroup(name)
        if (editableNameGroup.none { it.equals(name, ignoreCase = true) }) {
            editableNameGroup.add(name)
            editableNameGroup.sortBy { it.lowercase(Locale.ROOT) }
        }
    }

    private fun removeFromNameGroup(name: String) {
        viewModel.removePlayerNameFromGroup(name)
        editableNameGroup.removeAll { it.equals(name, ignoreCase = true) }
    }

    private companion object {
        val BEST_OF_OPTIONS = listOf(1, 3, 5, 7)
        const val DEFAULT_BEST_OF = 5
    }
}
