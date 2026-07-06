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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NewEpisodesFragment : Fragment() {

    private lateinit var rvNew: RecyclerView
    private lateinit var loader: ProgressBar
    private lateinit var prefHelper: PreferenceHelper
    private lateinit var txtEmptyState: TextView
    private val allNewEpisodes = mutableListOf<RssItem>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_new_episodes, container, false)

        prefHelper = PreferenceHelper(requireContext())
        rvNew = view.findViewById(R.id.rv_new_episodes)
        loader = view.findViewById(R.id.loader)
        txtEmptyState = view.findViewById(R.id.txt_empty_state)

        setupToolbar(view)
        fetchNewEpisodesFromSubs()

        return view
    }

    private fun setupToolbar(view: View) {
        val toolbar = view.findViewById<View>(R.id.common_toolbar)
        val btnBack = toolbar.findViewById<ImageButton>(R.id.btn_back_common)

        btnBack.scaleX = -1f
        btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        toolbar.findViewById<View>(R.id.btn_menu_common).visibility = View.GONE
    }

    private fun fetchNewEpisodesFromSubs() {
        val subscriptions = prefHelper.getSubscribedPodcasts()

        if (subscriptions.isEmpty()) {
            loader.visibility = View.GONE
            txtEmptyState.visibility = View.VISIBLE
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { loader.visibility = View.VISIBLE }

            allNewEpisodes.clear()

            subscriptions.forEach { podcast ->
                try {
                    val response = RetrofitClient.instance.getRssFeed(podcast.rss_url ?: "")
                    if (response.isSuccessful) {
                        val xml = response.body()?.string()
                        if (xml != null) {
                            val episodes = RssParser().parse(xml)
                            val latestEpisode = episodes.take(1).map { item ->
                                item.copy(
                                    imageUrl = item.imageUrl ?: podcast.thumbnail_url,
                                    artist = podcast.title
                                )
                            }
                            allNewEpisodes.addAll(latestEpisode)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            allNewEpisodes.sortByDescending { item ->
                try {
                    val sdf = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", java.util.Locale.ENGLISH)
                    sdf.parse(item.pubDate ?: "")?.time ?: 0L
                } catch (e: Exception) {
                    0L
                }
            }

            withContext(Dispatchers.Main) {
                loader.visibility = View.GONE
                setupRecyclerView()
            }
        }
    }

    private fun setupRecyclerView() {
        rvNew.layoutManager = LinearLayoutManager(requireContext())
        rvNew.adapter = NewEpisodeAdapter(allNewEpisodes) { episode ->
            AdManager.showAdWithCapping(requireActivity()) {
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