package com.avapod.app.adapters

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.avapod.app.R
import com.avapod.app.models.Podcast
import com.avapod.app.ui.HeroBackgroundDrawable
import com.bumptech.glide.Glide

class HomeHeroAdapter(private val open: (Podcast) -> Unit) : RecyclerView.Adapter<HomeHeroAdapter.Holder>() {
    private var items = emptyList<Podcast>()
    fun submit(value: List<Podcast>) { items = value; notifyDataSetChanged() }
    class Holder(view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_home_hero, parent, false)
        val d = parent.resources.displayMetrics.density
        val parentWidth = parent.width.takeIf { it > 0 }
            ?: (parent.resources.displayMetrics.widthPixels - 32f * d).toInt()
        val width = (parentWidth - parent.paddingLeft - parent.paddingRight).coerceAtLeast(1)
        val fontScale = parent.resources.configuration.fontScale.coerceAtLeast(1f)
        val height = maxOf(width * 305f / 785f, 136f * d * fontScale).toInt()
        view.layoutParams = RecyclerView.LayoutParams(width, height)
        view.background = HeroBackgroundDrawable(d)
        val coverSize = minOf((width * .30f).toInt(), height - (24f * d).toInt()).coerceAtLeast(1)
        view.findViewById<View>(R.id.hero_cover_frame).layoutParams =
            (view.findViewById<View>(R.id.hero_cover_frame).layoutParams as FrameLayout.LayoutParams).apply {
                this.width = coverSize
                this.height = coverSize
            }
        val content = view.findViewById<View>(R.id.hero_content_panel)
        content.layoutParams = (content.layoutParams as FrameLayout.LayoutParams).apply {
            leftMargin = coverSize + (28f * d).toInt()
            rightMargin = (12f * d).toInt()
        }
        view.findViewById<View>(R.id.hero_badge).setBackgroundResource(R.drawable.home_badge_compact)
        view.findViewById<View>(R.id.hero_listen_surface).background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.rgb(52, 120, 246), Color.rgb(7, 87, 216))
        ).apply { cornerRadius = 20f * d }
        view.findViewById<View>(R.id.hero_listen).layoutParams.apply {
            this.width = minOf((116f * d * fontScale).toInt(), width - coverSize - (40f * d).toInt())
            this.height = (48f * d * fontScale).toInt()
        }
        view.findViewById<View>(R.id.hero_listen_surface).layoutParams.height = (30f * d * fontScale).toInt()
        return Holder(view)
    }

    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.itemView.contentDescription = item.title
        holder.itemView.findViewById<TextView>(R.id.hero_title).text = item.title
        holder.itemView.findViewById<TextView>(R.id.hero_description).text = item.description
        val cover = holder.itemView.findViewById<ImageView>(R.id.hero_cover)
        Glide.with(cover).load(item.thumbnail_url)
            .placeholder(R.drawable.placeholder_podcast).centerCrop().into(cover)
        holder.itemView.findViewById<View>(R.id.hero_listen).setOnClickListener { open(item) }
        holder.itemView.setOnClickListener { open(item) }
    }
}
