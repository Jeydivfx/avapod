package com.avapod.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.avapod.app.utils.SleepTimerManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackLifecycleTest {
    @Test fun playStillWorksAfterRepeatedMinimizeAndReturn() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(requireNotNull(app.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)), "playback-regression.wav")
        val dataSize = 8000 * 2 * 60
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + dataSize).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1.toShort()).putShort(1.toShort()).putInt(8000).putInt(16000)
            .putShort(2.toShort()).putShort(16.toShort()).put("data".toByteArray()).putInt(dataSize)
        file.outputStream().use { it.write(header.array()); it.write(ByteArray(dataSize)) }
        val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
        val controller = future.get(10, TimeUnit.SECONDS)
        try {
            instrumentation.runOnMainSync {
                controller.setMediaItem(MediaItem.Builder().setMediaId(Uri.fromFile(file).toString()).setUri(Uri.fromFile(file)).build())
                controller.prepare()
                controller.pause()
            }
            ActivityScenario.launch<PlayerActivity>(Intent(app, PlayerActivity::class.java)).use { scenario ->
                repeat(4) {
                    scenario.moveToState(Lifecycle.State.CREATED)
                    scenario.moveToState(Lifecycle.State.RESUMED)
                    instrumentation.waitForIdleSync()
                    // A tap made during reconnection must be queued, never sent to the old player.
                    scenario.onActivity { it.findViewById<android.view.View>(R.id.btn_play_pause).performClick() }
                    awaitState { controller.isPlaying }
                    instrumentation.runOnMainSync { controller.pause() }
                    awaitState { !controller.playWhenReady }
                }
                // Deadline expiry must pause in the service with no visible player Activity.
                instrumentation.runOnMainSync { controller.play() }
                scenario.moveToState(Lifecycle.State.CREATED)
                instrumentation.runOnMainSync { SleepTimerManager.startTimer(app, 1) }
                app.getSharedPreferences("AvapodPrefs", Context.MODE_PRIVATE).edit()
                    .putLong("sleep_deadline", System.currentTimeMillis() - 1).commit()
                instrumentation.runOnMainSync { SleepTimerManager.refresh(app) }
                awaitState { !controller.playWhenReady }
            }
        } finally {
            instrumentation.runOnMainSync { SleepTimerManager.stopTimer(); controller.pause(); controller.clearMediaItems(); MediaController.releaseFuture(future) }
            file.delete()
        }
    }
    private fun awaitState(predicate: () -> Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            var satisfied = false
            instrumentation.runOnMainSync { satisfied = predicate() }
            if (satisfied) return
            Thread.sleep(50)
        }
        fail("Playback command did not reach the active session")
    }
}
