package com.avapod.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.avapod.app.models.RssItem
import com.avapod.app.utils.AdManager
import com.avapod.app.utils.DownloadStore
import com.avapod.app.utils.PreferenceHelper
import com.avapod.app.utils.SleepTimerManager
import com.avapod.app.utils.UrlValidator
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.util.Locale

class PlayerActivity : AppCompatActivity() {
    private lateinit var connection: PlaybackConnection
    private lateinit var preferences: PreferenceHelper
    private lateinit var btnPlay: ImageView
    private lateinit var waveform: WaveformView
    private lateinit var currentTime: TextView
    private lateinit var totalTime: TextView
    private lateinit var speed: TextView
    private lateinit var title: TextView
    private lateinit var artist: TextView
    private lateinit var cover: ImageView
    private lateinit var timer: ImageView
    private var initialHandled = false
    private var requestedEpisode: RssItem? = null
    private var displayedCover: String? = null
    private var lastError: String? = null
    private var playlistSources = emptySet<String?>()
    private var activeSheet: BottomSheetDialog? = null
    private val handler = Handler(Looper.getMainLooper())
    private val progress = object : Runnable {
        override fun run() { render(); handler.postDelayed(this, 500) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        val barStyle = if (resources.getBoolean(R.bool.light_system_bars)) {
            SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.player_root_view)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val padding = (12 * resources.displayMetrics.density).toInt()
            view.setPadding(bars.left + padding, bars.top, bars.right + padding, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        // Size artwork from remaining viewport space, retaining scroll as an accessibility fallback.
        val scroll = findViewById<View>(R.id.player_scroll)
        val content = findViewById<android.widget.LinearLayout>(R.id.player_content)
        val artwork = findViewById<View>(R.id.player_cover_frame)
        fun fitArtwork() {
            if (scroll.height <= 0 || scroll.width <= 0) return
            var other = content.paddingTop + content.paddingBottom
            for (i in 0 until content.childCount) {
                val child = content.getChildAt(i)
                if (child.visibility == View.GONE) continue
                val lp = child.layoutParams as android.widget.LinearLayout.LayoutParams
                other += lp.topMargin + lp.bottomMargin
                if (child !== artwork) other += child.measuredHeight
            }
            val density = resources.displayMetrics.density
            val target = minOf(scroll.width * .52f, 200f * density,
                (scroll.height - other - 8 * density).coerceAtLeast(96f * density)).toInt()
            if (artwork.layoutParams.height != target) {
                artwork.layoutParams = artwork.layoutParams.apply { width = target; height = target }
            }
        }
        content.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> fitArtwork() }
        preferences = PreferenceHelper(this)
        initialHandled = savedInstanceState?.getBoolean("initial_handled") ?: false
        requestedEpisode = intent.getStringExtra("audio_url")?.let { source ->
            RssItem(audioUrl = source, title = intent.getStringExtra("title"),
                artist = intent.getStringExtra("podcast_name"), imageUrl = intent.getStringExtra("cover"),
                lastPosition = intent.getLongExtra("resume_position", preferences.getContinuePosition(source)))
        }
        btnPlay = findViewById(R.id.btn_play_pause)
        waveform = findViewById(R.id.player_waveform)
        currentTime = findViewById(R.id.txt_current_time)
        totalTime = findViewById(R.id.txt_total_time)
        speed = findViewById(R.id.btn_speed)
        title = findViewById(R.id.player_txt_title)
        artist = findViewById(R.id.player_txt_podcast)
        cover = findViewById(R.id.player_img_cover)
        timer = findViewById(R.id.btn_sleep_timer)
        connection = PlaybackConnection(this, { controller ->
            val request = requestedEpisode
            if (!initialHandled && request != null && PlayerManager.isPlayable(this, request.audioUrl)) {
                val queue = com.avapod.app.utils.PlaylistStore.queue(this, intent.getStringExtra("queue_id"))
                val index = queue.indexOfFirst { it.audioUrl == request.audioUrl }
                if (index >= 0) {
                    val position = if (!intent.getBooleanExtra("queue_restart", false) && controller.currentMediaItem?.mediaId == request.audioUrl) controller.currentPosition else request.lastPosition
                    controller.setMediaItems(queue.map { PlayerManager.mediaItem(this, it) }, index, position)
                } else if (controller.currentMediaItem?.mediaId != request.audioUrl) {
                    controller.setMediaItem(PlayerManager.mediaItem(this, request), request.lastPosition)
                }
                if (!connection.hasPendingAction) {
                    if (controller.playbackState == Player.STATE_ENDED) controller.seekTo(0)
                    controller.prepare()
                    controller.play()
                }
            } else if (controller.mediaItemCount == 0) {
                val last = preferences.getLastPlayback() ?: requestedEpisode
                if (last != null && PlayerManager.isPlayable(this, last.audioUrl)) {
                    controller.setMediaItem(PlayerManager.mediaItem(this, last), last.lastPosition)
                }
            }
            initialHandled = true
        }, { render() }, { toast(R.string.playback_connection_error) })

        if (requestedEpisode != null && !PlayerManager.isPlayable(this, requestedEpisode?.audioUrl)) {
            requestedEpisode = null
            initialHandled = true
            toast(R.string.invalid_audio_link)
        }

        findViewById<ImageButton>(R.id.btn_back_common).setOnClickListener { finish() }
        btnPlay.setOnClickListener { connection.run {
            if (it.mediaItemCount == 0) toast(R.string.error_audio_file_not_found) else PlayerManager.toggle(it)
        } }
        findViewById<View>(R.id.btn_rewind_15).setOnClickListener {
            connection.run { it.seekTo((it.currentPosition - 15_000).coerceAtLeast(0)) }
        }
        findViewById<View>(R.id.btn_forward_30).setOnClickListener {
            connection.run { it.seekTo((it.currentPosition + 30_000).coerceAtMost(it.duration.takeIf { d -> d > 0 } ?: Long.MAX_VALUE)) }
        }
        findViewById<View>(R.id.btn_previous_episode).setOnClickListener { connection.run {
            if (it.hasPreviousMediaItem()) { it.seekToPreviousMediaItem(); it.prepare(); it.play() }
        } }
        findViewById<View>(R.id.btn_next_episode).setOnClickListener { connection.run {
            if (it.hasNextMediaItem()) { it.seekToNextMediaItem(); it.prepare(); it.play() }
        } }
        findViewById<View>(R.id.btn_add_playlist).setOnClickListener { connection.run { player ->
            player.currentMediaItem?.let { item ->
                val added = com.avapod.app.utils.PlaylistStore.toggle(this, PlayerManager.episode(item))
                playlistSources = com.avapod.app.utils.PlaylistStore.all(this).map { it.audioUrl }.toSet()
                toast(if (added) R.string.playlist_added else R.string.playlist_removed)
                render()
            }
        } }
        findViewById<View>(R.id.btn_shuffle).setOnClickListener { connection.run {
            it.shuffleModeEnabled = !it.shuffleModeEnabled
            toast(if (it.shuffleModeEnabled) R.string.shuffle_on else R.string.shuffle_off)
        } }
        findViewById<View>(R.id.btn_repeat).setOnClickListener { connection.run {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            toast(when (it.repeatMode) {
                Player.REPEAT_MODE_ALL -> R.string.repeat_all
                Player.REPEAT_MODE_ONE -> R.string.repeat_one
                else -> R.string.repeat_off
            })
        } }
        waveform.onProgressChanged = { fraction -> connection.run {
            if (it.duration > 0) it.seekTo((it.duration * fraction).toLong())
        } }
        speed.setOnClickListener { connection.run {
            val next = when (it.playbackParameters.speed) { 1f -> 1.5f; 1.5f -> 2f; else -> 1f }
            it.setPlaybackSpeed(next)
        } }
        findViewById<ImageButton>(R.id.btn_menu_common).setOnClickListener { showMenu() }
        timer.setOnClickListener { showTimer() }
        findViewById<View>(R.id.sleep_timer_action).setOnClickListener { showTimer() }

    }

    private fun render() {
        if (!::connection.isInitialized) return
        val player = connection.controller
        listOf(R.id.btn_previous_episode to (player?.hasPreviousMediaItem() == true),
            R.id.btn_next_episode to (player?.hasNextMediaItem() == true)).forEach { (id, enabled) ->
            findViewById<View>(id).apply { isEnabled = enabled; alpha = if (enabled) 1f else .3f }
        }
        findViewById<ImageView>(R.id.btn_shuffle).apply {
            isEnabled = (player?.mediaItemCount ?: 0) > 1
            alpha = if (isEnabled) 1f else .35f
            isSelected = player?.shuffleModeEnabled == true
            imageTintList = android.content.res.ColorStateList.valueOf(getColor(if (isSelected) R.color.primary_blue else R.color.text_gray))
            contentDescription = getString(if (isSelected) R.string.shuffle_on else R.string.shuffle_off)
        }
        findViewById<ImageView>(R.id.btn_repeat).apply {
            isEnabled = (player?.mediaItemCount ?: 0) > 0
            isSelected = player != null && player.repeatMode != Player.REPEAT_MODE_OFF
            imageTintList = android.content.res.ColorStateList.valueOf(getColor(if (isSelected) R.color.primary_blue else R.color.text_gray))
            contentDescription = getString(when (player?.repeatMode) {
                Player.REPEAT_MODE_ONE -> R.string.repeat_one
                Player.REPEAT_MODE_ALL -> R.string.repeat_all
                else -> R.string.repeat_off
            })
            setImageResource(if (player?.repeatMode == Player.REPEAT_MODE_ONE) R.drawable.ic_player_repeat_one else R.drawable.ic_player_repeat)
        }
        val currentSource = player?.currentMediaItem?.mediaId
        findViewById<TextView>(R.id.btn_add_playlist).apply {
            isEnabled = currentSource != null
            setText(if (currentSource != null && currentSource in playlistSources) R.string.playlist_remove else R.string.playlist_add)
        }
        val episode = player?.currentMediaItem?.let(PlayerManager::episode) ?: requestedEpisode
        title.text = episode?.title.orEmpty()
        artist.text = episode?.artist.orEmpty()
        if (displayedCover != episode?.imageUrl) {
            displayedCover = episode?.imageUrl
            Glide.with(this).load(displayedCover).placeholder(R.drawable.placeholder_podcast).into(cover)
        }
        val playing = player != null && player.playWhenReady && player.playbackState != Player.STATE_ENDED &&
            player.playbackState != Player.STATE_IDLE && player.playerError == null
        btnPlay.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play_arrow)
        btnPlay.contentDescription = getString(if (playing) R.string.accessibility_pause else R.string.accessibility_play)
        val duration = player?.duration?.coerceAtLeast(0) ?: 0
        val position = player?.currentPosition?.coerceAtLeast(0) ?: 0
        waveform.progress = if (duration > 0) position.toFloat() / duration else 0f
        currentTime.text = formatTime(position)
        totalTime.text = formatTime(duration)
        speed.text = getString(R.string.playlist_speed, com.avapod.app.utils.StringUtils.toPersianNumber(getString(R.string.playback_speed, player?.playbackParameters?.speed ?: 1f)))
        SleepTimerManager.refresh(this)
        timer.imageTintList = android.content.res.ColorStateList.valueOf(getColor(
            if (SleepTimerManager.isTimerRunning) R.color.timer_accent_blue else R.color.text_white))
        val error = player?.playerError?.errorCodeName
        if (error != null && error != lastError) toast(R.string.playback_failed_retry)
        lastError = error
    }

    private fun showMenu() {
        val player = connection.controller ?: return
        val item = player.currentMediaItem ?: return
        val episode = PlayerManager.episode(item).copy(duration = (player.duration.coerceAtLeast(0) / 1000).toString())
        val source = episode.audioUrl ?: return
        lifecycleScope.launch {
            val store = DownloadStore(this@PlayerActivity)
            val result = withContext(Dispatchers.IO) { runCatching { store.reconcile() } }
            val records = result.getOrElse {
                toast(R.string.download_failed)
                return@launch
            }
            if (isFinishing || isDestroyed || !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return@launch
            val record = records.firstOrNull { it.episode.audioUrl == source }
            val sheet = BottomSheetDialog(this@PlayerActivity)
            val content = layoutInflater.inflate(R.layout.dialog_episode_menu, null)
            sheet.setContentView(content)
            content.findViewById<TextView>(R.id.txt_menu_episode_title).text = episode.title
            val bookmarked = preferences.isBookmarked(source)
            content.findViewById<TextView>(R.id.txt_menu_bookmark).setText(
                if (bookmarked) R.string.menu_remove_bookmark else R.string.menu_add_bookmark)
            content.findViewById<ImageView>(R.id.img_menu_bookmark_icon).setImageResource(
                if (bookmarked) R.drawable.ic_bookmark else R.drawable.ic_bookmark_border)
            content.findViewById<TextView>(R.id.txt_menu_download).setText(
                if (record?.completed == true) R.string.menu_delete_download
                else if (record != null && !record.failed) R.string.cancel_download else R.string.menu_download_episode)
            content.findViewById<View>(R.id.item_menu_download).setOnClickListener {
                sheet.dismiss()
                if (record != null && !record.failed) {
                    lifecycleScope.launch {
                        val removed = withContext(Dispatchers.IO) { runCatching { store.remove(source) }.getOrDefault(false) }
                        toast(if (removed) R.string.delete_from_memory else R.string.download_failed)
                    }
                } else {
                    lifecycleScope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching { store.enqueue(episode) } }
                        toast(if (result.isSuccess) R.string.toast_download_started else R.string.download_failed)
                    }
                }
            }
            content.findViewById<View>(R.id.item_menu_bookmark).setOnClickListener {
                if (bookmarked) {
                    preferences.removeBookmark(source)
                    toast(R.string.toast_bookmark_removed)
                } else {
                    preferences.addBookmark(episode, episode.imageUrl.orEmpty())
                    toast(R.string.toast_bookmark_added)
                }

                sheet.dismiss()
            }
            content.findViewById<View>(R.id.item_menu_share).setOnClickListener {
                if (UrlValidator.isWebUrl(source)) {
                    val encoded = Base64.encodeToString(source.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
                    val link = "https://jeydivfx.github.io/avapod-share/?audio=" + encoded +
                        "&title=" + URLEncoder.encode(episode.title.orEmpty(), "UTF-8") +
                        "&cover=" + URLEncoder.encode(episode.imageUrl.orEmpty(), "UTF-8")
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, episode.title.orEmpty() + "\n" + link)
                    }, getString(R.string.share_episode)))
                } else toast(R.string.invalid_audio_link)
                sheet.dismiss()
            }
            showPlayerSheet(sheet)
        }
    }

    private fun showTimer() {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_player_sleep_timer, null)
        sheet.setContentView(view)
        view.findViewById<View>(R.id.timer_off).setOnClickListener { SleepTimerManager.stopTimer(); render(); sheet.dismiss() }
        mapOf(R.id.timer_15 to 15, R.id.timer_30 to 30, R.id.timer_60 to 60).forEach { (id, minutes) ->
            view.findViewById<View>(id).setOnClickListener {
                SleepTimerManager.startTimer(this, minutes); render(); sheet.dismiss()
            }
        }
        showPlayerSheet(sheet)
    }
    private fun formatTime(ms: Long): String {
        val seconds = ms.coerceAtLeast(0) / 1000
        val formatted = if (seconds >= 3600) String.format(Locale.US, "%d:%02d:%02d", seconds / 3600, seconds % 3600 / 60, seconds % 60)
        else String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)
        return com.avapod.app.utils.StringUtils.toPersianNumber(formatted)
    }
    private fun showPlayerSheet(sheet: BottomSheetDialog) {
        if (isFinishing || isDestroyed || !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) ||
            activeSheet != null) return
        activeSheet = sheet
        AdManager.setBannerObscured(this, true)
        com.avapod.app.utils.DialogHelper.bind(sheet, this) {
            if (activeSheet === sheet) {
                activeSheet = null
                AdManager.setBannerObscured(this, false)
            }
        }
        sheet.show()
    }
    private fun toast(message: Int) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    override fun onStart() { super.onStart(); playlistSources = com.avapod.app.utils.PlaylistStore.all(this).map { it.audioUrl }.toSet(); connection.start(); handler.post(progress) }
    override fun onStop() {
        handler.removeCallbacks(progress)
        connection.stop()
        super.onStop()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("initial_handled", initialHandled)
        super.onSaveInstanceState(outState)
    }
    override fun onResume() { super.onResume(); AdManager.onResume(this) }
    override fun onPause() { AdManager.onPause(this); super.onPause() }
    override fun onDestroy() { AdManager.setBannerObscured(this, false); super.onDestroy() }
}
