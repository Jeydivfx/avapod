package com.avapod.app

import android.annotation.SuppressLint
import android.app.DownloadManager
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
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.bumptech.glide.Glide
import com.avapod.app.models.Podcast
import com.avapod.app.models.RssItem
import com.avapod.app.utils.PreferenceHelper
import com.google.android.gms.ads.AdView
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.avapod.app.utils.FileUtils
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

    //AdMob banner
    private var adView: AdView? = null
    private lateinit var bannerContainer: FrameLayout

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

    @SuppressLint("ResourceAsColor")
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.R.color.transparent,
                android.R.color.transparent
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.R.color.transparent,
                android.R.color.transparent
            )
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

    private fun loadDataFromManager() {
        val podcast = PlayerManager.currentPodcast
        txtTitle.text = PlayerManager.currentEpisodeTitle
        txtPodcast.text = podcast?.artist
        Glide.with(this).load(podcast?.thumbnail_url).into(imgCover)

        exoPlayer = PlayerManager.exoPlayer
    }

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

        PlayerManager.currentPodcast = Podcast(
            id = intent.getStringExtra("podcast_id") ?: "temp_id",
            title = intent.getStringExtra("podcast_title") ?: intent.getStringExtra("title") ?: "",
            artist = validatedArtist,
            thumbnail_url = intent.getStringExtra("cover") ?: ""
        )
        PlayerManager.currentEpisodeTitle = intent.getStringExtra("title") ?: ""

        val currentUriStr = exoPlayer?.currentMediaItem?.localConfiguration?.uri.toString()
        if (currentUriStr != url) {
            val mediaItem = MediaItem.fromUri(url)
            exoPlayer?.apply {
                setMediaItem(mediaItem)
                prepare()

                var resumePos = intent.getLongExtra("resume_position", 0L)
                if (resumePos == 0L) {
                    resumePos = prefHelper.getContinuePosition(url)
                }

                if (resumePos > 0) {
                    seekTo(resumePos)
                }
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

        if (exoPlayer?.currentMediaItem?.localConfiguration?.uri.toString() != url) {
            val mediaItem = MediaItem.fromUri(url)
            exoPlayer?.apply {
                setMediaItem(mediaItem)
                prepare()

                var resumePos = intent.getLongExtra("resume_position", 0L)

                if (resumePos == 0L) {
                    resumePos = prefHelper.getContinuePosition(url)
                }

                if (resumePos > 0) {
                    seekTo(resumePos)
                }

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

    private fun setupClickListeners() {
        val btnClose = findViewById<ImageButton>(R.id.btn_back_common)
        val btnMore = findViewById<ImageButton>(R.id.btn_menu_common)
        val btnRewind = findViewById<ImageView>(R.id.btn_rewind_15)
        val btnForward = findViewById<ImageView>(R.id.btn_forward_30)

        btnClose.setOnClickListener { finish() }
        btnPlayPause.setOnClickListener { togglePlay() }
        btnSpeed.setOnClickListener { changeSpeed() }

        btnRewind.setOnClickListener {
            val newPos = (exoPlayer?.currentPosition ?: 0) - 15000
            exoPlayer?.seekTo(if (newPos < 0) 0 else newPos)
        }

        btnForward.setOnClickListener {
            val newPos = (exoPlayer?.currentPosition ?: 0) + 30000
            exoPlayer?.seekTo(newPos)
        }

        waveformView.onProgressChanged = { progress ->
            exoPlayer?.let {
                val duration = it.duration
                if (duration > 0) {
                    it.seekTo((progress * duration).toLong())
                }
            }
        }

        btnMore.setOnClickListener {
            val audioUrl = intent.getStringExtra("audio_url") ?: PlayerManager.exoPlayer?.currentMediaItem?.localConfiguration?.uri.toString()
            val currentEpisode = RssItem(
                title = txtTitle.text.toString(),
                audioUrl = audioUrl,
                imageUrl = PlayerManager.currentPodcast?.thumbnail_url
            )
            showPlayerMenu(it, audioUrl, currentEpisode, currentEpisode.imageUrl ?: "")
        }

        btnSleepTimer.setOnClickListener {
            showSleepTimerDialog()
        }

    }

    private fun togglePlay() {
        exoPlayer?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
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
        exoPlayer?.let {
            val currentPos = it.currentPosition
            val duration = it.duration

            if (duration > 0) {
                waveformView.progress = currentPos.toFloat() / duration.toFloat()
                txtTotalTime.text = formatTime(duration)
            }
            txtCurrentTime.text = formatTime(currentPos)

            if (it.isPlaying) {
                btnPlayPause.setImageResource(R.drawable.ic_pause)
            } else {
                btnPlayPause.setImageResource(R.drawable.ic_play_arrow)
            }
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

    private fun showPlayerMenu(view: View, audioUrl: String, currentEpisode: RssItem, cover: String) {
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_episode_menu, null)
        bottomSheetDialog.setContentView(dialogView)

        val txtMenuTitle = dialogView.findViewById<TextView>(R.id.txt_menu_episode_title)
        val itemDownload = dialogView.findViewById<View>(R.id.item_menu_download)
        val txtDownload = dialogView.findViewById<TextView>(R.id.txt_menu_download)
        val itemBookmark = dialogView.findViewById<View>(R.id.item_menu_bookmark)
        val txtBookmark = dialogView.findViewById<TextView>(R.id.txt_menu_bookmark)
        val imgBookmarkIcon = dialogView.findViewById<ImageView>(R.id.img_menu_bookmark_icon)


        txtMenuTitle.text = currentEpisode.title ?: txtTitle.text.toString()
        val itemShare = dialogView.findViewById<View>(R.id.item_menu_share)

        txtMenuTitle.text = currentEpisode.title ?: txtTitle.text.toString()

        val isBookmarked = prefHelper.isBookmarked(audioUrl)
        if (isBookmarked) {
            txtBookmark.text = getString(R.string.menu_remove_bookmark)
            imgBookmarkIcon.setImageResource(R.drawable.ic_bookmark)
        } else {
            txtBookmark.text = getString(R.string.menu_add_bookmark)
            imgBookmarkIcon.setImageResource(R.drawable.ic_bookmark_border)
        }

        val downloadedEpisodes = prefHelper.getDownloadedEpisodes().toMutableList()
        val expectedName = FileUtils.getSafeFileName(currentEpisode.title)
        val downloadFolder = getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        val localFile = File(downloadFolder, expectedName)

        val isDownloaded = downloadedEpisodes.any { it.title == currentEpisode.title } || localFile.exists()

        txtDownload.text = if (isDownloaded) {
            getString(R.string.menu_delete_download)
        } else {
            getString(R.string.menu_download_episode)
        }

        itemShare.setOnClickListener {

            val episodeTitle = txtTitle.text.toString()
            val podcastName = txtPodcast.text.toString()
            val coverUrl = PlayerManager.currentPodcast?.thumbnail_url ?: ""
            val audioUrl = audioUrl

            val audioB64 = Base64.encodeToString(audioUrl.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)
            val encodedTitle = java.net.URLEncoder.encode(episodeTitle, "UTF-8")
            val encodedCover = java.net.URLEncoder.encode(coverUrl, "UTF-8")
            val shareLink = "https://jeydivfx.github.io/avapod-share/?audio=$audioB64&title=$encodedTitle&cover=$encodedCover"

            val shareMessage = """
        🎧 آواپاد | تجربه متفاوت شنیدن
        
        🎙️ اپیزود: $episodeTitle
        👤 پادکست: $podcastName
        
        برای گوش دادنِ مستقیم در آواپاد، روی لینک زیر بزن:
        $shareLink
    """.trimIndent()


            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareMessage)
            }

            startActivity(Intent.createChooser(shareIntent, "اشتراک‌گذاری با..."))

            bottomSheetDialog.dismiss()
        }


        itemDownload.setOnClickListener {
            if (isDownloaded) {
                if (downloadFolder != null && downloadFolder.exists() && expectedName.isNotEmpty()) {
                    val files = downloadFolder.listFiles()
                    if (files != null) {
                        for (file in files) {
                            if (file.isFile && file.name.contains(expectedName, ignoreCase = true)) {
                                file.delete()
                            }
                        }
                    }
                }

                val updatedDownloads = downloadedEpisodes.filterNot {
                    it.title == currentEpisode.title || it.audioUrl == audioUrl
                }

                prefHelper.clearAllDownloads()
                updatedDownloads.forEach { episode ->
                    prefHelper.saveDownloadedEpisode(episode)
                }

                val removeMessage = getString(R.string.delete_from_memory) ?: "از حافظه و لیست دانلودها حذف شد"
                Toast.makeText(this, removeMessage, Toast.LENGTH_SHORT).show()

            } else {
                exoPlayer?.pause()
                com.avapod.app.utils.AdManager.showRewardedAd(this) {
                    downloadAudio(currentEpisode)
                    prefHelper.saveDownloadedEpisode(currentEpisode)
                    downloadedEpisodes.add(currentEpisode)
                    exoPlayer?.play()
                }
            }
            bottomSheetDialog.dismiss()
        }

        itemBookmark.setOnClickListener {
            if (prefHelper.isBookmarked(audioUrl)) {
                prefHelper.removeBookmark(audioUrl)
                Toast.makeText(this, getString(R.string.toast_bookmark_removed), Toast.LENGTH_SHORT).show()
            } else {
                val time = intent.getStringExtra("duration") ?: formatTime(exoPlayer?.duration ?: 0L)
                val episodeToSave = RssItem(
                    title = currentEpisode.title ?: txtTitle.text.toString(),
                    audioUrl = audioUrl,
                    imageUrl = cover,
                    duration = time
                )
                prefHelper.addBookmark(episodeToSave, cover)
                Toast.makeText(this, getString(R.string.toast_bookmark_added), Toast.LENGTH_SHORT).show()
            }
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.setOnShowListener {
            val bottomSheetContainer = dialogView.parent as? View
            bottomSheetContainer?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }

        bottomSheetDialog.show()
    }

    override fun onPause() {
        super.onPause()
        exoPlayer?.let { player ->
            if (player.currentPosition > 0) {
                val durationStr = formatTime(player.duration)

                val currentEpisode = RssItem(
                    title = txtTitle.text.toString(),
                    audioUrl = PlayerManager.exoPlayer?.currentMediaItem?.localConfiguration?.uri.toString(),
                    imageUrl = PlayerManager.currentPodcast?.thumbnail_url,
                    duration = durationStr,
                    artist = txtPodcast.text.toString()
                )
                prefHelper.saveContinueListening(currentEpisode, player.currentPosition)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateProgressAction)

        try {
            unregisterReceiver(sleepTimerReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }

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

        val timerOptions = mapOf(
            R.id.timer_15 to 15,
            R.id.timer_30 to 30,
            R.id.timer_60 to 60
        )

        for ((viewId, mins) in timerOptions) {
            dialogView.findViewById<TextView>(viewId).setOnClickListener {

                com.avapod.app.utils.SleepTimerManager.startTimer(
                    context = this,
                    minutes = mins,
                    onTick = { _ -> },
                    onFinish = {
                        runOnUiThread { updateSleepTimerUI() }
                    }
                )

                updateSleepTimerUI()
                val message = getString(R.string.player_timer_set, mins)
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                bottomSheetDialog.dismiss()
            }
        }

        bottomSheetDialog.setOnShowListener {
            val bottomSheetContainer = dialogView.parent as? View
            bottomSheetContainer?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        bottomSheetDialog.show()
    }

    private fun updateSleepTimerUI() {
        val isActive = com.avapod.app.utils.SleepTimerManager.isTimerRunning
        val color = if (isActive) getColor(R.color.timer_accent_blue) else getColor(R.color.text_white)
        btnSleepTimer.imageTintList = android.content.res.ColorStateList.valueOf(color)
    }




}