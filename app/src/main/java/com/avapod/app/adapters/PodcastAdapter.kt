package com.avapod.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.avapod.app.R
import com.avapod.app.models.Podcast
import com.bumptech.glide.load.engine.DiskCacheStrategy

class PodcastAdapter(
    private var podcastList: List<Podcast>,
    private val onItemClick: (Podcast) -> Unit
) : RecyclerView.Adapter<PodcastAdapter.PodcastViewHolder>() {

    inner class PodcastViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgCover: ImageView = view.findViewById(R.id.img_cover)
        val txtArtist: TextView = view.findViewById(R.id.txt_artist)
        val txtTitle: TextView = view.findViewById(R.id.txt_title)

        fun bind(podcast: Podcast) {
            val context = itemView.context
            txtTitle.text = podcast.title
            txtArtist.text = podcast.artist ?: context.getString(R.string.unknown_artist)

            val appContext = context.applicationContext


            if (!podcast.thumbnail_url.isNullOrEmpty()) {
                Glide.with(appContext)
                    .load(podcast.thumbnail_url)
                    .placeholder(R.drawable.placeholder_podcast)
                    .error(R.drawable.placeholder_podcast)
                    .dontAnimate()
                    .diskCacheStrategy(DiskCacheStrategy.DATA)
                    .into(imgCover)
            } else {

                Glide.with(appContext).clear(imgCover)
                imgCover.setImageResource(R.drawable.placeholder_podcast)
            }

            itemView.setOnClickListener {
                onItemClick(podcast)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PodcastViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_podcast_trending, parent, false)
        return PodcastViewHolder(view)
    }

    override fun onBindViewHolder(holder: PodcastViewHolder, position: Int) {
        holder.bind(podcastList[position])
    }

    override fun getItemCount(): Int = podcastList.size

    fun updateData(newList: List<Podcast>) {
        this.podcastList = newList
        notifyDataSetChanged()
    }
}