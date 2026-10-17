package com.avapod.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.avapod.app.R
import com.avapod.app.models.Podcast

class AdminPodcastAdapter(
    private val podcastList: List<Podcast>,
    private val onEditClick: (Podcast) -> Unit,
    private val onDeleteClick: (Podcast) -> Unit // اضافه شده
) : RecyclerView.Adapter<AdminPodcastAdapter.AdminViewHolder>() {

    class AdminViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtTitle: TextView = view.findViewById(R.id.txt_admin_item_title)
        val btnEdit: Button = view.findViewById(R.id.btn_edit_admin)
        val btnDelete: Button = view.findViewById(R.id.btn_delete_admin)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AdminViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_admin_podcast, parent, false)
        return AdminViewHolder(view)
    }

    override fun onBindViewHolder(holder: AdminViewHolder, position: Int) {
        val podcast = podcastList[position]
        holder.txtTitle.text = podcast.title

        holder.btnEdit.setOnClickListener { onEditClick(podcast) }
        holder.btnDelete.setOnClickListener { onDeleteClick(podcast) }
    }

    override fun getItemCount() = podcastList.size
}