package com.avapod.app.utils

import android.content.Context
import android.content.Intent
import android.os.CountDownTimer

object SleepTimerManager {

    private var countDownTimer: CountDownTimer? = null
    var isTimerRunning = false
        private set

    var minutesLeft = 0
        private set

    private var onTickListener: ((Int) -> Unit)? = null
    private var onFinishListener: (() -> Unit)? = null

    fun startTimer(context: Context, minutes: Int, onTick: (Int) -> Unit, onFinish: () -> Unit) {
        stopTimer()

        this.onTickListener = onTick
        this.onFinishListener = onFinish
        isTimerRunning = true
        minutesLeft = minutes

        val appContext = context.applicationContext
        val millisInFuture = minutes * 60 * 1000L

        countDownTimer = object : CountDownTimer(millisInFuture, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val currentMinutesLeft = (millisUntilFinished / 60000L).toInt() + 1

                if (currentMinutesLeft != minutesLeft) {
                    minutesLeft = currentMinutesLeft
                    onTickListener?.invoke(minutesLeft)
                }
            }

            override fun onFinish() {
                isTimerRunning = false
                minutesLeft = 0
                onFinishListener?.invoke()

                val intent = Intent("ACTION_AVAPOD_SLEEP_TIMER_FORCED_PAUSE").apply {
                    setPackage(appContext.packageName)
                }
                appContext.sendBroadcast(intent)
            }
        }.start()
    }

    fun stopTimer() {
        countDownTimer?.cancel()
        countDownTimer = null
        isTimerRunning = false
        minutesLeft = 0
    }
}