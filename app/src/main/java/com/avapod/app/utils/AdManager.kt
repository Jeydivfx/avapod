package com.avapod.app.utils

import android.app.Activity
import android.content.Context
import android.util.DisplayMetrics
import android.widget.FrameLayout
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
    private var mInterstitialAd: InterstitialAd? = null
    private var isAdLoading = false
    private var actionCounter = 0
    private var lastShownTime: Long = 0
    private const val ACTIONS_REQUIRED = 3
    private const val TIME_INTERVAL_REQUIRED = 2 * 60 * 1000
    private var mRewardedAd: RewardedAd? = null
    private var isRewardedLoading = false

    fun preloadInterstitialAd(context: Context) {
        val dbHelper = DatabaseHelper(context)
        if (!dbHelper.isAdEnabled("interstitial")) return

        if (mInterstitialAd != null || isAdLoading) return

        isAdLoading = true
        val adRequest = AdRequest.Builder().build()
        val adUnitId = context.getString(R.string.admob_interstitial_ad_unit_id)

        InterstitialAd.load(context, adUnitId, adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    mInterstitialAd = interstitialAd
                    isAdLoading = false
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    mInterstitialAd = null
                    isAdLoading = false
                }
            })
    }

    fun showAdWithCapping(activity: Activity, onAdClosedAction: () -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) {
            onAdClosedAction()
            return
        }

        val dbHelper = DatabaseHelper(activity)
        if (!dbHelper.isAdEnabled("interstitial")) {
            onAdClosedAction()
            return
        }

        actionCounter++
        val currentTime = System.currentTimeMillis()
        val timePassed = currentTime - lastShownTime
        val isAdReady = mInterstitialAd != null

        if (isAdReady && actionCounter >= ACTIONS_REQUIRED && timePassed >= TIME_INTERVAL_REQUIRED) {
            mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    resetCounters(currentTime)
                    preloadInterstitialAd(activity)
                    onAdClosedAction()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    mInterstitialAd = null
                    preloadInterstitialAd(activity)
                    onAdClosedAction()
                }
            }
            mInterstitialAd?.show(activity)
        } else {
            onAdClosedAction()
        }
    }

    private fun resetCounters(currentTime: Long) {
        mInterstitialAd = null
        actionCounter = 0
        lastShownTime = currentTime
    }

    fun initAndLoadBanner(activity: Activity, container: FrameLayout): AdView? {

        val dbHelper = DatabaseHelper(activity)
        if (!dbHelper.isAdEnabled("banner")) {
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

        val dbHelper = DatabaseHelper(context)
        if (!dbHelper.isAdEnabled("rewarded")) return

        if (mRewardedAd != null || isRewardedLoading) return

        isRewardedLoading = true
        val adRequest = AdRequest.Builder().build()
        val adUnitId = context.getString(R.string.admob_rewarded_ad_unit_id)

        RewardedAd.load(context, adUnitId, adRequest, object : RewardedAdLoadCallback() {
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

        val dbHelper = DatabaseHelper(activity)
        if (!dbHelper.isAdEnabled("rewarded")) {
            onRewardEarned()
            return
        }

        if (mRewardedAd != null) {
            mRewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    mRewardedAd = null
                    preloadRewardedAd(activity)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    mRewardedAd = null
                    preloadRewardedAd(activity)
                    onRewardEarned()
                }
            }

            mRewardedAd?.show(activity) {
                onRewardEarned()
            }
        } else {
            preloadRewardedAd(activity)
            onRewardEarned()
        }
    }
}