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
    private var catalog: List<Podcast> = emptyList()
    private var catalogReady = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_search_results, container, false)

        if (searchQuery == null) searchQuery = arguments?.getString("search_query")

        rvResults = view.findViewById(R.id.rv_search_results)
        loader = view.findViewById(R.id.loader)
        txtNoResult = view.findViewById(R.id.txt_no_result)
        txtSearchTitle = view.findViewById(R.id.txt_search_title)

        setupToolbar(view)


        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val input = view.findViewById<android.widget.EditText>(R.id.search_query_input)
        input.setText(savedInstanceState?.getString("query") ?: searchQuery.orEmpty())
        searchQuery = input.text.toString()
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString().orEmpty()
                if (catalogReady) performSearch(searchQuery.orEmpty())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        input.setOnEditorActionListener { _, action, _ ->
            if (action == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                (requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                    .hideSoftInputFromWindow(input.windowToken, 0)
                input.clearFocus()
                true
            } else false
        }
        loader.visibility = View.VISIBLE
        com.avapod.app.network.PodcastRepository(requireContext(), viewLifecycleOwner)
            .getPodcasts(onComplete = { loader.visibility = View.GONE }) { items ->
                catalog = items
                catalogReady = true
                performSearch(searchQuery.orEmpty())
            }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("query", searchQuery)
    }

    private fun setupToolbar(view: View) {
        val toolbar = view.findViewById<View>(R.id.common_toolbar)
        val btnBack = toolbar.findViewById<ImageButton>(R.id.btn_back_common)

        btnBack.scaleX = 1f
        btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        toolbar.findViewById<View>(R.id.btn_menu_common).visibility = View.GONE
    }

    private fun performSearch(query: String) {
            val filteredList = catalog.filter {
                (com.avapod.app.utils.StringUtils.matchesSearch(it.title, query) == true) ||
                        (com.avapod.app.utils.StringUtils.matchesSearch(it.artist, query) == true) ||
                        (com.avapod.app.utils.StringUtils.matchesSearch(it.description, query) == true)
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

    private fun setupRecyclerView(list: List<Podcast>) {
        val gridLayoutManager = GridLayoutManager(requireContext(), 2)
        rvResults.layoutManager = gridLayoutManager

        rvResults.adapter = PodcastAdapter(list) { podcast ->

            com.avapod.app.utils.AdManager.showAdWithCapping(this) {
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
