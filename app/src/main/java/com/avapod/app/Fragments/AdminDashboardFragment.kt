package com.avapod.app.Fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import android.widget.EditText
import androidx.core.widget.doAfterTextChanged
import com.avapod.app.utils.PodcastOrder
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.avapod.app.R
import com.avapod.app.adapters.AdminPodcastAdapter
import com.avapod.app.models.Podcast
import com.avapod.app.network.AdminAccess
import com.avapod.app.network.awaitResult
import com.avapod.app.utils.DialogHelper
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class AdminDashboardFragment : Fragment() {
    private var allPodcasts = emptyList<Podcast>()
    private var searchQuery = ""
    private var loadJob: kotlinx.coroutines.Job? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_admin_dashboard, container, false)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        searchQuery = savedInstanceState?.getString("admin_search") ?: searchQuery
        view.findViewById<RecyclerView>(R.id.rv_admin_podcasts).layoutManager = LinearLayoutManager(requireContext())
        view.findViewById<EditText>(R.id.admin_search).apply {
            setText(searchQuery)
            doAfterTextChanged {
                searchQuery = it?.toString().orEmpty()
                renderList()
            }
        }
        view.findViewById<View>(R.id.btn_back_common).apply {
            scaleX = 1f
            setOnClickListener { parentFragmentManager.popBackStack() }
        }

        view.findViewById<ImageButton>(R.id.btn_menu_common).apply {
            setImageResource(R.drawable.ic_close)
            contentDescription = getString(R.string.admin_sign_out)
            setOnClickListener { AdminAccess.signOut(); parentFragmentManager.popBackStack() }
        }
        val add = view.findViewById<Button>(R.id.btn_add_new_podcast)
        add.isEnabled = false
        add.setOnClickListener {
            parentFragmentManager.beginTransaction().replace(R.id.nav_host_fragment, AddPodcastFragment()).addToBackStack(null).commit()
        }
        load()
    }
    private fun load() {
        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                AdminAccess.requireAdmin()
                val snapshot = withTimeout(20_000) { FirebaseDatabase.getInstance().getReference("podcasts").get().awaitResult() }
                val order = withTimeout(15_000) { FirebaseDatabase.getInstance().getReference("db_config/podcast_order").get().awaitResult() }
                allPodcasts = PodcastOrder.newest(snapshot.children.mapNotNull { data ->
                    val id = data.key.orEmpty()
                    data.getValue(Podcast::class.java)?.copy(id = id,
                        createdAt = order.child(id).child("created_at").getValue(Long::class.java) ?: 0L,
                        trendingAt = order.child(id).child("trending_at").getValue(Long::class.java) ?: 0L)
                })
                requireView().findViewById<Button>(R.id.btn_add_new_podcast).isEnabled = true
                renderList()
            } catch (e: Exception) {
                if (e is CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) throw e
                Toast.makeText(context, R.string.admin_access_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun renderList() {
        val root = view ?: return
        val query = PodcastOrder.searchKey(searchQuery)
        val filtered = allPodcasts.filter { query.isEmpty() || PodcastOrder.searchKey(it.title).contains(query) }
        root.findViewById<View>(R.id.admin_search_empty).visibility =
            if (filtered.isEmpty()) View.VISIBLE else View.GONE
        root.findViewById<RecyclerView>(R.id.rv_admin_podcasts).adapter =
            AdminPodcastAdapter(filtered, { podcast ->
                        parentFragmentManager.beginTransaction().replace(R.id.nav_host_fragment,
                            AddPodcastFragment().apply { arguments = Bundle().apply { putSerializable("podcast_data", podcast) } })
                            .addToBackStack(null).commit()
                    }, { podcast -> confirmDelete(podcast) })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("admin_search", searchQuery)
    }
    private fun confirmDelete(podcast: Podcast) {
        DialogHelper.showConfirmDialog(requireContext(), getString(R.string.dialog_delete_title),
            getString(R.string.dialog_delete_message, podcast.title), owner = viewLifecycleOwner) {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    AdminAccess.savePodcast(podcast.id, null)
                    Toast.makeText(context, R.string.toast_podcast_deleted, Toast.LENGTH_SHORT).show()
                    load()
                } catch (e: Exception) {
                    if (e is CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) throw e
                    Toast.makeText(context, R.string.admin_access_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
