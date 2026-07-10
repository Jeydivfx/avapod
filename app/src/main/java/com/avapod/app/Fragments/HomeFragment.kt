package com.avapod.app.Fragments

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.avapod.app.PlayerActivity
import com.avapod.app.R
import com.avapod.app.adapters.ContinueAdapter
import com.avapod.app.adapters.PodcastAdapter
import com.avapod.app.models.Podcast
import com.avapod.app.models.RssItem
import com.avapod.app.network.PodcastRepository
import com.avapod.app.utils.AdManager
import com.avapod.app.utils.DatabaseHelper
import com.avapod.app.utils.PreferenceHelper
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.facebook.shimmer.ShimmerFrameLayout
import java.util.Calendar

class HomeFragment : Fragment() {

    private lateinit var rvContinueListening: RecyclerView
    private lateinit var rvTrending: RecyclerView
    private lateinit var rvRecommended: RecyclerView
    private lateinit var prefHelper: PreferenceHelper
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var shimmerTrending: ShimmerFrameLayout
    private lateinit var progressLoading: ProgressBar
    private lateinit var cardEmptyContinue: CardView
    private lateinit var imgFeatured: ImageView
    private lateinit var txtFeaturedTitle: TextView
    private lateinit var txtFeaturedArtist: TextView
    private lateinit var featuredCard: CardView
    private lateinit var btnAllTrending: TextView
    private lateinit var btnAllResumes: TextView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private var trendingAdapter: PodcastAdapter? = null
    private var recommendedAdapter: PodcastAdapter? = null
    private var continueAdapter: ContinueAdapter? = null
    private var allPodcasts: List<Podcast> = emptyList()
    private var adCounter = 0
    private val handler = Handler(Looper.getMainLooper())
    private var isRefreshing = false
    private var featuredPodcast: Podcast? = null
    private var isFirstLoad = true

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

        prefHelper = PreferenceHelper(requireContext())
        dbHelper = DatabaseHelper(requireContext())

        initViews(view)
        setupSearch(view)
        setupQuickActions(view)
        setupTrendingSection()
        setupRecommendedSection()
        setupContinueListening()
        setupViewAllButtons()
        setupSwipeRefresh()

        loadDataFromLocalOrNetwork()

        AdManager.preloadInterstitialAd(requireContext())
        AdManager.preloadRewardedAd(requireContext())
    }

    private fun initViews(view: View) {
        rvContinueListening = view.findViewById(R.id.rv_continue_listening)
        rvTrending = view.findViewById(R.id.rv_trending)
        rvRecommended = view.findViewById(R.id.rv_recommended)
        shimmerTrending = view.findViewById(R.id.shimmer_trending)
        progressLoading = view.findViewById(R.id.progress_loading)
        cardEmptyContinue = view.findViewById(R.id.card_empty_continue)
        imgFeatured = view.findViewById(R.id.img_featured)
        txtFeaturedTitle = view.findViewById(R.id.txt_featured_title)
        txtFeaturedArtist = view.findViewById(R.id.txt_featured_artist)
        featuredCard = view.findViewById(R.id.featured_card)
        btnAllTrending = view.findViewById(R.id.btn_all_trending)
        btnAllResumes = view.findViewById(R.id.btn_all_resumes)
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout)
    }


    private fun loadDataFromLocalOrNetwork() {
        val localPodcasts = dbHelper.getAllPodcasts()

        if (localPodcasts.isNotEmpty()) {
            allPodcasts = localPodcasts
            updateUI(localPodcasts)
        } else {
            showLoading(true)
            fetchDataFromNetwork()
        }
    }

    private fun fetchDataFromNetwork() {
        val repository = PodcastRepository(requireContext())
        repository.getPodcasts { podcasts ->
            activity?.runOnUiThread {
                if (podcasts.isNotEmpty()) {
                    dbHelper.insertPodcasts(podcasts)
                    allPodcasts = podcasts
                    updateUI(podcasts)
                }
                showLoading(false)
            }
        }
    }

    private fun updateUI(podcasts: List<Podcast>) {
        setupFeaturedContent(podcasts)
        updateTrendingData(podcasts)
        updateRecommendedData(podcasts)
    }

    private fun showLoading(show: Boolean) {
        if (show) {
            shimmerTrending.visibility = View.VISIBLE
            shimmerTrending.startShimmer()
            progressLoading.visibility = View.VISIBLE
        } else {
            shimmerTrending.stopShimmer()
            shimmerTrending.visibility = View.GONE
            progressLoading.visibility = View.GONE
        }
    }

    private fun setupFeaturedContent(podcasts: List<Podcast>) {
        val selectedPodcast = getFeaturedPodcast(podcasts)
        featuredPodcast = selectedPodcast

        if (selectedPodcast != null) {
            Glide.with(requireContext())
                .load(selectedPodcast.thumbnail_url)
                .placeholder(R.drawable.featured_placeholder)
                .error(R.drawable.featured_placeholder)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(imgFeatured)

            txtFeaturedTitle.text = selectedPodcast.title ?: "پادکست ویژه امروز"
            txtFeaturedArtist.text = selectedPodcast.artist ?: "گوینده"

            featuredCard.setOnClickListener {
                navigateWithAd {
                    openPodcastDetail(selectedPodcast)
                }
            }
        } else {
            imgFeatured.setImageResource(R.drawable.featured_placeholder)
            txtFeaturedTitle.text = "پادکست ویژه امروز"
            txtFeaturedArtist.text = "به زودی..."
            featuredCard.setOnClickListener {
                Toast.makeText(requireContext(), "محتوای ویژه به زودی!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getFeaturedPodcast(podcasts: List<Podcast>): Podcast? {
        if (podcasts.isEmpty()) return null

        val featuredPodcasts = podcasts.filter { podcast ->
            podcast.is_featured &&
                    !podcast.title.isNullOrEmpty() &&
                    !podcast.thumbnail_url.isNullOrEmpty()
        }

        if (featuredPodcasts.isNotEmpty()) {
            return featuredPodcasts[0]
        }

        val validPodcasts = podcasts.filter { podcast ->
            !podcast.title.isNullOrEmpty() &&
                    !podcast.thumbnail_url.isNullOrEmpty()
        }

        return validPodcasts.firstOrNull()
    }

    private fun setupTrendingSection() {
        shimmerTrending.visibility = View.VISIBLE
        shimmerTrending.startShimmer()
        rvTrending.visibility = View.GONE

        trendingAdapter = PodcastAdapter(emptyList()) { podcast ->
            navigateWithAd {
                openPodcastDetail(podcast)
            }
        }

        rvTrending.adapter = trendingAdapter
        rvTrending.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
    }

    private fun updateTrendingData(podcasts: List<Podcast>) {
        val trendingList = podcasts.filter { it.is_trending }.reversed().take(8)

        activity?.runOnUiThread {
            shimmerTrending.stopShimmer()
            shimmerTrending.visibility = View.GONE
            rvTrending.visibility = View.VISIBLE
            trendingAdapter?.updateData(trendingList)
        }
    }

    private fun setupRecommendedSection() {
        recommendedAdapter = PodcastAdapter(emptyList()) { podcast ->
            navigateWithAd {
                openPodcastDetail(podcast)
            }
        }

        rvRecommended.adapter = recommendedAdapter
        rvRecommended.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
    }

    private fun updateRecommendedData(podcasts: List<Podcast>) {
        val recommendedList = podcasts.filter { !it.is_trending }.take(6)

        activity?.runOnUiThread {
            recommendedAdapter?.updateData(recommendedList)
        }
    }

    private fun setupContinueListening() {
        if (!::prefHelper.isInitialized) {
            return
        }

        val fullList = prefHelper.getContinueListening().toMutableList()
        val downloadFolder = requireContext().getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)

        val iterator = fullList.iterator()
        while (iterator.hasNext()) {
            val episode = iterator.next()
            val url = episode.audioUrl ?: ""

            if (url.startsWith("file://") || url.contains("/DIRECTORY_MUSIC/")) {
                val expectedName = "${episode.title?.replace(" ", "_")}.mp3"
                val localFile = java.io.File(downloadFolder, expectedName)

                if (!localFile.exists()) {
                    iterator.remove()
                    prefHelper.removeSingleContinueListening(url)
                }
            }
        }

        val limitedList = fullList.take(3)

        if (limitedList.isNotEmpty()) {
            cardEmptyContinue.visibility = View.GONE
            rvContinueListening.visibility = View.VISIBLE

            continueAdapter = ContinueAdapter(limitedList) { episode ->
                navigateWithAd {
                    openPlayer(episode)
                }
            }

            rvContinueListening.adapter = continueAdapter
            rvContinueListening.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        } else {
            rvContinueListening.visibility = View.GONE
            cardEmptyContinue.visibility = View.VISIBLE

            requireView().findViewById<Button>(R.id.btn_start_exploring)?.setOnClickListener {
                val scrollView = requireView().findViewById<NestedScrollView>(R.id.scrollView)
                scrollView?.smoothScrollTo(0, rvTrending.top)
            }
        }
    }

    private fun setupSearch(view: View) {
        val searchBar = view.findViewById<EditText>(R.id.search_bar)
        val rvSuggestions = view.findViewById<RecyclerView>(R.id.rv_search_suggestions)
        val txtSuggestionsHeader = view.findViewById<TextView>(R.id.txt_suggestions_header)

        val filteredList = mutableListOf<Podcast>()

        val suggestionAdapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val v = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_search_suggestion, parent, false)
                return object : RecyclerView.ViewHolder(v) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val podcast = filteredList[position]
                val txtTitle = holder.itemView.findViewById<TextView>(R.id.txt_suggestion_title)
                val icon = holder.itemView.findViewById<ImageView>(R.id.img_suggestion_icon)

                txtTitle.text = podcast.title

                Glide.with(requireContext())
                    .load(podcast.thumbnail_url)
                    .placeholder(R.drawable.ic_podcast)
                    .error(R.drawable.ic_podcast)
                    .circleCrop()
                    .into(icon)

                holder.itemView.setOnClickListener {
                    navigateWithAd {
                        openPodcastDetail(podcast)
                        searchBar.setText("")
                        rvSuggestions.visibility = View.GONE
                        txtSuggestionsHeader.visibility = View.GONE
                    }
                }
            }

            override fun getItemCount(): Int = filteredList.size
        }

        rvSuggestions.adapter = suggestionAdapter
        rvSuggestions.layoutManager = LinearLayoutManager(requireContext())

        searchBar.addTextChangedListener(object : android.text.TextWatcher {
            private val searchRunnable = Runnable {
                val query = searchBar.text.toString().trim()
                if (query.length >= 2) {
                    performSearch(query, filteredList, suggestionAdapter, rvSuggestions, txtSuggestionsHeader)
                } else {
                    rvSuggestions.visibility = View.GONE
                    txtSuggestionsHeader.visibility = View.GONE
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                handler.removeCallbacks(searchRunnable)
                handler.postDelayed(searchRunnable, 300)
            }

            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        searchBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = searchBar.text.toString().trim()
                if (query.isNotEmpty()) {
                    navigateWithAd {
                        openSearchFragment(query)
                        searchBar.setText("")
                        rvSuggestions.visibility = View.GONE
                        txtSuggestionsHeader.visibility = View.GONE
                    }
                    hideKeyboard(searchBar)
                }
                true
            } else {
                false
            }
        }

        view.findViewById<ImageView>(R.id.btn_voice_search)?.setOnClickListener {
            Toast.makeText(requireContext(), "جستجوی صوتی به زودی!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performSearch(
        query: String,
        filteredList: MutableList<Podcast>,
        adapter: RecyclerView.Adapter<*>,
        rvSuggestions: RecyclerView,
        txtSuggestionsHeader: TextView
    ) {
        if (allPodcasts.isEmpty()) {
            val repository = PodcastRepository(requireContext())
            repository.getPodcasts { podcasts ->
                allPodcasts = podcasts
                updateSearchResults(query, filteredList, adapter, rvSuggestions, txtSuggestionsHeader)
            }
        } else {
            updateSearchResults(query, filteredList, adapter, rvSuggestions, txtSuggestionsHeader)
        }
    }

    private fun updateSearchResults(
        query: String,
        filteredList: MutableList<Podcast>,
        adapter: RecyclerView.Adapter<*>,
        rvSuggestions: RecyclerView,
        txtSuggestionsHeader: TextView
    ) {
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
                txtSuggestionsHeader.visibility = View.VISIBLE
                adapter.notifyDataSetChanged()
            } else {
                rvSuggestions.visibility = View.GONE
                txtSuggestionsHeader.visibility = View.GONE
            }
        }
    }

    private fun setupQuickActions(view: View) {
        val actions = listOf(
            R.id.btn_new_episodes to { openFragment(NewEpisodesFragment()) },
            R.id.btn_nav_downloads to { openFragment(DownloadFragment()) },
            R.id.btn_nav_bookmarks to { openFragment(BookmarkFragment()) },
            R.id.btn_nav_settings to { openFragment(SettingsFragment()) }
        )

        actions.forEach { (id, action) ->
            view.findViewById<LinearLayout>(id)?.setOnClickListener {
                navigateWithAd(action)
            }
        }
    }

    private fun setupViewAllButtons() {
        btnAllTrending.setOnClickListener {
            navigateWithAd {
                openFragment(TrendingAllFragment())
            }
        }

        btnAllResumes.setOnClickListener {
            navigateWithAd {
                openFragment(ContinueAllFragment())
            }
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            refreshAllData()
        }

        swipeRefreshLayout.setColorSchemeColors(
            resources.getColor(R.color.primary_blue),
            resources.getColor(R.color.accent_cyan),
            resources.getColor(R.color.text_white)
        )
    }

    private fun refreshAllData() {
        if (isRefreshing) return
        isRefreshing = true

        swipeRefreshLayout.isRefreshing = true

        val repository = PodcastRepository(requireContext())
        repository.getPodcasts { podcasts ->
            activity?.runOnUiThread {
                if (podcasts.isNotEmpty()) {
                    dbHelper.insertPodcasts(podcasts)
                    allPodcasts = podcasts
                    updateUI(podcasts)
                }
                swipeRefreshLayout.isRefreshing = false
                isRefreshing = false
            }
        }
    }

    private fun openFragment(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun openPodcastDetail(podcast: Podcast) {
        val fragment = PodcastDetailFragment().apply {
            arguments = Bundle().apply {
                putString("rss_url", podcast.rss_url)
                putString("cover_url", podcast.thumbnail_url)
                putString("title", podcast.title)
                putString("description", podcast.description)
                putString("artist", podcast.artist)
            }
        }
        openFragment(fragment)
    }

    private fun openSearchFragment(query: String) {
        val fragment = SearchFragment().apply {
            arguments = Bundle().apply {
                putString("search_query", query)
            }
        }
        openFragment(fragment)
    }

    private fun openPlayer(episode: RssItem) {
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

    private fun navigateWithAd(action: () -> Unit) {
        adCounter++
        if (adCounter % 3 == 0) {
            AdManager.showAdWithCapping(requireActivity()) {
                action()
            }
        } else {
            action()
        }
    }

    private fun hideKeyboard(view: View) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    override fun onResume() {
        super.onResume()
        if (::prefHelper.isInitialized) {
            setupContinueListening()
        }

        activity?.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)?.let { bottomNav ->
            if (bottomNav.selectedItemId != R.id.nav_home) {
                bottomNav.selectedItemId = R.id.nav_home
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)

        rvContinueListening.adapter = null
        rvTrending.adapter = null
        rvRecommended.adapter = null
    }
}