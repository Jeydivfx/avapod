package com.avapod.app.Fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.avapod.app.PlayerActivity
import com.avapod.app.R
import com.avapod.app.adapters.EpisodeAdapter
import com.avapod.app.utils.DialogHelper
import com.avapod.app.utils.PreferenceHelper

class BookmarkFragment : Fragment() {

    private lateinit var rvBookmarks: RecyclerView
    private lateinit var prefHelper: PreferenceHelper
    private lateinit var txtEmpty: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_bookmark, container, false)

        prefHelper = PreferenceHelper(requireContext())
        rvBookmarks = view.findViewById(R.id.rv_bookmarks)
        txtEmpty = view.findViewById(R.id.txt_empty_bookmarks)
        val toolbar = view.findViewById<View>(R.id.common_toolbar)
        val btnBack = toolbar.findViewById<ImageButton>(R.id.btn_back_common)
        val btnMenu = toolbar.findViewById<ImageButton>(R.id.btn_menu_common)

        btnBack.scaleX = -1f

        btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        btnMenu.setOnClickListener { v ->
            showBookmarkMenu(v)
        }

        setupRecyclerView()

        return view
    }

    private fun showBookmarkMenu(anchorView: View) {
        val context = requireContext()
        val menuItems = arrayOf(context.getString(R.string.menu_clear_all_bookmarks))

        val popupWindow = androidx.appcompat.widget.ListPopupWindow(context)
        popupWindow.anchorView = anchorView
        popupWindow.setDropDownGravity(android.view.Gravity.END)

        popupWindow.setBackgroundDrawable(androidx.core.content.ContextCompat.getDrawable(context, R.drawable.bg_popup_menu))

        popupWindow.width = (200 * resources.displayMetrics.density).toInt()

        val adapter = android.widget.ArrayAdapter<String>(
            context,
            R.layout.item_popup_menu,
            menuItems
        )
        popupWindow.setAdapter(adapter)

        popupWindow.setOnItemClickListener { _, _, position, _ ->
            when (position) {
                0 -> {
                    showClearAllDialog()
                    popupWindow.dismiss()
                }
            }
        }

        popupWindow.show()
    }

    private fun showClearAllDialog() {
        val context = requireContext()
        DialogHelper.showConfirmDialog(
            context = context,
            title = context.getString(R.string.dialog_clear_all_title),
            message = context.getString(R.string.dialog_clear_all_message)
        ) {
            prefHelper.clearAllBookmarks()
            setupRecyclerView()
            Toast.makeText(context, context.getString(R.string.toast_list_cleared), Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecyclerView() {
        val bookmarkedEpisodes = prefHelper.getBookmarkedEpisodes()

        if (bookmarkedEpisodes.isEmpty()) {
            txtEmpty.visibility = View.VISIBLE
            rvBookmarks.visibility = View.GONE
        } else {
            txtEmpty.visibility = View.GONE
            rvBookmarks.visibility = View.VISIBLE

            rvBookmarks.layoutManager = LinearLayoutManager(requireContext())

            rvBookmarks.adapter = EpisodeAdapter(bookmarkedEpisodes, "") { episode ->

                com.avapod.app.utils.AdManager.showAdWithCapping(requireActivity()) {
                    val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                        putExtra("audio_url", episode.audioUrl)
                        putExtra("title", episode.title)
                        putExtra("cover", episode.imageUrl)
                        putExtra("duration", episode.duration)
                    }
                    startActivity(intent)
                }

            }
        }
    }
}