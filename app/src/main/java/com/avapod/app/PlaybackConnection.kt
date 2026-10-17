package com.avapod.app

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import java.util.ArrayDeque

/** One main-thread connection per visible UI. The service alone owns the player. */
@OptIn(UnstableApi::class)
class PlaybackConnection(context: Context,
    private val onConnected: (MediaController) -> Unit,
    private val onChanged: () -> Unit,
    private val onError: (Throwable) -> Unit
) {
    private val app = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var future: ListenableFuture<MediaController>? = null
    private var active = false
    private val pending = ArrayDeque<(MediaController) -> Unit>()
    val hasPendingAction: Boolean get() = pending.isNotEmpty()
    var controller: MediaController? = null
        private set
    private val timeout = Runnable {
        if (active && controller == null && future != null) {
            releaseConnection()
            pending.clear()
            onError(IllegalStateException("Timed out connecting to playback service"))
        }
    }
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = onChanged()
    }
    fun start() {
        active = true
        if (controller?.isConnected == false) releaseConnection()
        connect()
    }
    private fun connect() {
        if (!active || future != null) return
        try {
            val request = MediaController.Builder(app,
                SessionToken(app, ComponentName(app, PlaybackService::class.java)))
                .setListener(object : MediaController.Listener {
                    override fun onDisconnected(disconnected: MediaController) {
                        if (controller !== disconnected) return
                        releaseConnection()
                        onChanged()
                    }
                }).buildAsync()
            future = request
            handler.postDelayed(timeout, 10_000)
            request.addListener({
                if (!active || future !== request) return@addListener
                handler.removeCallbacks(timeout)
                try {
                    val connected = request.get()
                    check(connected.isConnected) { "Session disconnected during connection" }
                    controller = connected
                    connected.addListener(listener)
                    onConnected(connected)
                    while (active && controller === connected && pending.isNotEmpty()) {
                        pending.removeFirst().invoke(connected)
                    }
                    onChanged()
                } catch (e: Exception) {
                    releaseConnection()
                    pending.clear()
                    onError(e)
                }
            }, ContextCompat.getMainExecutor(app))
        } catch (e: Exception) {
            releaseConnection()
            pending.clear()
            onError(e)
        }
    }
    fun run(action: (MediaController) -> Unit) {
        if (!active) return
        val connected = controller
        if (connected != null && connected.isConnected) {
            try { action(connected) } catch (e: Exception) { onError(e) }
        } else {
            if (connected != null) releaseConnection()
            if (pending.size < 20) pending.addLast(action)
            connect()
        }
    }
    private fun releaseConnection() {
        handler.removeCallbacks(timeout)
        val previous = future
        val connected = controller
        future = null
        controller = null
        connected?.removeListener(listener)
        previous?.let(MediaController::releaseFuture)
    }
    fun stop() {
        active = false
        pending.clear()
        releaseConnection()
    }
}
