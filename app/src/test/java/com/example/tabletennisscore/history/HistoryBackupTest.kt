package com.example.tabletennisscore.history

import com.example.tabletennisscore.GameViewModel
import com.example.tabletennisscore.data.MatchResult
import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryBackupTest {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    private fun match(
        id: Int = 7,
        player1: String = "Ann",
        player2: String = "Bea",
        winner: Int = 1,
        mode: String = GameViewModel.MATCH_MODE_SINGLES,
    ) = MatchResult(
        id = id,
        tournamentName = "Cup",
        player1Name = player1,
        player2Name = player2,
        matchMode = mode,
        sets1 = 3,
        sets2 = 1,
        winner = winner,
        bestOfSets = 5,
        durationMs = 123_000L,
        setResultsJson = "11-7,9-11,11-5,11-3",
        pointHistoryJson = "1122,12",
        matchFirstServer = 2,
        matchRound = "Semi",
        isDataValid = false,
        isProtected = true,
        playedAt = 1_700_000_000_000L,
    )

    private fun exportJson(vararg matches: MatchResult): String =
        gson.toJson(BackupPayload(matches = matches.map { it.toBackupMatch() }))

    @Test
    fun exportedMatchesCanBeImportedAgainUnchangedExceptForTheId() {
        val original = match()
        val imported = parseBackupJson(gson, exportJson(original)).single()
        assertEquals(original.copy(id = 0), imported)
    }

    @Test
    fun severalMatchesKeepTheirOrder() {
        val imported = parseBackupJson(gson, exportJson(match(player1 = "A"), match(player1 = "B"), match(player1 = "C")))
        assertEquals(listOf("A", "B", "C"), imported.map { it.player1Name })
    }

    @Test
    fun aPlainListOfMatchesIsAccepted() {
        val json = """[{"player1Name":"Ann","player2Name":"Bea","winner":2}]"""
        val imported = parseBackupJson(gson, json).single()
        assertEquals("Ann", imported.player1Name)
        assertEquals(2, imported.winner)
    }

    @Test
    fun missingFieldsGetSensibleDefaults() {
        val json = """{"matches":[{"player1Name":"Ann","player2Name":"Bea","winner":1}]}"""
        val imported = parseBackupJson(gson, json).single()
        assertEquals("", imported.tournamentName)
        assertEquals(GameViewModel.MATCH_MODE_SINGLES, imported.matchMode)
        assertEquals(0, imported.sets1)
        assertEquals(5, imported.bestOfSets)
        assertEquals(0L, imported.durationMs)
        assertEquals("", imported.setResultsJson)
        assertEquals("", imported.pointHistoryJson)
        assertEquals(1, imported.matchFirstServer)
        assertTrue(imported.isDataValid)
        assertFalse(imported.isProtected)
        assertEquals(0, imported.id)
    }

    @Test
    fun matchesWithoutBothNamesOrAValidWinnerAreSkipped() {
        val json = """
            {"matches":[
              {"player1Name":"Ann","player2Name":"","winner":1},
              {"player1Name":"  ","player2Name":"Bea","winner":1},
              {"player1Name":"Ann","player2Name":"Bea","winner":0},
              {"player1Name":"Ann","player2Name":"Bea","winner":3},
              {"player1Name":"Ann","player2Name":"Bea"},
              {"player1Name":"Ok1","player2Name":"Ok2","winner":1}
            ]}
        """.trimIndent()
        val imported = parseBackupJson(gson, json)
        assertEquals(listOf("Ok1"), imported.map { it.player1Name })
    }

    @Test
    fun namesAreTrimmed() {
        val json = """[{"player1Name":"  Ann ","player2Name":" Bea  ","tournamentName":" Cup ","winner":1}]"""
        val imported = parseBackupJson(gson, json).single()
        assertEquals("Ann", imported.player1Name)
        assertEquals("Bea", imported.player2Name)
        assertEquals("Cup", imported.tournamentName)
    }

    @Test
    fun matchModeIsNormalizedIgnoringCaseAndSpaces() {
        fun modeOf(mode: String?): String {
            val field = if (mode == null) "" else ""","matchMode":"$mode""""
            val json = """[{"player1Name":"A","player2Name":"B","winner":1$field}]"""
            return parseBackupJson(gson, json).single().matchMode
        }
        assertEquals(GameViewModel.MATCH_MODE_DOUBLES, modeOf(" doubles "))
        assertEquals(GameViewModel.MATCH_MODE_DOUBLES, modeOf("DOUBLES"))
        assertEquals(GameViewModel.MATCH_MODE_SINGLES, modeOf("singles"))
        assertEquals(GameViewModel.MATCH_MODE_SINGLES, modeOf("mixed"))
        assertEquals(GameViewModel.MATCH_MODE_SINGLES, modeOf(null))
    }

    @Test
    fun firstServerIsLimitedToOneOrTwo() {
        fun firstServerOf(value: Int): Int {
            val json = """[{"player1Name":"A","player2Name":"B","winner":1,"matchFirstServer":$value}]"""
            return parseBackupJson(gson, json).single().matchFirstServer
        }
        assertEquals(2, firstServerOf(2))
        assertEquals(1, firstServerOf(1))
        assertEquals(1, firstServerOf(0))
        assertEquals(1, firstServerOf(7))
    }

    @Test
    fun emptyBackupsGiveNoMatches() {
        assertTrue(parseBackupJson(gson, """{"matches":[]}""").isEmpty())
        assertTrue(parseBackupJson(gson, "[]").isEmpty())
    }

    @Test
    fun exportKeepsProtectionAndValidityFlags() {
        val exported = match().toBackupMatch()
        assertEquals(true, exported.isProtected)
        assertEquals(false, exported.isDataValid)
        assertEquals(2, exported.matchFirstServer)
        assertNull(BackupMatch().player1Name)
    }

    @Test(expected = Exception::class)
    fun invalidJsonIsRejected() {
        parseBackupJson(gson, "this is not json")
    }
}
