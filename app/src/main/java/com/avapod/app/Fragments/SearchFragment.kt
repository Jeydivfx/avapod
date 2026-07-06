package com.avapod.app.Fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.avapod.app.R
import com.avapod.app.adapters.PodcastAdapter
import com.avapod.app.models.Podcast

class SearchFragment : Fragment() {

    private lateinit var rvResults: RecyclerView
    private lateinit var loader: ProgressBar
    private lateinit var txtNoResult: TextView
    private lateinit var txtSearchTitle: TextView
    private var searchQuery: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_search_results, container, false)

        searchQuery = arguments?.getString("search_query")

        rvResults = view.findViewById(R.id.rv_search_results)
        loader = view.findViewById(R.id.loader)
        txtNoResult = view.findViewById(R.id.txt_no_result)
        txtSearchTitle = view.findViewById(R.id.txt_search_title)

        setupToolbar(view)

        searchQuery?.let {
            performSearch(it)
        }

        return view
    }

    private fun setupToolbar(view: View) {
        val toolbar = view.findViewById<View>(R.id.common_toolbar)
        val btnBack = toolbar.findViewById<ImageButton>(R.id.btn_back_common)

        btnBack.scaleX = -1f
        btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        toolbar.findViewById<View>(R.id.btn_menu_common).visibility = View.GONE
    }

    private fun performSearch(query: String) {
        loader.visibility = View.VISIBLE

        val repository = com.avapod.app.network.PodcastRepository(requireContext())

        repository.getPodcasts { allPodcasts ->
            val filteredList = allPodcasts.filter {
                (it.title?.contains(query, ignoreCase = true) == true) ||
                        (it.artist?.contains(query, ignoreCase = true) == true) ||
                        (it.description?.contains(query, ignoreCase = true) == true)
            }

            activity?.runOnUiThread {
                loader.visibility = View.GONE
                if (filteredList.isEmpty()) {
                    txtNoResult.visibility = View.VISIBLE
                    rvResults.visibility = View.GONE
                } else {
                    txtNoResult.visibility = View.GONE
                    rvResults.visibility = View.VISIBLE
                    setupRecyclerView(filteredList)
                }
            }
        }
    }

    private fun setupRecyclerView(list: List<Podcast>) {
        val gridLayoutManager = GridLayoutManager(requireContext(), 2)
        rvResults.layoutManager = gridLayoutManager

        rvResults.adapter = PodcastAdapter(list) { podcast ->

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
    }
}