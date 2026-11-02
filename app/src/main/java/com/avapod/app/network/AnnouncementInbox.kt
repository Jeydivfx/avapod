package com.avapod.app.network

import android.content.Context
import kotlinx.coroutines.withTimeout

/** One-shot public message read. This is an in-app inbox, not background push. */
object AnnouncementInbox {
    fun unread(context: Context): Boolean {
        val message = cached(context)
        val seen = context.getSharedPreferences("announcements", Context.MODE_PRIVATE).getString("seen", "")
        val databaseMessages = history(context)
        val visiblePush = PushInbox.entries(context).filterNot { push -> databaseMessages.any { it.first == push.body } }
            .take((3 - databaseMessages.size).coerceAtLeast(0))
        return visiblePush.any { !it.read } ||
            (message.first.isNotBlank() && seen != message.first + "\n" + message.second)
    }
    fun markRead(context: Context, message: Pair<String, String>) {
        context.getSharedPreferences("announcements", Context.MODE_PRIVATE).edit()
            .putString("seen", message.first + "\n" + message.second).apply()
    }
    fun cached(context: Context): Pair<String, String> {
        val prefs = context.getSharedPreferences("announcements", Context.MODE_PRIVATE)
        return prefs.getString("text", "").orEmpty() to prefs.getString("date", "").orEmpty()
    }
    @Synchronized fun history(context: Context): List<Pair<String, String>> {
        val prefs = context.getSharedPreferences("announcements", Context.MODE_PRIVATE)
        val stored = runCatching {
            val array = org.json.JSONArray(prefs.getString("history", "[]"))
            (0 until array.length()).map { array.getJSONObject(it) }.map { it.optString("text") to it.optString("date") }
        }.getOrDefault(emptyList())
        val current = cached(context)
        return (listOf(current) + stored).filter { it.first.isNotBlank() }.distinct().take(3)
    }
    @Synchronized private fun remember(context: Context, message: Pair<String, String>) {
        val previous = cached(context)
        val entries = (listOf(message, previous) + history(context)).filter { it.first.isNotBlank() }.distinct().take(3)
        val array = org.json.JSONArray()
        entries.forEach { array.put(org.json.JSONObject().put("text", it.first).put("date", it.second)) }
        context.getSharedPreferences("announcements", Context.MODE_PRIVATE).edit()
            .putString("history", array.toString())
            .putString("text", message.first).putString("date", message.second)
            .putLong("fetched", System.currentTimeMillis()).apply()
    }
    suspend fun refresh(context: Context, force: Boolean = false): Pair<String, String> = withTimeout(8_000) {
        val prefs = context.getSharedPreferences("announcements", Context.MODE_PRIVATE)
        val age = System.currentTimeMillis() - prefs.getLong("fetched", 0L)
        if (!force && age >= 0L && age < 300_000L) return@withTimeout cached(context)
        val value = PublicDatabase.read("app_messages")
        val text = value.databaseString("text").take(5000)
        val date = value.databaseString("date").take(80)
        remember(context, text to date)
        text to date
    }
}
