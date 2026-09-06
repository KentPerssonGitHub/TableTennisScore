package com.example.tabletennisscore

import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.example.tabletennisscore.data.MatchDatabase
import com.example.tabletennisscore.data.MatchResult
import com.example.tabletennisscore.databinding.ActivityPointDetailsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PointDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPointDetailsBinding
    private val dao by lazy { MatchDatabase.getInstance(this).matchResultDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPointDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        hideSystemBars()

        val matchId = intent.getIntExtra(EXTRA_MATCH_ID, -1)
        if (matchId == -1) {
            finish()
            return
        }

        binding.btnPointsBack.setOnClickListener { finish() }

        lifecycleScope.launch {
            dao.getById(matchId).collectLatest { result ->
                result?.let { renderDetails(it) }
            }
        }
    }

    private fun renderDetails(result: MatchResult) {
        binding.layoutPointsContainer.removeAllViews()

        val setsPoints = result.pointHistoryJson.split(",").filter { it.isNotBlank() }
        if (setsPoints.isEmpty()) {
            val tv = TextView(this).apply {
                text = getString(R.string.point_history_empty)
                setTextColor(ContextCompat.getColor(context, R.color.score_text))
                alpha = 0.5f
                gravity = Gravity.CENTER
                setPadding(0, 48.dp(), 0, 0)
            }
            binding.layoutPointsContainer.addView(tv)
            return
        }

        // 1. Calculate stats first
        val p1MatchStats = ServeStats()
        val p2MatchStats = ServeStats()
        val setStats = mutableListOf<Pair<ServeStats, ServeStats>>()

        setsPoints.forEachIndexed { setIndex, pointsStr ->
            val p1SetStats = ServeStats()
            val p2SetStats = ServeStats()
            val setFirstServer = currentSetFirstServer(setIndex, result.matchFirstServer)
            var s1 = 0
            var s2 = 0
            
            pointsStr.forEach { char ->
                val pointWinner = if (char == '1') 1 else 2
                val currentServer = nextServer(s1, s2, s1 + s2, setFirstServer)
                if (currentServer == 1) {
                    p1SetStats.totalServes++
                    if (pointWinner == 1) p1SetStats.pointsWonOnServe++
                } else {
                    p2SetStats.totalServes++
                    if (pointWinner == 2) p2SetStats.pointsWonOnServe++
                }
                if (pointWinner == 1) s1++ else s2++
            }
            setStats.add(p1SetStats to p2SetStats)
            p1MatchStats.pointsWonOnServe += p1SetStats.pointsWonOnServe
            p1MatchStats.totalServes += p1SetStats.totalServes
            p2MatchStats.pointsWonOnServe += p2SetStats.pointsWonOnServe
            p2MatchStats.totalServes += p2SetStats.totalServes
        }

        // 2. Header Layout: [Tournament (Left Half)] [Winner (Center)] [Stats (Right Half)]
        val headerLayout = ConstraintLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(16.dp(), 12.dp(), 16.dp(), 16.dp())
            clipToPadding = false
        }

        // Tournament Name
        val tvTournament = if (result.tournamentName.isNotBlank()) {
            TextView(this).apply {
                id = View.generateViewId()
                text = result.tournamentName
                setTextColor(ContextCompat.getColor(context, R.color.player_name))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                alpha = 0.6f
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
            }.also { headerLayout.addView(it) }
        } else null

        // Winner Title
        val winnerName = if (result.winner == 1) result.player1Name else result.player2Name
        val tvWinner = TextView(this).apply {
            id = View.generateViewId()
            text = getString(R.string.history_winner_only, winnerName)
            setTextColor(ContextCompat.getColor(context, R.color.score_text))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        headerLayout.addView(tvWinner)

        // Sets Summary
        val tvSetsSummary = TextView(this).apply {
            id = View.generateViewId()
            text = buildMatchScoreSpannable(result.sets1, result.sets2, result.winner)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            alpha = 0.9f
            gravity = Gravity.CENTER
        }
        headerLayout.addView(tvSetsSummary)

        // All Sets Scores Summary
        val tvAllSetsSummary = TextView(this).apply {
            id = View.generateViewId()
            text = buildAllSetsSpannable(result.setResultsJson, result.winner)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            alpha = 0.9f
            gravity = Gravity.CENTER
        }
        headerLayout.addView(tvAllSetsSummary)

        // Stats Block
        val statsContainer = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }
        
        val statsLabel = TextView(this).apply {
            id = View.generateViewId()
            text = getString(R.string.serve_win_label)
            setTextColor(ContextCompat.getColor(context, R.color.history_loser_text))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setPadding(0, 0, 4.dp(), 0)
        }
        statsContainer.addView(statsLabel)

        val playerStatsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
        }
        
        fun createSmallStat(playerName: String, stats: ServeStats) = TextView(this).apply {
            text = getString(R.string.serve_win_pct_label_with_count, playerName, stats.percentage, stats.pointsWonOnServe, stats.totalServes)
            setTextColor(ContextCompat.getColor(context, R.color.score_text))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            includeFontPadding = false
            alpha = 0.8f
        }

        playerStatsLayout.addView(if (result.winner == 1) createSmallStat(result.player1Name, p1MatchStats) 
                                   else createSmallStat(result.player2Name, p2MatchStats))
        playerStatsLayout.addView(if (result.winner == 1) createSmallStat(result.player2Name, p2MatchStats) 
                                   else createSmallStat(result.player1Name, p1MatchStats))
        statsContainer.addView(playerStatsLayout)
        headerLayout.addView(statsContainer)

        // Constraints
        val set = androidx.constraintlayout.widget.ConstraintSet()
        set.clone(headerLayout)
        
        // Winner -> absolute top center
        set.connect(tvWinner.id, androidx.constraintlayout.widget.ConstraintSet.START, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.START)
        set.connect(tvWinner.id, androidx.constraintlayout.widget.ConstraintSet.END, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.END)
        set.connect(tvWinner.id, androidx.constraintlayout.widget.ConstraintSet.TOP, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.TOP)

        // Sets Summary -> centered under Winner
        set.connect(tvSetsSummary.id, androidx.constraintlayout.widget.ConstraintSet.START, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.START)
        set.connect(tvSetsSummary.id, androidx.constraintlayout.widget.ConstraintSet.END, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.END)
        set.connect(tvSetsSummary.id, androidx.constraintlayout.widget.ConstraintSet.TOP, tvWinner.id, androidx.constraintlayout.widget.ConstraintSet.BOTTOM)

        // All Sets Summary -> centered under Sets Summary
        set.connect(tvAllSetsSummary.id, androidx.constraintlayout.widget.ConstraintSet.START, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.START)
        set.connect(tvAllSetsSummary.id, androidx.constraintlayout.widget.ConstraintSet.END, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.END)
        set.connect(tvAllSetsSummary.id, androidx.constraintlayout.widget.ConstraintSet.TOP, tvSetsSummary.id, androidx.constraintlayout.widget.ConstraintSet.BOTTOM)
        set.connect(tvAllSetsSummary.id, androidx.constraintlayout.widget.ConstraintSet.BOTTOM, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.BOTTOM)
        
        // Create Guideline at 68% for stats start
        val statsGuidelineId = View.generateViewId()
        set.create(statsGuidelineId, androidx.constraintlayout.widget.ConstraintSet.VERTICAL_GUIDELINE)
        set.setGuidelinePercent(statsGuidelineId, 0.68f)

        // Stats -> start at guideline
        set.connect(statsContainer.id, androidx.constraintlayout.widget.ConstraintSet.START, statsGuidelineId, androidx.constraintlayout.widget.ConstraintSet.START)
        set.connect(statsContainer.id, androidx.constraintlayout.widget.ConstraintSet.TOP, tvSetsSummary.id, androidx.constraintlayout.widget.ConstraintSet.TOP)
        set.connect(statsContainer.id, androidx.constraintlayout.widget.ConstraintSet.BOTTOM, tvAllSetsSummary.id, androidx.constraintlayout.widget.ConstraintSet.BOTTOM)

        // Tournament -> pinned position on left
        tvTournament?.let {
            set.connect(it.id, androidx.constraintlayout.widget.ConstraintSet.START, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.START)
            set.connect(it.id, androidx.constraintlayout.widget.ConstraintSet.END, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.END)
            set.connect(it.id, androidx.constraintlayout.widget.ConstraintSet.TOP, tvSetsSummary.id, androidx.constraintlayout.widget.ConstraintSet.TOP)
            set.connect(it.id, androidx.constraintlayout.widget.ConstraintSet.BOTTOM, tvAllSetsSummary.id, androidx.constraintlayout.widget.ConstraintSet.BOTTOM)
            set.setHorizontalBias(it.id, 0.05f)
            set.constrainWidth(it.id, androidx.constraintlayout.widget.ConstraintSet.MATCH_CONSTRAINT)
            set.constrainMaxWidth(it.id, 130.dp())
        }

        set.applyTo(headerLayout)
        binding.layoutPointsContainer.addView(headerLayout)

        if (!result.isDataValid) {
            val warning = TextView(this).apply {
                text = getString(R.string.history_data_invalid_notice)
                setTextColor(ContextCompat.getColor(context, R.color.loss_vibrant))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(16.dp(), 0, 16.dp(), 8.dp())
                alpha = 0.9f
            }
            binding.layoutPointsContainer.addView(warning)
        }

        setsPoints.forEachIndexed { index, pointsStr ->
            val (p1S, p2S) = setStats[index]
            val setFirstServer = currentSetFirstServer(index, result.matchFirstServer)
            addSetHeaderWithStats(index + 1, p1S, p2S, result.player1Name, result.player2Name, result.winner)
            addPointsProgression(pointsStr, result.player1Name, result.player2Name, result.winner, setFirstServer)
        }
    }

    private fun buildMatchScoreSpannable(sets1: Int, sets2: Int, matchWinner: Int): CharSequence {
        val builder = SpannableStringBuilder("(")
        val winColor = ContextCompat.getColor(this, R.color.win_vibrant)
        val lossColor = ContextCompat.getColor(this, R.color.loss_vibrant)
        val normalColor = ContextCompat.getColor(this, R.color.player_name)
        
        // Match winner score always on the left
        val leftSets = if (matchWinner == 1) sets1 else sets2
        val rightSets = if (matchWinner == 1) sets2 else sets1
        
        // Score 1 (Winner)
        val start1 = builder.length
        builder.append(leftSets.toString())
        if (leftSets > 0) {
            builder.setSpan(ForegroundColorSpan(winColor), start1, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else {
            builder.setSpan(ForegroundColorSpan(normalColor), start1, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        
        builder.append(" - ")
        
        // Score 2 (Loser)
        val start2 = builder.length
        builder.append(rightSets.toString())
        if (rightSets > 0) {
            builder.setSpan(ForegroundColorSpan(lossColor), start2, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else {
            builder.setSpan(ForegroundColorSpan(normalColor), start2, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        
        builder.append(")")
        return builder
    }

    private fun buildAllSetsSpannable(setResultsJson: String, matchWinner: Int): CharSequence {
        val builder = SpannableStringBuilder("(")
        val winColor = ContextCompat.getColor(this, R.color.win_vibrant)
        val lossColor = ContextCompat.getColor(this, R.color.loss_vibrant)
        val normalColor = ContextCompat.getColor(this, R.color.score_text)
        
        val sets = setResultsJson.split(",").filter { it.isNotBlank() }
        sets.forEachIndexed { i, s ->
            val parts = s.split("-")
            if (parts.size == 2) {
                val rawS1 = parts[0].toIntOrNull() ?: 0
                val rawS2 = parts[1].toIntOrNull() ?: 0
                
                // Match winner score always on the left
                val leftScore = if (matchWinner == 1) rawS1 else rawS2
                val rightScore = if (matchWinner == 1) rawS2 else rawS1
                
                // Score 1
                val start1 = builder.length
                builder.append(leftScore.toString())
                if (leftScore > rightScore) {
                    builder.setSpan(ForegroundColorSpan(winColor), start1, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                } else if (rightScore > leftScore) {
                    // Match winner lost this set
                    builder.setSpan(ForegroundColorSpan(normalColor), start1, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                } else {
                    builder.setSpan(ForegroundColorSpan(normalColor), start1, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                
                builder.append(" - ")
                
                // Score 2
                val start2 = builder.length
                builder.append(rightScore.toString())
                if (rightScore > leftScore) {
                    builder.setSpan(ForegroundColorSpan(lossColor), start2, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                } else {
                    builder.setSpan(ForegroundColorSpan(normalColor), start2, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            if (i < (sets.size - 1)) builder.append(", ")
        }
        builder.append(")")
        return builder
    }

    private fun nextServer(s1: Int, s2: Int, total: Int, firstServer: Int): Int {
        return if ((s1 >= 10 && s2 >= 10)) {
            val pointsSinceDeuce = (s1 - 10) + (s2 - 10)
            if (pointsSinceDeuce % 2 == 0) firstServer else otherPlayer(firstServer)
        } else {
            val block = total / 2
            if (block % 2 == 0) firstServer else otherPlayer(firstServer)
        }
    }

    private fun otherPlayer(p: Int) = if (p == 1) 2 else 1

    private fun currentSetFirstServer(completedSetCount: Int, matchFirstServer: Int): Int {
        return if (completedSetCount % 2 == 0) matchFirstServer else otherPlayer(matchFirstServer)
    }

    private data class ServeStats(
        var pointsWonOnServe: Int = 0,
        var totalServes: Int = 0,
    ) {
        val percentage: Double
            get() = if (totalServes > 0) (pointsWonOnServe.toDouble() / totalServes * 100) else 0.0
    }

    private fun addSetHeaderWithStats(
        setNumber: Int,
        p1S: ServeStats,
        p2S: ServeStats,
        p1Name: String,
        p2Name: String,
        matchWinner: Int
    ) {
        val headerLayout = ConstraintLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(16.dp(), 8.dp(), 16.dp(), 0)
            clipToPadding = false
        }

        // Set Label
        val tvSet = TextView(this).apply {
            id = View.generateViewId()
            text = getString(R.string.point_history_set_label, setNumber)
            setTextColor(ContextCompat.getColor(context, R.color.player_name))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        headerLayout.addView(tvSet)

        // Stats Block
        val statsContainer = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }
        
        val statsLabel = TextView(this).apply {
            id = View.generateViewId()
            text = getString(R.string.serve_win_label)
            setTextColor(ContextCompat.getColor(context, R.color.history_loser_text))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setPadding(0, 0, 4.dp(), 0)
        }
        statsContainer.addView(statsLabel)

        val playerStatsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
        }

        fun smallStat(name: String, stats: ServeStats) = TextView(this).apply {
            text = getString(R.string.serve_win_pct_label_simple, name, stats.percentage)
            setTextColor(ContextCompat.getColor(context, R.color.score_text))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            includeFontPadding = false
            alpha = 0.7f
        }

        playerStatsLayout.addView(if (matchWinner == 1) smallStat(p1Name, p1S) else smallStat(p2Name, p2S))
        playerStatsLayout.addView(if (matchWinner == 1) smallStat(p2Name, p2S) else smallStat(p1Name, p1S))
        statsContainer.addView(playerStatsLayout)
        headerLayout.addView(statsContainer)

        val set = androidx.constraintlayout.widget.ConstraintSet()
        set.clone(headerLayout)
        
        set.connect(tvSet.id, androidx.constraintlayout.widget.ConstraintSet.START, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.START)
        set.connect(tvSet.id, androidx.constraintlayout.widget.ConstraintSet.END, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.END)
        set.connect(tvSet.id, androidx.constraintlayout.widget.ConstraintSet.TOP, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.TOP)
        set.connect(tvSet.id, androidx.constraintlayout.widget.ConstraintSet.BOTTOM, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.BOTTOM)
        
        // Guideline at same 68% for set stats
        val setStatsGuidelineId = View.generateViewId()
        set.create(setStatsGuidelineId, androidx.constraintlayout.widget.ConstraintSet.VERTICAL_GUIDELINE)
        set.setGuidelinePercent(setStatsGuidelineId, 0.68f)

        // Stats -> start at guideline
        set.connect(statsContainer.id, androidx.constraintlayout.widget.ConstraintSet.START, setStatsGuidelineId, androidx.constraintlayout.widget.ConstraintSet.START)
        set.connect(statsContainer.id, androidx.constraintlayout.widget.ConstraintSet.TOP, tvSet.id, androidx.constraintlayout.widget.ConstraintSet.TOP)
        set.connect(statsContainer.id, androidx.constraintlayout.widget.ConstraintSet.BOTTOM, tvSet.id, androidx.constraintlayout.widget.ConstraintSet.BOTTOM)

        set.applyTo(headerLayout)
        binding.layoutPointsContainer.addView(headerLayout)
    }

    private fun addPointsProgression(pointsStr: String, p1Name: String, p2Name: String, matchWinner: Int, setFirstServer: Int) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8.dp(), 0, 8.dp(), 12.dp())
            gravity = Gravity.CENTER_VERTICAL
        }

        var finalS1 = 0
        var finalS2 = 0
        pointsStr.forEach { char ->
            if (char == '1') finalS1++ else if (char == '2') finalS2++
        }

        // Labels Column - Match winner on top for all sets
        val labelsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 8.dp() }
        }
        
        val topPlayer = if (matchWinner == 1) 1 else 2
        val bottomPlayer = if (matchWinner == 1) 2 else 1
        
        labelsLayout.addView(
            createLabelRow(
                if (topPlayer == 1) p1Name else p2Name, 
                topPlayer == setFirstServer,
                if (topPlayer == 1) finalS1 else finalS2,
                if (topPlayer == 1) finalS1 > finalS2 else finalS2 > finalS1
            )
        )
        labelsLayout.addView(
            createLabelRow(
                if (bottomPlayer == 1) p1Name else p2Name, 
                bottomPlayer == setFirstServer,
                if (bottomPlayer == 1) finalS1 else finalS2,
                if (bottomPlayer == 1) finalS1 > finalS2 else finalS2 > finalS1
            )
        )
        container.addView(labelsLayout)

        // Scrollable Scores
        val scrollView = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        val scoresLayout = LinearLayout(this).apply {
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

            // Add scores
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { 
                    marginEnd = if (isLast) 24.dp() else 12.dp() 
                }
            }
            
            // Match winner row score on top
            if (matchWinner == 1) {
                col.addView(createScoreTextView(s1.toString(), pointWinner == 1, isLast))
                col.addView(createScoreTextView(s2.toString(), pointWinner == 2, isLast))
            } else {
                col.addView(createScoreTextView(s2.toString(), pointWinner == 2, isLast))
                col.addView(createScoreTextView(s1.toString(), pointWinner == 1, isLast))
            }
            scoresLayout.addView(col)

            // Add separator at 10-10
            if (!deuceReached && s1 == 10 && s2 == 10) {
                deuceReached = true
                val sep = TextView(this).apply {
                    text = "|"
                    setTextColor(ContextCompat.getColor(context, R.color.divider))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                    setPadding(0, 0, 12.dp(), 0)
                    gravity = Gravity.CENTER
                }
                scoresLayout.addView(sep)
            }
        }

        scrollView.addView(scoresLayout)
        container.addView(scrollView)
        binding.layoutPointsContainer.addView(container)
    }


    private fun createLabelRow(name: String, isFirstServer: Boolean, setScore: Int, isWinner: Boolean): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            // Increased width to 185dp to accommodate larger scores
            layoutParams = LinearLayout.LayoutParams(185.dp(), LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(0, 2.dp(), 0, 2.dp())

            // Ball icon
            val ivBall = ImageView(this@PointDetailsActivity).apply {
                val size = (12 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = 8.dp() 
                    topMargin = 1.dp() // Nudge down slightly for better visual alignment
                }
                setImageResource(R.drawable.stigaperform40size128)
                visibility = if (isFirstServer) View.VISIBLE else View.INVISIBLE
            }
            addView(ivBall)

            // Name
            val tvName = TextView(this@PointDetailsActivity).apply {
                val label = "$name: "
                text = label
                setTextColor(ContextCompat.getColor(context, R.color.player_name))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                includeFontPadding = false
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            addView(tvName)

            // Final Score - Highlight winner in blue
            val tvScore = TextView(this@PointDetailsActivity).apply {
                val formattedScore = if (setScore < 10) "[ %d]".format(setScore) else "[%d]".format(setScore)
                text = formattedScore
                val scoreColor = if (isWinner) ContextCompat.getColor(context, R.color.win_vibrant) 
                                 else ContextCompat.getColor(context, R.color.loss_muted)
                setTextColor(scoreColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f) // Increased from 12f
                typeface = android.graphics.Typeface.MONOSPACE
                includeFontPadding = false
                setPadding(4.dp(), 0, 0, 0)
            }
            addView(tvScore)
        }
    }

    private fun createScoreTextView(text: String, isPointWinner: Boolean, isLastPoint: Boolean): TextView {
        return TextView(this).apply {
            this.text = text
            if (isLastPoint) {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTypeface(null, android.graphics.Typeface.BOLD)
                if (isPointWinner) {
                    setTextColor(ContextCompat.getColor(context, R.color.sets_text))
                } else {
                    setTextColor(ContextCompat.getColor(context, R.color.score_text))
                    alpha = 0.9f
                }
            } else {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                if (isPointWinner) {
                    setTextColor(ContextCompat.getColor(context, R.color.sets_text))
                    setTypeface(null, android.graphics.Typeface.BOLD)
                } else {
                    setTextColor(ContextCompat.getColor(context, R.color.history_loser_text))
                    setTypeface(null, android.graphics.Typeface.NORMAL)
                }
            }
            setPadding(0, 2.dp(), 0, 2.dp())
            gravity = Gravity.CENTER
            minWidth = 20.dp()
        }
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    companion object {
        const val EXTRA_MATCH_ID = "extra_match_id"
    }
}
