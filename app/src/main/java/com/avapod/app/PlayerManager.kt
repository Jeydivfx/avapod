package com.avapod.app

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.avapod.app.models.RssItem
import com.avapod.app.utils.DownloadStore
import com.avapod.app.utils.UrlValidator
import java.io.File

/** Stateless helpers: PlaybackService is the only owner of the player. */
object PlayerManager {
    fun isPlayable(context: Context, source: String?): Boolean {
        if (UrlValidator.isWebUrl(source)) return true
        return runCatching {
            val uri = Uri.parse(source ?: return false)
            if (uri.scheme != "file") return false
            val folder = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)?.canonicalFile ?: return false
            val file = File(uri.path ?: return false).canonicalFile
            file.parentFile == folder && file.isFile && file.length() > 0
        }.getOrDefault(false)
    }

    fun mediaItem(context: Context, episode: RssItem): MediaItem {
        val source = requireNotNull(episode.audioUrl).also { require(it.isNotBlank()) }
        require(isPlayable(context, source)) { "Unsupported audio source" }
        val local = DownloadStore(context).localFile(source)
        return MediaItem.Builder().setMediaId(source)
            .setUri(local?.let(Uri::fromFile) ?: Uri.parse(source))
            .setMediaMetadata(MediaMetadata.Builder().setTitle(episode.title).setArtist(episode.artist)
                .setArtworkUri(episode.imageUrl?.takeIf { it.isNotBlank() }?.let(Uri::parse)).build())
            .build()
    }
    fun episode(item: MediaItem) = RssItem(audioUrl = item.mediaId,
        title = item.mediaMetadata.title?.toString(), artist = item.mediaMetadata.artist?.toString(),
        imageUrl = item.mediaMetadata.artworkUri?.toString())

    fun toggle(player: Player) {
        if (player.playWhenReady && player.playbackState != Player.STATE_ENDED &&
            player.playbackState != Player.STATE_IDLE && player.playerError == null) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            if (player.playbackState == Player.STATE_IDLE || player.playerError != null) player.prepare()
            player.play()
        }
    }
}
