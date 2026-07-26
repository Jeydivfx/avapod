package com.avapod.app.network

import com.avapod.app.models.RssItem
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

class RssParser {
    fun parse(xml: String): List<RssItem> {
        val items = mutableListOf<RssItem>()

        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var currentItem: RssItem? = null
            var text = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name

                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (tagName.equals("item", ignoreCase = true)) {
                            currentItem = RssItem()
                        } else if (currentItem != null) {
                            when {
                                tagName.equals("enclosure", ignoreCase = true) -> {
                                    currentItem.audioUrl = parser.getAttributeValue(null, "url")
                                }

                                tagName.equals("itunes:image", ignoreCase = true) -> {
                                    val imageHref = parser.getAttributeValue(null, "href")
                                    if (!imageHref.isNullOrEmpty()) {
                                        currentItem.imageUrl = imageHref
                                    }
                                }

                                tagName.equals("media:content", ignoreCase = true) -> {
                                    val mediaUrl = parser.getAttributeValue(null, "url")
                                    val mediaType = parser.getAttributeValue(null, "type")

                                    if (mediaUrl != null && (mediaType == null || mediaType.contains("image"))) {
                                        currentItem.imageUrl = mediaUrl
                                    }
                                }
                            }
                        }
                    }

                    XmlPullParser.TEXT -> {
                        text = parser.text.trim()
                    }

                    XmlPullParser.END_TAG -> {
                        if (tagName.equals("item", ignoreCase = true)) {
                            currentItem?.let { items.add(it) }
                            currentItem = null
                        } else if (currentItem != null) {
                            when {
                                tagName.equals("title", ignoreCase = true) -> currentItem.title = text
                                tagName.equals("pubDate", ignoreCase = true) -> currentItem.pubDate = text
                                tagName.equals("description", ignoreCase = true) -> currentItem.description = text

                                tagName.equals("itunes:duration", ignoreCase = true) ||
                                        tagName.equals("duration", ignoreCase = true) -> {
                                    currentItem.duration = text
                                }

                                tagName.equals("image", ignoreCase = true) -> {
                                    if (currentItem.imageUrl.isNullOrEmpty()) {
                                        currentItem.imageUrl = text
                                    }
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return items
    }
}