package com.avapod.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.avapod.app.R
import com.avapod.app.models.RssItem
import com.avapod.app.utils.PreferenceHelper
import com.avapod.app.utils.StringUtils
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import saman.zamani.persiandate.PersianDate

class EpisodeAdapter(
    private val episodes: List<RssItem>,
    private val defaultCover: String,
    private val onEpisodeClick: (RssItem) -> Unit
) : RecyclerView.Adapter<EpisodeAdapter.EpisodeViewHolder>() {

    class EpisodeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.txt_episode_title)
        val time: TextView = view.findViewById(R.id.txt_episode_time)
        val image: ImageView = view.findViewById(R.id.img_episode)
        val date: TextView = view.findViewById(R.id.txt_episode_date)
        val progressBar: ProgressBar = view.findViewById(R.id.progress_listening)
        val txtPercent: TextView = view.findViewById(R.id.txt_percent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_episode, parent, false)
        return EpisodeViewHolder(view)
    }

    override fun onBindViewHolder(holder: EpisodeViewHolder, position: Int) {
        val episode = episodes[position]
        val context = holder.itemView.context
        val prefHelper = PreferenceHelper(context)

        holder.title.text = episode.title
        holder.time.text = StringUtils.formatTime(episode.duration)

        if (!episode.pubDate.isNullOrEmpty()) {
            holder.date.text = context.getString(R.string.date_prefix_format, convertToPersianDate(episode.pubDate))
            holder.date.visibility = View.VISIBLE
        } else {
            holder.date.visibility = View.GONE
        }

        val playedPos = prefHelper.getContinuePosition(episode.audioUrl)
        val totalDurationMs = StringUtils.timeStringToMs(episode.duration)

        if (playedPos > 0 && totalDurationMs > 0) {
            val progressPercent = ((playedPos.toFloat() / totalDurationMs.toFloat()) * 100).toInt().coerceIn(0, 100)

            holder.progressBar.visibility = View.VISIBLE
            holder.txtPercent.visibility = View.VISIBLE

            holder.progressBar.layoutDirection = View.LAYOUT_DIRECTION_LTR
            holder.progressBar.rotationY = 0f

            holder.progressBar.progress = progressPercent
            holder.txtPercent.text = context.getString(R.string.percent_format, StringUtils.toPersianNumber(progressPercent.toString()))
        } else {
            holder.progressBar.visibility = View.INVISIBLE
            holder.txtPercent.visibility = View.INVISIBLE
            holder.progressBar.progress = 0
        }


        Glide.with(context.applicationContext)
            .load(if (episode.imageUrl.isNullOrEmpty()) defaultCover else episode.imageUrl)
            .placeholder(R.drawable.placeholder_podcast)
            .error(R.drawable.placeholder_podcast)
            .centerCrop()
            .diskCacheStrategy(DiskCacheStrategy.DATA)
            .into(holder.image)

        holder.itemView.setOnClickListener { onEpisodeClick(episode) }
    }

    private fun convertToPersianDate(dateString: String?): String {
        return try {
            val sdf = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", java.util.Locale.ENGLISH)
            val pDate = PersianDate(sdf.parse(dateString))
            StringUtils.toPersianNumber("${pDate.shYear}/${pDate.shMonth}/${pDate.shDay}")
        } catch (e: Exception) {
            StringUtils.toPersianNumber(dateString ?: "")
        }
    }

    override fun getItemCount() = episodes.size
}