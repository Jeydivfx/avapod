package com.avapod.app.network

import com.avapod.app.models.RssItem
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/** A malformed document is an error, not a successful partial feed. */
class RssParser {
    fun parse(xml: String, checkActive: () -> Unit = {}): List<RssItem> {
        require(!xml.contains("<!DOCTYPE", ignoreCase = true)) { "DTD is not supported" }
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
        parser.setInput(StringReader(xml))
        val result = mutableListOf<RssItem>()
        var item: RssItem? = null
        var itemDepth = -1
        var rootSeen = false
        val text = mutableListOf<StringBuilder>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            checkActive()
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (parser.depth == 1) {
                        require(parser.name.equals("rss", true) || parser.name.equals("RDF", true)) { "Not an RSS feed" }
                        rootSeen = true
                    }
                    text.add(StringBuilder())
                    val name = parser.name.lowercase(java.util.Locale.ROOT)
                    if (name == "item") { item = RssItem(); itemDepth = parser.depth }
                    else if (item != null) {
                        when {
                            name == "enclosure" -> item.audioUrl = parser.getAttributeValue(null, "url")
                            name == "image" -> parser.getAttributeValue(null, "href")?.let { item.imageUrl = it }
                            name == "content" && parser.namespace.contains("search.yahoo.com/mrss") -> {
                                val type = parser.getAttributeValue(null, "type").orEmpty()
                                if (type.startsWith("image/")) item.imageUrl = parser.getAttributeValue(null, "url")
                            }
                        }
                    }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF ->
                    if (text.isNotEmpty()) text.last().append(parser.text.orEmpty())
                XmlPullParser.END_TAG -> {
                    val value = text.removeAt(text.lastIndex).toString().trim()
                    if (text.isNotEmpty()) text.last().append(value)
                    val name = parser.name.lowercase(java.util.Locale.ROOT)
                    if (name == "item" && parser.depth == itemDepth) {
                        item?.let(result::add); item = null
                    } else if (item != null && parser.depth == itemDepth + 1) {
                        when (name) {
                            "title" -> item.title = value
                            "description", "encoded" -> item.description = value
                            "pubdate" -> item.pubDate = value
                            "duration" -> item.duration = value
                            "image" -> if (item.imageUrl.isNullOrBlank() && value.startsWith("http")) item.imageUrl = value
                        }
                    }
                }
            }
            event = parser.nextToken()
        }
        require(rootSeen) { "Empty RSS document" }
        return result
    }
}
