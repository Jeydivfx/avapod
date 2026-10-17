package com.avapod.app

import com.avapod.app.models.Podcast
import com.avapod.app.utils.WeeklyFeatured
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class WeeklyFeaturedTest {
    private fun at(date: String): Long = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("Asia/Tehran")
        isLenient = false
    }.parse(date)!!.time
    private fun podcast(id: String, featured: Boolean = true) = Podcast(id = id, title = id,
        rss_url = "https://example.com/$id.xml", thumbnail_url = "https://example.com/$id.jpg", is_featured = featured)
    private val items = listOf(podcast("a"), podcast("b"), podcast("c"))

    @Test fun selectionIsStableWithinWeekAndIndependentOfListOrder() {
        val first = WeeklyFeatured.select(items, at("2026-10-10 00:00:00"))
        assertEquals(first, WeeklyFeatured.select(items.reversed(), at("2026-10-16 23:59:59")))
    }

    @Test fun saturdayMidnightChangesSelectionAndCompletesCycle() {
        val friday = at("2026-10-09 23:59:59")
        assertNotEquals(WeeklyFeatured.select(items, friday), WeeklyFeatured.select(items, friday + 1000))
        val start = at("2026-10-10 00:00:00")
        val weekly = (0..3).map { WeeklyFeatured.select(items, start + it * 7L * 86_400_000L)!!.id }
        assertEquals(3, weekly.take(3).toSet().size)
        assertEquals(weekly[0], weekly[3])
    }

    @Test fun emptyOrUnmarkedPoolDoesNotFeatureUnapprovedPodcasts() {
        assertNull(WeeklyFeatured.select(emptyList()))
        assertNull(WeeklyFeatured.select(listOf(podcast("other", false))))
    }

    @Test fun singleApprovedPodcastRemainsSelected() {
        val one = podcast("only")
        assertEquals(one, WeeklyFeatured.select(listOf(one), at("2026-10-10 12:00:00")))
        assertEquals(one, WeeklyFeatured.select(listOf(one), at("2026-10-17 12:00:00")))
    }

    @Test fun incompleteAndDuplicateFeedsDoNotDistortRotation() {
        val one = podcast("a")
        val list = listOf(one, one.copy(id = "duplicate"), podcast("broken").copy(rss_url = ""),
            podcast("no-cover").copy(thumbnail_url = ""), podcast("not-featured", false))
        assertEquals(one, WeeklyFeatured.select(list))
    }

    @Test fun calendarYearChangeDoesNotResetWeek() {
        assertEquals(WeeklyFeatured.weekIndex(at("2026-12-31 12:00:00")),
            WeeklyFeatured.weekIndex(at("2027-01-01 12:00:00")))
        assertEquals(WeeklyFeatured.weekIndex(at("2027-01-01 23:59:59")) + 1,
            WeeklyFeatured.weekIndex(at("2027-01-02 00:00:00")))
    }
}
