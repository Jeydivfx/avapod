package com.avapod.app

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.avapod.app.models.RssItem
import com.avapod.app.utils.PreferenceHelper
import com.avapod.app.utils.SleepTimerManager

@UnstableApi
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var preferences: PreferenceHelper
    private var previousEpisode: RssItem? = null
    private val handler = Handler(Looper.getMainLooper())
    private val checkpoint = object : Runnable {
        override fun run() { if (player.isPlaying) saveProgress(); handler.postDelayed(this, 5_000) }
    }
    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) { saveProgress() }
        override fun onPlaybackStateChanged(playbackState: Int) { saveProgress() }
        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            oldPosition.mediaItem?.let { oldItem ->
                val old = previousEpisode?.takeIf { it.audioUrl == oldItem.mediaId } ?: PlayerManager.episode(oldItem)
                preferences.saveContinueListening(old, oldPosition.positionMs.coerceAtLeast(0))
            }
        }
        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            previousEpisode = mediaItem?.let(PlayerManager::episode)
            if (mediaItem == null) preferences.clearLastPlayback() else saveProgress()
        }
        override fun onEvents(player: Player, events: Player.Events) {
            if (player.mediaItemCount == 0) preferences.clearLastPlayback()
            else if (events.contains(Player.EVENT_POSITION_DISCONTINUITY)) saveProgress()
        }
    }
    override fun onCreate() {
        super.onCreate()
        preferences = PreferenceHelper(this)
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
            .setHandleAudioBecomingNoisy(true).setWakeMode(C.WAKE_MODE_NETWORK).build()
        preferences.getLastPlayback()?.let { episode ->
            if (PlayerManager.isPlayable(this, episode.audioUrl)) {
                player.setMediaItem(PlayerManager.mediaItem(this, episode), episode.lastPosition)
                previousEpisode = episode
                // Restore paused; only an explicit play command starts audio.
            } else preferences.clearLastPlayback()
        }
        player.addListener(listener)
        val openPlayer = PendingIntent.getActivity(this, 0, Intent(this, PlayerActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        mediaSession = MediaSession.Builder(this, player).setSessionActivity(openPlayer).build()
        SleepTimerManager.attach(this) { player.pause(); saveProgress() }
        handler.postDelayed(checkpoint, 5_000)
    }
    private fun saveProgress() {
        val item = player.currentMediaItem ?: return
        val duration = player.duration.takeIf { it > 0 } ?: 0
        val storedDuration = preferences.getContinueListening().firstOrNull { it.audioUrl == item.mediaId }?.duration
        val episode = PlayerManager.episode(item).copy(duration = if (duration > 0) (duration / 1000).toString() else storedDuration)
        previousEpisode = episode
        preferences.saveContinueListening(episode,
            if (player.playbackState == Player.STATE_ENDED) 0 else player.currentPosition.coerceAtLeast(0))
    }
    override fun onTaskRemoved(rootIntent: Intent?) {
        saveProgress()
        if (!player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) stopSelf()
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        saveProgress()
        SleepTimerManager.detach()
        player.removeListener(listener)
        mediaSession?.release()
        mediaSession = null
        player.release()
        super.onDestroy()
    }
}
