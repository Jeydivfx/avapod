package com.avapod.app.Fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.avapod.app.PlayerActivity
import com.avapod.app.R
import com.avapod.app.adapters.EpisodeAdapter
import com.avapod.app.adapters.NewEpisodeAdapter
import com.avapod.app.models.RssItem
import com.avapod.app.network.RetrofitClient
import com.avapod.app.network.RssParser
import com.avapod.app.utils.AdManager
import com.avapod.app.utils.PreferenceHelper
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NewEpisodesFragment : Fragment() {

    private lateinit var rvNew: RecyclerView
    private lateinit var loader: ProgressBar
    private lateinit var prefHelper: PreferenceHelper
    private lateinit var txtEmptyState: TextView
    private var allNewEpisodes: List<RssItem> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_new_episodes, container, false)

        prefHelper = PreferenceHelper(requireContext())
        rvNew = view.findViewById(R.id.rv_new_episodes)
        loader = view.findViewById(R.id.loader)
        txtEmptyState = view.findViewById(R.id.txt_empty_state)

        setupToolbar(view)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fetchNewEpisodesFromSubs()
    }

    private fun setupToolbar(view: View) {
        val toolbar = view.findViewById<View>(R.id.common_toolbar)
        val btnBack = toolbar.findViewById<ImageButton>(R.id.btn_back_common)

        btnBack.scaleX = 1f
        btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        toolbar.findViewById<View>(R.id.btn_menu_common).visibility = View.GONE
    }

    private fun fetchNewEpisodesFromSubs() {
        val subscriptions = prefHelper.getSubscribedPodcasts()
        val owner = viewLifecycleOwner
        owner.lifecycleScope.launch {
            loader.visibility = View.VISIBLE
            val collected = mutableListOf<RssItem>()
            var failed = false
            try {
                val completed = kotlinx.coroutines.withTimeoutOrNull(35_000) {
                    val limiter = Semaphore(4)
                    subscriptions.map { podcast ->
                        async {
                            limiter.withPermit {
                                try {
                                    val episodes = com.avapod.app.network.RssLoader.load(podcast.rss_url)
                                    episodes.firstOrNull()?.let { episode ->
                                        collected.add(episode.copy(
                                            imageUrl = episode.imageUrl?.takeIf(String::isNotBlank) ?: podcast.thumbnail_url,
                                            artist = podcast.title))
                                    }
                                } catch (e: Exception) {
                                    if (e is kotlinx.coroutines.CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) throw e
                                    android.util.Log.e("AvapodRss", "Could not load subscribed feed", e)
                                    failed = true
                                }
                            }
                        }
                    }.awaitAll()
                    true
                }
                if (completed == null) failed = true
                allNewEpisodes = collected.sortedByDescending { item ->
                    runCatching {
                        java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", java.util.Locale.ENGLISH)
                            .parse(item.pubDate.orEmpty())?.time ?: 0L
                    }.getOrDefault(0L)
                }
                setupRecyclerView()
                if (failed) Toast.makeText(context, R.string.some_feeds_failed, Toast.LENGTH_LONG).show()
            } finally {
                if (owner.lifecycle.currentState != androidx.lifecycle.Lifecycle.State.DESTROYED) loader.visibility = View.GONE
            }
        }
    }
    private fun setupRecyclerView() {
        txtEmptyState.visibility = if (allNewEpisodes.isEmpty()) View.VISIBLE else View.GONE
        rvNew.layoutManager = LinearLayoutManager(requireContext())
        rvNew.adapter = NewEpisodeAdapter(allNewEpisodes) { episode ->
            AdManager.showAdWithCapping(this) {
                val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                    putExtra("audio_url", episode.audioUrl)
                    putExtra("title", episode.title)
                    putExtra("cover", episode.imageUrl)
                    putExtra("duration", episode.duration)
                    putExtra("podcast_name", episode.artist)
                }
                startActivity(intent)
            }
        }
    }
}
