package com.example.tabletennisscore

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.graphics.Color
import android.text.InputFilter
import android.text.InputType
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.text.TextUtils
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.tabletennisscore.data.MatchDatabase
import com.example.tabletennisscore.data.MatchResult
import com.example.tabletennisscore.databinding.ActivityHistoryBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private val dao by lazy { MatchDatabase.getInstance(this).matchResultDao() }
    private val appPrefs by lazy { getSharedPreferences("table_tennis_prefs", MODE_PRIVATE) }
    private val backupPrefs by lazy { getSharedPreferences("history_backup_prefs", MODE_PRIVATE) }
    private val lastBackupDisplayFormat = SimpleDateFormat("dd MMM yyyy  HH:mm", Locale.getDefault())

    // Tracks which tournaments are collapsed. Persists during the activity's lifecycle.
    private val collapsedTournaments = mutableSetOf<String>()
    private var lastLoadedResults: List<MatchResult> = emptyList()
    private val gson = GsonBuilder().setPrettyPrinting().create()

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { exportBackupToUri(it) }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { showImportModeDialog(it) }
    }

    private val adapter = MatchHistoryAdapter(
        onDelete = { result ->
            showDeleteMatchDialog(result)
        },
        onDetails = { result ->
            val intent = Intent(this, PointDetailsActivity::class.java).apply {
                putExtra(PointDetailsActivity.EXTRA_MATCH_ID, result.id)
            }
            startActivity(intent)
        },
        onTournamentActions = { oldName, results ->
            showTournamentActionsDialog(oldName, results)
        },
        onEditMatchDetails = { result ->
            showEditMatchDetailsDialog(result)
        },
        onToggleDataValidity = { result ->
            lifecycleScope.launch {
                dao.update(result.copy(isDataValid = !result.isDataValid))
            }
        },
        onToggleExpand = { tournamentName ->
            if (collapsedTournaments.contains(tournamentName)) {
                collapsedTournaments.remove(tournamentName)
            } else {
                collapsedTournaments.add(tournamentName)
            }
            refreshList()
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        hideSystemBarsImmersive()

        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = adapter

        binding.btnHistoryBack.setOnClickListener { finish() }
        binding.btnHistoryExport.setOnClickListener { startExport() }
        binding.btnHistoryImport.setOnClickListener { startImport() }
        renderLastBackupTime()

        lifecycleScope.launch {
            dao.getAll().collectLatest { results ->
                lastLoadedResults = results
                refreshList()
            }
        }
    }

    private fun refreshList() {
        val grouped = groupMatches(lastLoadedResults)
        adapter.submitList(grouped)
        binding.tvHistoryEmpty.visibility = if (lastLoadedResults.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun groupMatches(results: List<MatchResult>): List<HistoryListItem> {
        val list = mutableListOf<HistoryListItem>()
        var currentTournament = ""
        
        results.forEachIndexed { index, match ->
            if (index == 0 || match.tournamentName != currentTournament) {
                currentTournament = match.tournamentName
                val isExpanded = !collapsedTournaments.contains(currentTournament)
                val matchesInGroup = results.filter { it.tournamentName == currentTournament }
                list.add(HistoryListItem.Header(currentTournament, matchesInGroup, isExpanded))
            }
            
            val isExpanded = !collapsedTournaments.contains(match.tournamentName)
            if (isExpanded) {
                list.add(HistoryListItem.Match(match))
            }
        }
        return list
    }

    private fun startExport() {
        if (lastLoadedResults.isEmpty()) {
            toast(R.string.history_export_no_data)
            return
        }
        val fileName = "table_tennis_backup_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.json"
        exportLauncher.launch(fileName)
    }

    private fun exportBackupToUri(uri: Uri) {
        lifecycleScope.launch {
            runCatching {
                val payload = BackupPayload(
                    matches = lastLoadedResults.map { it.toBackupMatch() },
                )
                val json = gson.toJson(payload)
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                        writer.write(json)
                    } ?: error("Unable to open output stream")
                }
            }.onSuccess {
                backupPrefs.edit().putLong("key_last_backup_ms", System.currentTimeMillis()).apply()
                renderLastBackupTime()
                toast(R.string.history_export_success)
            }.onFailure {
                toast(getString(R.string.history_export_failed) + " " + (it.message ?: ""))
            }
        }
    }

    private fun startImport() {
        importLauncher.launch(arrayOf("application/json", "text/*"))
    }

    private fun showImportModeDialog(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle(R.string.history_import_title)
            .setMessage(R.string.history_import_message)
            .setPositiveButton(R.string.history_import_replace) { _, _ ->
                importBackupFromUri(uri, replaceExisting = true)
            }
            .setNegativeButton(R.string.history_import_append) { _, _ ->
                importBackupFromUri(uri, replaceExisting = false)
            }
            .setNeutralButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun importBackupFromUri(uri: Uri, replaceExisting: Boolean) {
        lifecycleScope.launch {
            runCatching {
                val imported = withContext(Dispatchers.IO) {
                    val json = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("Unable to read backup file")
                    parseBackupJson(json)
                }
                if (imported.isEmpty()) {
                    toast(R.string.history_import_empty)
                    return@launch
                }
                if (replaceExisting) {
                    dao.deleteAll()
                }
                dao.insertAll(imported)
                toast(getString(R.string.history_import_success, imported.size))
            }.onFailure {
                toast(getString(R.string.history_import_failed) + " " + (it.message ?: ""))
            }
        }
    }

    private fun parseBackupJson(json: String): List<MatchResult> {
        val root = JsonParser.parseString(json)
        val backupMatches: List<BackupMatch> = if (root.isJsonObject) {
            val payload = gson.fromJson(root, BackupPayload::class.java)
            payload.matches
        } else {
            val listType = object : TypeToken<List<BackupMatch>>() {}.type
            gson.fromJson(root, listType)
        }
        return backupMatches.mapNotNull { it.toMatchResultOrNull() }
    }

    private fun toast(resId: Int) {
        Toast.makeText(this, getString(resId), Toast.LENGTH_SHORT).show()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun renderLastBackupTime() {
        val ts = backupPrefs.getLong("key_last_backup_ms", 0L)
        binding.tvHistoryLastBackup.text = if (ts <= 0L) {
            getString(R.string.history_last_backup_never)
        } else {
            getString(R.string.history_last_backup_format, lastBackupDisplayFormat.format(Date(ts)))
        }
    }

    private fun showEditTournamentDialog(oldName: String, matchesInGroup: List<MatchResult>) {
        val editText = EditText(this).apply {
            setText(oldName)
            setSelection(oldName.length)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.history_edit_tournament)
            .setView(editText)
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName != oldName) {
                    lifecycleScope.launch {
                        matchesInGroup.forEach { match ->
                            dao.update(match.copy(tournamentName = newName))
                        }
                    }
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun showTournamentActionsDialog(tournamentName: String, matchesInGroup: List<MatchResult>) {
        val allProtected = matchesInGroup.isNotEmpty() && matchesInGroup.all { it.isProtected }
        val displayName = tournamentName.ifBlank { getString(R.string.history_tournament_name_default) }
        val options = buildList {
            add(getString(R.string.history_edit_tournament))
            add(
                if (allProtected) getString(R.string.history_tournament_unprotect_all)
                else getString(R.string.history_tournament_protect_all)
            )
        }
        AlertDialog.Builder(this)
            .setTitle(displayName)
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> showEditTournamentDialog(tournamentName, matchesInGroup)
                    1 -> lifecycleScope.launch {
                        val newProtectedState = !allProtected
                        matchesInGroup.forEach { match ->
                            dao.update(match.copy(isProtected = newProtectedState))
                        }
                    }
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun showDeleteMatchDialog(result: MatchResult) {
        if (!result.isProtected) {
            confirmDeleteMatch(result)
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.history_protected_match_title)
            .setMessage(R.string.history_protected_match_message)
            .setNeutralButton(R.string.history_unprotect) { _, _ ->
                lifecycleScope.launch {
                    dao.update(result.copy(isProtected = false))
                }
            }
            .setPositiveButton(R.string.history_delete_anyway) { _, _ ->
                confirmDeleteMatch(result)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun confirmDeleteMatch(result: MatchResult) {
        val messageRes = if (result.isProtected) {
            R.string.history_confirm_delete_protected
        } else {
            R.string.history_confirm_delete
        }
        AlertDialog.Builder(this)
            .setMessage(getString(messageRes))
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                lifecycleScope.launch { dao.deleteById(result.id) }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun showEditMatchDetailsDialog(result: MatchResult) {
        val nameGroup = loadPlayerNameGroup()
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
            setTextColor(ContextCompat.getColor(this@HistoryActivity, R.color.player_name))
            strokeWidth = buttonPx(1)
            strokeColor = ColorStateList.valueOf(ContextCompat.getColor(this@HistoryActivity, R.color.history_loser_text))
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
            setTextColor(ContextCompat.getColor(this@HistoryActivity, R.color.player_name))
            strokeWidth = buttonPx(1)
            strokeColor = ColorStateList.valueOf(ContextCompat.getColor(this@HistoryActivity, R.color.history_loser_text))
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
                    ContextCompat.getColor(this@HistoryActivity, if (isSelected) R.color.score_text else R.color.history_loser_text),
                )
                chip.setTextColor(
                    ContextCompat.getColor(this@HistoryActivity, if (isSelected) R.color.score_text else R.color.player_name),
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
                toast(getString(R.string.history_edit_names_required))
                return false
            }
            savePlayerNamesToGroup(p1, p2)
            lifecycleScope.launch {
                dao.update(
                    result.copy(
                        player1Name = p1,
                        player2Name = p2,
                        matchRound = selectedRound,
                        isDataValid = isDataValid,
                        isProtected = protectMatchCheck.isChecked,
                    )
                )
            }
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

    private fun showPlayerNamePickerDialog(names: List<String>, current: String, onPicked: (String) -> Unit) {
        if (names.isEmpty()) return
        val checkedIndex = names.indexOfFirst { it.equals(current.trim(), ignoreCase = true) }
        var selectedIndex = checkedIndex
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_select_from_group)
            .setSingleChoiceItems(names.toTypedArray(), checkedIndex) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                if (selectedIndex in names.indices) {
                    onPicked(names[selectedIndex])
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun loadPlayerNameGroup(): List<String> {
        val raw = appPrefs.getString("player_name_group_json", null) ?: return emptyList()
        return try {
            gson.fromJson(raw, Array<String>::class.java)
                ?.toList()
                .orEmpty()
                .map { normalizePlayerNameForGroup(it) }
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase(Locale.ROOT) }
                .sortedBy { it.lowercase(Locale.ROOT) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun savePlayerNamesToGroup(vararg names: String) {
        val merged = (loadPlayerNameGroup() + names.toList())
            .map { normalizePlayerNameForGroup(it) }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .sortedBy { it.lowercase(Locale.ROOT) }
        appPrefs.edit().putString("player_name_group_json", gson.toJson(merged)).apply()
    }

    private fun normalizePlayerNameForGroup(value: String): String {
        return normalizeTitleCaseWords(value).take(GameViewModel.MAX_PLAYER_NAME_LENGTH)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBarsImmersive()
    }

    private data class BackupPayload(
        val schemaVersion: Int = 1,
        val exportedAt: Long = System.currentTimeMillis(),
        val matches: List<BackupMatch> = emptyList(),
    )

    private data class BackupMatch(
        val tournamentName: String? = null,
        val player1Name: String? = null,
        val player2Name: String? = null,
        val matchMode: String? = null,
        val sets1: Int? = null,
        val sets2: Int? = null,
        val winner: Int? = null,
        val bestOfSets: Int? = null,
        val durationMs: Long? = null,
        val setResultsJson: String? = null,
        val pointHistoryJson: String? = null,
        val matchFirstServer: Int? = null,
        val matchRound: String? = null,
        val isDataValid: Boolean? = null,
        val isProtected: Boolean? = null,
        val playedAt: Long? = null,
    ) {
        fun toMatchResultOrNull(): MatchResult? {
            val p1 = player1Name?.trim().orEmpty()
            val p2 = player2Name?.trim().orEmpty()
            val winnerSafe = winner ?: 0
            if (p1.isEmpty() || p2.isEmpty() || (winnerSafe != 1 && winnerSafe != 2)) {
                return null
            }
            return MatchResult(
                id = 0,
                tournamentName = tournamentName?.trim().orEmpty(),
                player1Name = p1,
                player2Name = p2,
                matchMode = when (matchMode?.trim()?.uppercase(Locale.ROOT)) {
                    GameViewModel.MATCH_MODE_DOUBLES -> GameViewModel.MATCH_MODE_DOUBLES
                    else -> GameViewModel.MATCH_MODE_SINGLES
                },
                sets1 = sets1 ?: 0,
                sets2 = sets2 ?: 0,
                winner = winnerSafe,
                bestOfSets = bestOfSets ?: 5,
                durationMs = durationMs ?: 0L,
                setResultsJson = setResultsJson.orEmpty(),
                pointHistoryJson = pointHistoryJson.orEmpty(),
                matchFirstServer = if ((matchFirstServer ?: 1) == 2) 2 else 1,
                matchRound = matchRound.orEmpty(),
                isDataValid = isDataValid ?: true,
                isProtected = isProtected ?: false,
                playedAt = playedAt ?: System.currentTimeMillis(),
            )
        }
    }

    private fun MatchResult.toBackupMatch(): BackupMatch {
        return BackupMatch(
            tournamentName = tournamentName,
            player1Name = player1Name,
            player2Name = player2Name,
            matchMode = matchMode,
            sets1 = sets1,
            sets2 = sets2,
            winner = winner,
            bestOfSets = bestOfSets,
            durationMs = durationMs,
            setResultsJson = setResultsJson,
            pointHistoryJson = pointHistoryJson,
            matchFirstServer = matchFirstServer,
            matchRound = matchRound,
            isDataValid = isDataValid,
            isProtected = isProtected,
            playedAt = playedAt,
        )
    }

    // ——— List Items ——————————————————————————————————————————————————————————

    sealed class HistoryListItem {
        data class Header(val tournamentName: String, val matches: List<MatchResult>, val isExpanded: Boolean) : HistoryListItem()
        data class Match(val result: MatchResult) : HistoryListItem()
    }

    // ——— Adapter ———————————————————————————————————————————————————————————

    class MatchHistoryAdapter(
        private val onDelete: (MatchResult) -> Unit,
        private val onDetails: (MatchResult) -> Unit,
        private val onTournamentActions: (String, List<MatchResult>) -> Unit,
        private val onEditMatchDetails: (MatchResult) -> Unit,
        private val onToggleDataValidity: (MatchResult) -> Unit,
        private val onToggleExpand: (String) -> Unit
    ) : ListAdapter<HistoryListItem, RecyclerView.ViewHolder>(DIFF) {

        private val TYPE_HEADER = 0
        private val TYPE_MATCH = 1

        override fun getItemViewType(position: Int): Int {
            return when (getItem(position)) {
                is HistoryListItem.Header -> TYPE_HEADER
                is HistoryListItem.Match -> TYPE_MATCH
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == TYPE_HEADER) {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_history_header, parent, false)
                HeaderViewHolder(view)
            } else {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_match_result, parent, false)
                MatchViewHolder(view)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = getItem(position)
            if (holder is HeaderViewHolder && item is HistoryListItem.Header) {
                holder.bind(item, onTournamentActions, onToggleExpand)
            } else if (holder is MatchViewHolder && item is HistoryListItem.Match) {
                holder.bind(item.result, onDelete, onDetails, onEditMatchDetails, onToggleDataValidity)
            }
        }

        class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val tvTournamentName: TextView = view.findViewById(R.id.tvHeaderTournamentName)
            private val ivExpandIcon: ImageView = view.findViewById(R.id.ivHeaderExpandIcon)
            
            fun bind(header: HistoryListItem.Header, onActions: (String, List<MatchResult>) -> Unit, onToggle: (String) -> Unit) {
                val baseName = if (header.tournamentName.isBlank()) itemView.context.getString(R.string.history_tournament_name_default) else header.tournamentName
                val allProtected = header.matches.isNotEmpty() && header.matches.all { it.isProtected }
                tvTournamentName.text = if (allProtected) "🔒 $baseName" else baseName
                
                // Rotate icon based on state
                ivExpandIcon.rotation = if (header.isExpanded) 0f else -90f
                
                itemView.setOnClickListener {
                    onToggle(header.tournamentName)
                }
                itemView.setOnLongClickListener {
                    onActions(header.tournamentName, header.matches)
                    true
                }
            }
        }

        class MatchViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val scoreGrid: LinearLayout = view.findViewById(R.id.layoutItemScoreGrid)
            private val tvDuration: TextView = view.findViewById(R.id.tvItemDuration)
            private val tvDate: TextView = view.findViewById(R.id.tvItemDate)
            private val tvDataStatus: TextView = view.findViewById(R.id.tvItemDataStatus)
            private val tvRound: TextView = view.findViewById(R.id.tvItemRound)
            private val winnerContainer: LinearLayout = view.findViewById(R.id.layoutItemWinner)
            private val tvTournament: TextView = view.findViewById(R.id.tvItemTournament)
            private val btnDelete: View = view.findViewById(R.id.btnItemDelete)
            private val btnDetails: View = view.findViewById(R.id.btnItemDetails)

            private val dateFormat = SimpleDateFormat("dd MMM yyyy  HH:mm", Locale.getDefault())
            private val density = view.resources.displayMetrics.density

            fun bind(
                result: MatchResult, 
                onDelete: (MatchResult) -> Unit, 
                onDetails: (MatchResult) -> Unit,
                onEditMatchDetails: (MatchResult) -> Unit,
                onToggleDataValidity: (MatchResult) -> Unit,
            ) {
                val winnerName = if (result.winner == 1) result.player1Name else result.player2Name
                val loserName = if (result.winner == 1) result.player2Name else result.player1Name
                val setResults = parseSetResults(result.setResultsJson)

                renderScoreGrid(result, winnerName, loserName, setResults)
                renderWinnerHeader(result, winnerName)
                tvDuration.text = if (result.isProtected) {
                    itemView.context.getString(R.string.history_duration_protected, formatClockDuration(result.durationMs))
                } else {
                    formatClockDuration(result.durationMs)
                }
                tvDate.text = dateFormat.format(Date(result.playedAt))
                tvDataStatus.text = itemView.context.getString(
                    if (result.isDataValid) R.string.history_data_status_ok else R.string.history_data_status_bad
                )
                tvDataStatus.setTextColor(
                    ContextCompat.getColor(
                        itemView.context,
                        if (result.isDataValid) R.color.win_vibrant else R.color.loss_vibrant,
                    )
                )
                tvDataStatus.alpha = 0.95f
                tvDataStatus.setOnClickListener { onToggleDataValidity(result) }
                btnDelete.alpha = if (result.isProtected) 0.55f else 1f

                tvRound.text = if (result.matchRound.isNotBlank()) "· ${result.matchRound}" else ""
                tvRound.visibility = if (result.matchRound.isNotBlank()) View.VISIBLE else View.GONE

                // Tournament name in card is hidden since we have headers now
                tvTournament.visibility = View.GONE
                
                btnDelete.setOnClickListener { onDelete(result) }
                btnDetails.setOnClickListener { onDetails(result) }
                
                itemView.setOnLongClickListener {
                    onEditMatchDetails(result)
                    true
                }
            }

            private fun renderScoreGrid(
                result: MatchResult,
                winnerName: String,
                loserName: String,
                setResults: List<Pair<Int, Int>>,
            ) {
                scoreGrid.removeAllViews()
                if (setResults.isEmpty()) {
                    scoreGrid.visibility = View.GONE
                    return
                }
                scoreGrid.visibility = View.VISIBLE

                val winnerScores = setResults.map { if (result.winner == 1) it.first else it.second }
                val loserScores = setResults.map { if (result.winner == 1) it.second else it.first }

                scoreGrid.addView(
                    createScoreRow(
                        name = winnerName,
                        nameColor = ContextCompat.getColor(itemView.context, R.color.score_text),
                        nameBold = false,
                        setCount = if (result.winner == 1) result.sets1 else result.sets2,
                        setCountColor = ContextCompat.getColor(itemView.context, R.color.score_text),
                        setCountBackgroundRes = R.drawable.history_set_count_box,
                        playerScores = winnerScores,
                        opponentScores = loserScores,
                        isMatchWinnerRow = true,
                    ),
                )
                scoreGrid.addView(
                    createScoreRow(
                        name = loserName,
                        nameColor = ContextCompat.getColor(itemView.context, R.color.player_name),
                        nameBold = false,
                        setCount = if (result.winner == 1) result.sets2 else result.sets1,
                        setCountColor = ContextCompat.getColor(itemView.context, R.color.score_text),
                        setCountBackgroundRes = R.drawable.history_set_count_box_loser,
                        playerScores = loserScores,
                        opponentScores = winnerScores,
                        isMatchWinnerRow = false,
                    ),
                )
            }

            private fun createScoreRow(
                name: String,
                nameColor: Int,
                nameBold: Boolean,
                setCount: Int,
                setCountColor: Int,
                setCountBackgroundRes: Int,
                playerScores: List<Int>,
                opponentScores: List<Int>,
                isMatchWinnerRow: Boolean,
            ): LinearLayout {
                return LinearLayout(itemView.context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        if (!isMatchWinnerRow) topMargin = 2.dp()
                    }

                    addView(createNameCell(name, nameColor, nameBold))
                    addView(createSetCountCell(setCount.toString(), setCountColor, setCountBackgroundRes))
                    addView(createSeparatorCell())
                    playerScores.forEachIndexed { index, score ->
                        val otherScore = opponentScores.getOrElse(index) { 0 }
                        val isWinningGame = score > otherScore
                        val scoreColor = when {
                            isWinningGame && isMatchWinnerRow -> ContextCompat.getColor(itemView.context, R.color.score_text)
                            isWinningGame -> ContextCompat.getColor(itemView.context, R.color.score_text)
                            else -> ContextCompat.getColor(itemView.context, R.color.history_loser_text)
                        }
                        val scoreSizeSp = when {
                            isWinningGame && isMatchWinnerRow -> 18f
                            else -> 18f
                        }
                        addView(createScoreCell(score.toString(), scoreColor, scoreSizeSp))
                    }
                }
            }

            private fun renderWinnerHeader(result: MatchResult, winnerName: String) {
                winnerContainer.removeAllViews()
                val isDoubles = result.matchMode == GameViewModel.MATCH_MODE_DOUBLES && winnerName.contains(" / ")
                if (!isDoubles) {
                    winnerContainer.orientation = LinearLayout.HORIZONTAL
                    winnerContainer.gravity = Gravity.CENTER
                    winnerContainer.addView(TextView(itemView.context).apply {
                        text = itemView.context.getString(R.string.history_winner_only, winnerName)
                        setTextColor(ContextCompat.getColor(itemView.context, R.color.score_text))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                        setTypeface(typeface, Typeface.BOLD)
                        gravity = Gravity.CENTER
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    })
                    return
                }

                winnerContainer.orientation = LinearLayout.HORIZONTAL
                winnerContainer.gravity = Gravity.CENTER

                winnerContainer.addView(LinearLayout(itemView.context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    )

                    addView(TextView(itemView.context).apply {
                        text = "🏆"
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                        includeFontPadding = false
                        gravity = Gravity.CENTER_VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        ).apply {
                            marginEnd = 8.dp()
                        }
                    })

                    addView(createStackedNameBlock(
                        winnerName,
                        ContextCompat.getColor(itemView.context, R.color.score_text),
                        true,
                        15f,
                    ))
                })
            }

            private fun createNameCell(name: String, color: Int, bold: Boolean): View {
                val isDoubles = name.contains(" / ")
                if (!isDoubles) {
                    return TextView(itemView.context).apply {
                        text = name
                        setTextColor(color)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        gravity = Gravity.START or Gravity.CENTER_VERTICAL
                        if (bold) setTypeface(typeface, Typeface.NORMAL)
                        layoutParams = LinearLayout.LayoutParams(130.dp(), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                            marginEnd = 10.dp()
                        }
                    }
                }

                return createStackedNameBlock(name, color, bold, 13f).apply {
                    layoutParams = LinearLayout.LayoutParams(130.dp(), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        marginEnd = 10.dp()
                    }
                }
            }

            private fun createStackedNameBlock(name: String, color: Int, bold: Boolean, textSizeSp: Float): LinearLayout {
                val names = name.split(" / ")
                return LinearLayout(itemView.context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    names.forEach { playerName ->
                        addView(TextView(itemView.context).apply {
                            text = playerName
                            setTextColor(color)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
                            includeFontPadding = false
                            maxLines = 1
                            ellipsize = TextUtils.TruncateAt.END
                            gravity = Gravity.START
                            if (bold) setTypeface(typeface, Typeface.NORMAL)
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                            )
                        })
                    }
                }
            }

            private fun createSetCountCell(text: String, color: Int, backgroundRes: Int): TextView {
                return TextView(itemView.context).apply {
                    this.text = text
                    setTextColor(color)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                    includeFontPadding = false
                    isSingleLine = true
                    gravity = Gravity.CENTER
                    setTypeface(typeface, Typeface.BOLD)
                    setBackgroundResource(backgroundRes)
                    setPadding(4.dp(), 0, 4.dp(), 0)
                    layoutParams = LinearLayout.LayoutParams(30.dp(), 30.dp()).apply {
                        marginEnd = 18.dp()
                    }
                }
            }

            private fun createSeparatorCell(): TextView {
                return TextView(itemView.context).apply {
                    text = "|"
                    setTextColor(ContextCompat.getColor(itemView.context, R.color.history_loser_text))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    gravity = Gravity.CENTER
                    setTypeface(typeface, Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        marginEnd = 16.dp()
                    }
                }
            }

            private fun createScoreCell(text: String, color: Int, sizeSp: Float): TextView {
                return TextView(itemView.context).apply {
                    this.text = text
                    setTextColor(color)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    setTypeface(typeface, Typeface.NORMAL)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        marginEnd = 16.dp()
                    }
                }
            }

            private fun parseSetResults(setResultsJson: String): List<Pair<Int, Int>> {
                if (setResultsJson.isBlank()) return emptyList()
                return setResultsJson.split(",").mapNotNull { token ->
                    val parts = token.trim().split("-")
                    val first = parts.getOrNull(0)?.trim()?.toIntOrNull()
                    val second = parts.getOrNull(1)?.trim()?.toIntOrNull()
                    if (first != null && second != null) first to second else null
                }
            }

            private fun Int.dp(): Int = (this * density).toInt()

        }

        companion object {
            val DIFF = object : DiffUtil.ItemCallback<HistoryListItem>() {
                override fun areItemsTheSame(a: HistoryListItem, b: HistoryListItem): Boolean {
                    return if (a is HistoryListItem.Header && b is HistoryListItem.Header) {
                        a.tournamentName == b.tournamentName
                    } else if (a is HistoryListItem.Match && b is HistoryListItem.Match) {
                        a.result.id == b.result.id
                    } else false
                }
                override fun areContentsTheSame(a: HistoryListItem, b: HistoryListItem): Boolean {
                    return a == b
                }
            }
        }
    }
}
