package com.example.tabletennisscore

import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import java.util.Locale
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
                if (result != null) {
                    renderDetails(result)
                }
            }
        }
    }

    private fun renderDetails(result: MatchResult) {
        binding.layoutPointsContainer.removeAllViews()

        val winnerName = if (result.winner == 1) result.player1Name else result.player2Name

        // Winner Title
        binding.layoutPointsContainer.addView(TextView(this).apply {
            text = getString(R.string.history_winner_only, winnerName)
            setTextColor(ContextCompat.getColor(context, R.color.score_text))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 8.dp(), 0, 8.dp())
        })

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

        // Match Summary Stats
        val matchSummaryLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16.dp())
        }
        matchSummaryLayout.addView(createStatTextView(
            getString(R.string.serve_win_pct_label, result.player1Name, String.format(Locale.getDefault(), "%.0f%%", p1MatchStats.percentage))
        ))
        matchSummaryLayout.addView(createStatTextView(
            getString(R.string.serve_win_pct_label, result.player2Name, String.format(Locale.getDefault(), "%.0f%%", p2MatchStats.percentage))
        ))
        binding.layoutPointsContainer.addView(matchSummaryLayout)

        setsPoints.forEachIndexed { index, pointsStr ->
            addSetHeader(index + 1)
            addPointsProgression(pointsStr, result.player1Name, result.player2Name)
            
            // Set Stats
            val (p1S, p2S) = setStats[index]
            val setStatsLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 16.dp())
            }
            setStatsLayout.addView(createStatTextView(String.format(Locale.getDefault(), "%.0f%%", p1S.percentage)).apply { 
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            setStatsLayout.addView(TextView(this).apply {
                text = getString(R.string.serve_win_pct_short)
                setTextColor(ContextCompat.getColor(context, R.color.history_loser_text))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                gravity = Gravity.CENTER
            })
            setStatsLayout.addView(createStatTextView(String.format(Locale.getDefault(), "%.0f%%", p2S.percentage)).apply { 
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            binding.layoutPointsContainer.addView(setStatsLayout)
        }
    }

    private fun createStatTextView(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(ContextCompat.getColor(context, R.color.score_text))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
        }
    }

    private fun nextServer(s1: Int, s2: Int, total: Int, firstServer: Int): Int {
        return if (s1 >= 10 && s2 >= 10) {
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
        var totalServes: Int = 0
    ) {
        val percentage: Double
            get() = if (totalServes > 0) (pointsWonOnServe.toDouble() / totalServes * 100) else 0.0
    }

    private fun addSetHeader(setNumber: Int) {
        val tv = TextView(this).apply {
            text = getString(R.string.point_history_set_label, setNumber)
            setTextColor(ContextCompat.getColor(context, R.color.player_name))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 8.dp(), 0, 4.dp())
        }
        binding.layoutPointsContainer.addView(tv)
    }

    private fun addPointsProgression(pointsStr: String, p1Name: String, p2Name: String) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8.dp(), 0, 8.dp(), 12.dp())
            gravity = Gravity.CENTER_VERTICAL
        }

        // Labels Column
        val labelsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 16.dp() }
        }
        labelsLayout.addView(createLabelTextView("$p1Name:"))
        labelsLayout.addView(createLabelTextView("$p2Name:"))
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
        pointsStr.forEach { char ->
            val winner = if (char == '1') 1 else 2
            if (winner == 1) s1++ else s2++

            // Add scores
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = 12.dp() }
            }
            col.addView(createScoreTextView(s1.toString(), winner == 1))
            col.addView(createScoreTextView(s2.toString(), winner == 2))
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

    private fun createLabelTextView(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(ContextCompat.getColor(context, R.color.player_name))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, 2.dp(), 0, 2.dp())
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(100.dp(), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun createScoreTextView(text: String, isPointWinner: Boolean): TextView {
        return TextView(this).apply {
            this.text = text
            if (isPointWinner) {
                setTextColor(ContextCompat.getColor(context, R.color.sets_text))
                setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                setTextColor(ContextCompat.getColor(context, R.color.history_loser_text))
                setTypeface(null, android.graphics.Typeface.NORMAL)
            }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
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
