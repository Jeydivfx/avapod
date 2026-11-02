package com.avapod.app.utils

import android.content.Context
import com.avapod.app.models.RssItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** A saved personal playlist and a separate persisted playback handoff; never send lists through Binder. */
object PlaylistStore {
    private val type = object : TypeToken<List<RssItem>>() {}.type
    private fun prefs(c: Context) = c.getSharedPreferences("playlist_v1", Context.MODE_PRIVATE)
    private fun read(c: Context, key: String): List<RssItem> = runCatching {
        Gson().fromJson<List<RssItem>>(prefs(c).getString(key, "[]"), type) ?: emptyList()
    }.getOrDefault(emptyList()).filter { com.avapod.app.PlayerManager.isPlayable(c, it.audioUrl) }
    fun all(c: Context) = read(c, "saved")
    @Synchronized fun toggle(c: Context, episode: RssItem): Boolean {
        val old = all(c)
        val exists = old.any { it.audioUrl == episode.audioUrl }
        val updated = if (exists) old.filterNot { it.audioUrl == episode.audioUrl } else old + episode.copy(description = "")
        prefs(c).edit().putString("saved", Gson().toJson(updated)).apply()
        return !exists
    }
    @Synchronized fun queue(c: Context, items: List<RssItem>): String {
        val id = java.util.UUID.randomUUID().toString()
        val editor = prefs(c).edit()
        // A single handoff is sufficient; the active queue lives in MediaSession.
        editor.putString("queue_id", id).putString("queue", Gson().toJson(items.distinctBy { it.audioUrl }.map { it.copy(description = "") })).apply()
        return id
    }
    fun queue(c: Context, id: String?): List<RssItem> =
        if (id != null && prefs(c).getString("queue_id", null) == id) read(c, "queue") else emptyList()
}
