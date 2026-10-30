package com.avapod.app.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CancellationException

class RssParserTest {
    @Test fun nestedFieldTextAndCdataSurviveWithoutLeakingBetweenEpisodes() {
        val items = RssParser().parse("""<rss><channel>
            <title>Channel title</title><item><title>First <b>episode</b></title>
            <description><![CDATA[<p>Description</p>]]></description>
            <enclosure url="https://example.org/one.mp3" /></item>
            <item><title>Second</title></item></channel></rss>""")
        assertEquals(2, items.size)
        assertEquals("First episode", items[0].title)
        assertEquals("<p>Description</p>", items[0].description)
        assertEquals("Second", items[1].title)
        assertEquals("", items[1].description)
    }

    @Test fun largeArchiveRetainsEveryEpisode() {
        val xml = buildString {
            append("<rss><channel>")
            repeat(2000) { append("<item><title>Episode $it</title><description>Text $it</description></item>") }
            append("</channel></rss>")
        }
        val items = RssParser().parse(xml)
        assertEquals(2000, items.size)
        assertEquals("Episode 1999", items.last().title)
    }

    @Test fun malformedFeedIsNotAcceptedAsAPartialCacheEntry() {
        val result = runCatching { RssParser().parse("<rss><channel><item><title>One</title></item>") }
        assertTrue(result.isFailure)
    }

    @Test(expected = CancellationException::class)
    fun leavingScreenCanCancelParsing() {
        RssParser().parse("<rss><channel/></rss>") { throw CancellationException() }
    }
}
