package com.example.tabletennisscore.history

import com.example.tabletennisscore.firstServerOfSet
import com.example.tabletennisscore.nextServer

/** How many of a player's service points they won. */
data class ServeStats(
    val pointsWonOnServe: Int = 0,
    val totalServes: Int = 0,
) {
    val percentage: Double
        get() = if (totalServes > 0) (pointsWonOnServe.toDouble() / totalServes * 100) else 0.0

    operator fun plus(other: ServeStats) = ServeStats(
        pointsWonOnServe = pointsWonOnServe + other.pointsWonOnServe,
        totalServes = totalServes + other.totalServes,
    )
}

/** Serve statistics of both players. */
data class PlayersServeStats(val player1: ServeStats, val player2: ServeStats) {
    operator fun plus(other: PlayersServeStats) = PlayersServeStats(player1 + other.player1, player2 + other.player2)
}

/** Serve statistics for a whole match: one entry per set, and the total. */
data class MatchServeStats(val perSet: List<PlayersServeStats>, val total: PlayersServeStats)

/**
 * Serve statistics for one set. [points] holds the winner of each point in order ('1' or '2')
 * and [setFirstServer] is the player who served first in that set.
 */
fun serveStatsForSet(points: String, setFirstServer: Int): PlayersServeStats {
    var p1Won = 0
    var p1Total = 0
    var p2Won = 0
    var p2Total = 0
    var s1 = 0
    var s2 = 0

    points.forEach { char ->
        val pointWinner = if (char == '1') 1 else 2
        val server = nextServer(s1, s2, s1 + s2, setFirstServer)
        if (server == 1) {
            p1Total++
            if (pointWinner == 1) p1Won++
        } else {
            p2Total++
            if (pointWinner == 2) p2Won++
        }
        if (pointWinner == 1) s1++ else s2++
    }
    return PlayersServeStats(ServeStats(p1Won, p1Total), ServeStats(p2Won, p2Total))
}

/** Serve statistics for a match, given the point history of each set and who served first in the match. */
fun serveStatsForMatch(pointsPerSet: List<String>, matchFirstServer: Int): MatchServeStats {
    val perSet = pointsPerSet.mapIndexed { setIndex, points ->
        serveStatsForSet(points, firstServerOfSet(matchFirstServer, setIndex))
    }
    val total = perSet.fold(PlayersServeStats(ServeStats(), ServeStats())) { sum, set -> sum + set }
    return MatchServeStats(perSet, total)
}
