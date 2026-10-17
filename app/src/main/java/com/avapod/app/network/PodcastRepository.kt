package com.avapod.app.network

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.avapod.app.R
import com.avapod.app.models.Category
import com.avapod.app.models.Podcast
import com.avapod.app.utils.DatabaseHelper
import com.avapod.app.utils.AdManager
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PodcastRepository(context: Context, private val owner: LifecycleOwner) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("AvapodPrefs", Context.MODE_PRIVATE)
    companion object {
        private val syncLock = Mutex()
        private val adsLock = Mutex()
        private var lastSuccess: Long? = null
        private var lastFailure: Long? = null
        private var lastAdsSuccess: Long? = null
        private var lastAdsFailure: Long? = null
        private const val SYNC_INTERVAL_MS = 5 * 60_000L
        private const val FAILURE_BACKOFF_MS = 30_000L

        // Called on the main thread after a successful admin write.
        fun invalidate() { lastSuccess = null; lastFailure = null }
    }

    fun getPodcasts(onComplete: () -> Unit = {}, forceRefresh: Boolean = false, onResult: (List<Podcast>) -> Unit) = load({ DatabaseHelper(app).use { it.getAllPodcasts() } }, onResult, onComplete, forceRefresh)
    fun getCategories(onResult: (List<Category>) -> Unit) = load({ DatabaseHelper(app).use { it.getAllCategories() } }, onResult)

    private fun <T> load(read: () -> List<T>, onResult: (List<T>) -> Unit, onComplete: () -> Unit = {}, forceRefresh: Boolean = false) {
        owner.lifecycleScope.launch {
            var delivered = false
            try {
                val cached = withContext(Dispatchers.IO) {
                    try { read() } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        android.util.Log.e("AvapodCatalog", "Could not read catalog cache", e)
                        emptyList<T>()
                    }
                }
                if (cached.isNotEmpty()) { onResult(cached); delivered = true }
                withTimeout(20_000) { sync(forceRefresh) }
                onResult(withContext(Dispatchers.IO) { read() })
                refreshAds()
            } catch (e: Exception) {
                if (e is CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) throw e
                android.util.Log.e("AvapodCatalog", "Catalog synchronization failed", e)
                if (!delivered) {
                    // A concurrent screen may have filled the cache while this request timed out.
                    val fallback = withContext(Dispatchers.IO) {
                        try { read() } catch (cacheError: Exception) {
                            if (cacheError is CancellationException) throw cacheError
                            emptyList<T>()
                        }
                    }
                    onResult(fallback)
                }
                Toast.makeText(app, R.string.catalog_sync_failed, Toast.LENGTH_LONG).show()
            } finally {
                if (owner.lifecycle.currentState != androidx.lifecycle.Lifecycle.State.DESTROYED) onComplete()
            }
        }
    }

    private suspend fun sync(force: Boolean) = syncLock.withLock {
        val now = SystemClock.elapsedRealtime()
        // Check inside the lock: another screen may have just populated the cache.
        val hasCache = withContext(Dispatchers.IO) {
            DatabaseHelper(app).use { it.getAllPodcasts().isNotEmpty() }
        }
        if (!force && hasCache && lastSuccess?.let { now - it < SYNC_INTERVAL_MS } == true) return@withLock
        if (!force && lastFailure?.let { now - it < FAILURE_BACKOFF_MS } == true) {
            if (hasCache) return@withLock
            throw java.io.IOException("Catalog retry temporarily delayed")
        }
        try {
            synchronizeCatalog(!hasCache)
            lastSuccess = SystemClock.elapsedRealtime()
            lastFailure = null
        } catch (e: Exception) {
            if (e !is CancellationException || e is kotlinx.coroutines.TimeoutCancellationException) {
                lastFailure = SystemClock.elapsedRealtime()
            }
            throw e
        }
    }

    private suspend fun remoteVersion(): Long {
        val value = PublicDatabase.read("db_config/version")
        return if (value.isJsonNull) 1L else value.asLong
    }

    private suspend fun synchronizeCatalog(missingCache: Boolean) {
        val version = remoteVersion()
        val localVersion = (prefs.all["db_version"] as? Number)?.toLong() ?: 0
        if (missingCache || version != localVersion) {
            var success = false
            // Detect a concurrent admin edit instead of caching a mixed catalog.
            for (attempt in 0..2) {
                val before = if (attempt == 0) version else remoteVersion()
                val categories = PublicDatabase.read("categories").databaseChildren().map { (id, item) ->
                    Category(id = id, name = item.databaseString("name"), icon = item.databaseString("icon"))
                }
                val podcasts = PublicDatabase.read("podcasts").databaseChildren().map { (id, item) ->
                    Podcast(id = id, title = item.databaseString("title"), artist = item.databaseString("artist"),
                        description = item.databaseString("description"), category_id = item.databaseString("category_id"),
                        rss_url = item.databaseString("rss_url"), thumbnail_url = item.databaseString("thumbnail_url"),
                        is_trending = item.databaseBoolean("is_trending"), is_featured = item.databaseBoolean("is_featured"))
                }
                val after = remoteVersion()
                if (before != after) continue
                withContext(Dispatchers.IO) {
                    DatabaseHelper(app).use { it.replaceCatalog(podcasts, categories) }
                    prefs.edit().putLong("db_version", after).apply()
                }
                success = true
                break
            }
            check(success) { "Catalog changed during synchronization" }
        }
    }

    private fun refreshAds() {
        // Ads are optional and never block the catalog.
        owner.lifecycleScope.launch {
            try {
                adsLock.withLock {
                    val now = SystemClock.elapsedRealtime()
                    if (lastAdsSuccess?.let { now - it < SYNC_INTERVAL_MS } == true) return@withLock
                    if (lastAdsFailure?.let { now - it < FAILURE_BACKOFF_MS } == true) return@withLock
                    val ads = try {
                        withTimeout(5_000) { PublicDatabase.read("ad_config") }
                    } catch (e: Exception) {
                        if (e !is CancellationException || e is kotlinx.coroutines.TimeoutCancellationException) {
                            lastAdsFailure = SystemClock.elapsedRealtime()
                        }
                        throw e
                    }
                    withContext(Dispatchers.IO) {
                        DatabaseHelper(app).use { db -> db.insertOrUpdateAdConfig(ads.databaseBoolean("ad_banner_enabled"),
                            ads.databaseBoolean("ad_interstitial_enabled"), ads.databaseBoolean("ad_rewarded_enabled")) }
                    }
                    lastAdsSuccess = SystemClock.elapsedRealtime()
                    lastAdsFailure = null
                    AdManager.preloadInterstitialAd(app)
                    AdManager.preloadRewardedAd(app)
                }
            } catch (e: Exception) {
                if (e is CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) throw e
            }
        }
    }
}
