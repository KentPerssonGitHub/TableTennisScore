package com.example.tabletennisscore

import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import java.util.Locale

/** Trims, title-cases and length-limits a player name; returns [fallback] when nothing is left. */
fun sanitizePlayerName(name: String, fallback: String): String {
    return normalizeTitleCaseWords(name)
        .take(GameViewModel.MAX_PLAYER_NAME_LENGTH)
        .ifEmpty { fallback }
}

fun sanitizeTournamentName(name: String): String {
    return normalizeTitleCaseWords(name).take(GameViewModel.MAX_TOURNAMENT_NAME_LENGTH)
}

/**
 * The saved list of player names offered in the "select from group" pickers.
 * The list is kept sanitized, de-duplicated (ignoring case) and sorted.
 */
class PlayerNameStore(private val prefs: SharedPreferences) {

    /**
     * Returns the saved names. When nothing has been saved yet, [defaultNames] (the current players)
     * are sanitized, saved and returned instead.
     */
    fun load(defaultNames: List<String>): List<String> {
        val raw = prefs.getString(KEY_PLAYER_NAME_GROUP, null)
        if (raw.isNullOrBlank()) {
            val defaults = cleaned(defaultNames)
            if (defaults.isNotEmpty()) save(defaults)
            return defaults
        }
        return try {
            cleaned(Gson().fromJson(raw, Array<String>::class.java)?.toList().orEmpty())
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun replaceAll(names: List<String>) {
        save(cleaned(names))
    }

    fun add(name: String, defaultNames: List<String>) {
        val normalized = sanitizePlayerName(name, "")
        if (normalized.isBlank()) return
        val existing = load(defaultNames).toMutableList()
        if (existing.none { it.equals(normalized, ignoreCase = true) }) {
            existing.add(normalized)
            existing.sortBy { it.lowercase(Locale.ROOT) }
            save(existing)
        }
    }

    private fun cleaned(names: List<String>): List<String> = names
        .map { sanitizePlayerName(it, "") }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase(Locale.ROOT) }
        .sortedBy { it.lowercase(Locale.ROOT) }

    private fun save(names: List<String>) {
        prefs.edit { putString(KEY_PLAYER_NAME_GROUP, Gson().toJson(names)) }
    }

    private companion object {
        const val KEY_PLAYER_NAME_GROUP = "player_name_group_json"
    }
}
