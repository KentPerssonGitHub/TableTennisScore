package com.example.tabletennisscore

import org.junit.Assert.assertEquals
import org.junit.Test

class NameAndTimeFormattingTest {

    @Test
    fun wordsAreTitleCasedAndWhitespaceCollapsed() {
        assertEquals("Anna Lisa", normalizeTitleCaseWords("  anna    lisa "))
    }

    @Test
    fun onlyTheFirstLetterOfEachWordChanges() {
        assertEquals("McDonald Al-amin", normalizeTitleCaseWords("mcDonald al-amin"))
    }

    @Test
    fun blankInputBecomesEmpty() {
        assertEquals("", normalizeTitleCaseWords("   "))
    }

    @Test
    fun playerNameFallsBackWhenEmpty() {
        assertEquals("Player 1", sanitizePlayerName("   ", "Player 1"))
    }

    @Test
    fun playerNameIsTitleCasedAndLimited() {
        assertEquals("Ann Lee", sanitizePlayerName(" ann  lee ", "x"))
        assertEquals(
            GameViewModel.MAX_PLAYER_NAME_LENGTH,
            sanitizePlayerName("b".repeat(100), "x").length,
        )
    }

    @Test
    fun tournamentNameIsLimitedButMayBeEmpty() {
        assertEquals("", sanitizeTournamentName("  "))
        assertEquals(
            GameViewModel.MAX_TOURNAMENT_NAME_LENGTH,
            sanitizeTournamentName("t".repeat(100)).length,
        )
    }

    @Test
    fun durationUnderAnHourShowsMinutesAndSeconds() {
        assertEquals("00:00", formatClockDuration(0))
        assertEquals("01:01", formatClockDuration(61_000))
        assertEquals("59:59", formatClockDuration(3_599_999))
    }

    @Test
    fun durationOfAnHourOrMoreShowsHours() {
        assertEquals("1:00:00", formatClockDuration(3_600_000))
        assertEquals("1:01:01", formatClockDuration(3_661_000))
    }
}
