package com.avapod.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.avapod.app.Fragments.*
import com.avapod.app.utils.UrlValidator
import com.bumptech.glide.Glide
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    private lateinit var connection: PlaybackConnection
    private lateinit var miniPlayer: View
    private lateinit var playButton: ImageButton
    private lateinit var progress: ProgressBar
    private lateinit var cover: ImageView
    private lateinit var title: TextView
    private lateinit var artist: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var displayedCover: String? = null
    private var lastBackPress = 0L
    private var keyboardVisible = false
    private val tick = object : Runnable {
        override fun run() { renderProgress(); handler.postDelayed(this, 500) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val barStyle = if (resources.getBoolean(R.bool.light_system_bars)) {
            SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
        val nav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_root_view)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            keyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            nav.visibility = if (keyboardVisible) View.GONE else View.VISIBLE
            if (::miniPlayer.isInitialized && ::connection.isInitialized) renderMiniPlayer()
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
            WindowInsetsCompat.CONSUMED
        }
        miniPlayer = findViewById(R.id.include_mini_player)
        cover = miniPlayer.findViewById(R.id.img_mini_player_cover)
        title = miniPlayer.findViewById(R.id.txt_mini_player_title)
        artist = miniPlayer.findViewById(R.id.txt_mini_player_podcast_name)
        progress = miniPlayer.findViewById(R.id.mini_player_progress)
        playButton = miniPlayer.findViewById(R.id.btn_mini_player_play_pause)
        connection = PlaybackConnection(this, { renderMiniPlayer() }, { renderMiniPlayer() },
            { Toast.makeText(this, R.string.playback_connection_error, Toast.LENGTH_SHORT).show() })
        playButton.setOnClickListener { connection.run { PlayerManager.toggle(it) } }
        miniPlayer.findViewById<ImageButton>(R.id.btn_mini_player_close).setOnClickListener {
            connection.run { it.pause(); it.clearMediaItems() }
        }
        miniPlayer.setOnClickListener { startActivity(Intent(this, PlayerActivity::class.java)) }
        nav.setOnItemSelectedListener {
            if (supportFragmentManager.isStateSaved) false else {
                supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
                val fragment: Fragment = when (it.itemId) {
                    R.id.nav_library -> LibraryFragment()
                    R.id.nav_explore -> ExploreFragment()
                    R.id.nav_about -> SettingsFragment()
                    else -> HomeFragment()
                }
                supportFragmentManager.beginTransaction().replace(R.id.nav_host_fragment, fragment).commit()
                true
            }
        }
        nav.setOnItemReselectedListener { /* Preserve scroll state and current view. */ }
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().replace(R.id.nav_host_fragment, HomeFragment()).commit()
            handleDeepLink(intent)
        }
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (supportFragmentManager.backStackEntryCount > 0) supportFragmentManager.popBackStack()
                else if (nav.selectedItemId != R.id.nav_home) nav.selectedItemId = R.id.nav_home
                else if (android.os.SystemClock.elapsedRealtime() - lastBackPress < 2000) finish()
                else { lastBackPress = android.os.SystemClock.elapsedRealtime(); Toast.makeText(this@MainActivity, R.string.exit_message, Toast.LENGTH_SHORT).show() }
            }
        })
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleDeepLink(intent) }
    private fun handleDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (!data.isHierarchical || data.scheme != "avapad" || data.host != "play") return
        val source = runCatching {
            val encoded = requireNotNull(data.getQueryParameter("audio"))
            require(encoded.length <= 16384)
            String(Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
        }.getOrNull()
        if (!UrlValidator.isWebUrl(source)) {
            Toast.makeText(this, R.string.invalid_audio_link, Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putExtra("audio_url", source)
            putExtra("title", data.getQueryParameter("title")?.take(500))
            putExtra("cover", data.getQueryParameter("cover")?.takeIf(UrlValidator::isWebUrl))
            putExtra("podcast_name", getString(R.string.app_name))
        })
    }
    private fun renderMiniPlayer() {
        val player = connection.controller ?: run {
            miniPlayer.visibility = View.GONE
            return
        }
        val item = player.currentMediaItem
        miniPlayer.visibility = if (item == null || keyboardVisible) View.GONE else View.VISIBLE
        if (item == null) return
        title.text = item.mediaMetadata.title ?: ""
        artist.text = item.mediaMetadata.artist ?: ""
        val url = item.mediaMetadata.artworkUri?.toString()
        if (displayedCover != url) {
            displayedCover = url
            Glide.with(this).load(url).placeholder(R.drawable.placeholder_podcast).into(cover)
        }
        val playing = player.playWhenReady && player.playbackState != androidx.media3.common.Player.STATE_ENDED &&
            player.playbackState != androidx.media3.common.Player.STATE_IDLE && player.playerError == null
        playButton.setImageResource(if (playing) R.drawable.ic_pause_circle_filled else R.drawable.ic_play_circle_filled)
        playButton.contentDescription = getString(if (playing) R.string.accessibility_pause else R.string.accessibility_play)
        renderProgress()
    }
    private fun renderProgress() {
        val player = connection.controller ?: return
        progress.progress = if (player.duration > 0) ((player.currentPosition * 100 / player.duration).toInt()).coerceIn(0, 100) else 0
    }
    override fun onStart() { super.onStart(); connection.start(); handler.post(tick) }
    override fun onStop() { handler.removeCallbacks(tick); connection.stop(); super.onStop() }
}
