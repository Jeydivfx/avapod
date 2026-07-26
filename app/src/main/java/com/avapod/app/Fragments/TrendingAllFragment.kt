package com.avapod.app.Fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.avapod.app.R
import com.avapod.app.adapters.PodcastAdapter
import com.avapod.app.network.PodcastRepository

class TrendingAllFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_trending_all, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<View>(R.id.common_toolbar)
        val btnBack = toolbar.findViewById<ImageButton>(R.id.btn_back_common)
        toolbar.findViewById<ImageButton>(R.id.btn_menu_common).visibility = View.GONE

        btnBack.scaleX = -1f
        btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        val rv = view.findViewById<RecyclerView>(R.id.rv_all_trending)
        val loader = view.findViewById<ProgressBar>(R.id.loader)

        val gridLayoutManager = GridLayoutManager(requireContext(), 2)
        rv.layoutManager = gridLayoutManager


        val adapter = PodcastAdapter(listOf()) { podcast ->

            com.avapod.app.utils.AdManager.showAdWithCapping(requireActivity()) {
                val fragment = PodcastDetailFragment().apply {
                    arguments = Bundle().apply {
                        putString("rss_url", podcast.rss_url)
                        putString("cover_url", podcast.thumbnail_url)
                        putString("title", podcast.title)
                        putString("description", podcast.description)
                        putString("artist", podcast.artist)
                    }
                }

                parentFragmentManager.beginTransaction()
                    .replace(R.id.nav_host_fragment, fragment)
                    .addToBackStack(null)
                    .commit()
            }

        }

        rv.adapter = adapter

        rv.adapter = adapter

        loader.visibility = View.VISIBLE
        val repository = PodcastRepository(requireContext())

        repository.getPodcasts { podcasts ->
            val trendingList = podcasts.filter { it.is_trending }
            activity?.runOnUiThread {
                loader.visibility = View.GONE
                adapter.updateData(trendingList)
            }
        }
    }
}