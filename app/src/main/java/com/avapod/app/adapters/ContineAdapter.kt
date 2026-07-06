package com.avapod.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.avapod.app.R
import com.avapod.app.models.RssItem
import com.avapod.app.utils.StringUtils

class ContinueAdapter(
    private val episodes: List<RssItem>,
    private val onEpisodeClick: (RssItem) -> Unit
) : RecyclerView.Adapter<ContinueAdapter.ContinueViewHolder>() {

    class ContinueViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.txt_episode_title)
        val time: TextView = view.findViewById(R.id.txt_episode_time)
        val image: ImageView = view.findViewById(R.id.img_episode)
        val progressBar: ProgressBar = view.findViewById(R.id.progress_listening)
        val txtPercent: TextView = view.findViewById(R.id.txt_percent)
        val date: TextView = view.findViewById(R.id.txt_episode_date)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContinueViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_episode, parent, false)
        return ContinueViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContinueViewHolder, position: Int) {
        val episode = episodes[position]
        val context = holder.itemView.context

        holder.title.text = episode.title

        holder.itemView.findViewById<View>(R.id.progress_listening)?.apply {
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }

        Glide.with(context.applicationContext).clear(holder.image)

        Glide.with(context.applicationContext)
            .load(episode.imageUrl)
            .placeholder(R.drawable.placeholder_podcast)
            .error(R.drawable.placeholder_podcast)
            .centerCrop()
            .dontAnimate()
            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.DATA)
            .into(holder.image)


        val totalMs = StringUtils.timeStringToMs(episode.duration ?: "0")
        val playedPos = episode.lastPosition

        if (totalMs > 0 && playedPos > 0) {
            val progressPercent = ((playedPos.toFloat() / totalMs.toFloat()) * 100).toInt().coerceIn(0, 100)
            holder.progressBar.visibility = View.VISIBLE
            holder.txtPercent.visibility = View.VISIBLE
            holder.progressBar.progress = progressPercent


            val localizedPercent = StringUtils.toPersianNumber(progressPercent.toString())
            holder.txtPercent.text = context.getString(R.string.percent_format, localizedPercent)
        } else {
            holder.progressBar.visibility = View.GONE
            holder.txtPercent.visibility = View.GONE
            holder.progressBar.progress = 0
        }

        holder.date.visibility = View.GONE
        val rawTime = episode.duration ?: "۰۰:۰۰"


        holder.time.text = if (rawTime.contains(":")) {
            StringUtils.toPersianNumber(rawTime)
        } else {
            StringUtils.formatTime(rawTime)
        }

        holder.itemView.setOnClickListener { onEpisodeClick(episode) }
    }

    override fun getItemCount() = episodes.size
}