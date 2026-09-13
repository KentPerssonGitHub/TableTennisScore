package com.example.tabletennisscore

/** Shared table-tennis scoring helpers used by both game logic and detail screens. */
fun nextServer(s1: Int, s2: Int, total: Int, firstServer: Int): Int {
    // At deuce (10-10 and beyond) service alternates every point.
    return if (s1 >= 10 && s2 >= 10) {
        val pointsSinceDeuce = (s1 - 10) + (s2 - 10)
        if (pointsSinceDeuce % 2 == 0) firstServer else otherPlayer(firstServer)
    } else {
        // Before deuce, service alternates every two points.
        val block = total / 2
        if (block % 2 == 0) firstServer else otherPlayer(firstServer)
    }
}

fun otherPlayer(p: Int): Int = if (p == 1) 2 else 1

