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

        val name = category.name.replace('ي', 'ی').replace('ك', 'ک')
        val icon = when {
            category.icon == "ic_social" || name.contains("جامعه") -> R.drawable.ic_category_community
            category.icon == "ic_crime" || name.contains("جنایی") -> R.drawable.ic_category_crime
            category.icon == "ic_horror" || name.contains("ترس") -> R.drawable.ic_category_horror
            category.icon == "ic_tech" || name.contains("دانش") || name.contains("علم") -> R.drawable.ic_category_science
            category.icon == "ic_cinema" || name.contains("سینما") -> R.drawable.ic_category_cinema
            category.icon == "ic_history" || name.contains("تاریخ") -> R.drawable.ic_category_history
            category.icon == "ic_literature" || name.contains("ادبیات") -> R.drawable.ic_category_book
            category.icon == "ic_thinking" || name.contains("فلسفه") -> R.drawable.ic_category_thought
            category.icon == "ic_apocalypse" || name.contains("پیشگویی") -> R.drawable.ic_category_future
            category.icon == "ic_politic" || name.contains("سیاس") -> R.drawable.ic_category_politics
            category.icon == "ic_sport" || name.contains("ورزش") -> R.drawable.ic_category_sport
            category.icon == "ic_music" || name.contains("موسیقی") -> R.drawable.ic_category_music
            else -> R.drawable.ic_category_audio
        }
        holder.imgIcon.setImageResource(icon)

        holder.itemView.setOnClickListener { onItemClick(category) }
    }

    override fun getItemCount() = categories.size
}
