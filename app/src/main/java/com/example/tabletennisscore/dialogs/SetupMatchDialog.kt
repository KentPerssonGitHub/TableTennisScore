package com.example.tabletennisscore.dialogs
import com.example.tabletennisscore.GameViewModel
import com.example.tabletennisscore.R

import android.graphics.Typeface
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

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
        .setTitle(R.string.dialog_setup_match)
        .setView(ScrollView(this).apply { addView(form.buildContent()) })
        .setPositiveButton(R.string.dialog_done) { _, _ -> form.submit() }
        .setNegativeButton(R.string.dialog_cancel, null)
        .create()
    dialog.setOnShowListener { styleDialogButtons(dialog) }
    dialog.show()
}

/**
 * The content of the "Setup match" dialog. [buildContent] stacks one card per section
 * (mode, singles names, doubles names, best-of, round) and [submit] applies the chosen values.
 */
private class SetupMatchForm(
    private val activity: AppCompatActivity,
    private val viewModel: GameViewModel,
    private val state: GameViewModel.GameState,
) {
    private val editableNameGroup = viewModel.getPlayerNameGroup().toMutableList()
    private val nameInputs = mutableListOf<AutoCompleteTextView>()
    private val groupButtons = mutableListOf<MaterialButton>()

    private var selectedMode = when (state.matchMode) {
        GameViewModel.MATCH_MODE_DOUBLES -> GameViewModel.MATCH_MODE_DOUBLES
        else -> GameViewModel.MATCH_MODE_SINGLES
    }
    private var selectedBestOf = if (state.bestOfSets in BEST_OF_OPTIONS) state.bestOfSets else DEFAULT_BEST_OF
    private var selectedRound = state.matchRound.ifBlank { activity.getString(R.string.round_pool) }

    private lateinit var singlesPlayer1Input: AutoCompleteTextView
    private lateinit var singlesPlayer2Input: AutoCompleteTextView
    private lateinit var team1AInput: AutoCompleteTextView
    private lateinit var team1BInput: AutoCompleteTextView
    private lateinit var team2AInput: AutoCompleteTextView
    private lateinit var team2BInput: AutoCompleteTextView

    private fun dp(value: Int) = activity.dp(value)

    fun buildContent(): View {
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), 0)
        }
        content.addView(editNameGroupButton())

        val singlesCard = singlesCard()
        val doublesCard = doublesCard()
        fun showCardForSelectedMode() {
            singlesCard.visibility = if (selectedMode == GameViewModel.MATCH_MODE_SINGLES) View.VISIBLE else View.GONE
            doublesCard.visibility = if (selectedMode == GameViewModel.MATCH_MODE_DOUBLES) View.VISIBLE else View.GONE
        }
        showCardForSelectedMode()

        content.addView(modeCard(onChanged = ::showCardForSelectedMode))
        content.addView(singlesCard)
        content.addView(doublesCard)
        content.addView(bestOfCard())
        content.addView(roundCard())
        refreshGroupButtons()
        return content
    }

    fun submit() {
        viewModel.setMatchRound(selectedRound)
        viewModel.setupMatch(
            player1Name = singlesPlayer1Input.text.toString(),
            player2Name = singlesPlayer2Input.text.toString(),
            firstServer = state.server,
            bestOfSets = selectedBestOf,
            matchMode = selectedMode,
            team1PlayerA = team1AInput.text.toString(),
            team1PlayerB = team1BInput.text.toString(),
            team2PlayerA = team2AInput.text.toString(),
            team2PlayerB = team2BInput.text.toString(),
        )
    }

    // ----- Sections -----

    private fun editNameGroupButton(): MaterialButton = activity.outlinedButton(
        activity.getString(R.string.dialog_edit_player_names),
        textSizeSp = 14f, heightDp = 34, horizontalPaddingDp = 18, verticalPaddingDp = 4,
        cornerRadiusDp = 20, topMarginDp = 6,
    ).apply {
        setOnClickListener {
            activity.showNameGroupEditorDialog(editableNameGroup) { updatedNames ->
                editableNameGroup.clear()
                editableNameGroup.addAll(updatedNames)
                viewModel.setPlayerNameGroup(updatedNames)
                nameInputs.forEach { bindNameGroup(it) }
                refreshGroupButtons()
            }
        }
    }

    private fun modeCard(onChanged: () -> Unit): MaterialCardView {
        val modes = listOf(
            GameViewModel.MATCH_MODE_SINGLES to activity.getString(R.string.dialog_mode_singles),
            GameViewModel.MATCH_MODE_DOUBLES to activity.getString(R.string.dialog_mode_doubles),
        )
        val chips = choiceChips(modes, selectedMode) {
            selectedMode = it
            onChanged()
        }
        return labelledChoiceCard(R.string.dialog_match_mode, chips, topMarginDp = 14)
    }

    private fun singlesCard(): MaterialCardView {
        singlesPlayer1Input = nameInput(R.string.dialog_player1_name, state.player1Name)
        singlesPlayer2Input = nameInput(R.string.dialog_player2_name, state.player2Name)
        val section = verticalSection(R.string.dialog_name_group).apply {
            addView(singlesPlayer1Input)
            addView(selectFromGroupButton(singlesPlayer1Input))
            addView(singlesPlayer2Input)
            addView(selectFromGroupButton(singlesPlayer2Input))
        }
        return activity.outlinedCard(topMarginDp = 10, content = section)
    }

    private fun doublesCard(): MaterialCardView {
        team1AInput = nameInput(R.string.dialog_team1_player_a_hint, state.team1PlayerA)
        team1BInput = nameInput(R.string.dialog_team1_player_b_hint, state.team1PlayerB)
        team2AInput = nameInput(R.string.dialog_team2_player_a_hint, state.team2PlayerA)
        team2BInput = nameInput(R.string.dialog_team2_player_b_hint, state.team2PlayerB)
        val section = verticalSection(R.string.dialog_doubles_team_names).apply {
            listOf(team1AInput, team1BInput, team2AInput, team2BInput).forEach { input ->
                addView(input)
                addView(selectFromGroupButton(input))
            }
            addView(swapPartnersButton())
            addView(swapTeamsButton())
        }
        return activity.outlinedCard(topMarginDp = 10, content = section)
    }

    private fun bestOfCard(): MaterialCardView {
        val options = BEST_OF_OPTIONS.map { it to it.toString() }
        val chips = choiceChips(options, selectedBestOf) { selectedBestOf = it }
        return labelledChoiceCard(R.string.dialog_best_of_sets, chips, topMarginDp = 14)
    }

    private fun roundCard(): MaterialCardView {
        val rounds = listOf(
            R.string.round_pool,
            R.string.round_group,
            R.string.round_32,
            R.string.round_16,
            R.string.round_8,
            R.string.round_semi,
            R.string.round_final,
        ).map { activity.getString(it).let { name -> name to name } }
        val chips = choiceChips(rounds, selectedRound, wideChips = true) { selectedRound = it }
        chips.setPadding(0, 6, 0, 8)
        val section = verticalSection(R.string.history_round_label).apply { addView(chips) }
        return activity.outlinedCard(topMarginDp = 10, content = section)
    }

    // ----- Building blocks -----

    private fun swapPartnersButton(): MaterialButton = swapButton(R.string.dialog_swap_partners) {
        val team1Partner = team1BInput.text?.toString().orEmpty()
        val team2Partner = team2BInput.text?.toString().orEmpty()
        team1BInput.setText(team2Partner)
        team1BInput.setSelection(team1BInput.text.length)
        team2BInput.setText(team1Partner)
        team2BInput.setSelection(team2BInput.text.length)
    }

    private fun swapTeamsButton(): MaterialButton = swapButton(R.string.dialog_swap_teams) {
        val t1A = team1AInput.text?.toString().orEmpty()
        val t1B = team1BInput.text?.toString().orEmpty()
        val t2A = team2AInput.text?.toString().orEmpty()
        val t2B = team2BInput.text?.toString().orEmpty()

        team1AInput.setText(t2A)
        team1BInput.setText(t2B)
        team2AInput.setText(t1A)
        team2BInput.setText(t1B)

        team2BInput.setSelection(team2BInput.text.length)
    }

    private fun swapButton(labelRes: Int, onClick: () -> Unit): MaterialButton = activity.outlinedButton(
        activity.getString(labelRes),
        textSizeSp = 13f, heightDp = 34, horizontalPaddingDp = 16, verticalPaddingDp = 4,
        cornerRadiusDp = 20, topMarginDp = 8,
    ).apply { setOnClickListener { onClick() } }

    private fun verticalSection(titleRes: Int): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(12), dp(12), dp(12))
        addView(
            TextView(activity).apply {
                text = activity.getString(titleRes)
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, 0, 0, dp(8))
            },
        )
    }

    /** A card with a label on the left and a group of choice chips on the right. */
    private fun labelledChoiceCard(labelRes: Int, chips: ChipGroup, topMarginDp: Int): MaterialCardView {
        val section = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            addView(
                AppCompatTextView(activity).apply {
                    text = activity.getString(labelRes)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setPadding(0, 0, 12, 0)
                },
            )
            addView(chips)
        }
        return activity.outlinedCard(topMarginDp, section)
    }

    /** A single-selection group of outlined chips; [onSelected] gets the value of the chosen chip. */
    private fun <T : Any> choiceChips(
        options: List<Pair<T, String>>,
        initial: T,
        wideChips: Boolean = false,
        onSelected: (T) -> Unit,
    ): ChipGroup {
        val group = ChipGroup(activity).apply {
            isSingleSelection = true
            isSelectionRequired = true
            chipSpacingHorizontal = dp(8)
            chipSpacingVertical = dp(8)
        }
        var selected = initial
        fun refreshOutlines() {
            for (i in 0 until group.childCount) {
                val chip = group.getChildAt(i) as? Chip ?: continue
                activity.styleChoiceChip(chip, chip.tag == selected)
            }
        }
        options.forEach { (value, label) ->
            group.addView(choiceChip(label, value == selected, wideChips).apply { tag = value })
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

    private fun choiceChip(label: String, selected: Boolean, wide: Boolean): Chip = Chip(activity).apply {
        id = View.generateViewId()
        text = label
        isCheckable = true
        isChecked = selected
        isClickable = true
        isAllCaps = false
        isCheckedIconVisible = false
        if (wide) {
            // Material Chip does not support multiline text; keep a wider single-line pill.
            minWidth = dp(84)
            chipMinHeight = dp(36).toFloat()
        } else {
            minWidth = dp(52)
            chipMinHeight = dp(34).toFloat()
        }
    }

    // ----- Player-name inputs backed by the saved name group -----

    private fun nameInput(hintRes: Int, initial: String): AutoCompleteTextView =
        AutoCompleteTextView(activity).apply {
            hint = activity.getString(hintRes)
            setText(initial)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            filters = arrayOf(InputFilter.LengthFilter(GameViewModel.MAX_PLAYER_NAME_LENGTH), TitleCaseInputFilter())
            maxLines = 1
            bindNameGroup(this)
            nameInputs.add(this)
        }

    private fun bindNameGroup(input: AutoCompleteTextView) {
        if (editableNameGroup.isNotEmpty()) {
            input.setAdapter(ArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, editableNameGroup))
            input.threshold = 0
            input.setOnClickListener { input.showDropDown() }
            input.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) input.showDropDown()
            }
        } else {
            input.setAdapter(null)
            input.setOnClickListener(null)
            input.setOnFocusChangeListener(null)
        }
    }

    private fun selectFromGroupButton(targetInput: AutoCompleteTextView): MaterialButton =
        activity.outlinedButton(activity.getString(R.string.dialog_select_from_group), bottomMarginDp = 8).apply {
            setOnClickListener {
                activity.showPlayerNamePickerDialog(editableNameGroup, targetInput.text.toString()) { selected ->
                    targetInput.setText(selected)
                    targetInput.setSelection(targetInput.text.length)
                }
            }
            groupButtons.add(this)
        }

    private fun refreshGroupButtons() {
        val hasNames = editableNameGroup.isNotEmpty()
        groupButtons.forEach { button ->
            button.isEnabled = hasNames
            button.alpha = if (hasNames) 1f else 0.45f
        }
    }

    private companion object {
        val BEST_OF_OPTIONS = listOf(1, 3, 5, 7)
        const val DEFAULT_BEST_OF = 5
    }
}
