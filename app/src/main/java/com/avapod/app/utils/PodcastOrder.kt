package com.avapod.app.utils

import com.avapod.app.models.Podcast

object PodcastOrder {
    private const val PUSH_CHARS = "-0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz"
    fun createdTime(podcast: Podcast): Long {
        if (podcast.createdAt > 0) return podcast.createdAt
        // Existing Firebase push IDs encode creation time in their first eight characters.
        if (podcast.id.length != 20 || podcast.id.any { it !in PUSH_CHARS }) return 0L
        return podcast.id.take(8).fold(0L) { time, char -> time * 64 + PUSH_CHARS.indexOf(char) }
    }
    fun newest(items: List<Podcast>): List<Podcast> = items.sortedWith(
        compareByDescending<Podcast> { createdTime(it) }.thenByDescending { it.id })
    fun trending(items: List<Podcast>): List<Podcast> = items.filter { it.is_trending }.sortedWith(
        compareByDescending<Podcast> { it.trendingAt.takeIf { time -> time > 0 } ?: createdTime(it) }
            .thenByDescending { createdTime(it) }.thenByDescending { it.id })

    fun searchKey(value: String): String = value.replace('ي', 'ی').replace('ك', 'ک')
        .replace('\u200c', ' ').trim().lowercase(java.util.Locale.ROOT)
        .replace(Regex("\\s+"), " ")
}
