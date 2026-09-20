package com.example.tabletennisscore

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.tabletennisscore.GameViewModel.GameState
import com.example.tabletennisscore.data.MatchDatabase
import com.example.tabletennisscore.data.MatchResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/** Tests [GameViewModel] together with real (Robolectric) preferences, LiveData and Room. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameViewModelTest {

    private lateinit var app: Application

    private val prefs get() = app.getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        resetDatabase()
        prefs.edit().clear().commit()
    }

    @After
    fun tearDown() {
        resetDatabase()
    }

    /** The database is a process-wide singleton; every test needs a fresh one. */
    private fun resetDatabase() {
        val field = MatchDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        (field.get(null) as? MatchDatabase)?.close()
        field.set(null, null)
    }

    private fun newViewModel() = GameViewModel(app)

    private val GameViewModel.s: GameState get() = state.value!!

    private fun GameViewModel.startMatch() = startOrResumeMatch()

    private fun GameViewModel.score(vararg players: Int) = players.forEach { addPoint(it) }

    private fun GameViewModel.winSet(player: Int) = repeat(11) { addPoint(player) }

    private fun advanceClock(seconds: Long) {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(seconds))
    }

    /** Waits for the asynchronous database write and returns the stored matches. */
    private fun storedMatches(expected: Int): List<MatchResult> {
        val dao = MatchDatabase.getInstance(app).matchResultDao()
        repeat(200) {
            shadowOf(Looper.getMainLooper()).idle()
            val rows = runBlocking { dao.getAll().first() }
            if (rows.size >= expected) return rows
            Thread.sleep(25)
        }
        return runBlocking { dao.getAll().first() }
    }

    // ----- Starting state -----

    @Test
    fun startsWithDefaultPlayersAndNoScore() {
        val vm = newViewModel()
        assertEquals("Player 1", vm.s.player1Name)
        assertEquals("Player 2", vm.s.player2Name)
        assertEquals(0, vm.s.score1)
        assertEquals(1, vm.s.server)
        assertEquals(5, vm.s.bestOfSets)
        assertFalse(vm.s.isMatchRunning)
        assertFalse(vm.s.hasMatchStarted)
    }

    @Test
    fun savedNamesAreLoadedAtStart() {
        prefs.edit()
            .putString("singles_p1_name", "Ann")
            .putString("singles_p2_name", "Bea")
            .putString("tournament_name", "Cup")
            .putString("doubles_t1_a", "A1").putString("doubles_t1_b", "B1")
            .putString("doubles_t2_a", "A2").putString("doubles_t2_b", "B2")
            .commit()
        val s = newViewModel().s
        assertEquals("Ann", s.player1Name)
        assertEquals("Bea", s.player2Name)
        assertEquals("Cup", s.tournamentName)
        assertEquals("A1", s.team1PlayerA)
        assertEquals("B2", s.team2PlayerB)
    }

    @Test
    fun namesFromOlderVersionsAreUsedWhenNoNewKeyExists() {
        prefs.edit().putString("p1_name", "Old One").putString("p2_name", "Old Two").commit()
        val s = newViewModel().s
        assertEquals("Old One", s.player1Name)
        assertEquals("Old Two", s.player2Name)
    }

    // ----- Running a match -----

    @Test
    fun pointsAreIgnoredUntilTheMatchIsRunning() {
        val vm = newViewModel()
        vm.score(1, 2)
        assertEquals(0, vm.s.score1 + vm.s.score2)
    }

    @Test
    fun startingTheMatchMarksItStartedAndRunning() {
        val vm = newViewModel()
        vm.startMatch()
        assertTrue(vm.s.isMatchRunning)
        assertTrue(vm.s.hasMatchStarted)
    }

    @Test
    fun pointsCountWhileRunningAndNotWhilePaused() {
        val vm = newViewModel()
        vm.startMatch()
        vm.score(1, 1, 2)
        assertEquals(2, vm.s.score1)
        assertEquals(1, vm.s.score2)

        vm.pauseMatch()
        vm.score(1)
        assertEquals(2, vm.s.score1)

        vm.startMatch()
        vm.score(1)
        assertEquals(3, vm.s.score1)
    }

    @Test
    fun aRunningMatchAdvancesTheServerEveryTwoPoints() {
        val vm = newViewModel()
        vm.startMatch()
        vm.score(1, 1)
        assertEquals(2, vm.s.server)
    }

    // ----- Undo -----

    @Test
    fun undoRestoresThePreviousStateStepByStep() {
        val vm = newViewModel()
        vm.startMatch()
        vm.score(1, 2, 1)
        vm.undo()
        assertEquals(1, vm.s.score1)
        assertEquals(1, vm.s.score2)
        vm.undo()
        assertEquals(1, vm.s.score1)
        assertEquals(0, vm.s.score2)
    }

    @Test
    fun undoWithoutHistoryDoesNothing() {
        val vm = newViewModel()
        vm.undo()
        assertEquals(0, vm.s.score1)
    }

    @Test
    fun undoGoesBackOverAFinishedSet() {
        val vm = newViewModel()
        vm.startMatch()
        repeat(10) { vm.score(1) }
        vm.score(2, 2, 2, 2, 2)
        repeat(1) { vm.score(1) } // 11-5 finishes the set
        assertEquals(1, vm.s.sets1)
        vm.undo()
        assertEquals(0, vm.s.sets1)
        assertEquals(10, vm.s.score1)
        assertEquals(5, vm.s.score2)
        assertTrue(vm.s.setResults.isEmpty())
    }

    @Test
    fun undoHistoryIsLimitedToFiftySteps() {
        val vm = newViewModel()
        vm.startMatch()
        repeat(30) { vm.score(1, 2) } // 60 points, alternating, never ends the set
        repeat(60) { vm.undo() }
        assertEquals(10, vm.s.score1 + vm.s.score2)
    }

    // ----- Server and sides -----

    @Test
    fun swappingTheServerChangesWhoServesFirstInLaterSets() {
        val vm = newViewModel()
        vm.swapServer()
        assertEquals(2, vm.s.server)
        vm.startMatch()
        vm.winSet(1)
        // Player 2 served first in set 1, so player 1 serves first in set 2.
        assertEquals(1, vm.s.server)
    }

    @Test
    fun swappingSidesTogglesAndCanBeUndone() {
        val vm = newViewModel()
        vm.swapSides()
        assertTrue(vm.s.sidesSwapped)
        vm.undo()
        assertFalse(vm.s.sidesSwapped)
    }

    @Test
    fun serverAndSidesCannotBeSwappedAfterTheMatchIsWon() {
        val vm = newViewModel()
        vm.setupMatch("Ann", "Bea", firstServer = 1, bestOfSets = 1)
        vm.startMatch()
        vm.winSet(1)
        assertEquals(1, vm.s.matchWinner)
        val serverAtTheEnd = vm.s.server
        vm.swapServer()
        vm.swapSides()
        assertEquals(serverAtTheEnd, vm.s.server)
        assertFalse(vm.s.sidesSwapped)
    }

    // ----- Reset and setup -----

    @Test
    fun resetKeepsNamesAndSettingsButClearsTheMatch() {
        val vm = newViewModel()
        vm.setupMatch("Ann", "Bea", firstServer = 2, bestOfSets = 3)
        vm.setTournamentName("Cup")
        vm.startMatch()
        vm.score(1, 1, 2)

        vm.resetMatch()
        assertEquals(0, vm.s.score1 + vm.s.score2)
        assertFalse(vm.s.hasMatchStarted)
        assertFalse(vm.s.isMatchRunning)
        assertEquals("Ann", vm.s.player1Name)
        assertEquals(3, vm.s.bestOfSets)
        assertEquals("Cup", vm.s.tournamentName)
        assertEquals(1, vm.s.server)

        vm.undo()
        assertEquals(0, vm.s.score1 + vm.s.score2)
    }

    @Test
    fun setupMatchSanitizesNamesAndSavesThem() {
        val vm = newViewModel()
        vm.setupMatch("  ann   lee ", "   ", firstServer = 2, bestOfSets = 7)
        assertEquals("Ann Lee", vm.s.player1Name)
        assertEquals("Player 2", vm.s.player2Name)
        assertEquals(2, vm.s.server)
        assertEquals(7, vm.s.bestOfSets)
        assertFalse(vm.s.hasMatchStarted)
        assertEquals("Ann Lee", prefs.getString("singles_p1_name", null))
        assertEquals("Player 2", prefs.getString("p2_name", null))
        assertTrue(vm.getPlayerNameGroup().containsAll(listOf("Ann Lee", "Player 2")))
    }

    @Test
    fun setupMatchFallsBackToFiveSetsForUnsupportedLengths() {
        val vm = newViewModel()
        vm.setupMatch("A", "B", firstServer = 1, bestOfSets = 4)
        assertEquals(5, vm.s.bestOfSets)
        vm.setupMatch("A", "B", firstServer = 1, bestOfSets = 3)
        assertEquals(3, vm.s.bestOfSets)
    }

    @Test
    fun setupMatchTreatsAnyFirstServerButTwoAsPlayerOne() {
        val vm = newViewModel()
        vm.setupMatch("A", "B", firstServer = 9, bestOfSets = 5)
        assertEquals(1, vm.s.server)
    }

    @Test
    fun setupDoublesMatchComposesTeamNamesAndSavesTheTeams() {
        val vm = newViewModel()
        vm.setupMatch(
            "x", "y", firstServer = 1, bestOfSets = 3,
            matchMode = GameViewModel.MATCH_MODE_DOUBLES,
            team1PlayerA = "ann", team1PlayerB = "bea", team2PlayerA = "cid", team2PlayerB = "",
        )
        assertEquals("Ann / Bea", vm.s.player1Name)
        assertEquals("Cid / Player 2B", vm.s.player2Name)
        assertEquals(GameViewModel.MATCH_MODE_DOUBLES, vm.s.matchMode)
        assertEquals("Ann", prefs.getString("doubles_t1_a", null))
        assertNull("singles names are not touched", prefs.getString("singles_p1_name", null))
        assertTrue(vm.getPlayerNameGroup().containsAll(listOf("Ann", "Bea", "Cid", "Player 2B")))
    }

    @Test
    fun setupMatchStopsARunningMatch() {
        val vm = newViewModel()
        vm.startMatch()
        vm.score(1)
        vm.setupMatch("A", "B", firstServer = 1, bestOfSets = 5)
        assertFalse(vm.s.isMatchRunning)
        assertEquals(0, vm.s.score1)
    }

    // ----- Names -----

    @Test
    fun playerNameIsSanitizedSavedAndAddedToTheNameGroup() {
        val vm = newViewModel()
        vm.setPlayerName(1, "  zed  ")
        assertEquals("Zed", vm.s.player1Name)
        assertEquals("Zed", prefs.getString("singles_p1_name", null))
        assertTrue(vm.getPlayerNameGroup().contains("Zed"))
    }

    @Test
    fun blankPlayerNameFallsBackToTheDefault() {
        val vm = newViewModel()
        vm.setPlayerName(2, "   ")
        assertEquals("Player 2", vm.s.player2Name)
    }

    @Test
    fun doublesPlayerNamesAreNotSavedAsSinglesNames() {
        val vm = newViewModel()
        vm.setupMatch(
            "a", "b", 1, 3, GameViewModel.MATCH_MODE_DOUBLES, "A", "B", "C", "D",
        )
        prefs.edit().remove("singles_p1_name").commit()
        vm.setPlayerName(1, "Team")
        assertNull(prefs.getString("singles_p1_name", null))
    }

    @Test
    fun doublesTeamNamesAreOnlyChangedInDoubles() {
        val vm = newViewModel()
        vm.setDoublesTeamNames(1, "Ann", "Bea")
        assertEquals("Player 1", vm.s.player1Name)

        vm.setupMatch("a", "b", 1, 3, GameViewModel.MATCH_MODE_DOUBLES, "A", "B", "C", "D")
        vm.setDoublesTeamNames(2, "zed", "  ")
        assertEquals("Zed / Player 2B", vm.s.player2Name)
        assertEquals("Zed", vm.s.team2PlayerA)
        assertEquals("Zed", prefs.getString("doubles_t2_a", null))
    }

    @Test
    fun tournamentNameIsSanitizedSavedAndLimited() {
        val vm = newViewModel()
        vm.setTournamentName("  spring  cup ")
        assertEquals("Spring Cup", vm.s.tournamentName)
        assertEquals("Spring Cup", prefs.getString("tournament_name", null))

        vm.setTournamentName("t".repeat(100))
        assertEquals(GameViewModel.MAX_TOURNAMENT_NAME_LENGTH, vm.s.tournamentName.length)
    }

    @Test
    fun matchRoundIsKeptInTheState() {
        val vm = newViewModel()
        vm.setMatchRound("Semi")
        assertEquals("Semi", vm.s.matchRound)
    }

    @Test
    fun nameGroupStartsFromTheCurrentPlayersAndCanBeReplacedOrExtended() {
        val vm = newViewModel()
        assertEquals(listOf("Player 1", "Player 2"), vm.getPlayerNameGroup())

        vm.setPlayerNameGroup(listOf("zed", "ann"))
        assertEquals(listOf("Ann", "Zed"), vm.getPlayerNameGroup())

        vm.addPlayerNameToGroup("bea")
        vm.addPlayerNameToGroup("ANN")
        assertEquals(listOf("Ann", "Bea", "Zed"), vm.getPlayerNameGroup())
    }

    // ----- Time -----

    @Test
    fun playedTimeOnlyCountsWhileTheMatchRuns() {
        val vm = newViewModel()
        assertEquals(0L, vm.getElapsedPlayedMs())

        vm.startMatch()
        advanceClock(5)
        assertEquals(5_000L, vm.getElapsedPlayedMs())

        vm.pauseMatch()
        advanceClock(60)
        assertEquals(5_000L, vm.getElapsedPlayedMs())

        vm.startMatch()
        advanceClock(2)
        assertEquals(7_000L, vm.getElapsedPlayedMs())
    }

    @Test
    fun resetClearsThePlayedTime() {
        val vm = newViewModel()
        vm.startMatch()
        advanceClock(5)
        vm.resetMatch()
        assertEquals(0L, vm.getElapsedPlayedMs())
    }

    // ----- Deciding set: side swap at five points -----

    private fun GameViewModel.toDecidingSetFourTwo() {
        startMatch()
        pauseMatch()
        assertTrue(updatePausedMatchScores(listOf(11 to 5, 5 to 11, 11 to 5, 5 to 11), 4, 2))
        startMatch()
    }

    @Test
    fun reachingFiveInTheDecidingSetPausesUntilTheSwapIsConfirmed() {
        val vm = newViewModel()
        vm.toDecidingSetFourTwo()
        vm.score(1)

        assertEquals(5, vm.s.score1)
        assertTrue(vm.s.sidesSwapped)
        assertFalse(vm.s.isMatchRunning)
        assertTrue(vm.s.awaitingDecidingSetSwapConfirmation)

        vm.score(1)
        assertEquals("points are ignored while waiting", 5, vm.s.score1)
        vm.startMatch()
        assertFalse("cannot resume before confirming", vm.s.isMatchRunning)

        vm.confirmDecidingSetSideSwapDone()
        assertTrue(vm.s.isMatchRunning)
        assertFalse(vm.s.awaitingDecidingSetSwapConfirmation)
        vm.score(1)
        assertEquals(6, vm.s.score1)
    }

    @Test
    fun confirmingWithoutAPendingSwapDoesNothing() {
        val vm = newViewModel()
        vm.startMatch()
        vm.confirmDecidingSetSideSwapDone()
        assertTrue(vm.s.isMatchRunning)
    }

    @Test
    fun thePlayedClockStopsDuringTheSwapConfirmation() {
        val vm = newViewModel()
        vm.toDecidingSetFourTwo()
        advanceClock(10)
        vm.score(1)
        advanceClock(60)
        assertEquals(10_000L, vm.getElapsedPlayedMs())
        vm.confirmDecidingSetSideSwapDone()
        advanceClock(3)
        assertEquals(13_000L, vm.getElapsedPlayedMs())
    }

    // ----- Manual score edits -----

    @Test
    fun scoresCanOnlyBeEditedWhilePaused() {
        val vm = newViewModel()
        assertFalse("not started yet", vm.updatePausedScore(3, 2))
        vm.startMatch()
        assertFalse("running", vm.updatePausedScore(3, 2))
        assertFalse(vm.updatePausedMatchScores(emptyList(), 3, 2))

        vm.pauseMatch()
        assertTrue(vm.updatePausedScore(3, 2))
        assertEquals(3, vm.s.score1)
        assertEquals(2, vm.s.score2)
    }

    @Test
    fun editedScoresCanBeUndone() {
        val vm = newViewModel()
        vm.startMatch()
        vm.score(1)
        vm.pauseMatch()
        vm.updatePausedScore(7, 4)
        vm.undo()
        assertEquals(1, vm.s.score1)
        assertEquals(0, vm.s.score2)
    }

    @Test
    fun invalidEditsAreRejectedAndChangeNothing() {
        val vm = newViewModel()
        vm.startMatch()
        vm.score(1)
        vm.pauseMatch()
        assertFalse(vm.updatePausedScore(-1, 0))
        assertFalse(vm.updatePausedScore(11, 3))
        assertFalse(vm.updatePausedMatchScores(listOf(10 to 9), 0, 0))
        assertEquals(1, vm.s.score1)
    }

    @Test
    fun editingSetsUpdatesTheSetCountAndServer() {
        val vm = newViewModel()
        vm.startMatch()
        vm.pauseMatch()
        assertTrue(vm.updatePausedMatchScores(listOf(11 to 5), 2, 1))
        assertEquals(1, vm.s.sets1)
        assertEquals(listOf(11 to 5), vm.s.setResults)
        assertEquals(2, vm.s.score1)
        assertEquals(1, vm.s.server) // player 2 serves first in set 2; after three points it is player 1 turn
    }

    // ----- Finished matches are stored -----

    @Test
    fun aWonMatchIsStoredInTheHistory() {
        val vm = newViewModel()
        vm.setupMatch("Ann", "Bea", firstServer = 2, bestOfSets = 1)
        vm.setTournamentName("Cup")
        vm.setMatchRound("Final")
        vm.startMatch()
        advanceClock(90)
        vm.winSet(2)

        assertEquals(2, vm.s.matchWinner)
        val stored = storedMatches(expected = 1).single()
        assertEquals("Cup", stored.tournamentName)
        assertEquals("Final", stored.matchRound)
        assertEquals("Ann", stored.player1Name)
        assertEquals("Bea", stored.player2Name)
        assertEquals(2, stored.winner)
        assertEquals(0, stored.sets1)
        assertEquals(1, stored.sets2)
        assertEquals(1, stored.bestOfSets)
        assertEquals("0-11", stored.setResultsJson)
        assertEquals("22222222222", stored.pointHistoryJson)
        assertEquals(2, stored.matchFirstServer)
        assertEquals(90_000L, stored.durationMs)
        assertEquals(GameViewModel.MATCH_MODE_SINGLES, stored.matchMode)
        assertTrue(stored.isDataValid)
        assertFalse(stored.isProtected)
    }

    @Test
    fun setsAreStoredAsCommaSeparatedScoresAndPointsPerSet() {
        val vm = newViewModel()
        vm.setupMatch("Ann", "Bea", firstServer = 1, bestOfSets = 3)
        vm.startMatch()
        vm.winSet(1)
        vm.winSet(2)
        vm.winSet(1)

        val stored = storedMatches(expected = 1).single()
        assertEquals("11-0,0-11,11-0", stored.setResultsJson)
        assertEquals(
            "11111111111,22222222222,11111111111",
            stored.pointHistoryJson,
        )
        assertEquals(2, stored.sets1)
        assertEquals(1, stored.sets2)
    }

    @Test
    fun aMatchDecidedByAManualEditIsStored() {
        val vm = newViewModel()
        vm.setupMatch("Ann", "Bea", firstServer = 1, bestOfSets = 1)
        vm.startMatch()
        vm.pauseMatch()
        assertTrue(vm.updatePausedMatchScores(emptyList(), 11, 5))

        assertEquals(1, vm.s.matchWinner)
        val stored = storedMatches(expected = 1).single()
        assertEquals("11-5", stored.setResultsJson)
        assertEquals("", stored.pointHistoryJson.filter { it != ',' })
    }

    @Test
    fun aDoublesMatchIsStoredWithItsMode() {
        val vm = newViewModel()
        vm.setupMatch("a", "b", 1, 1, GameViewModel.MATCH_MODE_DOUBLES, "Ann", "Bea", "Cid", "Dan")
        vm.startMatch()
        vm.winSet(1)
        val stored = storedMatches(expected = 1).single()
        assertEquals(GameViewModel.MATCH_MODE_DOUBLES, stored.matchMode)
        assertTrue(stored.player1Name.contains(" / "))
    }

    @Test
    fun anUnfinishedMatchIsNotStored() {
        val vm = newViewModel()
        vm.setupMatch("Ann", "Bea", firstServer = 1, bestOfSets = 3)
        vm.startMatch()
        vm.winSet(1)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(storedMatches(expected = 1).isEmpty())
    }

    // ----- Saving and resuming a match in progress -----

    @Test
    fun aMatchInProgressIsSavedAfterEveryPoint() {
        val vm = newViewModel()
        assertFalse(prefs.contains("match_in_progress"))
        vm.startMatch()
        vm.score(1)
        assertTrue(prefs.contains("match_in_progress"))
    }

    @Test
    fun aSavedMatchCanBeResumedInANewViewModel() {
        val first = newViewModel()
        first.setupMatch("Ann", "Bea", firstServer = 2, bestOfSets = 3)
        first.startMatch()
        first.score(1, 2, 1)
        advanceClock(30)
        first.pauseMatch()

        val second = newViewModel()
        assertEquals(true, second.ongoingMatchExists.value)
        assertFalse(second.hasRespondedToOngoingMatch)

        second.resumeMatch()
        assertEquals(2, second.s.score1)
        assertEquals(1, second.s.score2)
        assertEquals("Ann", second.s.player1Name)
        assertEquals(3, second.s.bestOfSets)
        assertFalse("resumes paused", second.s.isMatchRunning)
        assertTrue(second.s.hasMatchStarted)
        assertEquals(30_000L, second.getElapsedPlayedMs())
        assertEquals(false, second.ongoingMatchExists.value)
        assertTrue(second.hasRespondedToOngoingMatch)

        // The first server of the match is restored too, so later sets are served correctly.
        second.startMatch()
        repeat(9) { second.score(1) } // 11-2 wins set 1
        assertEquals(1, second.s.sets1)
        assertEquals(1, second.s.server) // player 2 served first in set 1
    }

    @Test
    fun discardingASavedMatchRemovesIt() {
        val first = newViewModel()
        first.startMatch()
        first.score(1)

        val second = newViewModel()
        second.discardMatch()
        assertFalse(prefs.contains("match_in_progress"))
        assertTrue(second.hasRespondedToOngoingMatch)
        assertEquals(false, second.ongoingMatchExists.value)
        assertEquals(0, second.s.score1)

        assertEquals(false, newViewModel().ongoingMatchExists.value)
    }

    @Test
    fun aMatchThatHasNotStartedIsNotSaved() {
        val vm = newViewModel()
        vm.saveMatchInProgress()
        assertFalse(prefs.contains("match_in_progress"))
    }

    @Test
    fun aFinishedMatchIsNoLongerSavedAsInProgress() {
        val vm = newViewModel()
        vm.setupMatch("A", "B", firstServer = 1, bestOfSets = 1)
        vm.startMatch()
        assertTrue(prefs.contains("match_in_progress"))
        vm.winSet(1)
        assertFalse(prefs.contains("match_in_progress"))
    }

    @Test
    fun resettingRemovesTheSavedMatch() {
        val vm = newViewModel()
        vm.startMatch()
        vm.score(1)
        vm.resetMatch()
        assertFalse(prefs.contains("match_in_progress"))
    }

    @Test
    fun aCorruptSavedMatchIsDiscardedInsteadOfCrashing() {
        prefs.edit().putString("match_in_progress", "not json at all {").commit()
        val vm = newViewModel()
        assertEquals(true, vm.ongoingMatchExists.value)
        vm.resumeMatch()
        assertEquals(0, vm.s.score1)
        assertFalse(prefs.contains("match_in_progress"))
        assertTrue(vm.hasRespondedToOngoingMatch)
    }

    @Test
    fun resumingWithNothingSavedDoesNothing() {
        val vm = newViewModel()
        vm.resumeMatch()
        assertFalse(vm.hasRespondedToOngoingMatch)
    }
}
