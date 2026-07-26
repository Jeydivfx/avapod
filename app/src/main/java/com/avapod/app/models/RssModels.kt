package com.avapod.app.models

data class RssFeed(
    var channel: RssChannel? = null
)

data class RssChannel(
    var title: String? = "",
    var items: List<RssItem>? = null
)

data class RssItem(
    var title: String? = "",
    var pubDate: String? = "",
    var description: String? = "",
    var audioUrl: String? = "",
    var artist: String? = null,
    var lastPosition: Long = 0,
    var imageUrl: String? = null,
    var duration: String? = ""
)