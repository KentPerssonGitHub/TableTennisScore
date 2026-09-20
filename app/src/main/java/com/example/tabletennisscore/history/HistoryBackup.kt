package com.example.tabletennisscore.history
import com.example.tabletennisscore.GameViewModel

import com.example.tabletennisscore.data.MatchResult
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.util.Locale

internal data class BackupPayload(
    val schemaVersion: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val matches: List<BackupMatch> = emptyList(),
)

internal data class BackupMatch(
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

internal fun MatchResult.toBackupMatch(): BackupMatch {
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

internal fun parseBackupJson(gson: Gson, json: String): List<MatchResult> {
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
