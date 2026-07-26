package com.avapod.app

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.avapod.app.models.Podcast

object PlayerManager {

    var exoPlayer: ExoPlayer? = null
    var currentPodcast: Podcast? = null
    var currentEpisodeTitle: String? = null

    fun initPlayer(context: Context) {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context).build()
        }
    }

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

    fun releasePlayer() {
        exoPlayer?.release()
        exoPlayer = null
    }

    fun isPlaying(): Boolean = exoPlayer?.isPlaying ?: false
}