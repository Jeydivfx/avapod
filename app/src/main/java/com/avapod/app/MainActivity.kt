package com.avapod.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.media3.common.Player
import com.avapod.app.Fragments.ExploreFragment
import com.avapod.app.Fragments.HomeFragment
import com.avapod.app.Fragments.LibraryFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.bumptech.glide.Glide
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
<<<<<<< HEAD
import com.avapod.app.Fragments.AboutFragment
import android.util.Base64
=======
import com.avapod.app.Fragments.CalendarFragment
>>>>>>> d211ee2b997d4e0d4f8b0e5e734b0f33ab6f3151

class MainActivity : AppCompatActivity() {

    private lateinit var miniPlayerContainer: View
    private lateinit var imgCover: ImageView
    private lateinit var txtTitle: TextView
    private lateinit var txtPodcastNameTop: TextView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnClose: ImageButton

    private lateinit var miniPlayerProgress: ProgressBar
    private val progressHandler = Handler(Looper.getMainLooper())

    private val sleepTimerMiniPlayerReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: android.content.Intent?) {
            if (intent?.action == "ACTION_AVAPOD_SLEEP_TIMER_FORCED_PAUSE") {

                PlayerManager.exoPlayer?.pause()
                updatePlayPauseIcon(false)
                progressHandler.removeCallbacks(updateProgressRunnable)

            }
        }
    }

    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            updateMiniPlayerProgress()
            progressHandler.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_main)

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


<<<<<<< HEAD
        handleDeepLink(intent)
=======
>>>>>>> d211ee2b997d4e0d4f8b0e5e734b0f33ab6f3151

        val mainView = findViewById<View>(R.id.nav_host_fragment)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)


        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { view, insets ->
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())

            val params = view.layoutParams as ViewGroup.MarginLayoutParams
            params.bottomMargin = navBar.bottom
            view.layoutParams = params

            insets
        }

        PlayerManager.initPlayer(this)
        initMiniPlayer()
        setupBottomNavigation()

        var doubleBackToExitPressedOnce = false


        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                } else {
                    if (bottomNav.selectedItemId != R.id.nav_home) {
                        bottomNav.selectedItemId = R.id.nav_home
                    } else {
                        if (doubleBackToExitPressedOnce) {
                            finish()
                            return
                        }

                        doubleBackToExitPressedOnce = true
                        Toast.makeText(this@MainActivity, R.string.exit_message, Toast.LENGTH_SHORT).show()

                        Handler(Looper.getMainLooper()).postDelayed({
                            doubleBackToExitPressedOnce = false
                        }, 2000)
                    }
                }
            }
        })

        if (savedInstanceState == null) {
            loadFragment(HomeFragment())
        }

        val filter = android.content.IntentFilter("ACTION_AVAPOD_SLEEP_TIMER_FORCED_PAUSE")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(sleepTimerMiniPlayerReceiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(sleepTimerMiniPlayerReceiver, filter)
        }
    }

<<<<<<< HEAD

    private fun handleDeepLink(intent: Intent?) {
        val data = intent?.data
        if (data != null && data.scheme == "avapad" && data.host == "play") {
            val audioB64 = data.getQueryParameter("audio")
            val title = data.getQueryParameter("title")
            val cover = data.getQueryParameter("cover")

            if (audioB64 != null) {
                val audioUrl = String(Base64.decode(audioB64, Base64.URL_SAFE or Base64.NO_WRAP))

                val playerIntent = Intent(this, PlayerActivity::class.java).apply {
                    putExtra("audio_url", audioUrl)
                    putExtra("title", title)
                    putExtra("cover", cover)
                    putExtra("podcast_name", "آواپاد")
                }
                startActivity(playerIntent)
            }
        }
    }

=======
>>>>>>> d211ee2b997d4e0d4f8b0e5e734b0f33ab6f3151
    private fun initMiniPlayer() {
        miniPlayerContainer = findViewById(R.id.include_mini_player)
        imgCover = miniPlayerContainer.findViewById(R.id.img_mini_player_cover)
        txtTitle = miniPlayerContainer.findViewById(R.id.txt_mini_player_title)
        txtPodcastNameTop = miniPlayerContainer.findViewById(R.id.txt_mini_player_podcast_name)
        miniPlayerProgress = miniPlayerContainer.findViewById(R.id.mini_player_progress)
        btnPlayPause = miniPlayerContainer.findViewById(R.id.btn_mini_player_play_pause)
        btnClose = miniPlayerContainer.findViewById(R.id.btn_mini_player_close)

        btnClose.setOnClickListener {
            progressHandler.removeCallbacks(updateProgressRunnable)
            PlayerManager.exoPlayer?.stop()
            PlayerManager.currentPodcast = null
            miniPlayerContainer.visibility = View.GONE
        }

        PlayerManager.exoPlayer?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlayPauseIcon(isPlaying)
                if (isPlaying) {
                    progressHandler.post(updateProgressRunnable)
                } else {
                    progressHandler.removeCallbacks(updateProgressRunnable)
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    updateMiniPlayerUI()
                    if (PlayerManager.isPlaying()) {
                        progressHandler.post(updateProgressRunnable)
                    }
                } else if (state == Player.STATE_ENDED) {
                    progressHandler.removeCallbacks(updateProgressRunnable)
                    miniPlayerProgress.progress = 0
                }
            }
        })

        btnPlayPause.setOnClickListener { PlayerManager.togglePlayPause() }
        miniPlayerContainer.setOnClickListener {
            val intent = Intent(this, PlayerActivity::class.java)
            startActivity(intent)
        }
        updateMiniPlayerUI()
    }

    private fun updateMiniPlayerUI() {

        if (isFinishing || isDestroyed) {
            return
        }

        val podcast = PlayerManager.currentPodcast
        if (podcast != null) {
            miniPlayerContainer.visibility = View.VISIBLE
            txtTitle.text = PlayerManager.currentEpisodeTitle ?: podcast.title

            if (!podcast.artist.isNullOrEmpty()) {
                txtPodcastNameTop.text = podcast.artist
            } else {
                txtPodcastNameTop.text = getString(R.string.default_podcast_artist)
            }

            Glide.with(this)
                .load(podcast.thumbnail_url)
                .placeholder(R.drawable.placeholder_podcast)
                .dontAnimate()
                .into(imgCover)

            updatePlayPauseIcon(PlayerManager.isPlaying())

            if (PlayerManager.isPlaying()) {
                progressHandler.post(updateProgressRunnable)
            }
        } else {
            miniPlayerContainer.visibility = View.GONE
        }
    }

    private fun updateMiniPlayerProgress() {
        PlayerManager.exoPlayer?.let { player ->
            if (player.isPlaying && player.duration > 0) {
                val currentPos = player.currentPosition
                val duration = player.duration
                miniPlayerProgress.progress = ((currentPos * 100) / duration).toInt()
            }
        }
    }

    private fun updatePlayPauseIcon(isPlaying: Boolean) {
        if (isPlaying) {
            btnPlayPause.setImageResource(R.drawable.ic_pause_circle_filled)
        } else {
            btnPlayPause.setImageResource(R.drawable.ic_play_circle_filled)
        }
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        val params = bottomNav.layoutParams
        params.height = 56.dpToPx()
        bottomNav.layoutParams = params

        bottomNav.setOnItemSelectedListener { item ->
            executeNavigation(item.itemId)
            true
        }

        bottomNav.setOnItemReselectedListener { item ->
            executeNavigation(item.itemId)
        }
    }

    // Extension function برای تبدیل dp به px
    fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private fun executeNavigation(itemId: Int) {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }

        val fragment = when (itemId) {
            R.id.nav_home -> HomeFragment()
            R.id.nav_library -> LibraryFragment()
            R.id.nav_explore -> ExploreFragment()
<<<<<<< HEAD
            R.id.nav_calendar -> AboutFragment()
=======
            R.id.nav_calendar -> CalendarFragment()
>>>>>>> d211ee2b997d4e0d4f8b0e5e734b0f33ab6f3151
            else -> null
        }

        fragment?.let { loadFragment(it) }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.executePendingTransactions()
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .commitAllowingStateLoss()
    }

    override fun onResume() {
        super.onResume()
        if (PlayerManager.exoPlayer != null && PlayerManager.currentPodcast != null) {
            miniPlayerContainer.post { updateMiniPlayerUI() }
        }
    }

    override fun onPause() {
        super.onPause()
        progressHandler.removeCallbacks(updateProgressRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(sleepTimerMiniPlayerReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}