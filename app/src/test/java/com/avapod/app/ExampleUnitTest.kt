package com.avapod.app

import com.avapod.app.network.RssParser
import com.avapod.app.utils.FileUtils
import com.avapod.app.utils.StringUtils
import com.avapod.app.utils.UrlValidator
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test fun rssParsingHonorsCancellation() {
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            RssParser().parse("<rss><channel><item><title>test</title></item></channel></rss>") {
                throw java.util.concurrent.CancellationException()
            }
        }
    }
    @Test fun persianEpisodesHaveDistinctStableFileIdentities() {
        val first = FileUtils.getSafeFileName("https://example.org/تاریخ.mp3")
        val second = FileUtils.getSafeFileName("https://example.org/موسیقی.mp3")
        assertNotEquals(first, second)
        assertEquals(first, FileUtils.getSafeFileName("https://example.org/تاریخ.mp3"))
        assertFalse(first.contains("/"))
        assertFalse(first.contains("\\"))
        assertTrue(first.length < 100)
    }
    @Test fun searchMatchesArabicVariantsAndHalfSpaces() {
        assertTrue(StringUtils.matchesSearch("كتاب هاي تاريخي", "کتاب‌های تاریخی"))
        assertTrue(StringUtils.matchesSearch("پادکستِ فارسی", "پادکست فارسی"))
        assertFalse(StringUtils.matchesSearch("موسیقی", "تاریخ"))
    }
    @Test fun onlyValidWebUrlsAreAccepted() {
        assertTrue(UrlValidator.isWebUrl("https://example.org/podcast.mp3"))
        assertTrue(UrlValidator.isWebUrl("http://example.org/feed"))
        listOf(null, "", "file:///data/private.mp3", "content://private/1", "javascript:alert(1)",
            "https:///missing-host", "https://user:password@example.org/file", "not a URL").forEach {
            assertFalse(it.toString(), UrlValidator.isWebUrl(it))
        }
    }
    @Test fun emptyRssTagsDoNotReusePreviousText() {
        val item = RssParser().parse("<rss><channel><item><title>عنوان</title><description/><duration/></item></channel></rss>").single()
        assertEquals("عنوان", item.title)
        assertEquals("", item.description)
        assertEquals("", item.duration)
    }
    @Test fun rssNamespacesAndCdataAreSupported() {
        val xml = """<rss xmlns:i="http://www.itunes.com/dtds/podcast-1.0.dtd"><channel><item>
            <title><![CDATA[تاریخ & فرهنگ]]></title><i:duration>01:02:03</i:duration>
            <i:image href="https://example.org/image.jpg"/><enclosure url="https://example.org/audio.mp3"/>
            </item></channel></rss>"""
        val item = RssParser().parse(xml).single()
        assertEquals("تاریخ & فرهنگ", item.title)
        assertEquals("01:02:03", item.duration)
        assertEquals("https://example.org/image.jpg", item.imageUrl)
        assertEquals("https://example.org/audio.mp3", item.audioUrl)
    }
    @Test fun emptyValidFeedIsDifferentFromBrokenFeed() {
        assertTrue(RssParser().parse("<rss><channel/></rss>").isEmpty())
        for (xml in listOf("", "<html/>", "<rss><channel><item>", "<!DOCTYPE rss><rss/>")) {
            assertThrows(Exception::class.java) { RssParser().parse(xml) }
        }
    }
    @Test fun durationUsesExplicitSecondsAndClockFormats() {
        assertEquals(3_723_000L, StringUtils.timeStringToMs("1:02:03"))
        assertEquals(65_000L, StringUtils.timeStringToMs("65"))
        assertEquals(0L, StringUtils.timeStringToMs("unknown"))
        assertEquals(0L, StringUtils.timeStringToMs("1:99"))
        assertEquals(0L, StringUtils.timeStringToMs("-1:00"))
        assertEquals(0L, StringUtils.timeStringToMs(Long.MAX_VALUE.toString()))
        assertEquals("۰۱۲۳۴۵۶۷۸۹", StringUtils.toPersianNumber("0123456789"))
    }
}
