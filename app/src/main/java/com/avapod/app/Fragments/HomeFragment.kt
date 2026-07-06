package com.avapod.app.Fragments

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.avapod.app.PlayerActivity
import com.avapod.app.R
import com.avapod.app.adapters.ContinueAdapter
import com.avapod.app.adapters.PodcastAdapter
import com.avapod.app.network.PodcastRepository
import com.avapod.app.utils.AdManager
import com.avapod.app.utils.PreferenceHelper
import com.avapod.app.utils.StringUtils
import saman.zamani.persiandate.PersianDate

class HomeFragment : Fragment() {

    private lateinit var rvContinueListening: RecyclerView
    private lateinit var prefHelper: PreferenceHelper

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    @SuppressLint("ServiceCast")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //Persian date
        val txtDate = view.findViewById<TextView>(R.id.txt_date)
        val pdate = PersianDate()
        val rawDateString = "${pdate.dayName()}، ${pdate.shDay} ${pdate.monthName()} ${pdate.shYear}"
        val persianDateString = StringUtils.toPersianNumber(rawDateString)
        txtDate.text = persianDateString

        prefHelper = PreferenceHelper(requireContext())
        rvContinueListening = view.findViewById(R.id.rv_continue_listening)


        val trendingAdapter = PodcastAdapter(listOf()) { podcast ->
            AdManager.showAdWithCapping(requireActivity()) {
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

        val rvTrending = view.findViewById<RecyclerView>(R.id.rv_trending)
        rvTrending.adapter = trendingAdapter
        rvTrending.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        val repository = PodcastRepository(requireContext())
        repository.getPodcasts { podcasts ->
            val trendingList = podcasts.filter { it.is_trending }.reversed().take(5)

            activity?.runOnUiThread {
                trendingAdapter.updateData(trendingList)
            }
        }


        val btnBookmarkNav = view.findViewById<LinearLayout>(R.id.btn_nav_bookmarks)
        btnBookmarkNav.setOnClickListener {
            AdManager.showAdWithCapping(requireActivity()) {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.nav_host_fragment, BookmarkFragment())
                    .addToBackStack(null)
                    .commit()
            }
        }


        val btnDownload = view.findViewById<LinearLayout>(R.id.btn_nav_downloads)
        btnDownload.setOnClickListener {
            AdManager.showAdWithCapping(requireActivity()) {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.nav_host_fragment, DownloadFragment())
                    .addToBackStack(null)
                    .commit()
            }
        }



        val btnSettings = view.findViewById<LinearLayout>(R.id.btn_nav_settings)
        btnSettings.setOnClickListener {
            AdManager.showAdWithCapping(requireActivity()) {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.nav_host_fragment, SettingsFragment())
                    .addToBackStack(null)
                    .commit()
            }
        }


        val btnNewEpisodes = view.findViewById<LinearLayout>(R.id.btn_new_episodes)
        btnNewEpisodes.setOnClickListener {
            AdManager.showAdWithCapping(requireActivity()) {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.nav_host_fragment, NewEpisodesFragment())
                    .addToBackStack(null)
                    .commit()
            }
        }


        val btnAllTrending = view.findViewById<TextView>(R.id.btn_all_trending)
        btnAllTrending.setOnClickListener {
            AdManager.showAdWithCapping(requireActivity()) {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.nav_host_fragment, TrendingAllFragment())
                    .addToBackStack(null)
                    .commit()
            }
        }

        val btnAllResumes = view.findViewById<TextView>(R.id.btn_all_resumes)
        btnAllResumes.setOnClickListener {
            AdManager.showAdWithCapping(requireActivity()) {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.nav_host_fragment, ContinueAllFragment())
                    .addToBackStack(null)
                    .commit()
            }
        }

        val searchBar = view.findViewById<EditText>(R.id.search_bar)
        searchBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = searchBar.text.toString().trim()
                if (query.isNotEmpty()) {
                    AdManager.showAdWithCapping(requireActivity()) {
                        openSearchFragment(query)
                        searchBar.setText("")
                    }

                    val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(searchBar.windowToken, 0)
                }
                true
            } else {
                false
            }
        }

        setupLiveSearch(view)

        AdManager.preloadInterstitialAd(requireContext())
        AdManager.preloadRewardedAd(requireContext())
    }

    override fun onResume() {
        super.onResume()

        activity?.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)?.let { bottomNav ->
            if (bottomNav.selectedItemId != R.id.nav_home) {
                bottomNav.selectedItemId = R.id.nav_home
            }
        }

        setupContinueListening()
    }


    private fun setupContinueListening() {
        val fullList = prefHelper.getContinueListening().toMutableList()
        val downloadFolder = requireContext().getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)

        val iterator = fullList.iterator()
        var hasChanges = false

        while (iterator.hasNext()) {
            val episode = iterator.next()
            val url = episode.audioUrl ?: ""

            if (url.startsWith("file://") || url.contains("/DIRECTORY_MUSIC/")) {
                val expectedName = "${episode.title?.replace(" ", "_")}.mp3"
                val localFile = java.io.File(downloadFolder, expectedName)

                if (!localFile.exists()) {
                    iterator.remove()
                    prefHelper.removeSingleContinueListening(episode.audioUrl ?: "")
                }
            }
        }

        val limitedList = fullList.take(3)

        if (limitedList.isNotEmpty()) {
            rvContinueListening.visibility = View.VISIBLE
            rvContinueListening.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
            rvContinueListening.isNestedScrollingEnabled = false

            rvContinueListening.layoutDirection = View.LAYOUT_DIRECTION_LTR
            rvContinueListening.adapter = ContinueAdapter(limitedList) { episode ->
                AdManager.showAdWithCapping(requireActivity()) {
                    val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                        putExtra("audio_url", episode.audioUrl)
                        putExtra("title", episode.title)
                        putExtra("cover", episode.imageUrl)
                        putExtra("duration", episode.duration)
                        putExtra("resume_position", episode.lastPosition)
                        putExtra("podcast_name", episode.artist ?: getString(R.string.default_podcast_artist))
                    }
                    startActivity(intent)
                }
            }
        } else {
            rvContinueListening.visibility = View.GONE
        }
    }

    private fun openSearchFragment(query: String) {
        val fragment = SearchFragment()
        val bundle = Bundle().apply {
            putString("search_query", query)
        }
        fragment.arguments = bundle

        parentFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {

            if (::rvContinueListening.isInitialized) {
                rvContinueListening.adapter = null
            }

            val rvTrending = view?.findViewById<RecyclerView>(R.id.rv_trending)
            if (rvTrending != null) {
                rvTrending.adapter = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    private fun setupLiveSearch(view: View) {
        val searchBar = view.findViewById<EditText>(R.id.search_bar)
        val rvSuggestions = view.findViewById<RecyclerView>(R.id.rv_search_suggestions)

        val filteredList = mutableListOf<com.avapod.app.models.Podcast>()

        val suggestionAdapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_search_suggestion, parent, false)
                return object : RecyclerView.ViewHolder(v) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val podcast = filteredList[position]
                val txtTitle = holder.itemView.findViewById<TextView>(R.id.txt_suggestion_title)

                txtTitle.text = podcast.title

                holder.itemView.setOnClickListener {
                    AdManager.showAdWithCapping(requireActivity()) {
                        val fragment = PodcastDetailFragment().apply {
                            arguments = Bundle().apply {
                                putString("rss_url", podcast.rss_url)
                                putString("cover_url", podcast.thumbnail_url)
                                putString("title", podcast.title)
                                putString("description", podcast.description)
                                putString("artist", podcast.artist)
                            }
                        }
                        searchBar.setText("")
                        rvSuggestions.visibility = View.GONE
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.nav_host_fragment, fragment)
                            .addToBackStack(null)
                            .commit()
                    }
                }
            }

            override fun getItemCount(): Int = filteredList.size
        }

        rvSuggestions.adapter = suggestionAdapter
        rvSuggestions.layoutManager = LinearLayoutManager(requireContext())

        searchBar.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()

                if (query.length >= 2) {
                    val repository = com.avapod.app.network.PodcastRepository(requireContext())
                    repository.getPodcasts { allPodcasts ->
                        val matches = allPodcasts.filter { podcast ->
                            (podcast.title?.contains(query, ignoreCase = true) ?: false) ||
                                    (podcast.description?.contains(query, ignoreCase = true) ?: false) ||
                                    (podcast.artist?.contains(query, ignoreCase = true) ?: false)
                        }.take(5)

                        activity?.runOnUiThread {
                            filteredList.clear()
                            filteredList.addAll(matches)

                            if (filteredList.isNotEmpty()) {
                                rvSuggestions.visibility = View.VISIBLE
                                suggestionAdapter.notifyDataSetChanged()
                            } else {
                                rvSuggestions.visibility = View.GONE
                            }
                        }
                    }
                } else {
                    activity?.runOnUiThread { rvSuggestions.visibility = View.GONE }
                }
            }

            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        searchBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = searchBar.text.toString().trim()
                if (query.isNotEmpty()) {
                    AdManager.showAdWithCapping(requireActivity()) {
                        openSearchFragment(query)
                        searchBar.setText("")
                        rvSuggestions.visibility = View.GONE
                    }

                    val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(searchBar.windowToken, 0)
                }
                true
            } else {
                false
            }
        }
    }


}