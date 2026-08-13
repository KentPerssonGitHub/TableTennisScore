package com.example.tabletennisscore

import android.os.Bundle
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.text.TextUtils
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.tabletennisscore.data.MatchDatabase
import com.example.tabletennisscore.data.MatchResult
import com.example.tabletennisscore.databinding.ActivityHistoryBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private val dao by lazy { MatchDatabase.getInstance(this).matchResultDao() }
    private val adapter = MatchHistoryAdapter(
        onDelete = { result ->
            AlertDialog.Builder(this)
                .setMessage(getString(R.string.history_confirm_delete))
                .setPositiveButton(R.string.dialog_ok) { _, _ ->
                    lifecycleScope.launch { dao.deleteById(result.id) }
                }
                .setNegativeButton(R.string.dialog_cancel, null)
                .show()
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        hideSystemBars()

        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = adapter

        binding.btnHistoryBack.setOnClickListener { finish() }

        lifecycleScope.launch {
            dao.getAll().collectLatest { results ->
                adapter.submitList(results)
                binding.tvHistoryEmpty.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

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

    // ——— Adapter ———————————————————————————————————————————————————————————

    class MatchHistoryAdapter(
        private val onDelete: (MatchResult) -> Unit,
    ) : ListAdapter<MatchResult, MatchHistoryAdapter.ViewHolder>(DIFF) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_match_result, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) =
            holder.bind(getItem(position), onDelete)

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val scoreGrid: LinearLayout = view.findViewById(R.id.layoutItemScoreGrid)
            private val tvDuration: TextView = view.findViewById(R.id.tvItemDuration)
            private val tvDate: TextView = view.findViewById(R.id.tvItemDate)
            private val tvTournament: TextView = view.findViewById(R.id.tvItemTournament)
            private val btnDelete: View = view.findViewById(R.id.btnItemDelete)

            private val dateFormat = SimpleDateFormat("dd MMM yyyy  HH:mm", Locale.getDefault())
            private val density = view.resources.displayMetrics.density

            fun bind(result: MatchResult, onDelete: (MatchResult) -> Unit) {
                val winnerName = if (result.winner == 1) result.player1Name else result.player2Name
                val loserName = if (result.winner == 1) result.player2Name else result.player1Name
                val setResults = parseSetResults(result.setResultsJson)

                renderScoreGrid(result, winnerName, loserName, setResults)
                tvDuration.text = formatDuration(result.durationMs)
                tvDate.text = dateFormat.format(Date(result.playedAt))
                tvTournament.text = itemView.context.getString(
                    R.string.history_tournament_label,
                    result.tournamentName,
                )
                tvTournament.visibility = if (result.tournamentName.isBlank()) View.GONE else View.VISIBLE
                btnDelete.setOnClickListener { onDelete(result) }
            }

            private fun renderScoreGrid(
                result: MatchResult,
                winnerName: String,
                loserName: String,
                setResults: List<Pair<Int, Int>>,
            ) {
                scoreGrid.removeAllViews()
                if (setResults.isEmpty()) {
                    scoreGrid.visibility = View.GONE
                    return
                }
                scoreGrid.visibility = View.VISIBLE

                val winnerScores = setResults.map { if (result.winner == 1) it.first else it.second }
                val loserScores = setResults.map { if (result.winner == 1) it.second else it.first }

                scoreGrid.addView(
                    createScoreRow(
                        name = winnerName,
                        nameColor = ContextCompat.getColor(itemView.context, R.color.score_text),
                        nameBold = true,
                        setCount = if (result.winner == 1) result.sets1 else result.sets2,
                        setCountColor = ContextCompat.getColor(itemView.context, R.color.score_text),
                        setCountBackgroundRes = R.drawable.history_set_count_box,
                        playerScores = winnerScores,
                        opponentScores = loserScores,
                        isMatchWinnerRow = true,
                    ),
                )
                scoreGrid.addView(
                    createScoreRow(
                        name = loserName,
                        nameColor = ContextCompat.getColor(itemView.context, R.color.player_name),
                        nameBold = false,
                        setCount = if (result.winner == 1) result.sets2 else result.sets1,
                        setCountColor = ContextCompat.getColor(itemView.context, R.color.score_text),
                        setCountBackgroundRes = R.drawable.history_set_count_box_loser,
                        playerScores = loserScores,
                        opponentScores = winnerScores,
                        isMatchWinnerRow = false,
                    ),
                )
            }

            private fun createScoreRow(
                name: String,
                nameColor: Int,
                nameBold: Boolean,
                setCount: Int,
                setCountColor: Int,
                setCountBackgroundRes: Int,
                playerScores: List<Int>,
                opponentScores: List<Int>,
                isMatchWinnerRow: Boolean,
            ): LinearLayout {
                return LinearLayout(itemView.context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        if (!isMatchWinnerRow) topMargin = 2.dp()
                    }

                    addView(createNameCell(name, nameColor, nameBold))
                    addView(createSetCountCell(setCount.toString(), setCountColor, setCountBackgroundRes))
                    addView(createSeparatorCell())
                    playerScores.forEachIndexed { index, score ->
                        val otherScore = opponentScores.getOrElse(index) { 0 }
                        val isWinningGame = score > otherScore
                        val scoreColor = when {
                            isWinningGame && isMatchWinnerRow -> ContextCompat.getColor(itemView.context, R.color.score_text)
                            isWinningGame -> ContextCompat.getColor(itemView.context, R.color.score_text)
                            else -> ContextCompat.getColor(itemView.context, R.color.history_loser_text)
                        }
                        val scoreSizeSp = when {
                            isWinningGame && isMatchWinnerRow -> 18f
                            else -> 16f
                        }
                        addView(createScoreCell(score.toString(), scoreColor, scoreSizeSp))
                    }
                }
            }

            private fun createNameCell(name: String, color: Int, bold: Boolean): TextView {
                return TextView(itemView.context).apply {
                    text = name
                    setTextColor(color)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    if (bold) setTypeface(typeface, Typeface.BOLD)
                    // Fixed width keeps the set-count box aligned across 1/3/5/7 set variants.
                    layoutParams = LinearLayout.LayoutParams(130.dp(), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        marginEnd = 10.dp()
                    }
                }
            }

            private fun createSetCountCell(text: String, color: Int, backgroundRes: Int): TextView {
                return TextView(itemView.context).apply {
                    this.text = text
                    setTextColor(color)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                    includeFontPadding = false
                    isSingleLine = true
                    gravity = Gravity.CENTER
                    setTypeface(typeface, Typeface.BOLD)
                    setBackgroundResource(backgroundRes)
                    setPadding(4.dp(), 0, 4.dp(), 0)
                    layoutParams = LinearLayout.LayoutParams(30.dp(), 30.dp()).apply {
                        marginEnd = 18.dp()
                    }
                }
            }

            private fun createSeparatorCell(): TextView {
                return TextView(itemView.context).apply {
                    text = "|"
                    setTextColor(ContextCompat.getColor(itemView.context, R.color.history_loser_text))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    gravity = Gravity.CENTER
                    setTypeface(typeface, Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        marginEnd = 16.dp()
                    }
                }
            }

            private fun createScoreCell(text: String, color: Int, sizeSp: Float): TextView {
                return TextView(itemView.context).apply {
                    this.text = text
                    setTextColor(color)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    setTypeface(typeface, Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        marginEnd = 16.dp()
                    }
                }
            }

            private fun parseSetResults(setResultsJson: String): List<Pair<Int, Int>> {
                if (setResultsJson.isBlank()) return emptyList()
                return setResultsJson.split(",").mapNotNull { token ->
                    val parts = token.trim().split("-")
                    val first = parts.getOrNull(0)?.trim()?.toIntOrNull()
                    val second = parts.getOrNull(1)?.trim()?.toIntOrNull()
                    if (first != null && second != null) first to second else null
                }
            }

            private fun Int.dp(): Int = (this * density).toInt()

            private fun formatDuration(ms: Long): String {
                val totalSecs = ms / 1000
                val hours = totalSecs / 3600
                val minutes = (totalSecs % 3600) / 60
                val seconds = totalSecs % 60
                return if (hours > 0) {
                    String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
                } else {
                    String.format(Locale.US, "%02d:%02d", minutes, seconds)
                }
            }
        }

        companion object {
            val DIFF = object : DiffUtil.ItemCallback<MatchResult>() {
                override fun areItemsTheSame(a: MatchResult, b: MatchResult) = a.id == b.id
                override fun areContentsTheSame(a: MatchResult, b: MatchResult) = a == b
            }
        }
    }
}

