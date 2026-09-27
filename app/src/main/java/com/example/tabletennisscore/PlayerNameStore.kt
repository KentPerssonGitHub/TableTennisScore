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
 * A saved list of names offered in the name pickers: player names by default, or tournament
 * names via [tournamentNames].
 * The list is kept sanitized, de-duplicated (ignoring case) and sorted.
 */
class PlayerNameStore(
    private val prefs: SharedPreferences,
    private val key: String = KEY_PLAYER_NAME_GROUP,
    private val sanitize: (String) -> String = { sanitizePlayerName(it, "") },
) {

    /**
     * Returns the saved names. When nothing has been saved yet, [defaultNames] (e.g. the current players)
     * are sanitized, saved and returned instead.
     */
    fun load(defaultNames: List<String>): List<String> {
        val raw = prefs.getString(key, null)
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
        val normalized = sanitize(name)
        if (normalized.isBlank()) return
        val existing = load(defaultNames).toMutableList()
        if (existing.none { it.equals(normalized, ignoreCase = true) }) {
            existing.add(normalized)
            existing.sortBy { it.lowercase(Locale.ROOT) }
            save(existing)
        }
    }

    /** Removes [name] (ignoring case) from the saved names. */
    fun remove(name: String, defaultNames: List<String>) {
        val existing = load(defaultNames)
        val remaining = existing.filterNot { it.equals(name.trim(), ignoreCase = true) }
        if (remaining.size != existing.size) save(remaining)
    }

    private fun cleaned(names: List<String>): List<String> = names
        .map(sanitize)
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase(Locale.ROOT) }
        .sortedBy { it.lowercase(Locale.ROOT) }

    private fun save(names: List<String>) {
        prefs.edit { putString(key, Gson().toJson(names)) }
    }

    companion object {
        /** The saved tournament names, offered when picking the tournament. */
        fun tournamentNames(prefs: SharedPreferences) =
            PlayerNameStore(prefs, KEY_TOURNAMENT_NAME_GROUP, ::sanitizeTournamentName)

        private const val KEY_PLAYER_NAME_GROUP = "player_name_group_json"
        private const val KEY_TOURNAMENT_NAME_GROUP = "tournament_name_group_json"
    }
}
