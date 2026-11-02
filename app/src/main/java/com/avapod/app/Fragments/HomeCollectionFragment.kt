package com.avapod.app.Fragments

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.avapod.app.R
import com.avapod.app.PlayerActivity
import com.avapod.app.adapters.HomeCard
import com.avapod.app.adapters.HomeShelfAdapter
import com.avapod.app.models.Podcast
import com.avapod.app.models.RssItem
import com.google.gson.Gson

class HomeCollectionFragment : Fragment(R.layout.fragment_home_collection) {
    companion object {
        fun episodes(title: String, items: List<RssItem>) = HomeCollectionFragment().apply {
            arguments = Bundle().apply {
                putString("title", title)
                putString("episodes", Gson().toJson(items.take(100).map { it.copy(description = "") }))
            }
        }
        fun podcasts(title: String, items: List<Podcast>) = HomeCollectionFragment().apply {
            arguments = Bundle().apply {
                putString("title", title)
                putString("podcasts", Gson().toJson(items.take(100).map { it.copy(description = "") }))
            }
        }
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<TextView>(R.id.collection_title).text = arguments?.getString("title")
        view.findViewById<View>(R.id.btn_menu_common).visibility = View.GONE
        view.findViewById<View>(R.id.btn_back_common).apply {
            scaleX = 1f
            setOnClickListener { parentFragmentManager.popBackStack() }
        }
        val cards = arguments?.getString("episodes")?.let { json ->
            Gson().fromJson(json, Array<RssItem>::class.java).map { item ->
                HomeCard(item.title.orEmpty(), item.artist.orEmpty(), item.imageUrl, item.duration) {
                    startActivity(Intent(requireContext(), PlayerActivity::class.java).apply {
                        putExtra("audio_url", item.audioUrl); putExtra("title", item.title)
                        putExtra("cover", item.imageUrl); putExtra("podcast_name", item.artist)
                        putExtra("duration", item.duration)
                    })
                }
            }
        } ?: arguments?.getString("podcasts")?.let { json ->
            Gson().fromJson(json, Array<Podcast>::class.java).map { podcast ->
                HomeCard(podcast.title, podcast.artist, podcast.thumbnail_url, null) {
                    parentFragmentManager.beginTransaction().replace(R.id.nav_host_fragment,
                        PodcastDetailFragment().apply {
                            arguments = Bundle().apply {
                                putString("rss_url", podcast.rss_url); putString("cover_url", podcast.thumbnail_url)
                                putString("title", podcast.title); putString("artist", podcast.artist)
                            }
                        }).addToBackStack(null).commit()
                }
            }
        }.orEmpty()
        view.findViewById<RecyclerView>(R.id.collection_items).apply {
            val columns = if (resources.configuration.screenWidthDp < 360 || resources.configuration.fontScale > 1.3f) 2 else 3
            layoutManager = GridLayoutManager(requireContext(), columns)
            adapter = HomeShelfAdapter().apply { submit(cards) }
        }
    }
    override fun onDestroyView() {
        view?.findViewById<RecyclerView>(R.id.collection_items)?.adapter = null
        super.onDestroyView()
    }
}
