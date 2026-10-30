package com.avapod.app.network

import com.avapod.app.models.RssItem
import com.avapod.app.utils.UrlValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/** Shared bounded RSS loading for detail pages and subscriptions. */
object RssLoader {
    data class CachedFeed(val episodes: List<RssItem>, val fresh: Boolean)
    private const val MAX_FEED_BYTES = 8L * 1024 * 1024
    private const val FRESH_MS = 5 * 60_000L
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val diskLock = Any()
    private fun file(context: Context, url: String): File {
        val key = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(context.cacheDir, "rss-feeds/$key.xml")
    }
    private suspend fun readCache(context: Context, url: String, freshOnly: Boolean): CachedFeed? = withContext(Dispatchers.IO) {
        var fresh = false
        val xml = synchronized(diskLock) {
            val target = file(context, url)
            val age = System.currentTimeMillis() - target.lastModified()
            fresh = age in 0 until FRESH_MS
            if (!target.exists() || target.length() > MAX_FEED_BYTES ||
                (freshOnly && age !in 0 until FRESH_MS)) null
            else try { AtomicFile(target).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() } }
            catch (_: IOException) { null }
        } ?: return@withContext null
        val coroutine = currentCoroutineContext()
        try { CachedFeed(RssParser().parse(xml) { coroutine.ensureActive() }, fresh) }
        catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }
    suspend fun cached(context: Context, url: String): CachedFeed? = readCache(context, url, false)

    private fun saveCache(context: Context, url: String, xml: String) = synchronized(diskLock) {
        val target = file(context, url)
        target.parentFile?.mkdirs()
        val atomic = AtomicFile(target)
        var stream: java.io.FileOutputStream? = null
        try {
            stream = atomic.startWrite()
            stream.write(xml.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
            // Cache is disposable, bounded by both count and bytes.
            var bytes = 0L
            target.parentFile?.listFiles()?.filter { it.extension == "xml" }
                ?.sortedByDescending { it.lastModified() }?.forEachIndexed { index, entry ->
                    bytes += entry.length()
                    if (index >= 24 || bytes > 32L * 1024 * 1024) entry.delete()
                }
        } catch (_: IOException) { atomic.failWrite(stream) }
    }

    suspend fun load(url: String, context: Context): List<RssItem> = withTimeout(30_000) {
        locks.getOrPut(url) { Mutex() }.withLock {
        readCache(context, url, true)?.let { return@withLock it.episodes }
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
            val coroutine = currentCoroutineContext()
            val episodes = RssParser().parse(xml) { coroutine.ensureActive() }
            saveCache(context, url, xml)
            episodes
        }
        }
    }
}
