package com.avapod.app.Fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.avapod.app.R
import com.avapod.app.adapters.AdminPodcastAdapter
import com.avapod.app.models.Podcast
import com.avapod.app.utils.DatabaseHelper
import com.avapod.app.utils.DialogHelper
import com.google.firebase.database.FirebaseDatabase

class AdminDashboardFragment : Fragment() {

    private lateinit var rvAdminPodcasts: RecyclerView
    private lateinit var btnAddNew: Button

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_admin_dashboard, container, false)

        rvAdminPodcasts = view.findViewById(R.id.rv_admin_podcasts)
        btnAddNew = view.findViewById(R.id.btn_add_new_podcast)

        setupToolbar(view)

        btnAddNew.text = getString(R.string.btn_add_new_podcast)

        rvAdminPodcasts.layoutManager = LinearLayoutManager(requireContext())

        btnAddNew.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, AddPodcastFragment())
                .addToBackStack(null).commit()
        }

        loadAllPodcasts()
        return view
    }

    private fun loadAllPodcasts() {
        FirebaseDatabase.getInstance().getReference("podcasts")
            .get().addOnSuccessListener { snapshot ->
                val list = mutableListOf<Podcast>()
                for (child in snapshot.children) {
                    val pod = child.getValue(Podcast::class.java)?.apply { id = child.key ?: "" }
                    if (pod != null) list.add(pod)
                }

                rvAdminPodcasts.adapter = AdminPodcastAdapter(
                    list,
                    onEditClick = { podcast ->
                        val fragment = AddPodcastFragment().apply {
                            arguments = Bundle().apply { putSerializable("podcast_data", podcast) }
                        }
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.nav_host_fragment, fragment)
                            .addToBackStack(null).commit()
                    },
                    onDeleteClick = { podcast ->
                        showDeleteConfirmation(podcast)
                    }
                )
            }.addOnFailureListener {
                Toast.makeText(context, getString(R.string.error_firebase_connection), Toast.LENGTH_SHORT).show()
            }
    }

    private fun showDeleteConfirmation(podcast: Podcast) {
        DialogHelper.showConfirmDialog(
            context = requireContext(),
            title = getString(R.string.dialog_delete_title),
            message = getString(R.string.dialog_delete_message, podcast.title),
            onPositiveClick = {
                FirebaseDatabase.getInstance().getReference("podcasts")
                    .child(podcast.id).removeValue()
                    .addOnSuccessListener {
                        DatabaseHelper(requireContext()).deletePodcast(podcast.id)
                        Toast.makeText(context, getString(R.string.toast_podcast_deleted), Toast.LENGTH_SHORT).show()
                        loadAllPodcasts()
                    }
            }
        )
    }

    private fun setupToolbar(view: View) {
        val toolbar = view.findViewById<View>(R.id.common_toolbar)
        if (toolbar != null) {
            val btnBack = toolbar.findViewById<ImageButton>(R.id.btn_back_common)
            val btnMenu = toolbar.findViewById<ImageButton>(R.id.btn_menu_common)

            btnMenu.visibility = View.GONE
            btnBack.scaleX = -1f

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
}