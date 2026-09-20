package com.example.tabletennisscore.dialogs
import com.example.tabletennisscore.normalizeTitleCaseWords
import com.example.tabletennisscore.GameViewModel
import com.example.tabletennisscore.R

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputFilter
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
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
        val editableNameGroup = viewModel.getPlayerNameGroup().toMutableList()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val horizontalPadding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                10f,
                resources.displayMetrics,
            ).toInt()
            val topPadding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                8f,
                resources.displayMetrics,
            ).toInt()
            setPadding(horizontalPadding, topPadding, horizontalPadding, 0)
        }
        val density = resources.displayMetrics.density
        fun px(dp: Int): Int = (dp * density).toInt()
        val setupNameInputs = mutableListOf<AutoCompleteTextView>()
        val setupGroupButtons = mutableListOf<MaterialButton>()
        fun bindNameGroup(input: AutoCompleteTextView) {
            if (editableNameGroup.isNotEmpty()) {
                input.setAdapter(ArrayAdapter(this@showSetupMatchDialog, android.R.layout.simple_dropdown_item_1line, editableNameGroup))
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
        fun refreshSetupGroupButtons() {
            val hasNames = editableNameGroup.isNotEmpty()
            setupGroupButtons.forEach { button ->
                button.isEnabled = hasNames
                button.alpha = if (hasNames) 1f else 0.45f
            }
        }
        fun createSelectFromGroupButton(targetInput: AutoCompleteTextView): MaterialButton {
            return MaterialButton(this).apply {
                text = getString(R.string.dialog_select_from_group)
                isAllCaps = false
                insetTop = 0
                insetBottom = 0
                minimumHeight = px(30)
                minHeight = px(30)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(px(14), px(2), px(14), px(2))
                setTextColor(ContextCompat.getColor(this@showSetupMatchDialog, R.color.player_name))
                strokeWidth = px(1)
                strokeColor = ColorStateList.valueOf(ContextCompat.getColor(this@showSetupMatchDialog, R.color.history_loser_text))
                backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                cornerRadius = px(18)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    topMargin = px(4)
                    bottomMargin = px(8)
                }
                setOnClickListener {
                    showPlayerNamePickerDialog(editableNameGroup, targetInput.text.toString()) { selected ->
                        targetInput.setText(selected)
                        targetInput.setSelection(targetInput.text.length)
                    }
                }
                setupGroupButtons.add(this)
            }
        }
        fun createNameInput(hintRes: Int, initial: String): AutoCompleteTextView {
            return AutoCompleteTextView(this).apply {
                hint = getString(hintRes)
                setText(initial)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
                filters = arrayOf(InputFilter.LengthFilter(GameViewModel.MAX_PLAYER_NAME_LENGTH), TitleCaseInputFilter())
                maxLines = 1
                bindNameGroup(this)
                setupNameInputs.add(this)
            }
        }
        fun choiceChip(label: String, selected: Boolean, allowTwoLines: Boolean = false): Chip {
            return Chip(this).apply {
                id = View.generateViewId()
                text = label
                isCheckable = true
                isChecked = selected
                isClickable = true
                isAllCaps = false
                isCheckedIconVisible = false
                if (allowTwoLines) {
                    // Material Chip does not support multiline text; keep a wider single-line pill.
                    minWidth = px(84)
                    chipMinHeight = px(36).toFloat()
                } else {
                    minWidth = px(52)
                    chipMinHeight = px(34).toFloat()
                }
            }
        }

        val editNameGroupButton = MaterialButton(this).apply {
            text = getString(R.string.dialog_edit_player_names)
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            minimumHeight = px(34)
            minHeight = px(34)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(px(18), px(4), px(18), px(4))
            setTextColor(ContextCompat.getColor(this@showSetupMatchDialog, R.color.player_name))
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            strokeWidth = px(1)
            strokeColor = ColorStateList.valueOf(ContextCompat.getColor(this@showSetupMatchDialog, R.color.history_loser_text))
            cornerRadius = px(20)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = px(6)
            }
            setOnClickListener {
                showNameGroupEditorDialog(editableNameGroup) { updatedNames ->
                    editableNameGroup.clear()
                    editableNameGroup.addAll(updatedNames)
                    viewModel.setPlayerNameGroup(updatedNames)
                    setupNameInputs.forEach { bindNameGroup(it) }
                    refreshSetupGroupButtons()
                }
            }
        }

        content.addView(editNameGroupButton)

        val modeSection = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(12), px(12), px(12), px(12))
        }
        val modeLabel = androidx.appcompat.widget.AppCompatTextView(this).apply {
            text = getString(R.string.dialog_match_mode)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(0, 0, 12, 0)
        }
        val modeGroup = ChipGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
            chipSpacingHorizontal = px(8)
            chipSpacingVertical = px(8)
        }
        var selectedMode = when (state.matchMode) {
            GameViewModel.MATCH_MODE_DOUBLES -> GameViewModel.MATCH_MODE_DOUBLES
            else -> GameViewModel.MATCH_MODE_SINGLES
        }
        var onModeChanged: (() -> Unit)? = null
        fun refreshModeOutline() {
            for (i in 0 until modeGroup.childCount) {
                val chip = modeGroup.getChildAt(i) as? Chip ?: continue
                val isSelected = (chip.tag as? String) == selectedMode
                chip.chipBackgroundColor = ColorStateList.valueOf(Color.TRANSPARENT)
                chip.chipStrokeWidth = if (isSelected) px(2).toFloat() else px(1).toFloat()
                chip.chipStrokeColor = ColorStateList.valueOf(
                    ContextCompat.getColor(this@showSetupMatchDialog, if (isSelected) R.color.score_text else R.color.history_loser_text),
                )
                chip.setTextColor(
                    ContextCompat.getColor(this@showSetupMatchDialog, if (isSelected) R.color.score_text else R.color.player_name),
                )
            }
        }
        listOf(
            GameViewModel.MATCH_MODE_SINGLES to getString(R.string.dialog_mode_singles),
            GameViewModel.MATCH_MODE_DOUBLES to getString(R.string.dialog_mode_doubles),
        ).forEach { (modeValue, modeText) ->
            modeGroup.addView(
                choiceChip(modeText, modeValue == selectedMode).apply {
                    tag = modeValue
                }
            )
        }
        refreshModeOutline()
        modeGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            val selectedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            selectedMode = (group.findViewById<Chip>(selectedId).tag as? String) ?: selectedMode
            refreshModeOutline()
            onModeChanged?.invoke()
        }

        modeSection.addView(modeLabel)
        modeSection.addView(modeGroup)
        val modeCard = MaterialCardView(this).apply {
            setCardBackgroundColor(Color.TRANSPARENT)
            strokeWidth = px(1)
            strokeColor = ContextCompat.getColor(this@showSetupMatchDialog, R.color.player_name)
            radius = px(8).toFloat()
            useCompatPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = px(14) }
            addView(modeSection)
        }
        content.addView(modeCard)

        val singlesSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(12), px(12), px(12), px(12))
        }
        val singlesLabel = TextView(this).apply {
            text = getString(R.string.dialog_name_group)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, px(8))
        }
        val singlesPlayer1Input = createNameInput(R.string.dialog_player1_name, state.player1Name)
        val singlesPlayer1GroupButton = createSelectFromGroupButton(singlesPlayer1Input)
        val singlesPlayer2Input = createNameInput(R.string.dialog_player2_name, state.player2Name)
        val singlesPlayer2GroupButton = createSelectFromGroupButton(singlesPlayer2Input)
        singlesSection.addView(singlesLabel)
        singlesSection.addView(singlesPlayer1Input)
        singlesSection.addView(singlesPlayer1GroupButton)
        singlesSection.addView(singlesPlayer2Input)
        singlesSection.addView(singlesPlayer2GroupButton)

        val singlesCard = MaterialCardView(this).apply {
            setCardBackgroundColor(Color.TRANSPARENT)
            strokeWidth = px(1)
            strokeColor = ContextCompat.getColor(this@showSetupMatchDialog, R.color.player_name)
            radius = px(8).toFloat()
            useCompatPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = px(10) }
            addView(singlesSection)
            visibility = if (selectedMode == GameViewModel.MATCH_MODE_SINGLES) View.VISIBLE else View.GONE
        }
        content.addView(singlesCard)

        val doublesSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(12), px(12), px(12), px(12))
        }
        val doublesLabel = TextView(this).apply {
            text = getString(R.string.dialog_doubles_team_names)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, px(8))
        }
        val team1AInput = createNameInput(R.string.dialog_team1_player_a_hint, state.team1PlayerA)
        val team1AGroupButton = createSelectFromGroupButton(team1AInput)
        val team1BInput = createNameInput(R.string.dialog_team1_player_b_hint, state.team1PlayerB)
        val team1BGroupButton = createSelectFromGroupButton(team1BInput)
        val team2AInput = createNameInput(R.string.dialog_team2_player_a_hint, state.team2PlayerA)
        val team2AGroupButton = createSelectFromGroupButton(team2AInput)
        val team2BInput = createNameInput(R.string.dialog_team2_player_b_hint, state.team2PlayerB)
        val team2BGroupButton = createSelectFromGroupButton(team2BInput)
        val swapPartnersButton = MaterialButton(this).apply {
            text = getString(R.string.dialog_swap_partners)
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            minimumHeight = px(34)
            minHeight = px(34)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(px(16), px(4), px(16), px(4))
            setTextColor(ContextCompat.getColor(this@showSetupMatchDialog, R.color.player_name))
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            strokeWidth = px(1)
            strokeColor = ColorStateList.valueOf(ContextCompat.getColor(this@showSetupMatchDialog, R.color.history_loser_text))
            cornerRadius = px(20)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = px(8)
            }
            setOnClickListener {
                val team1Partner = team1BInput.text?.toString().orEmpty()
                val team2Partner = team2BInput.text?.toString().orEmpty()
                team1BInput.setText(team2Partner)
                team1BInput.setSelection(team1BInput.text.length)
                team2BInput.setText(team1Partner)
                team2BInput.setSelection(team2BInput.text.length)
            }
        }
        val swapTeamsButton = MaterialButton(this).apply {
            text = getString(R.string.dialog_swap_teams)
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            minimumHeight = px(34)
            minHeight = px(34)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(px(16), px(4), px(16), px(4))
            setTextColor(ContextCompat.getColor(this@showSetupMatchDialog, R.color.player_name))
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            strokeWidth = px(1)
            strokeColor = ColorStateList.valueOf(ContextCompat.getColor(this@showSetupMatchDialog, R.color.history_loser_text))
            cornerRadius = px(20)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = px(8)
            }
            setOnClickListener {
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
        }
        doublesSection.addView(doublesLabel)
        doublesSection.addView(team1AInput)
        doublesSection.addView(team1AGroupButton)
        doublesSection.addView(team1BInput)
        doublesSection.addView(team1BGroupButton)
        doublesSection.addView(team2AInput)
        doublesSection.addView(team2AGroupButton)
        doublesSection.addView(team2BInput)
        doublesSection.addView(team2BGroupButton)
        doublesSection.addView(swapPartnersButton)
        doublesSection.addView(swapTeamsButton)

        val doublesCard = MaterialCardView(this).apply {
            setCardBackgroundColor(Color.TRANSPARENT)
            strokeWidth = px(1)
            strokeColor = ContextCompat.getColor(this@showSetupMatchDialog, R.color.player_name)
            radius = px(8).toFloat()
            useCompatPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = px(10) }
            addView(doublesSection)
            visibility = if (selectedMode == GameViewModel.MATCH_MODE_DOUBLES) View.VISIBLE else View.GONE
        }
        content.addView(doublesCard)

        onModeChanged = {
            singlesCard.visibility = if (selectedMode == GameViewModel.MATCH_MODE_SINGLES) View.VISIBLE else View.GONE
            doublesCard.visibility = if (selectedMode == GameViewModel.MATCH_MODE_DOUBLES) View.VISIBLE else View.GONE
        }
        refreshSetupGroupButtons()

        val bestOfSection = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(12), px(12), px(12), px(12))
        }
        val bestOfLabel = androidx.appcompat.widget.AppCompatTextView(this).apply {
            text = getString(R.string.dialog_best_of_sets)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(0, 0, 12, 0)
        }
        val bestOfGroup = ChipGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
            chipSpacingHorizontal = px(8)
            chipSpacingVertical = px(8)
        }
        var selectedBestOf = if (state.bestOfSets in setOf(1, 3, 5, 7)) state.bestOfSets else 5
        fun refreshBestOfOutline() {
            for (i in 0 until bestOfGroup.childCount) {
                val chip = bestOfGroup.getChildAt(i) as? Chip ?: continue
                val isSelected = (chip.tag as? Int) == selectedBestOf
                chip.chipBackgroundColor = ColorStateList.valueOf(Color.TRANSPARENT)
                chip.chipStrokeWidth = if (isSelected) px(2).toFloat() else px(1).toFloat()
                chip.chipStrokeColor = ColorStateList.valueOf(
                    ContextCompat.getColor(this@showSetupMatchDialog, if (isSelected) R.color.score_text else R.color.history_loser_text),
                )
                chip.setTextColor(
                    ContextCompat.getColor(this@showSetupMatchDialog, if (isSelected) R.color.score_text else R.color.player_name),
                )
            }
        }
        listOf(1, 3, 5, 7).forEach { bestOf ->
            bestOfGroup.addView(
                choiceChip(bestOf.toString(), bestOf == selectedBestOf).apply {
                    tag = bestOf
                }
            )
        }
        refreshBestOfOutline()
        bestOfGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            val selectedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            selectedBestOf = (group.findViewById<Chip>(selectedId).tag as? Int) ?: selectedBestOf
            refreshBestOfOutline()
        }

        bestOfSection.addView(bestOfLabel)
        bestOfSection.addView(bestOfGroup)
        val bestOfCard = MaterialCardView(this).apply {
            setCardBackgroundColor(Color.TRANSPARENT)
            strokeWidth = px(1)
            strokeColor = ContextCompat.getColor(this@showSetupMatchDialog, R.color.player_name)
            radius = px(8).toFloat()
            useCompatPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = px(14) }
            addView(bestOfSection)
        }
        content.addView(bestOfCard)

        // Round Selection
        val roundSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(12), px(12), px(12), px(12))
        }
        val roundLabel = TextView(this).apply {
            text = getString(R.string.history_round_label)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, px(8))
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
        
        val roundGroup = ChipGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
            chipSpacingHorizontal = px(8)
            chipSpacingVertical = px(8)
            setPadding(0, 6, 0, 8)
        }

        var selectedRound = if (state.matchRound.isNotBlank()) state.matchRound else getString(R.string.round_pool)
        fun refreshRoundOutline() {
            for (i in 0 until roundGroup.childCount) {
                val chip = roundGroup.getChildAt(i) as? Chip ?: continue
                val isSelected = (chip.tag as? String) == selectedRound
                chip.chipBackgroundColor = ColorStateList.valueOf(Color.TRANSPARENT)
                chip.chipStrokeWidth = if (isSelected) px(2).toFloat() else px(1).toFloat()
                chip.chipStrokeColor = ColorStateList.valueOf(
                    ContextCompat.getColor(this@showSetupMatchDialog, if (isSelected) R.color.score_text else R.color.history_loser_text),
                )
                chip.setTextColor(
                    ContextCompat.getColor(this@showSetupMatchDialog, if (isSelected) R.color.score_text else R.color.player_name),
                )
            }
        }
        rounds.forEach { round ->
            roundGroup.addView(
                choiceChip(round, round == selectedRound, allowTwoLines = true).apply {
                    tag = round
                }
            )
        }
        refreshRoundOutline()
        roundGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            val selectedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            selectedRound = (group.findViewById<Chip>(selectedId).tag as? String) ?: selectedRound
            refreshRoundOutline()
        }

        roundSection.addView(roundLabel)
        roundSection.addView(roundGroup)
        val roundCard = MaterialCardView(this).apply {
            setCardBackgroundColor(Color.TRANSPARENT)
            strokeWidth = px(1)
            strokeColor = ContextCompat.getColor(this@showSetupMatchDialog, R.color.player_name)
            radius = px(8).toFloat()
            useCompatPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = px(10) }
            addView(roundSection)
        }
        content.addView(roundCard)

        val scrollContent = ScrollView(this).apply {
            addView(content)
        }

        val setupDialog = AlertDialog.Builder(this)
            .setTitle(R.string.dialog_setup_match)
            .setView(scrollContent)
            .setPositiveButton(R.string.dialog_done) { _, _ ->
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
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()
        setupDialog.setOnShowListener {
            styleDialogButtons(setupDialog)
        }
        setupDialog.show()
    }

    fun AppCompatActivity.showNameGroupEditorDialog(existingNames: List<String>, onSave: (List<String>) -> Unit) {
        val density = resources.displayMetrics.density
        fun px(dp: Int): Int = (dp * density).toInt()

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
