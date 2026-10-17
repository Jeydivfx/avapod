package com.avapod.app.network

import com.avapod.app.models.RssItem
import com.avapod.app.utils.UrlValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException

/** Shared bounded RSS loading for detail pages and subscriptions. */
object RssLoader {
    private const val MAX_FEED_BYTES = 8L * 1024 * 1024

    suspend fun load(url: String): List<RssItem> = withTimeout(30_000) {
        withContext(Dispatchers.IO) {
            require(UrlValidator.isWebUrl(url)) { "Invalid RSS address" }
            val response = RetrofitClient.instance.getRssFeed(url)
            if (!response.isSuccessful) {
                response.errorBody()?.close()
                throw IOException("RSS HTTP ${response.code()}")
            }
            val xml = (response.body() ?: throw IOException("Empty RSS response")).use { body ->
                if (body.contentLength() > MAX_FEED_BYTES) throw IOException("RSS exceeds size limit")
                if (body.source().request(MAX_FEED_BYTES + 1)) throw IOException("RSS exceeds size limit")
                body.string()
            }
            val context = currentCoroutineContext()
            RssParser().parse(xml) { context.ensureActive() }
        }
    }
}
