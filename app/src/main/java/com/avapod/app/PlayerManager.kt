package com.avapod.app

import android.content.Context
<<<<<<< HEAD
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.avapod.app.models.Podcast

=======
import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.avapod.app.models.Podcast

@UnstableApi

>>>>>>> 37b85b7 (Fixed play in background error)
object PlayerManager {

    var exoPlayer: ExoPlayer? = null
    var currentPodcast: Podcast? = null
    var currentEpisodeTitle: String? = null
<<<<<<< HEAD

    fun initPlayer(context: Context) {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context).build()
        }
    }

=======
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
>>>>>>> 37b85b7 (Fixed play in background error)
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
<<<<<<< HEAD
=======

>>>>>>> 37b85b7 (Fixed play in background error)
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

<<<<<<< HEAD
    fun releasePlayer() {
        exoPlayer?.release()
        exoPlayer = null
=======
    fun releasePlayer(context: Context) {
        exoPlayer?.stop()
        exoPlayer?.release()
        exoPlayer = null
        currentPodcast = null
        currentEpisodeTitle = null


        val serviceIntent = Intent(context, PlaybackService::class.java)
        context.stopService(serviceIntent)
>>>>>>> 37b85b7 (Fixed play in background error)
    }

    fun isPlaying(): Boolean = exoPlayer?.isPlaying ?: false
}