package com.avapod.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.avapod.app.R
import com.avapod.app.utils.StringUtils
import com.bumptech.glide.Glide

data class HomeCard(val title: String, val subtitle: String, val cover: String?, val duration: String?, val open: () -> Unit)

class HomeShelfAdapter : RecyclerView.Adapter<HomeShelfAdapter.Holder>() {
    private var cards = emptyList<HomeCard>()
    fun submit(items: List<HomeCard>) { cards = items; notifyDataSetChanged() }
    class Holder(view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_home_episode, parent, false)
        val d = parent.resources.displayMetrics.density
        val total = parent.width.takeIf { it > 0 }
            ?: (parent.resources.displayMetrics.widthPixels - 32f * d).toInt()
        val usable = (total - parent.paddingLeft - parent.paddingRight).coerceAtLeast(1)
        val columns = ((parent as? RecyclerView)?.layoutManager as? androidx.recyclerview.widget.GridLayoutManager)?.spanCount ?: 3
        val width = (usable / columns).coerceAtLeast(1)
        // RecyclerView.LayoutParams must carry spacing: XML root margins are lost
        // when layoutParams are replaced here.
        view.layoutParams = RecyclerView.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = if ((parent as? RecyclerView)?.layoutManager is androidx.recyclerview.widget.GridLayoutManager) (16f * d).toInt() else 0
        }
        // Item content width excludes its end margin, keeping exactly 3 cards visible.
        val content = view.findViewById<View>(R.id.home_cover_frame)
        val imageSize = (width - (5f * d).toInt()).coerceAtLeast(1)
        content.layoutParams = content.layoutParams.apply { height = imageSize }
        // The XML root is wrap_content; its measured width follows the RecyclerView item width.
        return Holder(view)
    }

    override fun getItemCount() = cards.size
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val card = cards[position]
        holder.itemView.findViewById<TextView>(R.id.home_card_title).apply {
            maxLines = 1
            minLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            text = card.title
        }
        holder.itemView.findViewById<TextView>(R.id.home_card_artist).text = card.subtitle
        holder.itemView.findViewById<TextView>(R.id.home_card_duration).apply {
            visibility = if (card.duration.isNullOrBlank()) View.GONE else View.VISIBLE
            text = StringUtils.formatTime(card.duration)
        }
        val image = holder.itemView.findViewById<ImageView>(R.id.home_card_cover)
        Glide.with(image).load(card.cover).placeholder(R.drawable.placeholder_podcast)
            .centerCrop().into(image)
        holder.itemView.contentDescription = "${card.title}، ${card.subtitle}"
        holder.itemView.setOnClickListener { card.open() }
    }
}
