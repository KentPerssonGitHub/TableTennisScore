package com.example.tabletennisscore.history
import com.example.tabletennisscore.normalizeTitleCaseWords
import com.example.tabletennisscore.hideSystemBarsImmersive
import com.example.tabletennisscore.GameViewModel
import com.example.tabletennisscore.R

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tabletennisscore.data.MatchDatabase
import com.example.tabletennisscore.data.MatchResult
import com.example.tabletennisscore.databinding.ActivityHistoryBinding
import com.google.gson.GsonBuilder
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
            showEditMatchDetailsDialog(result, loadPlayerNameGroup()) { updated ->
                savePlayerNamesToGroup(updated.player1Name, updated.player2Name)
                lifecycleScope.launch { dao.update(updated) }
            }
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
                    parseBackupJson(gson, json)
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

}
