package com.example.tabletennisscore.history

import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import com.example.tabletennisscore.GameViewModel
import com.example.tabletennisscore.R
import com.example.tabletennisscore.data.MatchResult
import com.example.tabletennisscore.dialogs.dp
import com.example.tabletennisscore.firstServerOfSet

private const val TEAM_SEPARATOR = " / "

/** Where the serve statistics block starts in the headers, as a fraction of the width. */
private const val STATS_GUIDELINE_PERCENT = 0.68f

/**
 * Builds the point-by-point view of a finished match into [container]: a match header with the winner,
 * set scores and serve statistics, then for every set a small header and the score after each point.
 */
internal class PointDetailsRenderer(
    private val activity: AppCompatActivity,
    private val container: LinearLayout,
) {

    private fun Int.dp() = activity.dp(this)

    private fun color(id: Int) = ContextCompat.getColor(activity, id)

    fun render(result: MatchResult) {
        container.removeAllViews()

        val setsPoints = result.pointHistoryJson.split(",").filter { it.isNotBlank() }
        if (setsPoints.isEmpty()) {
            addEmptyMessage()
            return
        }

        val stats = serveStatsForMatch(setsPoints, result.matchFirstServer)
        addMatchHeader(result, stats.total)
        if (!result.isDataValid) addInvalidDataNotice()

        setsPoints.forEachIndexed { index, pointsStr ->
            addSetHeaderWithStats(index + 1, stats.perSet[index], result)
            addPointsProgression(pointsStr, result, firstServerOfSet(result.matchFirstServer, index))
        }
    }

    private fun addEmptyMessage() {
        container.addView(
            TextView(activity).apply {
                text = activity.getString(R.string.point_history_empty)
                setTextColor(color(R.color.score_text))
                alpha = 0.5f
                gravity = Gravity.CENTER
                setPadding(0, 48.dp(), 0, 0)
            },
        )
    }

    private fun addInvalidDataNotice() {
        container.addView(
            TextView(activity).apply {
                text = activity.getString(R.string.history_data_invalid_notice)
                setTextColor(color(R.color.loss_vibrant))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(16.dp(), 0, 16.dp(), 8.dp())
                alpha = 0.9f
            },
        )
    }

    // ----- Match header -----

    /** Header layout: [Tournament (left)] [Winner (center)] [Serve stats (right)]. */
    private fun addMatchHeader(result: MatchResult, totals: PlayersServeStats) {
        val header = headerLayout(topPaddingDp = 12, bottomPaddingDp = 16)

        val tournament = result.tournamentName.takeIf { it.isNotBlank() }?.let { name ->
            tournamentView(name).also { header.addView(it) }
        }
        val winner = winnerView(result).also { header.addView(it) }
        val setsSummary = summaryText(activity.matchScoreText(result.sets1, result.sets2, result.winner), 13f)
            .also { header.addView(it) }
        val allSetsSummary = summaryText(activity.allSetsScoreText(result.setResultsJson, result.winner), 11f)
            .also { header.addView(it) }

        val isDoubles = result.matchMode == GameViewModel.MATCH_MODE_DOUBLES
        val statsBlock = serveStatsBlock(
            lines = statLines(result, totals, splitTeams = isDoubles),
            lineAlpha = 0.8f,
        ) { name, stats ->
            activity.getString(
                R.string.serve_win_pct_label_with_count,
                name, stats.percentage, stats.pointsWonOnServe, stats.totalServes,
            )
        }.also { header.addView(it) }

        constrainMatchHeader(header, winner, setsSummary, allSetsSummary, statsBlock, tournament)
        container.addView(header)
    }

    private fun headerLayout(topPaddingDp: Int, bottomPaddingDp: Int) = ConstraintLayout(activity).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        setPadding(16.dp(), topPaddingDp.dp(), 16.dp(), bottomPaddingDp.dp())
        clipToPadding = false
    }

    private fun tournamentView(name: String) = TextView(activity).apply {
        id = View.generateViewId()
        text = name
        setTextColor(color(R.color.player_name))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        alpha = 0.6f
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER
    }

    private fun winnerView(result: MatchResult): View {
        val winnerName = if (result.winner == 1) result.player1Name else result.player2Name
        val isDoubles = result.matchMode == GameViewModel.MATCH_MODE_DOUBLES
        return if (isDoubles && winnerName.contains(TEAM_SEPARATOR)) {
            doublesWinnerView(winnerName)
        } else {
            TextView(activity).apply {
                id = View.generateViewId()
                text = activity.getString(R.string.history_winner_only, winnerName)
                setTextColor(color(R.color.score_text))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
            }
        }
    }

    /** A large trophy icon next to the names of both winning players. */
    private fun doublesWinnerView(winnerName: String): LinearLayout {
        val trophy = TextView(activity).apply {
            text = "🏆"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
            includeFontPadding = false
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = 12.dp() }
        }
        val names = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            winnerName.split(TEAM_SEPARATOR).forEach { playerName ->
                addView(
                    TextView(activity).apply {
                        text = playerName
                        setTextColor(color(R.color.score_text))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                        setTypeface(null, Typeface.BOLD)
                        gravity = Gravity.START
                        includeFontPadding = false
                    },
                )
            }
        }
        return LinearLayout(activity).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(trophy)
            addView(names)
        }
    }

    private fun summaryText(content: CharSequence, sizeSp: Float) = TextView(activity).apply {
        id = View.generateViewId()
        text = content
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        alpha = 0.9f
        gravity = Gravity.CENTER
    }

    private fun constrainMatchHeader(
        header: ConstraintLayout,
        winner: View,
        setsSummary: View,
        allSetsSummary: View,
        statsBlock: View,
        tournament: View?,
    ) {
        val parent = ConstraintSet.PARENT_ID
        val set = ConstraintSet()
        set.clone(header)

        // Winner -> absolute top center
        set.connect(winner.id, ConstraintSet.START, parent, ConstraintSet.START)
        set.connect(winner.id, ConstraintSet.END, parent, ConstraintSet.END)
        set.connect(winner.id, ConstraintSet.TOP, parent, ConstraintSet.TOP)

        // Sets summary -> centered under winner
        set.connect(setsSummary.id, ConstraintSet.START, parent, ConstraintSet.START)
        set.connect(setsSummary.id, ConstraintSet.END, parent, ConstraintSet.END)
        set.connect(setsSummary.id, ConstraintSet.TOP, winner.id, ConstraintSet.BOTTOM)

        // All sets summary -> centered under sets summary
        set.connect(allSetsSummary.id, ConstraintSet.START, parent, ConstraintSet.START)
        set.connect(allSetsSummary.id, ConstraintSet.END, parent, ConstraintSet.END)
        set.connect(allSetsSummary.id, ConstraintSet.TOP, setsSummary.id, ConstraintSet.BOTTOM)
        set.connect(allSetsSummary.id, ConstraintSet.BOTTOM, parent, ConstraintSet.BOTTOM)

        // Serve stats -> start at the guideline, spanning both summary lines
        val statsGuidelineId = View.generateViewId()
        set.create(statsGuidelineId, ConstraintSet.VERTICAL_GUIDELINE)
        set.setGuidelinePercent(statsGuidelineId, STATS_GUIDELINE_PERCENT)
        set.connect(statsBlock.id, ConstraintSet.START, statsGuidelineId, ConstraintSet.START)
        set.connect(statsBlock.id, ConstraintSet.TOP, setsSummary.id, ConstraintSet.TOP)
        set.connect(statsBlock.id, ConstraintSet.BOTTOM, allSetsSummary.id, ConstraintSet.BOTTOM)

        // Tournament -> pinned position on the left
        tournament?.let {
            set.connect(it.id, ConstraintSet.START, parent, ConstraintSet.START)
            set.connect(it.id, ConstraintSet.END, parent, ConstraintSet.END)
            set.connect(it.id, ConstraintSet.TOP, setsSummary.id, ConstraintSet.TOP)
            set.connect(it.id, ConstraintSet.BOTTOM, allSetsSummary.id, ConstraintSet.BOTTOM)
            set.setHorizontalBias(it.id, 0.05f)
            set.constrainWidth(it.id, ConstraintSet.MATCH_CONSTRAINT)
            set.constrainMaxWidth(it.id, 130.dp())
        }

        set.applyTo(header)
    }

    // ----- Serve statistics (shared by the match header and the set headers) -----

    /** The winner's line(s) first, then the loser's. Teams are split into one line per player when [splitTeams]. */
    private fun statLines(
        result: MatchResult,
        stats: PlayersServeStats,
        splitTeams: Boolean,
    ): List<Pair<String, ServeStats>> {
        val player1 = result.player1Name to stats.player1
        val player2 = result.player2Name to stats.player2
        val ordered = if (result.winner == 1) listOf(player1, player2) else listOf(player2, player1)
        return ordered.flatMap { (name, playerStats) ->
            if (splitTeams && name.contains(TEAM_SEPARATOR)) {
                name.split(TEAM_SEPARATOR).map { it to playerStats }
            } else {
                listOf(name to playerStats)
            }
        }
    }

    /** "Serve win:" followed by one small line per entry in [lines]. */
    private fun serveStatsBlock(
        lines: List<Pair<String, ServeStats>>,
        lineAlpha: Float,
        lineText: (String, ServeStats) -> String,
    ): LinearLayout {
        val label = TextView(activity).apply {
            id = View.generateViewId()
            text = activity.getString(R.string.serve_win_label)
            setTextColor(color(R.color.history_loser_text))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setPadding(0, 0, 4.dp(), 0)
        }
        val playerLines = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            lines.forEach { (name, stats) ->
                addView(
                    TextView(activity).apply {
                        text = lineText(name, stats)
                        setTextColor(color(R.color.score_text))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                        includeFontPadding = false
                        alpha = lineAlpha
                    },
                )
            }
        }
        return LinearLayout(activity).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            addView(label)
            addView(playerLines)
        }
    }

    // ----- One set -----

    private fun addSetHeaderWithStats(setNumber: Int, stats: PlayersServeStats, result: MatchResult) {
        val header = headerLayout(topPaddingDp = 8, bottomPaddingDp = 0)

        val setLabel = TextView(activity).apply {
            id = View.generateViewId()
            text = activity.getString(R.string.point_history_set_label, setNumber)
            setTextColor(color(R.color.player_name))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        header.addView(setLabel)

        val splitTeams = result.player1Name.contains(TEAM_SEPARATOR) || result.player2Name.contains(TEAM_SEPARATOR)
        val statsBlock = serveStatsBlock(
            lines = statLines(result, stats, splitTeams),
            lineAlpha = 0.7f,
        ) { name, playerStats ->
            activity.getString(R.string.serve_win_pct_label_simple, name, playerStats.percentage)
        }
        header.addView(statsBlock)

        val parent = ConstraintSet.PARENT_ID
        val set = ConstraintSet()
        set.clone(header)
        set.connect(setLabel.id, ConstraintSet.START, parent, ConstraintSet.START)
        set.connect(setLabel.id, ConstraintSet.END, parent, ConstraintSet.END)
        set.connect(setLabel.id, ConstraintSet.TOP, parent, ConstraintSet.TOP)

        // Guideline at the same place as in the match header
        val guidelineId = View.generateViewId()
        set.create(guidelineId, ConstraintSet.VERTICAL_GUIDELINE)
        set.setGuidelinePercent(guidelineId, STATS_GUIDELINE_PERCENT)
        set.connect(statsBlock.id, ConstraintSet.START, guidelineId, ConstraintSet.START)
        set.connect(statsBlock.id, ConstraintSet.TOP, setLabel.id, ConstraintSet.TOP)
        set.applyTo(header)

        container.addView(header)
    }

    /** The score after every point of a set, with the match winner on the top row. */
    private fun addPointsProgression(pointsStr: String, result: MatchResult, setFirstServer: Int) {
        val matchWinner = result.winner
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8.dp(), 0, 8.dp(), 12.dp())
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(setLabels(pointsStr, result, setFirstServer))

        val scores = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        var s1 = 0
        var s2 = 0
        var deuceReached = false
        pointsStr.forEachIndexed { index, char ->
            val isLast = index == pointsStr.length - 1
            val pointWinner = if (char == '1') 1 else 2
            if (pointWinner == 1) s1++ else s2++

            scores.addView(scoreColumn(s1, s2, pointWinner, matchWinner, isLast))

            // Add separator at 10-10
            if (!deuceReached && s1 == 10 && s2 == 10) {
                deuceReached = true
                scores.addView(deuceSeparator())
            }
        }

        row.addView(
            HorizontalScrollView(activity).apply {
                isHorizontalScrollBarEnabled = false
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(scores)
            },
        )
        container.addView(row)
    }

    /** The two name labels (match winner on top) with each player's final score in the set. */
    private fun setLabels(pointsStr: String, result: MatchResult, setFirstServer: Int): LinearLayout {
        val finalS1 = pointsStr.count { it == '1' }
        val finalS2 = pointsStr.count { it == '2' }
        val topPlayer = if (result.winner == 1) 1 else 2
        val bottomPlayer = if (result.winner == 1) 2 else 1

        fun labelRow(player: Int) = createLabelRow(
            name = if (player == 1) result.player1Name else result.player2Name,
            isFirstServer = player == setFirstServer,
            setScore = if (player == 1) finalS1 else finalS2,
            isWinner = if (player == 1) finalS1 > finalS2 else finalS2 > finalS1,
            matchMode = result.matchMode,
        )

        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = 8.dp() }
            addView(labelRow(topPlayer))
            addView(labelRow(bottomPlayer))
        }
    }

    /** One column of the progression: both players' score after a point, match winner on top. */
    private fun scoreColumn(s1: Int, s2: Int, pointWinner: Int, matchWinner: Int, isLast: Boolean) =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = if (isLast) 24.dp() else 12.dp() }

            if (matchWinner == 1) {
                addView(createScoreTextView(s1.toString(), pointWinner == 1, isLast))
                addView(createScoreTextView(s2.toString(), pointWinner == 2, isLast))
            } else {
                addView(createScoreTextView(s2.toString(), pointWinner == 2, isLast))
                addView(createScoreTextView(s1.toString(), pointWinner == 1, isLast))
            }
        }

    private fun deuceSeparator() = TextView(activity).apply {
        text = "|"
        setTextColor(color(R.color.divider))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        setPadding(0, 0, 12.dp(), 0)
        gravity = Gravity.CENTER
    }

    // ----- Name label rows -----

    private fun createLabelRow(
        name: String,
        isFirstServer: Boolean,
        setScore: Int,
        isWinner: Boolean,
        matchMode: String,
    ): View {
        return if (matchMode == GameViewModel.MATCH_MODE_DOUBLES && name.contains(TEAM_SEPARATOR)) {
            doublesLabelRow(name, isFirstServer, setScore, isWinner)
        } else {
            singlesLabelRow(name, isFirstServer, setScore, isWinner)
        }
    }

    /** For doubles, one shared serve ball in front of the two-player block. */
    private fun doublesLabelRow(name: String, isFirstServer: Boolean, setScore: Int, isWinner: Boolean) =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(185.dp(), LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(0, 2.dp(), 0, 2.dp())

            addView(
                ImageView(activity).apply {
                    val size = 20.dp()
                    layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = 8.dp() }
                    setImageResource(R.drawable.stigaperform40size128)
                    visibility = if (isFirstServer) View.VISIBLE else View.INVISIBLE
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                },
            )

            val playerNames = name.split(TEAM_SEPARATOR)
            addView(
                LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    playerNames.forEachIndexed { index, playerName ->
                        // One score for the pair: on the last name of the winners, the first name of the losers.
                        val showScore = (isWinner && index == playerNames.size - 1) || (!isWinner && index == 0)
                        addView(doublesPlayerRow(playerName, if (showScore) setScore else null, isWinner))
                    }
                },
            )
        }

    private fun doublesPlayerRow(playerName: String, setScore: Int?, isWinner: Boolean) =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            addView(nameLabel("$playerName: ", textSizeSp = 11f))
            if (setScore != null) addView(setScoreLabel(setScore, isWinner))
        }

    private fun singlesLabelRow(name: String, isFirstServer: Boolean, setScore: Int, isWinner: Boolean) =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            // Wide enough for the larger scores
            layoutParams = LinearLayout.LayoutParams(185.dp(), LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(0, 2.dp(), 0, 2.dp())

            addView(
                ImageView(activity).apply {
                    val size = 12.dp()
                    layoutParams = LinearLayout.LayoutParams(size, size).apply {
                        marginEnd = 8.dp()
                        topMargin = 1.dp() // Nudge down slightly for better visual alignment
                    }
                    setImageResource(R.drawable.stigaperform40size128)
                    visibility = if (isFirstServer) View.VISIBLE else View.INVISIBLE
                },
            )
            addView(nameLabel("$name: ", textSizeSp = 12f))
            addView(setScoreLabel(setScore, isWinner))
        }

    private fun nameLabel(label: String, textSizeSp: Float) = TextView(activity).apply {
        text = label
        setTextColor(color(R.color.player_name))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        includeFontPadding = false
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }

    /** "[ 7]" / "[11]": the final score of a set, highlighted for the winner. */
    private fun setScoreLabel(setScore: Int, isWinner: Boolean) = TextView(activity).apply {
        text = if (setScore < 10) "[ %d]".format(setScore) else "[%d]".format(setScore)
        setTextColor(color(if (isWinner) R.color.win_vibrant else R.color.loss_muted))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        typeface = Typeface.MONOSPACE
        includeFontPadding = false
        setPadding(4.dp(), 0, 0, 0)
    }

    private fun createScoreTextView(text: String, isPointWinner: Boolean, isLastPoint: Boolean) =
        TextView(activity).apply {
            this.text = text
            if (isLastPoint) {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTypeface(null, Typeface.BOLD)
                if (isPointWinner) {
                    setTextColor(color(R.color.sets_text))
                } else {
                    setTextColor(color(R.color.score_text))
                    alpha = 0.9f
                }
            } else {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                if (isPointWinner) {
                    setTextColor(color(R.color.sets_text))
                    setTypeface(null, Typeface.BOLD)
                } else {
                    setTextColor(color(R.color.history_loser_text))
                    setTypeface(null, Typeface.NORMAL)
                }
            }
            setPadding(0, 2.dp(), 0, 2.dp())
            gravity = Gravity.CENTER
            minWidth = 20.dp()
        }
}
