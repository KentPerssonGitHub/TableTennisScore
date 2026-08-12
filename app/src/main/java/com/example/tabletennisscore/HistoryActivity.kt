package com.example.tabletennisscore

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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

        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = adapter

        binding.btnHistoryBack.setOnClickListener { finish() }
        binding.btnClearHistory.setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage(getString(R.string.history_confirm_clear_all))
                .setPositiveButton(R.string.dialog_ok) { _, _ ->
                    lifecycleScope.launch { dao.deleteAll() }
                }
                .setNegativeButton(R.string.dialog_cancel, null)
                .show()
        }

        lifecycleScope.launch {
            dao.getAll().collectLatest { results ->
                adapter.submitList(results)
                binding.tvHistoryEmpty.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE
                binding.btnClearHistory.visibility = if (results.isEmpty()) View.GONE else View.VISIBLE
            }
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
            private val tvPlayers: TextView = view.findViewById(R.id.tvItemPlayers)
            private val tvSets: TextView = view.findViewById(R.id.tvItemSets)
            private val tvSetScores: TextView = view.findViewById(R.id.tvItemSetScores)
            private val tvDuration: TextView = view.findViewById(R.id.tvItemDuration)
            private val tvDate: TextView = view.findViewById(R.id.tvItemDate)
            private val btnDelete: View = view.findViewById(R.id.btnItemDelete)

            private val dateFormat = SimpleDateFormat("dd MMM yyyy  HH:mm", Locale.getDefault())

            fun bind(result: MatchResult, onDelete: (MatchResult) -> Unit) {
                val winnerName = if (result.winner == 1) result.player1Name else result.player2Name

                tvPlayers.text = itemView.context.getString(
                    R.string.history_players,
                    result.player1Name,
                    result.player2Name,
                )
                tvSets.text = itemView.context.getString(
                    R.string.history_set_score,
                    result.sets1,
                    result.sets2,
                )
                tvSetScores.text = buildSetScoresLine(result)
                tvDuration.text = formatDuration(result.durationMs)
                tvDate.text = dateFormat.format(Date(result.playedAt))
                tvPlayers.text = itemView.context.getString(
                    R.string.history_winner_label,
                    winnerName,
                    result.player1Name,
                    result.player2Name,
                )
                btnDelete.setOnClickListener { onDelete(result) }
            }

            private fun buildSetScoresLine(result: MatchResult): String {
                if (result.setResultsJson.isBlank()) return ""
                return result.setResultsJson
                    .split(",")
                    .joinToString("  ") { it.replace("-", "–") }
            }

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

