package com.avapod.app.models

import com.google.firebase.database.IgnoreExtraProperties
import com.google.firebase.database.PropertyName
import java.io.Serializable

data class Podcast(
    var id: String = "",
    val title: String = "",
    val artist: String = "",
    val description: String = "",
    val category_id: String = "",
    val rss_url: String = "",
    val thumbnail_url: String = "",
    @get:PropertyName("is_trending") val is_trending: Boolean = false,
    @get:PropertyName("is_featured") val is_featured: Boolean = false
) : Serializable

@IgnoreExtraProperties
data class Category(
    var id: String = "",
    var name: String = "",
    var icon: String = ""
)


data class ContinueEpisode(
    val audioUrl: String?,
    val title: String?,
    val imageUrl: String?,
    val duration: String?,
    val lastPosition: Int,
    val artist: String? = null
)