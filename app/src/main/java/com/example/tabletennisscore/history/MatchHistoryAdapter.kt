package com.example.tabletennisscore.history
import com.example.tabletennisscore.formatClockDuration
import com.example.tabletennisscore.GameViewModel
import com.example.tabletennisscore.R

import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.tabletennisscore.data.MatchResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class HistoryListItem {
    data class Header(val tournamentName: String, val matches: List<MatchResult>, val isExpanded: Boolean) : HistoryListItem()
    data class Match(val result: MatchResult) : HistoryListItem()
}

// ——— Adapter ———————————————————————————————————————————————————————————

class MatchHistoryAdapter(
    private val onDelete: (MatchResult) -> Unit,
    private val onDetails: (MatchResult) -> Unit,
    private val onTournamentActions: (String, List<MatchResult>) -> Unit,
    private val onEditMatchDetails: (MatchResult) -> Unit,
    private val onToggleDataValidity: (MatchResult) -> Unit,
    private val onToggleExpand: (String) -> Unit
) : ListAdapter<HistoryListItem, RecyclerView.ViewHolder>(DIFF) {

    private val TYPE_HEADER = 0
    private val TYPE_MATCH = 1

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is HistoryListItem.Header -> TYPE_HEADER
            is HistoryListItem.Match -> TYPE_MATCH
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_history_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_match_result, parent, false)
            MatchViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        if (holder is HeaderViewHolder && item is HistoryListItem.Header) {
            holder.bind(item, onTournamentActions, onToggleExpand)
        } else if (holder is MatchViewHolder && item is HistoryListItem.Match) {
            holder.bind(item.result, onDelete, onDetails, onEditMatchDetails, onToggleDataValidity)
        }
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvTournamentName: TextView = view.findViewById(R.id.tvHeaderTournamentName)
        private val ivExpandIcon: ImageView = view.findViewById(R.id.ivHeaderExpandIcon)
        
        fun bind(header: HistoryListItem.Header, onActions: (String, List<MatchResult>) -> Unit, onToggle: (String) -> Unit) {
            val baseName = if (header.tournamentName.isBlank()) itemView.context.getString(R.string.history_tournament_name_default) else header.tournamentName
            val allProtected = header.matches.isNotEmpty() && header.matches.all { it.isProtected }
            tvTournamentName.text = if (allProtected) "🔒 $baseName" else baseName
            
            // Rotate icon based on state
            ivExpandIcon.rotation = if (header.isExpanded) 0f else -90f
            
            itemView.setOnClickListener {
                onToggle(header.tournamentName)
            }
            itemView.setOnLongClickListener {
                onActions(header.tournamentName, header.matches)
                true
            }
        }
    }

    class MatchViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val scoreGrid: LinearLayout = view.findViewById(R.id.layoutItemScoreGrid)
        private val tvDuration: TextView = view.findViewById(R.id.tvItemDuration)
        private val tvDate: TextView = view.findViewById(R.id.tvItemDate)
        private val tvDataStatus: TextView = view.findViewById(R.id.tvItemDataStatus)
        private val tvRound: TextView = view.findViewById(R.id.tvItemRound)
        private val winnerContainer: LinearLayout = view.findViewById(R.id.layoutItemWinner)
        private val tvTournament: TextView = view.findViewById(R.id.tvItemTournament)
        private val btnDelete: View = view.findViewById(R.id.btnItemDelete)
        private val btnDetails: View = view.findViewById(R.id.btnItemDetails)

        private val dateFormat = SimpleDateFormat("dd MMM yyyy  HH:mm", Locale.getDefault())
        private val density = view.resources.displayMetrics.density

        fun bind(
            result: MatchResult, 
            onDelete: (MatchResult) -> Unit, 
            onDetails: (MatchResult) -> Unit,
            onEditMatchDetails: (MatchResult) -> Unit,
            onToggleDataValidity: (MatchResult) -> Unit,
        ) {
            val winnerName = if (result.winner == 1) result.player1Name else result.player2Name
            val loserName = if (result.winner == 1) result.player2Name else result.player1Name
            val setResults = parseSetResults(result.setResultsJson)

            renderScoreGrid(result, winnerName, loserName, setResults)
            renderWinnerHeader(result, winnerName)
            tvDuration.text = if (result.isProtected) {
                itemView.context.getString(R.string.history_duration_protected, formatClockDuration(result.durationMs))
            } else {
                formatClockDuration(result.durationMs)
            }
            tvDate.text = dateFormat.format(Date(result.playedAt))
            tvDataStatus.text = itemView.context.getString(
                if (result.isDataValid) R.string.history_data_status_ok else R.string.history_data_status_bad
            )
            tvDataStatus.setTextColor(
                ContextCompat.getColor(
                    itemView.context,
                    if (result.isDataValid) R.color.win_vibrant else R.color.loss_vibrant,
                )
            )
            tvDataStatus.alpha = 0.95f
            tvDataStatus.setOnClickListener { onToggleDataValidity(result) }
            btnDelete.alpha = if (result.isProtected) 0.55f else 1f

            tvRound.text = if (result.matchRound.isNotBlank()) "· ${result.matchRound}" else ""
            tvRound.visibility = if (result.matchRound.isNotBlank()) View.VISIBLE else View.GONE

            // Tournament name in card is hidden since we have headers now
            tvTournament.visibility = View.GONE
            
            btnDelete.setOnClickListener { onDelete(result) }
            btnDetails.setOnClickListener { onDetails(result) }
            
            itemView.setOnLongClickListener {
                onEditMatchDetails(result)
                true
            }
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
                    nameBold = false,
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
                        else -> 18f
                    }
                    addView(createScoreCell(score.toString(), scoreColor, scoreSizeSp))
                }
            }
        }

        private fun renderWinnerHeader(result: MatchResult, winnerName: String) {
            winnerContainer.removeAllViews()
            val isDoubles = result.matchMode == GameViewModel.MATCH_MODE_DOUBLES && winnerName.contains(" / ")
            if (!isDoubles) {
                winnerContainer.orientation = LinearLayout.HORIZONTAL
                winnerContainer.gravity = Gravity.CENTER
                winnerContainer.addView(TextView(itemView.context).apply {
                    text = itemView.context.getString(R.string.history_winner_only, winnerName)
                    setTextColor(ContextCompat.getColor(itemView.context, R.color.score_text))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    setTypeface(typeface, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })
                return
            }

            winnerContainer.orientation = LinearLayout.HORIZONTAL
            winnerContainer.gravity = Gravity.CENTER

            winnerContainer.addView(LinearLayout(itemView.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )

                addView(TextView(itemView.context).apply {
                    text = "🏆"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                    includeFontPadding = false
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        marginEnd = 8.dp()
                    }
                })

                addView(createStackedNameBlock(
                    winnerName,
                    ContextCompat.getColor(itemView.context, R.color.score_text),
                    true,
                    15f,
                ))
            })
        }

        private fun createNameCell(name: String, color: Int, bold: Boolean): View {
            val isDoubles = name.contains(" / ")
            if (!isDoubles) {
                return TextView(itemView.context).apply {
                    text = name
                    setTextColor(color)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    if (bold) setTypeface(typeface, Typeface.NORMAL)
                    layoutParams = LinearLayout.LayoutParams(130.dp(), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        marginEnd = 10.dp()
                    }
                }
            }

            return createStackedNameBlock(name, color, bold, 13f).apply {
                layoutParams = LinearLayout.LayoutParams(130.dp(), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = 10.dp()
                }
            }
        }

        private fun createStackedNameBlock(name: String, color: Int, bold: Boolean, textSizeSp: Float): LinearLayout {
            val names = name.split(" / ")
            return LinearLayout(itemView.context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                names.forEach { playerName ->
                    addView(TextView(itemView.context).apply {
                        text = playerName
                        setTextColor(color)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
                        includeFontPadding = false
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        gravity = Gravity.START
                        if (bold) setTypeface(typeface, Typeface.NORMAL)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        )
                    })
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
                setTypeface(typeface, Typeface.NORMAL)
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

    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<HistoryListItem>() {
            override fun areItemsTheSame(a: HistoryListItem, b: HistoryListItem): Boolean {
                return if (a is HistoryListItem.Header && b is HistoryListItem.Header) {
                    a.tournamentName == b.tournamentName
                } else if (a is HistoryListItem.Match && b is HistoryListItem.Match) {
                    a.result.id == b.result.id
                } else false
            }
            override fun areContentsTheSame(a: HistoryListItem, b: HistoryListItem): Boolean {
                return a == b
            }
        }
    }
}
