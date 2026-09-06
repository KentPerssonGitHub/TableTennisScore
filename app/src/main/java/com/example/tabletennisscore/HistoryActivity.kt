package com.example.tabletennisscore

import android.content.Intent
import android.os.Bundle
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
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
    
    // Tracks which tournaments are collapsed. Persists during the activity's lifecycle.
    private val collapsedTournaments = mutableSetOf<String>()
    private var lastLoadedResults: List<MatchResult> = emptyList()

    private val adapter = MatchHistoryAdapter(
        onDelete = { result ->
            AlertDialog.Builder(this)
                .setMessage(getString(R.string.history_confirm_delete))
                .setPositiveButton(R.string.dialog_ok) { _, _ ->
                    lifecycleScope.launch { dao.deleteById(result.id) }
                }
                .setNegativeButton(R.string.dialog_cancel, null)
                .show()
        },
        onDetails = { result ->
            val intent = Intent(this, PointDetailsActivity::class.java).apply {
                putExtra(PointDetailsActivity.EXTRA_MATCH_ID, result.id)
            }
            startActivity(intent)
        },
        onEditTournament = { oldName, results ->
            showEditTournamentDialog(oldName, results)
        },
        onEditMatchDetails = { result ->
            showEditMatchDetailsDialog(result)
        },
        onToggleExpand = { tournamentName ->
            if (collapsedTournaments.contains(tournamentName)) {
                collapsedTournaments.remove(tournamentName)
            } else {
                collapsedTournaments.add(tournamentName)
            }
            refreshList()
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
                lastLoadedResults = results
                refreshList()
            }
        }
    }

    private fun refreshList() {
        val grouped = groupMatches(lastLoadedResults)
        adapter.submitList(grouped)
        binding.tvHistoryEmpty.visibility = if (lastLoadedResults.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun groupMatches(results: List<MatchResult>): List<HistoryListItem> {
        val list = mutableListOf<HistoryListItem>()
        var currentTournament = ""
        
        results.forEachIndexed { index, match ->
            if (index == 0 || match.tournamentName != currentTournament) {
                currentTournament = match.tournamentName
                val isExpanded = !collapsedTournaments.contains(currentTournament)
                val matchesInGroup = results.filter { it.tournamentName == currentTournament }
                list.add(HistoryListItem.Header(currentTournament, matchesInGroup, isExpanded))
            }
            
            val isExpanded = !collapsedTournaments.contains(match.tournamentName)
            if (isExpanded) {
                list.add(HistoryListItem.Match(match))
            }
        }
        return list
    }

    private fun showEditTournamentDialog(oldName: String, matchesInGroup: List<MatchResult>) {
        val editText = EditText(this).apply {
            setText(oldName)
            setSelection(oldName.length)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.history_edit_tournament)
            .setView(editText)
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName != oldName) {
                    lifecycleScope.launch {
                        matchesInGroup.forEach { match ->
                            dao.update(match.copy(tournamentName = newName))
                        }
                    }
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun showEditMatchDetailsDialog(result: MatchResult) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, 0)
        }
        val p1Edit = EditText(this).apply {
            hint = getString(R.string.history_edit_player1)
            setText(result.player1Name)
        }
        val p2Edit = EditText(this).apply {
            hint = getString(R.string.history_edit_player2)
            setText(result.player2Name)
        }
        
        val roundLabel = TextView(this).apply {
            text = getString(R.string.history_round_label)
            setPadding(0, 16, 0, 8)
        }
        val rounds = listOf(
            getString(R.string.round_pool),
            getString(R.string.round_group),
            getString(R.string.round_32),
            getString(R.string.round_16),
            getString(R.string.round_8),
            getString(R.string.round_semi),
            getString(R.string.round_final)
        )
        
        val roundGrid = android.widget.GridLayout(this).apply {
            columnCount = 3
            setPadding(0, 8, 0, 8)
        }
        
        val radioButtons = mutableListOf<RadioButton>()
        rounds.forEach { round ->
            val rb = RadioButton(this).apply {
                text = round
                tag = round
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                isChecked = (result.matchRound == round)
                setOnClickListener { view ->
                    radioButtons.forEach { it.isChecked = (it == view) }
                }
            }
            radioButtons.add(rb)
            roundGrid.addView(rb)
        }

        layout.addView(p1Edit)
        layout.addView(p2Edit)
        layout.addView(roundLabel)
        layout.addView(roundGrid)

        AlertDialog.Builder(this)
            .setTitle("Edit Match Details")
            .setView(layout)
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                val p1 = p1Edit.text.toString().trim()
                val p2 = p2Edit.text.toString().trim()
                val selectedRb = radioButtons.find { it.isChecked }
                val selectedRound = selectedRb?.tag as? String ?: ""
                if (p1.isNotEmpty() && p2.isNotEmpty()) {
                    lifecycleScope.launch {
                        dao.update(result.copy(player1Name = p1, player2Name = p2, matchRound = selectedRound))
                    }
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
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

    // ——— List Items ——————————————————————————————————————————————————————————

    sealed class HistoryListItem {
        data class Header(val tournamentName: String, val matches: List<MatchResult>, val isExpanded: Boolean) : HistoryListItem()
        data class Match(val result: MatchResult) : HistoryListItem()
    }

    // ——— Adapter ———————————————————————————————————————————————————————————

    class MatchHistoryAdapter(
        private val onDelete: (MatchResult) -> Unit,
        private val onDetails: (MatchResult) -> Unit,
        private val onEditTournament: (String, List<MatchResult>) -> Unit,
        private val onEditMatchDetails: (MatchResult) -> Unit,
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
                holder.bind(item, onEditTournament, onToggleExpand)
            } else if (holder is MatchViewHolder && item is HistoryListItem.Match) {
                holder.bind(item.result, onDelete, onDetails, onEditMatchDetails)
            }
        }

        class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val tvTournamentName: TextView = view.findViewById(R.id.tvHeaderTournamentName)
            private val ivExpandIcon: ImageView = view.findViewById(R.id.ivHeaderExpandIcon)
            
            fun bind(header: HistoryListItem.Header, onEdit: (String, List<MatchResult>) -> Unit, onToggle: (String) -> Unit) {
                tvTournamentName.text = if (header.tournamentName.isBlank()) "No Tournament" else header.tournamentName
                
                // Rotate icon based on state
                ivExpandIcon.rotation = if (header.isExpanded) 0f else -90f
                
                itemView.setOnClickListener {
                    onToggle(header.tournamentName)
                }
                itemView.setOnLongClickListener {
                    onEdit(header.tournamentName, header.matches)
                    true
                }
            }
        }

        class MatchViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val scoreGrid: LinearLayout = view.findViewById(R.id.layoutItemScoreGrid)
            private val tvDuration: TextView = view.findViewById(R.id.tvItemDuration)
            private val tvDate: TextView = view.findViewById(R.id.tvItemDate)
            private val tvRound: TextView = view.findViewById(R.id.tvItemRound)
            private val tvWinner: TextView = view.findViewById(R.id.tvItemWinner)
            private val tvTournament: TextView = view.findViewById(R.id.tvItemTournament)
            private val btnDelete: View = view.findViewById(R.id.btnItemDelete)
            private val btnDetails: View = view.findViewById(R.id.btnItemDetails)

            private val dateFormat = SimpleDateFormat("dd MMM yyyy  HH:mm", Locale.getDefault())
            private val density = view.resources.displayMetrics.density

            fun bind(
                result: MatchResult, 
                onDelete: (MatchResult) -> Unit, 
                onDetails: (MatchResult) -> Unit,
                onEditMatchDetails: (MatchResult) -> Unit
            ) {
                val winnerName = if (result.winner == 1) result.player1Name else result.player2Name
                val loserName = if (result.winner == 1) result.player2Name else result.player1Name
                val setResults = parseSetResults(result.setResultsJson)

                renderScoreGrid(result, winnerName, loserName, setResults)
                tvWinner.text = itemView.context.getString(R.string.history_winner_only, winnerName)
                tvDuration.text = formatDuration(result.durationMs)
                tvDate.text = dateFormat.format(Date(result.playedAt))
                
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

            private fun createNameCell(name: String, color: Int, bold: Boolean): TextView {
                return TextView(itemView.context).apply {
                    text = name
                    setTextColor(color)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    if (bold) setTypeface(typeface, Typeface.NORMAL)
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
}
