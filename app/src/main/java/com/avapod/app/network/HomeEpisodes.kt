package com.avapod.app.network

import android.content.Context
import com.avapod.app.models.RssItem
import com.avapod.app.models.Podcast
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.security.MessageDigest

/** Public snapshots keep the home screen independent from publisher RSS availability. */
object HomeEpisodes {
    const val PATH = "home_content/top_episodes"
    private val type = object : TypeToken<List<RssItem>>() {}.type
    fun key(audio: String): String = MessageDigest.getInstance("SHA-256")
        .digest(audio.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    suspend fun cached(context: Context, section: String): List<RssItem> = withContext(Dispatchers.IO) {
        runCatching { Gson().fromJson<List<RssItem>>(
            context.getSharedPreferences("HomeEpisodes", Context.MODE_PRIVATE).getString(section, "[]"), type)
            ?: emptyList() }.getOrDefault(emptyList())
    }
    suspend fun remember(context: Context, section: String, items: List<RssItem>) = withContext(Dispatchers.IO) {
        context.getSharedPreferences("HomeEpisodes", Context.MODE_PRIVATE).edit()
            .putString(section, Gson().toJson(items.take(100).map { it.copy(description = "") })).apply()
    }
    suspend fun top(context: Context): List<RssItem> {
        val json = withTimeout(8_000) { PublicDatabase.read(PATH) }
        val result = withContext(Dispatchers.Default) {
            json.databaseChildren().sortedByDescending { (_, value) ->
                value.get("selected_at")?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L
            }.mapNotNull { (_, value) ->
                val item = Gson().fromJson(value, RssItem::class.java)
                item.takeIf { com.avapod.app.utils.UrlValidator.isWebUrl(it.audioUrl) }
            }.take(100)
        }
        remember(context, "top", result)
        return result
    }
    suspend fun save(podcast: Podcast, episodes: List<RssItem>, selected: BooleanArray) = withTimeout(25_000) {
        AdminAccess.requireAdmin()
        val updates = mutableMapOf<String, Any?>()
        val root = FirebaseDatabase.getInstance().getReference(PATH)
        val existing = root.get().awaitResult()
        episodes.forEachIndexed { index, item ->
            val audio = item.audioUrl?.takeIf(com.avapod.app.utils.UrlValidator::isWebUrl) ?: return@forEachIndexed
            val id = key(audio)
            if (!selected[index]) {
                if (existing.hasChild(id)) updates[id] = null
            } else if (!existing.hasChild(id)) {
                updates[id] = mapOf("title" to item.title.orEmpty().take(500), "audioUrl" to audio,
                    "artist" to podcast.title.take(500),
                    "imageUrl" to (item.imageUrl?.takeIf { it.isNotBlank() } ?: podcast.thumbnail_url),
                    "duration" to item.duration.orEmpty(), "pubDate" to item.pubDate.orEmpty(),
                    "podcast_id" to podcast.id, "selected_at" to ServerValue.TIMESTAMP)
            }
        }
        if (updates.isNotEmpty()) root.updateChildren(updates).awaitResult()
    }
}
