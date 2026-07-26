package com.avapod.app.Fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.appcompat.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.avapod.app.PlayerActivity
import com.avapod.app.R
import com.avapod.app.adapters.EpisodeAdapter
import com.avapod.app.utils.DialogHelper
import com.avapod.app.utils.FileUtils
import com.avapod.app.utils.PreferenceHelper
import java.io.File

class DownloadFragment : Fragment() {

    private lateinit var rvDownloads: RecyclerView
    private lateinit var prefHelper: PreferenceHelper
    private lateinit var txtEmpty: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_download, container, false)

        prefHelper = PreferenceHelper(requireContext())
        rvDownloads = view.findViewById(R.id.rv_downloads)
        txtEmpty = view.findViewById(R.id.txt_empty_downloads)

        setupToolbar(view)
        setupRecyclerView()

        return view
    }

    private fun setupToolbar(view: View) {
        val toolbar = view.findViewById<View>(R.id.common_toolbar)
        val btnBack = toolbar.findViewById<ImageButton>(R.id.btn_back_common)
        val btnMenu = toolbar.findViewById<ImageButton>(R.id.btn_menu_common)

        btnBack.scaleX = -1f
        btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        btnMenu.setOnClickListener { v ->
            showDeleteMenu(v)
        }
    }

    private fun showDeleteMenu(view: View) {
        val context = requireContext()
        val popup = PopupMenu(context, view)
        popup.menu.add(context.getString(R.string.menu_clear_downloads))

        popup.setOnMenuItemClickListener {
            showConfirmDialog()
            true
        }
        popup.show()
    }

    private fun showConfirmDialog() {
        val context = requireContext()
        DialogHelper.showConfirmDialog(
            context = context,
            title = context.getString(R.string.dialog_delete_downloads_title),
            message = context.getString(R.string.dialog_delete_downloads_message)
        ) {
            val downloadFolder = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)


            if (downloadFolder != null && downloadFolder.exists()) {
                val files = downloadFolder.listFiles()
                if (files != null) {
                    for (file in files) {
                        if (file.isFile && file.name.endsWith(".mp3")) {
                            file.delete()
                        }
                    }
                }
            }


            prefHelper.clearAllDownloads()
            setupRecyclerView()
            Toast.makeText(context, context.getString(R.string.toast_list_cleared), Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecyclerView() {
        val downloadedEpisodes = prefHelper.getDownloadedEpisodes()

        if (downloadedEpisodes.isEmpty()) {
            txtEmpty.visibility = View.VISIBLE
            rvDownloads.visibility = View.GONE
        } else {
            txtEmpty.visibility = View.GONE
            rvDownloads.visibility = View.VISIBLE
            rvDownloads.layoutManager = LinearLayoutManager(requireContext())

            rvDownloads.adapter = EpisodeAdapter(downloadedEpisodes, "") { episode ->
                com.avapod.app.utils.AdManager.showAdWithCapping(requireActivity()) {

                    val fileName = FileUtils.getSafeFileName(episode.title)
                    val downloadFolder = requireContext().getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                    val localFile = File(downloadFolder, fileName)


                    val finalAudioUrl = if (localFile.exists()) {
                        Uri.fromFile(localFile).toString()
                    } else {
                        episode.audioUrl
                    }

                    val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                        putExtra("audio_url", finalAudioUrl)
                        putExtra("title", episode.title)
                        putExtra("cover", episode.imageUrl)
                        putExtra("duration", episode.duration)
                        putExtra("podcast_name", episode.artist ?: getString(R.string.default_podcast_artist))
                    }
                    startActivity(intent)
                }
            }
        }
    }
}