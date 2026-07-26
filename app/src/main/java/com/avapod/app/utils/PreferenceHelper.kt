package com.avapod.app.utils

import android.content.Context
import android.content.SharedPreferences
import com.avapod.app.models.Podcast
import com.avapod.app.models.RssItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PreferenceHelper(context: Context) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("AvapodPrefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val KEY_SUBSCRIPTIONS = "subscriptions"
    private val KEY_BOOKMARKS = "episode_bookmarks"
    private val KEY_DOWNLOADS = "downloaded_episodes"
    private val KEY_CONTINUE = "continue_listening"

    private fun savePodcastList(list: List<Podcast>) {
        val json = gson.toJson(list)
        sharedPreferences.edit().putString(KEY_SUBSCRIPTIONS, json).apply()
    }

    fun getSubscribedPodcasts(): MutableList<Podcast> {
        val json = sharedPreferences.getString(KEY_SUBSCRIPTIONS, null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<Podcast>>() {}.type
        return gson.fromJson(json, type)
    }

    fun subscribe(podcast: Podcast) {
        val list = getSubscribedPodcasts()
        if (!isSubscribed(podcast.rss_url)) {
            list.add(podcast)
            savePodcastList(list)
        }
    }

    fun unsubscribe(rssUrl: String?) {
        if (rssUrl == null) return
        val list = getSubscribedPodcasts()
        list.removeAll { it.rss_url == rssUrl }
        savePodcastList(list)
    }

    fun isSubscribed(rssUrl: String?): Boolean {
        if (rssUrl == null) return false
        return getSubscribedPodcasts().any { it.rss_url == rssUrl }
    }

    fun getBookmarkedEpisodes(): MutableList<RssItem> {
        val json = sharedPreferences.getString(KEY_BOOKMARKS, null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<RssItem>>() {}.type
        return gson.fromJson(json, type)
    }

    private fun saveBookmarkList(list: List<RssItem>) {
        val json = gson.toJson(list)
        sharedPreferences.edit().putString(KEY_BOOKMARKS, json).apply()
    }

    fun addBookmark(episode: RssItem, coverUrl: String) {
        val list = getBookmarkedEpisodes()

        if (list.none { it.audioUrl == episode.audioUrl }) {
            val updatedEpisode = episode.copy(
                imageUrl = coverUrl,
                duration = episode.duration
            )

            list.add(0, updatedEpisode)
            saveBookmarkList(list)
        }
    }

    fun removeBookmark(audioUrl: String?) {
        if (audioUrl == null) return
        val list = getBookmarkedEpisodes()
        list.removeAll { it.audioUrl == audioUrl }
        saveBookmarkList(list)
    }

    fun isBookmarked(audioUrl: String?): Boolean {
        if (audioUrl == null) return false
        return getBookmarkedEpisodes().any { it.audioUrl == audioUrl }
    }

    fun saveDownloadedEpisode(episode: RssItem) {
        val list = getDownloadedEpisodes()
        if (list.none { it.audioUrl == episode.audioUrl }) {
            list.add(episode)
            val json = gson.toJson(list)
            sharedPreferences.edit().putString(KEY_DOWNLOADS, json).apply()
        }
    }

    fun getDownloadedEpisodes(): MutableList<RssItem> {
        val json = sharedPreferences.getString(KEY_DOWNLOADS, null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<RssItem>>() {}.type
        return gson.fromJson(json, type)
    }

    fun isDownloaded(audioUrl: String?): Boolean {
        if (audioUrl == null) return false
        return getDownloadedEpisodes().any { it.audioUrl == audioUrl }
    }

    fun saveContinueListening(episode: RssItem, position: Long) {
        val list = getContinueListening().toMutableList()

        list.removeAll { it.audioUrl == episode.audioUrl }

        episode.lastPosition = position
        list.add(0, episode)

        val limitedList = list.take(10)

        val json = gson.toJson(limitedList)
        sharedPreferences.edit().putString(KEY_CONTINUE, json).apply()
    }

    fun getContinueListening(): List<RssItem> {
        val json = sharedPreferences.getString(KEY_CONTINUE, null) ?: return emptyList()
        val type = object : TypeToken<List<RssItem>>() {}.type
        return gson.fromJson(json, type)
    }

    fun getContinuePosition(audioUrl: String?): Long {
        if (audioUrl == null) return 0L
        val list = getContinueListening()
        return list.find { it.audioUrl == audioUrl }?.lastPosition ?: 0L
    }

    fun clearAllDownloads() {
        sharedPreferences.edit().remove(KEY_DOWNLOADS).apply()
    }

    fun clearAllBookmarks() {
        sharedPreferences.edit().remove(KEY_BOOKMARKS).apply()
    }

    fun removeSingleContinueListening(audioUrl: String) {
        val currentList = getContinueListening().toMutableList()
        val updatedList = currentList.filterNot { it.audioUrl == audioUrl }

        val json = gson.toJson(updatedList)
        sharedPreferences.edit().putString(KEY_CONTINUE, json).apply()
    }

    fun clearAllContinueListening() {
        sharedPreferences.edit().remove(KEY_CONTINUE).apply()
    }
}