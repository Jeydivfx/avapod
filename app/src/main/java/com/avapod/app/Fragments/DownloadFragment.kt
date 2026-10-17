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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.avapod.app.PlayerActivity
import com.avapod.app.R
import com.avapod.app.adapters.EpisodeAdapter
import com.avapod.app.models.RssItem
import com.avapod.app.utils.DialogHelper
import com.avapod.app.utils.DownloadStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DownloadFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.fragment_download, container, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view, state)
        val app = requireContext().applicationContext
        val store = DownloadStore(app)

        val toolbar = view.findViewById<View>(R.id.common_toolbar)
        val btnBack = toolbar.findViewById<ImageButton>(R.id.btn_back_common)

        btnBack.scaleX = 1f

        view.findViewById<ImageButton>(R.id.btn_back_common).setOnClickListener { parentFragmentManager.popBackStack() }
        view.findViewById<ImageButton>(R.id.btn_menu_common).setOnClickListener {
            DialogHelper.showConfirmDialog(requireContext(), getString(R.string.dialog_delete_downloads_title),
                getString(R.string.dialog_delete_downloads_message), owner = viewLifecycleOwner) {
                viewLifecycleOwner.lifecycleScope.launch {
                    val success = withContext(Dispatchers.IO) { runCatching { store.clear() }.getOrDefault(false) }
                    Toast.makeText(context, if (success) R.string.toast_list_cleared else R.string.download_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
        val list = view.findViewById<RecyclerView>(R.id.rv_downloads)
        val status = view.findViewById<TextView>(R.id.txt_empty_downloads)
        list.layoutManager = LinearLayoutManager(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var displayed: List<RssItem>? = null
                var reportedError = false
                while (true) {
                    val result = withContext(Dispatchers.IO) { runCatching { store.reconcile() } }
                    result.onSuccess { records ->
                        reportedError = false
                        val episodes = records.filter { it.completed }.map { it.episode }
                        if (episodes != displayed) {
                            displayed = episodes
                            list.adapter = EpisodeAdapter(episodes, "") { episode ->
                                startActivity(Intent(requireContext(), PlayerActivity::class.java).apply {
                                    putExtra("audio_url", episode.audioUrl)
                                    putExtra("title", episode.title)
                                    putExtra("cover", episode.imageUrl)
                                    putExtra("podcast_name", episode.artist)
                                })
                            }
                        }
                        status.visibility = if (episodes.isEmpty()) View.VISIBLE else View.GONE
                        list.visibility = if (episodes.isEmpty()) View.GONE else View.VISIBLE
                        status.setText(when {
                            records.any { !it.completed && !it.failed } -> R.string.downloads_in_progress
                            records.any { it.failed } -> R.string.downloads_need_retry
                            else -> R.string.no_completed_downloads
                        })
                    }.onFailure {
                        if (!reportedError) Toast.makeText(context, R.string.download_failed, Toast.LENGTH_SHORT).show()
                        reportedError = true
                    }
                    delay(2_000)
                }
            }
        }
    }
    override fun onDestroyView() {
        view?.findViewById<RecyclerView>(R.id.rv_downloads)?.adapter = null
        super.onDestroyView()
    }
}
