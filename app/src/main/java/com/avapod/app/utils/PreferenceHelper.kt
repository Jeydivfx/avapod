package com.avapod.app.utils

import android.content.Context
import com.avapod.app.models.Podcast
import com.avapod.app.models.RssItem
import com.google.gson.Gson

class PreferenceHelper(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("AvapodPrefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    companion object { private val lock = Any() }
    private fun <T : Any> readList(key: String, arrayClass: Class<Array<T>>): MutableList<T> = try {
        gson.fromJson(prefs.getString(key, null), arrayClass)?.filterNotNull()?.toMutableList()
            ?: mutableListOf()
    } catch (e: Exception) {
        android.util.Log.e("AvapodStorage", "Cannot read saved list: $key", e)
        mutableListOf()
    }
    private fun save(key: String, value: Any) { prefs.edit().putString(key, gson.toJson(value)).apply() }

    fun getSubscribedPodcasts(): MutableList<Podcast> = readList("subscriptions", Array<Podcast>::class.java)
    fun isSubscribed(url: String?) = url != null && getSubscribedPodcasts().any { it.rss_url == url }
    fun subscribe(podcast: Podcast) = synchronized(lock) {
        val list = getSubscribedPodcasts()
        if (list.none { it.rss_url == podcast.rss_url }) { list.add(podcast); save("subscriptions", list) }
    }
    fun unsubscribe(url: String?) = synchronized(lock) {
        save("subscriptions", getSubscribedPodcasts().filterNot { it.rss_url == url })
    }
    fun getBookmarkedEpisodes(): MutableList<RssItem> = readList("episode_bookmarks", Array<RssItem>::class.java)
    fun isBookmarked(url: String?) = url != null && getBookmarkedEpisodes().any { it.audioUrl == url }
    fun addBookmark(episode: RssItem, coverUrl: String) = synchronized(lock) {
        val list = getBookmarkedEpisodes()
        if (!episode.audioUrl.isNullOrBlank() && list.none { it.audioUrl == episode.audioUrl }) {
            list.add(0, episode.copy(imageUrl = coverUrl)); save("episode_bookmarks", list)
        }
    }
    fun removeBookmark(url: String?) = synchronized(lock) {
        save("episode_bookmarks", getBookmarkedEpisodes().filterNot { it.audioUrl == url })
    }
    fun clearAllBookmarks() { prefs.edit().remove("episode_bookmarks").apply() }
    // Legacy metadata, read only by DownloadStore migration.
    fun getDownloadedEpisodes(): MutableList<RssItem> = readList("downloaded_episodes", Array<RssItem>::class.java)
    fun clearAllDownloads() { prefs.edit().remove("downloaded_episodes").apply() }
    fun getContinueListening(): List<RssItem> = readList("continue_listening", Array<RssItem>::class.java)
    fun getContinuePosition(url: String?): Long =
        getContinueListening().firstOrNull { it.audioUrl == url }?.lastPosition ?: 0L
    fun saveContinueListening(episode: RssItem, position: Long) = synchronized(lock) {
        if (episode.audioUrl.isNullOrBlank()) return@synchronized
        val updated = episode.copy(lastPosition = position.coerceAtLeast(0))
        val list = getContinueListening().filterNot { it.audioUrl == episode.audioUrl }
        save("continue_listening", (listOf(updated) + list).take(50))
        save("last_playback", updated)
    }
    fun getLastPlayback(): RssItem? = try {
        gson.fromJson(prefs.getString("last_playback", null), RssItem::class.java)
    } catch (_: Exception) { null }
    fun clearLastPlayback() { prefs.edit().remove("last_playback").apply() }
    fun removeSingleContinueListening(url: String) = synchronized(lock) {
        save("continue_listening", getContinueListening().filterNot { it.audioUrl == url })
    }
    fun clearAllContinueListening() { prefs.edit().remove("continue_listening").apply() }
}
