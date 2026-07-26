package com.avapod.app.Fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.avapod.app.PlayerActivity
import com.avapod.app.R
import com.avapod.app.adapters.ContinueAdapter
import com.avapod.app.utils.AdManager
import com.avapod.app.utils.DialogHelper
import com.avapod.app.utils.PreferenceHelper

class ContinueAllFragment : Fragment() {

    private lateinit var rv: RecyclerView
    private lateinit var prefHelper: PreferenceHelper

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_continue_all, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefHelper = PreferenceHelper(requireContext())
        rv = view.findViewById(R.id.rv_all_continue)

        setupToolbar(view)
        setupRecyclerView()
    }

    private fun setupToolbar(view: View) {
        val toolbar = view.findViewById<View>(R.id.common_toolbar)
        val btnBack = toolbar.findViewById<ImageButton>(R.id.btn_back_common)
        val btnMenu = toolbar.findViewById<ImageButton>(R.id.btn_menu_common)

        btnMenu.visibility = View.VISIBLE
        btnBack.scaleX = -1f
        btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        btnMenu.setOnClickListener { showDeleteMenu(it) }
    }

    private fun setupRecyclerView() {
        val fullList = prefHelper.getContinueListening()
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = ContinueAdapter(fullList) { episode ->
            AdManager.showAdWithCapping(requireActivity()) {
                val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                    putExtra("audio_url", episode.audioUrl)
                    putExtra("title", episode.title)
                    putExtra("cover", episode.imageUrl)
                    putExtra("resume_position", episode.lastPosition)
                }
                startActivity(intent)
            }
        }
    }

    private fun showDeleteMenu(view: View) {
        val context = requireContext()
        val popup = PopupMenu(context, view)

        popup.menu.add(getString(R.string.menu_clear_continue))

        popup.setOnMenuItemClickListener {
            showConfirmDialog()
            true
        }
        popup.show()
    }

    private fun showConfirmDialog() {
        DialogHelper.showConfirmDialog(
            context = requireContext(),
            title = getString(R.string.dialog_clear_continue_title),
            message = getString(R.string.dialog_clear_continue_message)
        ) {
            prefHelper.clearAllContinueListening()
            setupRecyclerView() // رفرش کردن لیست بعد از پاک کردن
            Toast.makeText(requireContext(), getString(R.string.toast_list_cleared), Toast.LENGTH_SHORT).show()
        }
    }
}