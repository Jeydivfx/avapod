package com.avapod.app

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.avapod.app.models.Podcast
import com.avapod.app.models.RssItem
import com.avapod.app.utils.FileUtils
import com.avapod.app.utils.PreferenceHelper
import com.bumptech.glide.Glide
import com.google.android.gms.ads.AdView
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.io.File


class PlayerActivity : AppCompatActivity() {

    private var exoPlayer: ExoPlayer? = null
    private lateinit var btnPlayPause: ImageView
    private lateinit var waveformView: WaveformView
    private lateinit var txtCurrentTime: TextView
    private lateinit var txtTotalTime: TextView
    private lateinit var btnSpeed: TextView
    private lateinit var prefHelper: PreferenceHelper
    private lateinit var imgCover: ImageView
    private lateinit var txtTitle: TextView
    private lateinit var txtPodcast: TextView
    private lateinit var btnSleepTimer: ImageView
    private lateinit var bannerContainer: FrameLayout
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var playerController: MediaController? = null
    private var adView: AdView? = null

    private var currentSpeed = 1.0f
    private val handler = Handler(Looper.getMainLooper())

    private val sleepTimerReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: android.content.Intent?) {
            if (intent?.action == "ACTION_AVAPOD_SLEEP_TIMER_FORCED_PAUSE") {
                exoPlayer?.pause()
                btnPlayPause.setImageResource(R.drawable.ic_play_arrow)
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                finish()
                Toast.makeText(this@PlayerActivity, R.string.sleep_timer_activated, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val updateProgressAction = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 100)
        }
    }

    @OptIn(UnstableApi::class)
    @SuppressLint("ResourceAsColor")
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.R.color.transparent, android.R.color.transparent),
            navigationBarStyle = SystemBarStyle.auto(android.R.color.transparent, android.R.color.transparent)
        )

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.player_root_view)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        prefHelper = PreferenceHelper(this)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        initViews()

        bannerContainer.post {
            adView = com.avapod.app.utils.AdManager.initAndLoadBanner(this, bannerContainer)
        }

        val audioUrl = intent.getStringExtra("audio_url")
        if (audioUrl == null && PlayerManager.exoPlayer != null) {
            loadDataFromManager()
        } else {
            loadDataFromIntent()
            setupPlayer(audioUrl ?: "")
        }

        setupClickListeners()

        val filter = android.content.IntentFilter("ACTION_AVAPOD_SLEEP_TIMER_FORCED_PAUSE")
        registerReceiver(sleepTimerReceiver, filter, RECEIVER_EXPORTED)

        if (PlayerManager.exoPlayer != null) {
            exoPlayer = PlayerManager.exoPlayer
            updateProgress()
        }

        handler.post(updateProgressAction)
        updateSleepTimerUI()
    }

    private fun initViews() {
        btnPlayPause = findViewById(R.id.btn_play_pause)
        waveformView = findViewById(R.id.player_waveform)
        txtCurrentTime = findViewById(R.id.txt_current_time)
        txtTotalTime = findViewById(R.id.txt_total_time)
        btnSpeed = findViewById(R.id.btn_speed)
        imgCover = findViewById(R.id.player_img_cover)
        txtTitle = findViewById(R.id.player_txt_title)
        txtPodcast = findViewById(R.id.player_txt_podcast)
        bannerContainer = findViewById(R.id.banner_container)
        btnSleepTimer = findViewById(R.id.btn_sleep_timer)
    }

    private fun loadDataFromIntent() {
        val title = intent.getStringExtra("title") ?: ""
        val podcastName = intent.getStringExtra("podcast_name") ?: getString(R.string.default_podcast_artist)
        val cover = intent.getStringExtra("cover") ?: ""

        txtTitle.text = title
        txtPodcast.text = podcastName
        Glide.with(this).load(cover).placeholder(R.drawable.placeholder_podcast).into(imgCover)
    }

    @OptIn(UnstableApi::class)
    private fun loadDataFromManager() {
        val podcast = PlayerManager.currentPodcast
        txtTitle.text = PlayerManager.currentEpisodeTitle ?: ""
        txtPodcast.text = podcast?.artist ?: ""
        if (!podcast?.thumbnail_url.isNullOrEmpty()) {
            Glide.with(this).load(podcast?.thumbnail_url).into(imgCover)
        }
        exoPlayer = PlayerManager.exoPlayer
        updateProgress()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @OptIn(UnstableApi::class)
    private fun setupPlayer(url: String) {
        if (PlayerManager.exoPlayer == null) {
            PlayerManager.initPlayer(this)
        }
        exoPlayer = PlayerManager.exoPlayer

        val intentArtist = intent.getStringExtra("podcast_name")
        val validatedArtist = if (!intentArtist.isNullOrEmpty() && intentArtist != getString(R.string.default_podcast_artist)) {
            intentArtist
        } else {
            txtPodcast.text.toString()
        }

        val coverUrl = intent.getStringExtra("cover") ?: ""
        val title = intent.getStringExtra("title") ?: intent.getStringExtra("podcast_title") ?: ""

        PlayerManager.currentPodcast = Podcast(
            id = intent.getStringExtra("podcast_id") ?: "temp_id",
            title = title,
            artist = validatedArtist,
            thumbnail_url = coverUrl
        )
        PlayerManager.currentEpisodeTitle = title

        val mediaMetadata = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(validatedArtist)
            .setArtworkUri(Uri.parse(coverUrl))
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(mediaMetadata)
            .build()

        val currentUriStr = exoPlayer?.currentMediaItem?.localConfiguration?.uri.toString()

        if (currentUriStr != url) {
            exoPlayer?.apply {
                setMediaItem(mediaItem)
                prepare()
                var resumePos = intent.getLongExtra("resume_position", 0L)
                if (resumePos == 0L) resumePos = prefHelper.getContinuePosition(url)
                if (resumePos > 0) seekTo(resumePos)
                play()
            }
        } else {
            if (exoPlayer?.isPlaying == false) {
                if (exoPlayer?.playbackState == Player.STATE_IDLE || exoPlayer?.playbackState == Player.STATE_ENDED) {
                    exoPlayer?.prepare()
                }
                exoPlayer?.play()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @OptIn(UnstableApi::class)
    private fun setupClickListeners() {
        findViewById<ImageButton>(R.id.btn_back_common).setOnClickListener { finish() }
        btnPlayPause.setOnClickListener { togglePlay() }
        btnSpeed.setOnClickListener { changeSpeed() }

        findViewById<ImageView>(R.id.btn_rewind_15).setOnClickListener {
            val newPos = (exoPlayer?.currentPosition ?: 0) - 15000
            exoPlayer?.seekTo((if (newPos < 0) 0 else newPos).coerceAtLeast(0))
        }

        findViewById<ImageView>(R.id.btn_forward_30).setOnClickListener {
            val newPos = (exoPlayer?.currentPosition ?: 0) + 30000
            exoPlayer?.seekTo(newPos)
        }

        waveformView.onProgressChanged = { progress ->
            exoPlayer?.let {
                if (it.duration > 0) it.seekTo((progress * it.duration).toLong())
            }
        }

        findViewById<ImageButton>(R.id.btn_menu_common).setOnClickListener {
            val audioUrl = intent.getStringExtra("audio_url") ?: PlayerManager.exoPlayer?.currentMediaItem?.localConfiguration?.uri.toString()
            val currentEpisode = RssItem(
                title = txtTitle.text.toString(),
                audioUrl = audioUrl,
                imageUrl = PlayerManager.currentPodcast?.thumbnail_url
            )
            showPlayerMenu(it, audioUrl, currentEpisode, currentEpisode.imageUrl ?: "")
        }

        btnSleepTimer.setOnClickListener { showSleepTimerDialog() }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @OptIn(UnstableApi::class)
    private fun togglePlay() {
        val player = playerController ?: exoPlayer
        if (player == null) {
            PlayerManager.initPlayer(this)
            return
        }

        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        if (player.isPlaying) player.pause() else player.play()
    }

    private fun changeSpeed() {
        currentSpeed = when (currentSpeed) {
            1.0f -> 1.5f
            1.5f -> 2.0f
            else -> 1.0f
        }
        exoPlayer?.playbackParameters = PlaybackParameters(currentSpeed)
        btnSpeed.text = "${currentSpeed}x"
    }

    private fun updateProgress() {
        val player = playerController ?: exoPlayer
        player?.let {
            val currentPos = it.currentPosition
            val duration = it.duration
            if (duration > 0) {
                waveformView.progress = currentPos.toFloat() / duration.toFloat()
                txtTotalTime.text = formatTime(duration)
            }
            txtCurrentTime.text = formatTime(currentPos)
            btnPlayPause.setImageResource(if (it.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow)
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val hr = totalSec / 3600
        val min = (totalSec % 3600) / 60
        val sec = totalSec % 60
        return if (hr > 0) String.format("%d:%02d:%02d", hr, min, sec)
        else String.format("%02d:%02d", min, sec)
    }

    private fun downloadAudio(episode: RssItem) {
        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = Uri.parse(episode.audioUrl)
        val fileName = FileUtils.getSafeFileName(episode.title)
        val request = DownloadManager.Request(uri)
            .setTitle(getString(R.string.download_title_prefix, episode.title))
            .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_MUSIC, fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        downloadManager.enqueue(request)
        Toast.makeText(this, getString(R.string.toast_download_started), Toast.LENGTH_SHORT).show()
    }

    @OptIn(UnstableApi::class)
    private fun showPlayerMenu(view: View, audioUrl: String, currentEpisode: RssItem, cover: String) {
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_episode_menu, null)
        bottomSheetDialog.setContentView(dialogView)

        val txtMenuTitle = dialogView.findViewById<TextView>(R.id.txt_menu_episode_title)
        val txtDownload = dialogView.findViewById<TextView>(R.id.txt_menu_download)
        val txtBookmark = dialogView.findViewById<TextView>(R.id.txt_menu_bookmark)
        val imgBookmarkIcon = dialogView.findViewById<ImageView>(R.id.img_menu_bookmark_icon)

        txtMenuTitle.text = currentEpisode.title ?: txtTitle.text.toString()

        val isBookmarked = prefHelper.isBookmarked(audioUrl)
        txtBookmark.text = getString(if (isBookmarked) R.string.menu_remove_bookmark else R.string.menu_add_bookmark)
        imgBookmarkIcon.setImageResource(if (isBookmarked) R.drawable.ic_bookmark else R.drawable.ic_bookmark_border)

        val downloadedEpisodes = prefHelper.getDownloadedEpisodes().toMutableList()
        val expectedName = FileUtils.getSafeFileName(currentEpisode.title)
        val localFile = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), expectedName)
        val isDownloaded = downloadedEpisodes.any { it.title == currentEpisode.title } || localFile.exists()

        txtDownload.text = getString(if (isDownloaded) R.string.menu_delete_download else R.string.menu_download_episode)

        dialogView.findViewById<View>(R.id.item_menu_share).setOnClickListener {
            val shareLink = "https://jeydivfx.github.io/avapod-share/?audio=${Base64.encodeToString(audioUrl.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)}&title=${java.net.URLEncoder.encode(txtTitle.text.toString(), "UTF-8")}&cover=${java.net.URLEncoder.encode(PlayerManager.currentPodcast?.thumbnail_url ?: "", "UTF-8")}"
            val shareMessage = "🎧 آواپاد | تجربه متفاوت شنیدن\n\n🎙️ اپیزود: ${txtTitle.text}\n👤 پادکست: ${txtPodcast.text}\n\nبرای گوش دادنِ مستقیم در آواپاد، روی لینک زیر بزن:\n$shareLink"
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, shareMessage) }, "اشتراک‌گذاری با..."))
            bottomSheetDialog.dismiss()
        }

        dialogView.findViewById<View>(R.id.item_menu_download).setOnClickListener {
            if (isDownloaded) {
                localFile.delete()
                prefHelper.saveDownloadedEpisode(currentEpisode) // Note: Simplified logic here
                Toast.makeText(this, getString(R.string.delete_from_memory), Toast.LENGTH_SHORT).show()
            } else {
                playerController?.pause() ?: exoPlayer?.pause()
                com.avapod.app.utils.AdManager.showRewardedAd(this) {
                    downloadAudio(currentEpisode)
                    prefHelper.saveDownloadedEpisode(currentEpisode)
                }
            }
            bottomSheetDialog.dismiss()
        }

        dialogView.findViewById<View>(R.id.item_menu_bookmark).setOnClickListener {
            if (prefHelper.isBookmarked(audioUrl)) {
                prefHelper.removeBookmark(audioUrl)
                Toast.makeText(this, getString(R.string.toast_bookmark_removed), Toast.LENGTH_SHORT).show()
            } else {
                prefHelper.addBookmark(currentEpisode.copy(duration = formatTime(exoPlayer?.duration ?: 0L)), cover)
                Toast.makeText(this, getString(R.string.toast_bookmark_added), Toast.LENGTH_SHORT).show()
            }
            bottomSheetDialog.dismiss()
        }
        bottomSheetDialog.show()
    }

    @OptIn(UnstableApi::class)
    override fun onPause() {
        super.onPause()
        exoPlayer?.let { player ->
            if (player.currentPosition > 0) {
                prefHelper.saveContinueListening(RssItem(
                    title = txtTitle.text.toString(),
                    audioUrl = player.currentMediaItem?.localConfiguration?.uri.toString(),
                    imageUrl = PlayerManager.currentPodcast?.thumbnail_url,
                    duration = formatTime(player.duration),
                    artist = txtPodcast.text.toString()
                ), player.currentPosition)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateProgressAction)
        try { unregisterReceiver(sleepTimerReceiver) } catch (e: Exception) { e.printStackTrace() }
        exoPlayer = null
    }

    private fun showSleepTimerDialog() {
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_player_sleep_timer, null)
        bottomSheetDialog.setContentView(dialogView)
        dialogView.findViewById<TextView>(R.id.timer_off).setOnClickListener {
            com.avapod.app.utils.SleepTimerManager.stopTimer()
            updateSleepTimerUI()
            Toast.makeText(this, R.string.sleep_timer_deactivated, Toast.LENGTH_SHORT).show()
            bottomSheetDialog.dismiss()
        }
        val timerOptions = mapOf(R.id.timer_15 to 15, R.id.timer_30 to 30, R.id.timer_60 to 60)
        for ((viewId, mins) in timerOptions) {
            dialogView.findViewById<TextView>(viewId).setOnClickListener {
                com.avapod.app.utils.SleepTimerManager.startTimer(this, mins, { _ -> }, { runOnUiThread { updateSleepTimerUI() } })
                updateSleepTimerUI()
                Toast.makeText(this, getString(R.string.player_timer_set, mins), Toast.LENGTH_SHORT).show()
                bottomSheetDialog.dismiss()
            }
        }
        bottomSheetDialog.show()
    }

    @OptIn(UnstableApi::class)
    override fun onStart() {
        super.onStart()
        val sessionToken = SessionToken(this, ComponentName(this, com.avapod.app.PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture?.addListener({
            playerController = controllerFuture?.get()
            updateUIFromController()
        }, MoreExecutors.directExecutor())
    }

    override fun onStop() {
        super.onStop()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        playerController = null
    }

    private fun updateUIFromController() {
        playerController?.let { controller ->
            controller.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) = updateProgress()
                override fun onIsPlayingChanged(isPlaying: Boolean) = updateProgress()
            })
            updateProgress()
        }
    }

    private fun updateSleepTimerUI() {
        val isActive = com.avapod.app.utils.SleepTimerManager.isTimerRunning
        btnSleepTimer.imageTintList = android.content.res.ColorStateList.valueOf(
            getColor(if (isActive) R.color.timer_accent_blue else R.color.text_white)
        )
    }

}