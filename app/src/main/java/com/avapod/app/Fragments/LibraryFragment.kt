package com.avapod.app.Fragments

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
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
import com.avapod.app.utils.GridSpacingItemDecoration
import com.avapod.app.utils.PreferenceHelper

class LibraryFragment : Fragment() {

    private lateinit var rvLibrary: RecyclerView
    private lateinit var txtEmpty: TextView
    private lateinit var prefHelper: PreferenceHelper

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_library, container, false)

        rvLibrary = view.findViewById(R.id.rv_library)
        txtEmpty = view.findViewById(R.id.txt_empty_library)
        prefHelper = PreferenceHelper(requireContext())

        view.findViewById<View>(R.id.library_notifications).setOnClickListener {
            if (!parentFragmentManager.isStateSaved) {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.nav_host_fragment, InboxFragment())
                    .addToBackStack(null).commit()
            }
        }
        val inboxContext = requireContext().applicationContext
        fun updateUnread() {
            view.findViewById<View>(R.id.library_unread_dot).visibility =
                if (com.avapod.app.network.AnnouncementInbox.unread(inboxContext)) View.VISIBLE else View.GONE
        }
        updateUnread()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
                while (true) {
                    updateUnread()
                    try { com.avapod.app.network.AnnouncementInbox.refresh(inboxContext, force = true) }
                    catch (e: Exception) {
                        if (e is CancellationException && e !is TimeoutCancellationException) throw e
                    }
                    updateUnread()
                    delay(60_000)
                }
            }
        }
        setupToolbar(view)
        view.findViewById<View>(R.id.common_toolbar).visibility = View.GONE
        listOf(
            R.id.library_new_episodes to { NewEpisodesFragment() },
            R.id.library_downloads to { DownloadFragment() },
            R.id.library_bookmarks to { BookmarkFragment() },
            R.id.library_history to { ContinueAllFragment() },
            R.id.library_playlist to { PlaylistFragment() }
        ).forEach { (id, destination) ->
            view.findViewById<View>(id).setOnClickListener {
                if (!parentFragmentManager.isStateSaved) {
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.nav_host_fragment, destination())
                        .addToBackStack(null).commit()
                }
            }
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        setupRecyclerView()

    }

    private fun setupToolbar(view: View) {
        val toolbar = view.findViewById<View>(R.id.common_toolbar)
        if (toolbar != null) {
            val btnBack = toolbar.findViewById<ImageButton>(R.id.btn_back_common)
            val btnMenu = toolbar.findViewById<ImageButton>(R.id.btn_menu_common)

            btnMenu.visibility = View.GONE
            btnBack.scaleX = 1f

            btnBack.setOnClickListener {
                val bottomNav = activity?.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)

                if (bottomNav != null) {

                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        bottomNav.selectedItemId = R.id.nav_home
                    }, 50)
                } else {
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.nav_host_fragment, HomeFragment())
                        .commitAllowingStateLoss()
                }
            }
        }
    }

    private fun setupRecyclerView() {
        val subscribedPodcasts = prefHelper.getSubscribedPodcasts()

        if (subscribedPodcasts.isEmpty()) {
            txtEmpty.visibility = View.VISIBLE
            rvLibrary.visibility = View.GONE
        } else {
            txtEmpty.visibility = View.GONE
            rvLibrary.visibility = View.VISIBLE

            val columns = if (resources.configuration.fontScale > 1.3f) 2 else 3
            rvLibrary.layoutManager = GridLayoutManager(context, columns)

            val spacingInPixels = resources.getDimensionPixelSize(R.dimen.grid_spacing)
            while (rvLibrary.itemDecorationCount > 0) rvLibrary.removeItemDecorationAt(0)
            rvLibrary.addItemDecoration(GridSpacingItemDecoration(columns, spacingInPixels, true))

            val adapter = PodcastAdapter(subscribedPodcasts, compact = true) { podcast ->
                com.avapod.app.utils.AdManager.navigateWithoutAd(this) {
                    val fragment = PodcastDetailFragment().apply {
                        arguments = Bundle().apply {
                            putString("rss_url", podcast.rss_url)
                            putString("cover_url", podcast.thumbnail_url)
                            putString("title", podcast.title)
                            putString("description", podcast.description)
                            putString("artist", podcast.artist)
                        }
                    }

                    parentFragmentManager.executePendingTransactions()
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.nav_host_fragment, fragment)
                        .addToBackStack(null)
                        .commitAllowingStateLoss()
                }
            }
            rvLibrary.adapter = adapter
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            if (::rvLibrary.isInitialized) {
                rvLibrary.adapter = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
