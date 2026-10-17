package com.avapod.app.utils

import android.app.Activity
import android.content.Context
import android.util.DisplayMetrics
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.avapod.app.R
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object AdManager {
    private fun isEnabled(context: Context, kind: String): Boolean =
        context.applicationContext.getSharedPreferences("AvapodAdConfig", Context.MODE_PRIVATE).getBoolean(kind, false)

    private var mInterstitialAd: InterstitialAd? = null
    private var isAdLoading = false
    private var isShowing = false
    private var actionCounter = 0
    private var lastShownTime: Long = 0
    private const val ACTIONS_REQUIRED = 3
    private const val TIME_INTERVAL_REQUIRED = 2 * 60 * 1000
    private var mRewardedAd: RewardedAd? = null
    private var isRewardedLoading = false

    fun preloadInterstitialAd(context: Context) {
        if (!isEnabled(context, "interstitial")) return

        if (mInterstitialAd != null || isAdLoading) return

        isAdLoading = true
        val adRequest = AdRequest.Builder().build()
        val adUnitId = context.getString(R.string.admob_interstitial_ad_unit_id)

        InterstitialAd.load(context.applicationContext, adUnitId, adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    mInterstitialAd = interstitialAd
                    isAdLoading = false
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    mInterstitialAd = null
                    isAdLoading = false
                    android.util.Log.w("AvapodAds", "Interstitial load failed: ${adError.code} (${adError.domain}): ${adError.message}")
                }
            })
    }

    fun showAdWithCapping(fragment: Fragment, action: () -> Unit) {
        val activity = fragment.activity ?: return
        val owner = fragment.viewLifecycleOwnerLiveData.value ?: return
        var delivered = false
        val deliverAction: () -> Unit = {
            if (!delivered && owner.lifecycle.currentState != Lifecycle.State.DESTROYED) {
                delivered = true
                // Never navigate inside FragmentManager's ON_RESUME dispatch.
                val handler = Handler(Looper.getMainLooper())
                val observer = object : DefaultLifecycleObserver {
                    private val resumeAction = Runnable {
                        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                            owner.lifecycle.removeObserver(this)
                            if (fragment.isAdded && fragment.viewLifecycleOwnerLiveData.value === owner &&
                                !activity.isFinishing && !activity.isDestroyed &&
                                !fragment.parentFragmentManager.isStateSaved) action()
                        }
                    }
                    override fun onResume(owner: LifecycleOwner) {
                        handler.removeCallbacks(resumeAction)
                        handler.post(resumeAction)
                    }
                    override fun onDestroy(owner: LifecycleOwner) {
                        handler.removeCallbacks(resumeAction)
                        owner.lifecycle.removeObserver(this)
                    }
                }
                owner.lifecycle.addObserver(observer)
            }
        }
        showAdWithCapping(activity, deliverAction)
    }

    private fun showAdWithCapping(activity: Activity, onAdClosedAction: () -> Unit) {
        if (isShowing) return
        if (activity.isFinishing || activity.isDestroyed) {
            return
        }

        if (!isEnabled(activity, "interstitial")) {
            onAdClosedAction()
            return
        }

        actionCounter++
        val currentTime = System.currentTimeMillis()
        val timePassed = currentTime - lastShownTime
        val isAdReady = mInterstitialAd != null

        if (isAdReady && actionCounter >= ACTIONS_REQUIRED && timePassed >= TIME_INTERVAL_REQUIRED) {
            val ad = mInterstitialAd ?: return
            mInterstitialAd = null
            isShowing = true
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    ad.fullScreenContentCallback = null
                    isShowing = false
                    resetCounters(currentTime)
                    preloadInterstitialAd(activity.applicationContext)
                    onAdClosedAction()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    ad.fullScreenContentCallback = null
                    isShowing = false
                    mInterstitialAd = null
                    preloadInterstitialAd(activity.applicationContext)
                    onAdClosedAction()
                }
            }
            ad.show(activity)
        } else {
            // Retry loading on a later user action after a network/no-fill failure.
            // Never delay navigation or show an ad asynchronously after this action.
            if (!isAdReady) preloadInterstitialAd(activity.applicationContext)
            onAdClosedAction()
        }
    }

    private fun resetCounters(currentTime: Long) {
        mInterstitialAd = null
        actionCounter = 0
        lastShownTime = currentTime
    }

    fun initAndLoadBanner(activity: Activity, container: FrameLayout): AdView? {

        if (!isEnabled(activity, "banner")) {
            container.removeAllViews()
            return null
        }

        val adView = AdView(activity)
        adView.adUnitId = activity.getString(R.string.admob_banner_ad_unit_id)

        container.removeAllViews()
        container.addView(adView)

        val adRequest = AdRequest.Builder().build()
        val adSize = getAdaptiveAdSize(activity, container)

        adView.setAdSize(adSize)
        adView.loadAd(adRequest)

        return adView
    }

    private fun getAdaptiveAdSize(activity: Activity, container: FrameLayout): AdSize {
        val outMetrics = DisplayMetrics()
        activity.windowManager.defaultDisplay.getMetrics(outMetrics)

        val density = outMetrics.density
        var adWidthPixels = container.width.toFloat()

        if (adWidthPixels == 0f) {
            adWidthPixels = outMetrics.widthPixels.toFloat()
        }

        val adWidth = (adWidthPixels / density).toInt()
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth)
    }

    fun preloadRewardedAd(context: Context) {

        if (!isEnabled(context, "rewarded")) return

        if (mRewardedAd != null || isRewardedLoading) return

        isRewardedLoading = true
        val adRequest = AdRequest.Builder().build()
        val adUnitId = context.getString(R.string.admob_rewarded_ad_unit_id)

        RewardedAd.load(context.applicationContext, adUnitId, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdLoaded(rewardedAd: RewardedAd) {
                mRewardedAd = rewardedAd
                isRewardedLoading = false
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                mRewardedAd = null
                isRewardedLoading = false
            }
        })
    }

    fun showRewardedAd(activity: Activity, onRewardEarned: () -> Unit) {

        if (activity.isFinishing || activity.isDestroyed) {
            return
        }

        if (!isEnabled(activity, "rewarded")) {
            onRewardEarned()
            return
        }

        if (mRewardedAd != null) {
            mRewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    mRewardedAd = null
                    preloadRewardedAd(activity.applicationContext)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    mRewardedAd = null
                    preloadRewardedAd(activity.applicationContext)
                    onRewardEarned()
                }
            }

            mRewardedAd?.show(activity) {
                onRewardEarned()
            }
        } else {
            preloadRewardedAd(activity.applicationContext)
            onRewardEarned()
        }
    }
}
