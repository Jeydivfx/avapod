package com.avapod.app.utils

import android.content.Context
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.view.View
import android.util.Log
import androidx.core.view.doOnLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import com.appodeal.ads.Appodeal
import com.appodeal.ads.BannerCallbacks
import com.appodeal.ads.InterstitialCallbacks
import com.appodeal.consent.ConsentManager
import com.avapod.app.BuildConfig
import com.avapod.app.R
import java.lang.ref.WeakReference

/** Main-thread only. No launch/exit ads, rewarded ads or delayed interstitial shows. */
object AdManager {
    private val gap = if (BuildConfig.DEBUG) 15_000L else 5 * 60_000L
    private val requiredInteractions = if (BuildConfig.DEBUG) 2 else 5
    private fun trace(message: String) { if (BuildConfig.DEBUG) Log.d("AvapodAds", message) }
    private fun status(message: String) { Log.i("AvapodAds", message) }
    private val sessionStarted = SystemClock.elapsedRealtime()
    private var foreground = WeakReference<FragmentActivity>(null)
    private var initialized = false
    private var initializing = false
    private var interactions = 0
    private var pending: (() -> Unit)? = null
    private var showing = false
    private var lastInterstitialCache = 0L
    private var lastBannerCache = 0L
    private var lastInitAttempt = 0L
    private var bannerVisible = false
    private var bannerObscured = false
    private val handler = Handler(Looper.getMainLooper())
    private val bannerRetry = Runnable { refresh() }

    fun setBannerObscured(activity: FragmentActivity, obscured: Boolean) {
        bannerObscured = obscured
        if (obscured && initialized) {
            Appodeal.hide(activity, Appodeal.BANNER)
            bannerVisible = false
        } else if (!obscured) showBanner()
    }

    private fun enabled(context: Context, kind: String) = context.getSharedPreferences(
        "AvapodAdConfig", Context.MODE_PRIVATE).getBoolean(kind, false)
    private fun active(activity: FragmentActivity) = !activity.isFinishing && !activity.isDestroyed &&
        activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

    fun onResume(activity: FragmentActivity) {
        foreground = WeakReference(activity)
        trace("Resume ${activity.javaClass.simpleName}; testMode=${BuildConfig.DEBUG}")
        activity.findViewById<View>(R.id.banner_container)?.doOnLayout {
            if (foreground.get() === activity) showBanner()
        }
        activity.window.decorView.post { if (foreground.get() === activity) refresh() }
    }
    fun onPause(activity: FragmentActivity) {
        handler.removeCallbacks(bannerRetry)
        if (initialized) Appodeal.hide(activity, Appodeal.BANNER)
        bannerVisible = false
        if (foreground.get() === activity) foreground.clear()
    }
    fun onDestroy() { pending = null; showing = false }

    fun refresh() {
        val activity = foreground.get()?.takeIf(::active) ?: return
        status("Ad config: banner=${enabled(activity, "banner")}; interstitial=${enabled(activity, "interstitial")}; testMode=${BuildConfig.DEBUG}")
        if (!enabled(activity, "banner") && !enabled(activity, "interstitial")) {
            handler.removeCallbacks(bannerRetry)
            trace("Ads disabled by Firebase configuration (or not fetched yet)")
            if (initialized) Appodeal.hide(activity, Appodeal.BANNER)
            bannerVisible = false
            return
        }
        if (!initialized) {
            val now = SystemClock.elapsedRealtime()
            if (initializing || (lastInitAttempt != 0L && now - lastInitAttempt < 60_000)) return
            initializing = true
            lastInitAttempt = now
            Appodeal.setTesting(BuildConfig.DEBUG)
            trace("Initializing banner and interstitial")
            Appodeal.setAutoCache(Appodeal.INTERSTITIAL or Appodeal.BANNER, false)
            Appodeal.setBannerViewId(R.id.appodeal_banner)
            Appodeal.setSmartBanners(false)
            Appodeal.set728x90Banners(false)
            Appodeal.setBannerAnimation(false)
            Appodeal.setInterstitialCallbacks(object : InterstitialCallbacks {
                override fun onInterstitialLoaded(isPrecache: Boolean) { trace("Interstitial loaded") }
                override fun onInterstitialFailedToLoad() { Log.w("AvapodAds", "Interstitial failed to load") }
                override fun onInterstitialShown() { trace("Interstitial shown") }
                override fun onInterstitialClicked() = Unit
                override fun onInterstitialExpired() = Unit
                override fun onInterstitialClosed() = finishReturn()
                override fun onInterstitialShowFailed() { trace("Interstitial show failed"); finishReturn() }
            })
            Appodeal.setBannerCallbacks(object : BannerCallbacks {
                override fun onBannerLoaded(height: Int, isPrecache: Boolean) { trace("Banner loaded; height=$height"); showBanner() }
                override fun onBannerFailedToLoad() { Log.w("AvapodAds", "Banner failed to load") }
                override fun onBannerShown() { trace("Banner shown") }
                override fun onBannerShowFailed() { bannerVisible = false; trace("Banner show failed") }
                override fun onBannerClicked() = Unit
                override fun onBannerExpired() { bannerVisible = false }
            })
            // SDK handles the required consent form before initialization completes.
            Appodeal.initialize(activity, activity.getString(R.string.appodeal_app_key),
                Appodeal.INTERSTITIAL or Appodeal.BANNER) { errors ->
                initializing = false
                initialized = Appodeal.isInitialized(Appodeal.INTERSTITIAL) ||
                    Appodeal.isInitialized(Appodeal.BANNER)
                status("Initialization: banner=${Appodeal.isInitialized(Appodeal.BANNER)}; interstitial=${Appodeal.isInitialized(Appodeal.INTERSTITIAL)}; consentCanShow=${ConsentManager.canShowAds()}")
                if (!errors.isNullOrEmpty()) Log.w("AvapodAds", "Initialization errors: $errors")
                if (initialized) refresh()
            }
            return
        }
        if (!ConsentManager.canShowAds()) {
            status("Requests blocked: consent cannot show ads")
            Appodeal.hide(activity, Appodeal.BANNER)
            bannerVisible = false
            return
        }
        if (!enabled(activity, "banner")) {
            handler.removeCallbacks(bannerRetry)
            Appodeal.hide(activity, Appodeal.BANNER)
            bannerVisible = false
        }
        showBanner()
        val now = SystemClock.elapsedRealtime()
        if (Appodeal.isInitialized(Appodeal.INTERSTITIAL) && enabled(activity, "interstitial") && !Appodeal.isLoaded(Appodeal.INTERSTITIAL) &&
            (lastInterstitialCache == 0L || now - lastInterstitialCache >= 60_000)) {
            lastInterstitialCache = now
            status("Requesting interstitial")
            Appodeal.cache(activity, Appodeal.INTERSTITIAL)
        }
        if (Appodeal.isInitialized(Appodeal.BANNER) && activity.findViewById<View>(R.id.appodeal_banner) != null && enabled(activity, "banner") &&
            !Appodeal.isLoaded(Appodeal.BANNER) && (lastBannerCache == 0L || now - lastBannerCache >= 60_000)) {
            handler.removeCallbacks(bannerRetry)
            lastBannerCache = now
            status("Requesting banner")
            Appodeal.cache(activity, Appodeal.BANNER)
        } else if (Appodeal.isInitialized(Appodeal.BANNER) && activity.findViewById<View>(R.id.appodeal_banner) != null && enabled(activity, "banner") &&
            !Appodeal.isLoaded(Appodeal.BANNER) && lastBannerCache != 0L && now - lastBannerCache < 60_000) {
            // A return during cooldown gets one deferred request, not an endless retry loop.
            handler.removeCallbacks(bannerRetry)
            handler.postDelayed(bannerRetry, 60_000 - (now - lastBannerCache))
        }
    }

    private fun showBanner() {
        val activity = foreground.get()?.takeIf(::active) ?: return
        val slot = activity.findViewById<View>(R.id.banner_container) ?: return
        trace("Banner check: widthDp=${slot.width / activity.resources.displayMetrics.density}; loaded=${Appodeal.isLoaded(Appodeal.BANNER)}; initialized=$initialized; obscured=$bannerObscured")
        // A fixed slot prevents layout jumps when the ad arrives.
        if (Appodeal.isInitialized(Appodeal.BANNER) && !showing && !bannerObscured && !bannerVisible &&
            slot.width / activity.resources.displayMetrics.density >= 320 && enabled(activity, "banner") &&
            ConsentManager.canShowAds() && slot.isShown && Appodeal.isLoaded(Appodeal.BANNER)) {
            bannerVisible = Appodeal.show(activity, Appodeal.BANNER_VIEW)
        }
    }

    fun navigateWithoutAd(fragment: Fragment, action: () -> Unit) {
        if (!fragment.isAdded || !fragment.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
            fragment.parentFragmentManager.isStateSaved || showing) return
        interactions++
        action()
    }

    /** Only an explicit return from podcast details may call this. */
    fun onDetailsReturn(activity: FragmentActivity, playbackIdle: Boolean, action: () -> Unit) {
        if (showing) return
        interactions++
        val now = System.currentTimeMillis()
        val prefs = activity.getSharedPreferences(if (BuildConfig.DEBUG) "AppodealTestFrequency" else "AppodealFrequency", Context.MODE_PRIVATE)
        val last = prefs.getLong("last_interstitial_attempt", 0L)
        val eligible = active(activity) && playbackIdle && Appodeal.isInitialized(Appodeal.INTERSTITIAL) &&
            enabled(activity, "interstitial") && ConsentManager.canShowAds() &&
            interactions >= requiredInteractions && SystemClock.elapsedRealtime() - sessionStarted >= gap &&
            (last == 0L || (now >= last && now - last >= gap))
        trace("Interstitial check: eligible=$eligible; playbackIdle=$playbackIdle; interactions=$interactions; loaded=${Appodeal.isLoaded(Appodeal.INTERSTITIAL)}")
        if (!eligible || !Appodeal.isLoaded(Appodeal.INTERSTITIAL) || !Appodeal.canShow(Appodeal.INTERSTITIAL)) {
            action()
            refresh()
            return
        }
        // Count attempts too: failures must not cause rapid repeat shows.
        prefs.edit().putLong("last_interstitial_attempt", now).apply()
        interactions = 0
        showing = true
        pending = action
        try {
            if (!Appodeal.show(activity, Appodeal.INTERSTITIAL)) finishReturn()
        } catch (e: RuntimeException) {
            Log.e("AvapodAds", "Interstitial display failed; continuing navigation", e)
            finishReturn()
        }
    }

    private fun finishReturn() {
        showing = false
        val action = pending
        pending = null
        action?.invoke()
    }

    fun showPrivacyOptions(activity: FragmentActivity) {
        if (!initialized) {
            Toast.makeText(activity, R.string.ads_privacy_unavailable, Toast.LENGTH_LONG).show()
            refresh()
            return
        }
        ConsentManager.showPrivacyOptionsForm(activity) { error ->
            if (error != null) Toast.makeText(activity, R.string.ads_privacy_unavailable, Toast.LENGTH_LONG).show()
            refresh()
        }
    }
}
