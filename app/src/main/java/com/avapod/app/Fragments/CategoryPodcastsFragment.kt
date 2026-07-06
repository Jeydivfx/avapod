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

class CategoryPodcastsFragment : Fragment() {

    private lateinit var rvPodcasts: RecyclerView
    private lateinit var loader: ProgressBar
    private lateinit var txtTitle: TextView
    private lateinit var txtEmpty: TextView

    private var categoryId: String? = null
    private var categoryName: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_category_podcasts, container, false)

        categoryId = arguments?.getString("category_id")
        categoryName = arguments?.getString("category_name")

        rvPodcasts = view.findViewById(R.id.rv_category_podcasts)
        loader = view.findViewById(R.id.loader)
        txtTitle = view.findViewById(R.id.txt_category_name)
        txtEmpty = view.findViewById(R.id.txt_empty)

        txtTitle.text = categoryName

        setupToolbar(view)
        fetchPodcastsByCategory()

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

    private fun fetchPodcastsByCategory() {
        if (categoryId == null) return

        loader.visibility = View.VISIBLE

        val repository = com.avapod.app.network.PodcastRepository(requireContext())

        repository.getPodcasts { allPodcasts ->
            val filteredList = allPodcasts.filter { it.category_id == categoryId }

            activity?.runOnUiThread {
                loader.visibility = View.GONE
                if (filteredList.isEmpty()) {
                    txtEmpty.visibility = View.VISIBLE
                    rvPodcasts.visibility = View.GONE
                } else {
                    txtEmpty.visibility = View.GONE
                    rvPodcasts.visibility = View.VISIBLE
                    setupRecyclerView(filteredList)
                }
            }
        }
    }

    private fun setupRecyclerView(list: List<Podcast>) {
        val gridLayoutManager = GridLayoutManager(requireContext(), 2)
        rvPodcasts.layoutManager = gridLayoutManager

        rvPodcasts.adapter = PodcastAdapter(list) { podcast ->

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

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            if (::rvPodcasts.isInitialized) {
                rvPodcasts.adapter = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}