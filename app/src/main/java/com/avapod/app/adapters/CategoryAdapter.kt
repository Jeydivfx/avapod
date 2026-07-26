package com.avapod.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.avapod.app.R
import com.avapod.app.models.Category

class CategoryAdapter(
    private val categories: List<Category>,
    private val onItemClick: (Category) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    class CategoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtName: TextView = view.findViewById(R.id.txt_cat_name)
        val imgIcon: ImageView = view.findViewById(R.id.img_cat_icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categories[position]
        holder.txtName.text = category.name

        val context = holder.itemView.context
        val resourceId = context.resources.getIdentifier(category.icon, "drawable", context.packageName)

        if (resourceId != 0) {
            holder.imgIcon.setImageResource(resourceId)
        } else {
            holder.imgIcon.setImageResource(R.drawable.placeholder_podcast)
        }

        holder.itemView.setOnClickListener { onItemClick(category) }
    }

    override fun getItemCount() = categories.size
}