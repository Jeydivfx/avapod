package com.avapod.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.avapod.app.R
import com.avapod.app.models.RssItem

class NewEpisodeAdapter(
    private val episodes: List<RssItem>,
    private val onEpisodeClick: (RssItem) -> Unit
) : RecyclerView.Adapter<NewEpisodeAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.txt_episode_title)
        val channel: TextView = view.findViewById(R.id.txt_channel_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_new_episode_simple, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val episode = episodes[position]
        holder.title.text = episode.title
        holder.channel.text = episode.artist ?: "نام کانال"

        holder.itemView.setOnClickListener { onEpisodeClick(episode) }
    }

    override fun getItemCount() = episodes.size
}