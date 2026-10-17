package com.avapod.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avapod.app.models.RssItem
import com.avapod.app.utils.DownloadStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DownloadStateTest {
    @Test fun queuedOrFailedDownloadIsNeverListedAsCompletedAndCanBeRemoved() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = DownloadStore(context)
        val url = "http://127.0.0.1:9/" + UUID.randomUUID() + ".mp3"
        try {
            store.enqueue(RssItem(title = "اپیزود فارسی", audioUrl = url))
            assertFalse(store.downloaded().any { it.audioUrl == url })
            assertNull(store.localFile(url))
            assertTrue(store.remove(url))
            assertFalse(store.reconcile().any { it.episode.audioUrl == url })
        } finally { store.remove(url) }
    }
}
