package com.avapod.app

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avapod.app.models.RssItem
import com.avapod.app.utils.DownloadRecord
import com.avapod.app.utils.DownloadStore
import com.avapod.app.utils.PreferenceHelper
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class StorageRegressionTest {
    private fun isolatedContext(): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val prefix = "storage-test-${UUID.randomUUID()}-"
        return object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) =
                base.getSharedPreferences(prefix + name, mode)
        }
    }

    @Test fun bookmarksAndHistorySurviveNewHelperInstances() {
        val context = isolatedContext()
        val first = RssItem(title = "اپیزود فارسی", audioUrl = "https://example.org/one.mp3")
        val second = first.copy(audioUrl = "https://example.org/two.mp3")
        try {
            PreferenceHelper(context).addBookmark(first, "cover")
            PreferenceHelper(context).addBookmark(second, "cover")
            assertEquals(2, PreferenceHelper(context).getBookmarkedEpisodes().size)
            assertTrue(PreferenceHelper(context).isBookmarked(first.audioUrl))
            PreferenceHelper(context).saveContinueListening(first, 15_000)
            PreferenceHelper(context).saveContinueListening(second, 30_000)
            assertEquals(15_000L, PreferenceHelper(context).getContinuePosition(first.audioUrl))
            assertEquals(30_000L, PreferenceHelper(context).getLastPlayback()!!.lastPosition)
            PreferenceHelper(context).removeBookmark(first.audioUrl)
            assertEquals(second.audioUrl, PreferenceHelper(context).getBookmarkedEpisodes().single().audioUrl)
        } finally {
            context.getSharedPreferences("AvapodPrefs", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test fun completedDownloadJsonRemainsReadableAndPlayableLocally() {
        val context = isolatedContext()
        val folder = requireNotNull(context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC))
        val audio = File.createTempFile("storage-regression-", ".mp3", folder)
        val episode = RssItem(title = "دانلود فارسی", audioUrl = "https://example.org/download.mp3")
        try {
            audio.writeBytes(byteArrayOf(1, 2, 3))
            val record = DownloadRecord(episode, -1, audio.absolutePath, completed = true)
            context.getSharedPreferences("AvapodDownloads", Context.MODE_PRIVATE).edit()
                .putBoolean("migrated", true).putString("records", Gson().toJson(listOf(record))).commit()
            assertEquals(episode.audioUrl, DownloadStore(context).downloaded().single().audioUrl)
            assertEquals(audio.canonicalFile, DownloadStore(context).localFile(episode.audioUrl!!)!!.canonicalFile)
        } finally {
            audio.delete()
            context.getSharedPreferences("AvapodDownloads", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
