package com.example.tabletennisscore.history
import com.example.tabletennisscore.hideSystemBarsImmersive

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.tabletennisscore.data.MatchDatabase
import com.example.tabletennisscore.databinding.ActivityPointDetailsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Shows the score after every point of a finished match, with serve statistics. */
class PointDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPointDetailsBinding
    private val dao by lazy { MatchDatabase.getInstance(this).matchResultDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPointDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        hideSystemBarsImmersive()

        val matchId = intent.getIntExtra(EXTRA_MATCH_ID, -1)
        if (matchId == -1) {
            finish()
            return
        }

        binding.btnPointsBack.setOnClickListener { finish() }

        val renderer = PointDetailsRenderer(this, binding.layoutPointsContainer)
        lifecycleScope.launch {
            dao.getById(matchId).collectLatest { result ->
                result?.let { renderer.render(it) }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBarsImmersive()
    }

    companion object {
        const val EXTRA_MATCH_ID = "extra_match_id"
    }
}
