package com.avapod.app.utils

import com.avapod.app.models.Podcast
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/** Deterministic rotation among editor-approved podcasts; no server writes or extra requests. */
object WeeklyFeatured {
    fun weekIndex(nowMillis: Long = System.currentTimeMillis()): Long {
        val tehran = GregorianCalendar(TimeZone.getTimeZone("Asia/Tehran")).apply { timeInMillis = nowMillis }
        // Encode the local civil date as UTC before counting days, avoiding DST-length days.
        val civilDate = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(tehran.get(Calendar.YEAR), tehran.get(Calendar.MONTH), tehran.get(Calendar.DAY_OF_MONTH))
        }
        val days = Math.floorDiv(civilDate.timeInMillis, 86_400_000L)
        // 1970-01-03 (epoch day 2) was a Saturday.
        return Math.floorDiv(days - 2L, 7L)
    }

    fun select(podcasts: List<Podcast>, nowMillis: Long = System.currentTimeMillis()): Podcast? {
        val candidates = podcasts.filter {
            it.is_featured && it.title.isNotBlank() && it.thumbnail_url.isNotBlank() && it.rss_url.isNotBlank()
        }.sortedWith(compareBy<Podcast> { it.id.ifBlank { it.rss_url } }.thenBy { it.rss_url })
            .distinctBy { it.rss_url }
        if (candidates.isEmpty()) return null
        return candidates[Math.floorMod(weekIndex(nowMillis), candidates.size.toLong()).toInt()]
    }
}
