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
    private var pendingDetailsReturn = false
    private var pendingPush: Bundle? = null
    private val tick = object : Runnable {
        override fun run() { renderProgress(); handler.postDelayed(this, 500) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingPush = savedInstanceState?.getBundle("pending_push")
        setContentView(R.layout.activity_main)
        val barStyle = if (resources.getBoolean(R.bool.light_system_bars)) {
            SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
        val nav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        // Bring the navigation labels closer to their icons without moving the icons.
        nav.post {
            val menu = nav.getChildAt(0) as? ViewGroup ?: return@post
            val offset = 6f * resources.displayMetrics.density
            fun moveLabels(view: View) {
                if (view is TextView) {
                    view.translationY = -offset
                } else if (view is ViewGroup) {
                    for (index in 0 until view.childCount) {
                        moveLabels(view.getChildAt(index))
                    }
                }
            }
            for (index in 0 until menu.childCount) {
                moveLabels(menu.getChildAt(index))
            }
        }
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
        if (BuildConfig.DEBUG && com.google.firebase.FirebaseApp.getApps(this).isNotEmpty()) {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnSuccessListener { android.util.Log.d("AvapodFCM", "Test token: $it") }
                .addOnFailureListener { android.util.Log.w("AvapodFCM", "Token registration failed", it) }
        }
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (pendingDetailsReturn || supportFragmentManager.isStateSaved) return
                if (supportFragmentManager.backStackEntryCount > 0) {
                    val current = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
                    if (current is PodcastDetailFragment) {
                        val player = connection.controller
                        com.avapod.app.utils.AdManager.onDetailsReturn(this@MainActivity,
                            player != null && !player.playWhenReady && !player.isPlaying) {
                            pendingDetailsReturn = true
                            handler.post { completeDetailsReturn() }
                        }
                    } else supportFragmentManager.popBackStack()
                }
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
    override fun onSaveInstanceState(outState: Bundle) {
        pendingPush?.let { outState.putBundle("pending_push", it) }
        super.onSaveInstanceState(outState)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleDeepLink(intent) }
    private fun handleDeepLink(intent: Intent?) {
        if (intent?.getStringExtra("open_inbox") == "1" || intent?.hasExtra("google.message_id") == true) {
            com.avapod.app.network.PushInbox.save(this,
                intent.getStringExtra("push_id") ?: intent.getStringExtra("google.message_id"),
                intent.getStringExtra("push_title").orEmpty(), intent.getStringExtra("push_body").orEmpty())
            pendingPush = Bundle().apply {
                putString("push_title", intent.getStringExtra("push_title")?.take(200))
                putString("push_body", intent.getStringExtra("push_body")?.take(4000))
            }
            intent.removeExtra("open_inbox")
            intent.removeExtra("google.message_id")
            return
        }
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
    private fun completeDetailsReturn() {
        if (!pendingDetailsReturn || !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) ||
            supportFragmentManager.isStateSaved || isFinishing || isDestroyed) return
        pendingDetailsReturn = false
        if (supportFragmentManager.findFragmentById(R.id.nav_host_fragment) is PodcastDetailFragment &&
            supportFragmentManager.backStackEntryCount > 0) supportFragmentManager.popBackStack()
    }
    override fun onResume() { super.onResume(); com.avapod.app.utils.AdManager.onResume(this) }
    override fun onPostResume() {
        super.onPostResume()
        handler.post {
            completeDetailsReturn()
            if (!supportFragmentManager.isStateSaved && !isFinishing && !isDestroyed) {
                pendingPush?.let { args ->
                    pendingPush = null
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.nav_host_fragment, InboxFragment().apply { arguments = args })
                        .addToBackStack("push_inbox").commit()
                }
            }
        }
    }
    override fun onPause() { com.avapod.app.utils.AdManager.onPause(this); super.onPause() }
    override fun onDestroy() { com.avapod.app.utils.AdManager.onDestroy(); super.onDestroy() }
    override fun onStart() { super.onStart(); connection.start(); handler.post(tick) }
    override fun onStop() { handler.removeCallbacks(tick); connection.stop(); super.onStop() }
}
