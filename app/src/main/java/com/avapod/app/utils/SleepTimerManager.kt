package com.avapod.app.utils

import android.content.Context
import android.os.Handler
import android.os.Looper

/** Stores a deadline, never UI callbacks. The service owns the pause action. */
object SleepTimerManager {
    private const val KEY = "sleep_deadline"
    private val handler = Handler(Looper.getMainLooper())
    private var app: Context? = null
    private var pause: (() -> Unit)? = null
    private var deadline = 0L
    val isTimerRunning: Boolean get() = deadline > System.currentTimeMillis()
    val minutesLeft: Int get() = ((deadline - System.currentTimeMillis()).coerceAtLeast(0) + 59_999).div(60_000).toInt()
    private val tick = object : Runnable {
        override fun run() {
            if (deadline == 0L) return
            if (System.currentTimeMillis() >= deadline) {
                pause?.invoke()
                stopTimer()
            } else handler.postDelayed(this, 1_000)
        }
    }
    private fun initialize(context: Context) {
        app = context.applicationContext
        deadline = app!!.getSharedPreferences("AvapodPrefs", Context.MODE_PRIVATE).getLong(KEY, 0)
    }
    fun attach(context: Context, pausePlayback: () -> Unit) {
        initialize(context)
        pause = pausePlayback
        handler.removeCallbacks(tick)
        if (deadline != 0L) handler.post(tick)
    }
    fun detach() {
        handler.removeCallbacks(tick)
        pause = null
    }
    fun startTimer(context: Context, minutes: Int) {
        require(minutes > 0)
        initialize(context)
        deadline = System.currentTimeMillis() + minutes * 60_000L
        app!!.getSharedPreferences("AvapodPrefs", Context.MODE_PRIVATE).edit().putLong(KEY, deadline).apply()
        handler.removeCallbacks(tick)
        if (pause != null) handler.post(tick)
    }
    fun refresh(context: Context) { initialize(context) }
    fun stopTimer() {
        handler.removeCallbacks(tick)
        deadline = 0
        app?.getSharedPreferences("AvapodPrefs", Context.MODE_PRIVATE)?.edit()?.remove(KEY)?.apply()
    }
}
