package com.avapod.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.avapod.app.utils.SleepTimerManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class SleepTimerTest {
    @Test fun expiredDeadlinePausesWithoutAnActivityAndCancelRemovesDeadline() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val paused = CountDownLatch(1)
        app.getSharedPreferences("AvapodPrefs", Context.MODE_PRIVATE).edit()
            .putLong("sleep_deadline", System.currentTimeMillis() - 1000).commit()
        try {
            instrumentation.runOnMainSync { SleepTimerManager.attach(app) { paused.countDown() } }
            assertTrue(paused.await(3, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                assertFalse(SleepTimerManager.isTimerRunning)
                SleepTimerManager.startTimer(app, 30)
                SleepTimerManager.stopTimer()
                assertFalse(SleepTimerManager.isTimerRunning)
            }
            assertFalse(app.getSharedPreferences("AvapodPrefs", Context.MODE_PRIVATE).contains("sleep_deadline"))
        } finally { instrumentation.runOnMainSync { SleepTimerManager.stopTimer(); SleepTimerManager.detach() } }
    }
}
