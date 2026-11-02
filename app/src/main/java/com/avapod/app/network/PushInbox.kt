package com.avapod.app.network

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Bounded device-local history, separate from the database announcement. */
object PushInbox {
    data class Entry(val id: String, val title: String, val body: String, val read: Boolean)
    @Synchronized fun entries(context: Context): List<Entry> = runCatching {
        val array = JSONArray(context.getSharedPreferences("push_inbox", Context.MODE_PRIVATE).getString("messages", "[]"))
        (0 until array.length()).map { array.getJSONObject(it) }.map {
            Entry(it.getString("id"), it.optString("title"), it.optString("body"), it.optBoolean("read"))
        }
    }.getOrDefault(emptyList())
    private fun write(context: Context, entries: List<Entry>) {
        val array = JSONArray()
        entries.take(30).forEach { array.put(JSONObject().put("id", it.id).put("title", it.title).put("body", it.body).put("read", it.read)) }
        context.getSharedPreferences("push_inbox", Context.MODE_PRIVATE).edit().putString("messages", array.toString()).apply()
    }
    @Synchronized fun save(context: Context, id: String?, title: String, body: String) {
        if (body.isBlank()) return
        val key = id ?: HomeEpisodes.key(title + "\n" + body)
        val old = entries(context)
        if (old.any { it.id == key }) return
        write(context, listOf(Entry(key, title.take(200), body.take(4000), false)) + old)
    }
    @Synchronized fun markRead(context: Context, ids: Set<String>) {
        write(context, entries(context).map { if (it.id in ids) it.copy(read = true) else it })
    }
}
