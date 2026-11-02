package com.avapod.app.Fragments

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.avapod.app.R
import com.avapod.app.PlayerActivity
import com.avapod.app.models.RssItem
import com.avapod.app.utils.PlaylistStore

class PlaylistFragment : Fragment(R.layout.fragment_playlist) {
    private var items = emptyList<RssItem>()
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<View>(R.id.btn_menu_common).visibility = View.GONE
        view.findViewById<View>(R.id.btn_back_common).setOnClickListener { parentFragmentManager.popBackStack() }
        view.findViewById<View>(R.id.playlist_play_all).setOnClickListener { items.firstOrNull()?.let { play(it, true) } }
        view.findViewById<RecyclerView>(R.id.playlist_items).layoutManager = LinearLayoutManager(requireContext())
    }
    override fun onResume() { super.onResume(); refresh() }
    private fun play(episode: RssItem, fromStart: Boolean = false) {
        startActivity(Intent(requireContext(), PlayerActivity::class.java).apply {
            putExtra("audio_url", episode.audioUrl)
            putExtra("title", episode.title)
            putExtra("podcast_name", episode.artist)
            putExtra("cover", episode.imageUrl)
            putExtra("queue_id", PlaylistStore.queue(requireContext(), items))
            if (fromStart) { putExtra("resume_position", 0L); putExtra("queue_restart", true) }
        })
    }
    private fun refresh() {
        val view = view ?: return
        items = PlaylistStore.all(requireContext())
        view.findViewById<TextView>(R.id.playlist_count).text = getString(R.string.playlist_count,
            com.avapod.app.utils.StringUtils.toPersianNumber(items.size.toString()))
        view.findViewById<View>(R.id.playlist_empty).visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.playlist_play_all).isEnabled = items.isNotEmpty()
        view.findViewById<RecyclerView>(R.id.playlist_items).adapter = object : RecyclerView.Adapter<Row>() {
            override fun getItemCount() = items.size
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Row {
                val row = android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_playlist_episode, parent, false)
                return Row(row)
            }
            override fun onBindViewHolder(holder: Row, position: Int) {
                val item = items[position]
                holder.title.text = item.title.orEmpty()
                holder.artist.text = item.artist.orEmpty()
                com.bumptech.glide.Glide.with(holder.cover).load(item.imageUrl)
                    .placeholder(R.drawable.placeholder_podcast).centerCrop().into(holder.cover)
                holder.play.contentDescription = getString(R.string.accessibility_play) + " " + item.title.orEmpty()
                holder.play.setOnClickListener { play(item) }
                holder.remove.setOnClickListener { PlaylistStore.toggle(requireContext(), item); refresh() }
            }
        }
    }
    private class Row(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.playlist_row_title)
        val artist: TextView = view.findViewById(R.id.playlist_row_artist)
        val cover: android.widget.ImageView = view.findViewById(R.id.playlist_row_cover)
        val play: View = view.findViewById(R.id.playlist_row_play)
        val remove: View = view.findViewById(R.id.playlist_row_remove)
    }
}
