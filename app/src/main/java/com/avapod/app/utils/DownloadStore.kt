package com.avapod.app.utils

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.avapod.app.models.RssItem
import com.google.gson.Gson
import java.io.File

data class DownloadRecord(
    val episode: RssItem,
    val downloadId: Long,
    val path: String,
    val completed: Boolean = false,
    val failed: Boolean = false
)

/** DownloadManager confirms completion. A queued request is not an offline episode. */
class DownloadStore(context: Context) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val prefs = app.getSharedPreferences("AvapodDownloads", Context.MODE_PRIVATE)
    private val gson = Gson()
    companion object { private val lock = Any() }

    private fun read(): MutableList<DownloadRecord> = try {
        (gson.fromJson(prefs.getString("records", null), Array<DownloadRecord>::class.java) ?: emptyArray())
            .filterNotNull().filter { !it.episode.audioUrl.isNullOrBlank() }.toMutableList()
    } catch (e: Exception) {
        android.util.Log.e("AvapodStorage", "Cannot read saved downloads", e)
        mutableListOf()
    }

    private fun save(records: List<DownloadRecord>) {
        prefs.edit().putString("records", gson.toJson(records)).apply()
    }

    private fun safeFile(path: String): File? = runCatching {
        val directory = app.getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.canonicalFile
        File(path).canonicalFile.takeIf { directory != null && it.parentFile == directory }
    }.getOrNull()

    private fun checkRecord(record: DownloadRecord): DownloadRecord {
        val file = safeFile(record.path) ?: return record
        if (record.completed) {
            return if (file.isFile && file.length() > 0) record
            else record.copy(completed = false, failed = true)
        }
        if (record.failed) return record
        return manager.query(DownloadManager.Query().setFilterById(record.downloadId))?.use { cursor ->
            if (!cursor.moveToFirst()) return@use record.copy(failed = true)
            when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                DownloadManager.STATUS_SUCCESSFUL ->
                    record.copy(completed = file.isFile && file.length() > 0, failed = !file.isFile || file.length() == 0L)
                DownloadManager.STATUS_FAILED -> record.copy(failed = true)
                else -> record
            }
        } ?: record
    }

    fun reconcile(): List<DownloadRecord> = synchronized(lock) {
        migrateLegacy()
        // Preserve metadata during temporarily unavailable storage/provider access.
        val records = read()
        val updated = records.map(::checkRecord)
        if (updated != records) save(updated)
        updated
    }

    fun downloaded(): List<RssItem> = reconcile().filter { it.completed }.map { it.episode }
    fun isPending(url: String) = reconcile().any { it.episode.audioUrl == url && !it.completed && !it.failed }

    fun localFile(url: String): File? = synchronized(lock) {
        val records = read()
        val index = records.indexOfFirst { it.episode.audioUrl == url }
        if (index < 0) return@synchronized null
        val old = records[index]
        // Handles a completion whose broadcast was missed, querying only this episode.
        val current = runCatching { checkRecord(old) }.getOrDefault(old)
        if (current != old) { records[index] = current; save(records) }
        if (!current.completed) return@synchronized null
        safeFile(current.path)?.takeIf { it.isFile && it.length() > 0 }
    }

    fun enqueue(episode: RssItem) = synchronized(lock) {
        val source = episode.audioUrl.orEmpty()
        require(UrlValidator.isWebUrl(source)) { "Invalid download URL" }
        val previous = reconcile().firstOrNull { it.episode.audioUrl == source }
        if (previous != null && !previous.failed) return@synchronized
        if (previous != null) check(remove(source)) { "Could not remove failed download" }
        val records = read()
        val directory = requireNotNull(app.getExternalFilesDir(Environment.DIRECTORY_MUSIC))
        val fileName = FileUtils.getSafeFileName(source)
        val file = File(directory, fileName)
        check(!file.exists() || file.delete()) { "Cannot replace incomplete download" }
        val request = DownloadManager.Request(Uri.parse(source))
            .setTitle(episode.title.orEmpty())
            .setDestinationInExternalFilesDir(app, Environment.DIRECTORY_MUSIC, fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        val id = manager.enqueue(request)
        records.add(DownloadRecord(episode, id, file.absolutePath))
        save(records)
    }

    fun remove(url: String): Boolean = synchronized(lock) {
        val records = read()
        var success = true
        records.filter { it.episode.audioUrl == url }.forEach { record ->
            val file = safeFile(record.path)
            if (file == null) { success = false }
            else {
                manager.remove(record.downloadId)
                if (file.exists() && !file.delete()) success = false
            }
        }
        if (success) save(records.filterNot { it.episode.audioUrl == url })
        success
    }

    fun clear(): Boolean = synchronized(lock) {
        migrateLegacy()
        var success = true
        read().mapNotNull { it.episode.audioUrl }.forEach { if (!remove(it)) success = false }
        // These are exclusively the app's audio files, including pre-migration orphans.
        app.getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.let { folder ->
            val files = folder.listFiles()
            if (files == null) success = false
            files?.filter { it.isFile && it.extension == "mp3" }?.forEach {
                val safe = safeFile(it.path)
                if (safe == null || (safe.exists() && !safe.delete())) success = false
            }
        } ?: run { success = false }
        if (success) PreferenceHelper(app).clearAllDownloads()
        success
    }

    private fun migrateLegacy() {
        if (prefs.getBoolean("migrated", false)) return
        val legacy = PreferenceHelper(app).getDownloadedEpisodes()
        val records = read()
        if (legacy.isNotEmpty()) {
            val downloaded = manager.query(DownloadManager.Query().setFilterByStatus(DownloadManager.STATUS_SUCCESSFUL)) ?: return
            downloaded.use { cursor ->
                while (cursor.moveToNext()) {
                    val source = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_URI))
                    val episode = legacy.firstOrNull { it.audioUrl == source } ?: continue
                    val local = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)) ?: continue
                    val uri = Uri.parse(local)
                    if (uri.scheme != "file") continue
                    val file = uri.path?.let(::safeFile) ?: continue
                    if (file.isFile && file.length() > 0 && records.none { it.path == file.path || it.episode.audioUrl == source }) {
                        records.add(DownloadRecord(episode,
                            cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)), file.path, true))
                    }
                }
            }
        }
        save(records)
        prefs.edit().putBoolean("migrated", true).apply()
    }
}
