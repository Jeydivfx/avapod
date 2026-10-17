package com.avapod.app.Fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
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
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_admin_dashboard, container, false)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
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
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                AdminAccess.requireAdmin()
                val snapshot = withTimeout(20_000) { FirebaseDatabase.getInstance().getReference("podcasts").get().awaitResult() }
                val list = snapshot.children.mapNotNull { data -> data.getValue(Podcast::class.java)?.copy(id = data.key.orEmpty()) }
                requireView().findViewById<Button>(R.id.btn_add_new_podcast).isEnabled = true
                requireView().findViewById<RecyclerView>(R.id.rv_admin_podcasts).apply {
                    layoutManager = LinearLayoutManager(requireContext())
                    adapter = AdminPodcastAdapter(list, { podcast ->
                        parentFragmentManager.beginTransaction().replace(R.id.nav_host_fragment,
                            AddPodcastFragment().apply { arguments = Bundle().apply { putSerializable("podcast_data", podcast) } })
                            .addToBackStack(null).commit()
                    }, { podcast -> confirmDelete(podcast) })
                }
            } catch (e: Exception) {
                if (e is CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) throw e
                Toast.makeText(context, R.string.admin_access_failed, Toast.LENGTH_LONG).show()
            }
        }
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
