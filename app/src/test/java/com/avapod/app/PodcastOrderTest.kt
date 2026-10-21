package com.avapod.app

import com.avapod.app.models.Podcast
import com.avapod.app.utils.PodcastOrder
import org.junit.Assert.*
import org.junit.Test

class PodcastOrderTest {
    @Test fun newlyTrendingOldPodcastComesFirst() {
        val older = Podcast(id = "old", createdAt = 100, trendingAt = 300, is_trending = true)
        val newer = Podcast(id = "new", createdAt = 200, trendingAt = 200, is_trending = true)
        assertEquals(listOf(newer, older), PodcastOrder.newest(listOf(older, newer)))
        assertEquals(listOf(older, newer), PodcastOrder.trending(listOf(newer, older)))
        assertEquals(listOf(older, newer), PodcastOrder.trending(listOf(older, newer, Podcast(id = "hidden"))))
    }
    @Test fun legacyPushIdTimeIsDecoded() {
        assertEquals(1L, PodcastOrder.createdTime(Podcast(id = "-------0------------")))
        assertEquals(0L, PodcastOrder.createdTime(Podcast(id = "podcast_1")))
        assertEquals(999L, PodcastOrder.createdTime(Podcast(id = "-------0------------", createdAt = 999)))
    }
    @Test fun titleEditsDoNotChangeOrder() {
        val one = Podcast(id = "1", createdAt = 100)
        val two = Podcast(id = "2", createdAt = 200)
        assertEquals(listOf("2", "1"), PodcastOrder.newest(listOf(one.copy(title = "ویرایش"), two)).map { it.id })
    }
    @Test fun persianArabicLettersAndSpacingMatch() {
        assertEquals(PodcastOrder.searchKey("کی  می‌آید"), PodcastOrder.searchKey("كي می آید"))
    }
}
