package com.avapod.app

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.avapod.app.models.Podcast

@UnstableApi

object PlayerManager {

    var exoPlayer: ExoPlayer? = null
    var currentPodcast: Podcast? = null
    var currentEpisodeTitle: String? = null
    private var playbackService: PlaybackService? = null


    @RequiresApi(Build.VERSION_CODES.O)
    @OptIn(UnstableApi::class)
    fun initPlayer(context: Context) {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context)
                .setHandleAudioBecomingNoisy(true)
                .build()
        }

    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun playEpisode(context: Context, podcast: Podcast, episodeTitle: String, streamUrl: String) {
        initPlayer(context)

        this.currentPodcast = podcast
        this.currentEpisodeTitle = episodeTitle

        val mediaItem = MediaItem.fromUri(streamUrl)
        exoPlayer?.apply {
            setMediaItem(mediaItem)
            prepare()
            play()
        }

    }

    fun togglePlayPause() {
        exoPlayer?.let {
            if (it.isPlaying) {
                it.pause()
            } else {
                it.play()
            }
        }
    }

    fun releasePlayer(context: Context) {
        exoPlayer?.stop()
        exoPlayer?.release()
        exoPlayer = null
        currentPodcast = null
        currentEpisodeTitle = null


        val serviceIntent = Intent(context, PlaybackService::class.java)
        context.stopService(serviceIntent)
    }

    fun isPlaying(): Boolean = exoPlayer?.isPlaying ?: false
}